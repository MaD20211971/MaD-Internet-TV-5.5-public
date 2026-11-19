package com.example.androidtvlight.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.media.ImageReader
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Persistent offscreen hardware buffer consumer ([ImageReader] with [ImageFormat.PRIVATE])
 * on a dedicated background [HandlerThread].
 *
 * Why this solves the 10-second stop, the screen-off stutter, AND avoids EGL_BAD_ATTRIBUTE:
 * 1) Disabling C.TRACK_TYPE_VIDEO on screen-off caused HlsSampleStreamWrapper to reset its sample
 *    queues and re-buffer over HTTP (~1s audio stutter).
 * 2) Passing null when SurfaceView is destroyed on screen-off causes MediaCodec teardown or buffer
 *    stalls after 10 seconds.
 * 3) Hot-swapping MediaCodec.setOutputSurface() to this EGL-free hardware ImageReader surface when
 *    SurfaceView is destroyed keeps the hardware video decoder running without resetting tracks,
 *    without flushing the audio buffer, and without creating an EGLContext/SurfaceTexture that
 *    triggers `eglQueryContext 32c0 EGL_BAD_ATTRIBUTE` on Android emulators.
 */
private class OffscreenVideoDrainSurface {
    private val handlerThread = HandlerThread("TvOffscreenVideoDrain").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private var imageReader: ImageReader? = null

    @Volatile
    var surface: Surface? = null
        private set

    init {
        val latch = CountDownLatch(1)
        handler.post {
            runCatching {
                val reader = ImageReader.newInstance(64, 64, ImageFormat.PRIVATE, 3).apply {
                    setOnImageAvailableListener({ ir ->
                        runCatching {
                            ir.acquireLatestImage()?.close()
                        }
                    }, handler)
                }
                imageReader = reader
                surface = reader.surface
            }
            latch.countDown()
        }
        runCatching { latch.await(1_000L, TimeUnit.MILLISECONDS) }
    }

    fun release() {
        handler.post {
            runCatching {
                surface = null
                imageReader?.close()
                imageReader = null
            }
            runCatching {
                handlerThread.quitSafely()
            }
        }
    }
}

@OptIn(UnstableApi::class)
object TvPlayerManager {
    @Volatile
    private var exoPlayerInstance: ExoPlayer? = null

    @Volatile
    private var trackSelectorInstance: DefaultTrackSelector? = null

    @Volatile
    private var offscreenDrain: OffscreenVideoDrainSurface? = null

    @Volatile
    private var activeScreenSurface: Surface? = null

    @Volatile
    var isUserPlaying: Boolean = true

    fun getTrackSelector(context: Context): DefaultTrackSelector {
        return trackSelectorInstance ?: synchronized(this) {
            trackSelectorInstance ?: DefaultTrackSelector(context.applicationContext).apply {
                setParameters(buildUponParameters().clearVideoSizeConstraints())
            }.also { trackSelectorInstance = it }
        }
    }

    private fun getOrCreateOffscreenSurface(): Surface? {
        val existing = offscreenDrain?.surface
        if (existing != null && existing.isValid) {
            return existing
        }
        return synchronized(this) {
            val current = offscreenDrain?.surface
            if (current != null && current.isValid) {
                current
            } else {
                offscreenDrain?.release()
                OffscreenVideoDrainSurface().also { offscreenDrain = it }.surface
            }
        }
    }

    fun getOrCreatePlayer(context: Context): ExoPlayer {
        return exoPlayerInstance ?: synchronized(this) {
            exoPlayerInstance ?: buildExoPlayer(context.applicationContext).also { player ->
                exoPlayerInstance = player
            }
        }
    }

    /**
     * Binds a [SurfaceView] to the shared [ExoPlayer] with zero-stutter hot-swapping:
     * - When the screen surface is created/available, video renders to the screen.
     * - When the display is turned off and Android destroys the [SurfaceView]'s surface,
     *   we immediately hot-swap ExoPlayer's output surface to the offscreen [ImageReader] drain
     *   instead of passing null or resetting tracks. This eliminates any audio stutter on screen-off.
     */
    fun bindSurfaceView(context: Context, surfaceView: SurfaceView) {
        val player = getOrCreatePlayer(context)
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val s = holder.surface
                if (s != null && s.isValid) {
                    activeScreenSurface = s
                    player.setVideoSurface(s)
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                val s = holder.surface
                if (s != null && s.isValid && activeScreenSurface !== s) {
                    activeScreenSurface = s
                    player.setVideoSurface(s)
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (activeScreenSurface == holder.surface || activeScreenSurface == null) {
                    activeScreenSurface = null
                    val drainSurface = getOrCreateOffscreenSurface()
                    if (drainSurface != null && drainSurface.isValid) {
                        player.setVideoSurface(drainSurface)
                    }
                }
            }
        }
        surfaceView.holder.addCallback(callback)
        val currentSurface = surfaceView.holder.surface
        if (currentSurface != null && currentSurface.isValid) {
            activeScreenSurface = currentSurface
            player.setVideoSurface(currentSurface)
        }
    }

