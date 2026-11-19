package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R

class StreamPlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var currentTitle: String = "MaD IPTV public"
    private var currentSubtitle: String = "Live Stream aktiv"
    private var isPlaying: Boolean = true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
        // Immediately promote to foreground in onCreate to satisfy Android's 5s requirement
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                currentTitle = intent?.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentSubtitle = intent?.getStringExtra(EXTRA_SUBTITLE) ?: currentSubtitle
                isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, true) ?: true
                startForegroundWithNotification()
            }
            ACTION_UPDATE -> {
                currentTitle = intent?.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentSubtitle = intent?.getStringExtra(EXTRA_SUBTITLE) ?: currentSubtitle
                isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying) ?: isPlaying
                val notification = buildNotification()
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_TOGGLE_PLAY -> {
                playerControlCallback?.invoke(PlayerAction.TOGGLE_PLAY)
            }
            ACTION_PLAY -> {
                playerControlCallback?.invoke(PlayerAction.PLAY)
            }
            ACTION_PAUSE -> {
                playerControlCallback?.invoke(PlayerAction.PAUSE)
            }
            ACTION_STOP -> {
                playerControlCallback?.invoke(PlayerAction.STOP)
                stopForegroundAndService()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        try {
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
        } catch (e: Throwable) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            // Stop self immediately if foreground promotion is rejected to prevent Android RemoteServiceException
            try {
                stopForegroundAndService()
            } catch (_: Throwable) {}
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play/Pause Action Intent
        val toggleIntent = Intent(this, StreamPlaybackService::class.java).apply {
            action = ACTION_TOGGLE_PLAY
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Action Intent
        val stopIntent = Intent(this, StreamPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseTitle = if (isPlaying) "Pause" else "Abspielen"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentSubtitle)
            .setSubText("IPTV Hintergrunddienst")
            .setSmallIcon(R.drawable.ic_tv_notification)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(playPauseIcon, playPauseTitle, togglePendingIntent)
            .addAction(R.drawable.ic_stop, "Stopp", stopPendingIntent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$currentSubtitle\n(Android 16 Standby-Schutz aktiv)")
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "IPTV Stream Wiedergabe",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hält den Stream im Hintergrund aktiv und verhindert Standby-Abbrüche auf Android 14/15/16."
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MaDInternetTV:PlaybackServiceWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(6 * 60 * 60 * 1000L) // 6 hours
                }
            }

            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm.createWifiLock(mode, "MaDInternetTV:WifiLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring locks", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null

            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing locks", e)
        }
    }

    private fun stopForegroundAndService() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    enum class PlayerAction {
        PLAY,
        PAUSE,
        TOGGLE_PLAY,
        STOP
    }

    companion object {
        private const val TAG = "StreamPlaybackService"
        const val CHANNEL_ID = "mad_iptv_privat_stream_playback_v1"
        const val NOTIFICATION_ID = 4091

        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_UPDATE = "com.example.service.action.UPDATE"
        const val ACTION_PLAY = "com.example.service.action.PLAY"
        const val ACTION_PAUSE = "com.example.service.action.PAUSE"
        const val ACTION_TOGGLE_PLAY = "com.example.service.action.TOGGLE_PLAY"
        const val ACTION_STOP = "com.example.service.action.STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        var playerControlCallback: ((PlayerAction) -> Unit)? = null

        fun start(context: Context, title: String, subtitle: String = "Live Stream aktiv", isPlaying: Boolean = true) {
            try {
                val intent = Intent(context, StreamPlaybackService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_SUBTITLE, subtitle)
                    putExtra(EXTRA_IS_PLAYING, isPlaying)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start StreamPlaybackService", e)
            }
        }

        fun update(context: Context, title: String, subtitle: String, isPlaying: Boolean) {
            try {
                val intent = Intent(context, StreamPlaybackService::class.java).apply {
                    action = ACTION_UPDATE
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_SUBTITLE, subtitle)
                    putExtra(EXTRA_IS_PLAYING, isPlaying)
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update StreamPlaybackService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, StreamPlaybackService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop StreamPlaybackService", e)
            }
        }
    }
}