    /**
     * Switches to the offscreen drain surface if no valid screen surface is currently active.
     * Never resets TrackSelectionParameters so the HLS audio buffer never stutters.
     */
    fun onScreenTurnedOff(context: Context) {
        val player = getOrCreatePlayer(context)
        val currentScreen = activeScreenSurface
        if (currentScreen == null || !currentScreen.isValid) {
            getOrCreateOffscreenSurface()?.let { drainSurface ->
                if (drainSurface.isValid) {
                    player.setVideoSurface(drainSurface)
                }
            }
        }
        if (isUserPlaying && !player.playWhenReady) {
            player.playWhenReady = true
            player.play()
        }
    }

    fun ensureForegroundServiceStarted(context: Context) {
        runCatching {
            val appCtx = context.applicationContext
            val serviceIntent = Intent(appCtx, TvMediaPlaybackService::class.java)
            ContextCompat.startForegroundService(appCtx, serviceIntent)
        }
    }

    fun shutdownAndRelease(context: Context) {
        isUserPlaying = false
        runCatching {
            exoPlayerInstance?.apply {
                playWhenReady = false
                stop()
                clearMediaItems()
                release()
            }
            exoPlayerInstance = null
        }
        runCatching {
            activeScreenSurface = null
            offscreenDrain?.release()
            offscreenDrain = null
        }
        runCatching {
            val appCtx = context.applicationContext
            appCtx.stopService(Intent(appCtx, TvMediaPlaybackService::class.java))
        }
    }

    private fun buildExoPlayer(appContext: Context): ExoPlayer {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://www.2ix2.com/",
                    "Origin" to "https://www.2ix2.com"
                )
            )
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                25_000,
                75_000,
                1_200,
                2_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(appContext)
            .setTrackSelector(getTrackSelector(appContext))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }
}

@OptIn(UnstableApi::class)
class TvMediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    acquireStandbyLocks()
                    TvPlayerManager.onScreenTurnedOff(applicationContext)
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    acquireStandbyLocks()
                }
            }
        }
    }

    private val keepAlivePlayerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = TvPlayerManager.getOrCreatePlayer(applicationContext)
            if (TvPlayerManager.isUserPlaying) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (!player.isPlaying) {
                            player.playWhenReady = true
                            player.play()
                        }
                    }
                    Player.STATE_ENDED, Player.STATE_IDLE -> {
                        if (player.mediaItemCount > 0) {
                            player.playWhenReady = true
                            player.seekToDefaultPosition()
                            player.prepare()
                            player.play()
                        }
                    }
                }
            }
            updateForegroundNotification()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val player = TvPlayerManager.getOrCreatePlayer(applicationContext)
            if (!playWhenReady && TvPlayerManager.isUserPlaying &&
                reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            ) {
                player.playWhenReady = true
                player.play()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val player = TvPlayerManager.getOrCreatePlayer(applicationContext)
            if (TvPlayerManager.isUserPlaying && player.mediaItemCount > 0) {
                serviceScope.launch {
                    delay(1_200L)
                    player.playWhenReady = true
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireStandbyLocks()
        promoteToForeground()

        val player = TvPlayerManager.getOrCreatePlayer(applicationContext)
        player.addListener(keepAlivePlayerListener)

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()

        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenStateReceiver, filter)
            }
        }

        startWatchdogLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        acquireStandbyLocks()
        promoteToForeground()
        return START_STICKY
    }

    private fun startWatchdogLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(3_000L)
                if (TvPlayerManager.isUserPlaying) {
                    acquireStandbyLocks()
                    val player = TvPlayerManager.getOrCreatePlayer(applicationContext)
                    if (player.mediaItemCount > 0) {
                        if (!player.playWhenReady) {
                            player.playWhenReady = true
                        }
                        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                            player.seekToDefaultPosition()
                            player.prepare()
                            player.play()
                        } else if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                            player.play()
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Android TV Light Live-Wiedergabe",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hält den TV-Live-Stream bei ausgeschaltetem Display aktiv"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val player = TvPlayerManager.getOrCreatePlayer(applicationContext)
        val currentTitle = player.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: "Android TV Light • Live-TV"
        val currentSubtitle = player.currentMediaItem?.mediaMetadata?.subtitle?.toString()
            ?: "Hintergrundwiedergabe aktiv (Display aus unterstützt)"

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(currentTitle)
            .setContentText(currentSubtitle)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun promoteToForeground() {
        runCatching {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun updateForegroundNotification() {
        runCatching {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun acquireStandbyLocks() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null && (wakeLock == null || wakeLock?.isHeld == false)) {
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AndroidTvLight::StandbyPreventionWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null && (wifiLock == null || wifiLock?.isHeld == false)) {
                @Suppress("DEPRECATION")
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm.createWifiLock(mode, "AndroidTvLight::StandbyPreventionWifiLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        }
    }

    private fun releaseStandbyLocks() {
        runCatching {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !TvPlayerManager.isUserPlaying || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        runCatching { unregisterReceiver(screenStateReceiver) }
        runCatching {
            TvPlayerManager.getOrCreatePlayer(applicationContext)
                .removeListener(keepAlivePlayerListener)
        }
        releaseStandbyLocks()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "tv_light_live_playback_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
