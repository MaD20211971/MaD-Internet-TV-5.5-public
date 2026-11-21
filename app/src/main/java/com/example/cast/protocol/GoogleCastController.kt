package com.example.cast.protocol

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.example.cast.model.CastDevice
import com.example.cast.model.CastMediaItem
import com.example.cast.model.CastProtocol
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import java.util.concurrent.Executors

/**
 * Offizielle Google Cast / Chromecast Integration über Google Play Services Cast Framework + MediaRouter.
 * Arbeitet vollständig crash-sicher (auch auf Geräten ohne Google Play Services, z. B. Fire TV).
 */
class GoogleCastController(
    private val context: Context,
    private val onDevicesUpdated: (List<CastDevice>) -> Unit,
    private val onSessionConnected: (CastDevice) -> Unit,
    private val onSessionEnded: () -> Unit,
    private val onPlaybackStateChanged: (isPlaying: Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "GoogleCastController"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val initExecutor = Executors.newSingleThreadExecutor()

    private var castContext: CastContext? = null
    private var mediaRouter: MediaRouter? = null
    private var routeSelector: MediaRouteSelector? = null
    private var isDiscoveryActive = false

    private var pendingDevice: CastDevice? = null
    private var pendingMediaUrl: String? = null
    private var pendingMediaItem: CastMediaItem? = null

    private val routeCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            publishRoutes(router)
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            publishRoutes(router)
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            publishRoutes(router)
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            val device = pendingDevice ?: CastDevice(
                id = "gcast_${session.castDevice?.deviceId ?: sessionId}",
                name = session.castDevice?.friendlyName ?: "Google Cast",
                protocol = CastProtocol.GOOGLE_CAST,
                modelName = session.castDevice?.modelName ?: "Chromecast"
            )
            onSessionConnected(device)
            val url = pendingMediaUrl
            val item = pendingMediaItem
            if (!url.isNullOrBlank() && item != null) {
                loadMediaOnSession(session, url, item)
            }
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            onError("Google Cast Verbindung fehlgeschlagen (Code $error)")
            onSessionEnded()
        }

        override fun onSessionEnding(session: CastSession) = Unit

        override fun onSessionEnded(session: CastSession, error: Int) {
            onSessionEnded()
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            val device = pendingDevice ?: CastDevice(
                id = "gcast_${session.castDevice?.deviceId ?: session.sessionId ?: "resumed"}",
                name = session.castDevice?.friendlyName ?: "Google Cast",
                protocol = CastProtocol.GOOGLE_CAST,
                modelName = session.castDevice?.modelName ?: "Chromecast"
            )
            onSessionConnected(device)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            onSessionEnded()
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    fun initializeSafely() {
        mainHandler.post {
            try {
                if (mediaRouter == null) {
                    mediaRouter = MediaRouter.getInstance(context.applicationContext)
                }
                if (routeSelector == null) {
                    routeSelector = MediaRouteSelector.Builder()
                        .addControlCategory(
                            CastMediaControlIntent.categoryForCast(
                                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                            )
                        )
                        .build()
                }
                if (castContext == null) {
                    CastContext.getSharedInstance(context.applicationContext, initExecutor)
                        .addOnSuccessListener { ctx ->
                            castContext = ctx
                            runCatching {
                                ctx.sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
                                ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                            }
                        }
                        .addOnFailureListener { err ->
                            Log.w(TAG, "Google Cast SDK auf diesem Gerät nicht verfügbar: ${err.message}")
                        }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Google Cast Initialisierung übersprungen: ${t.message}")
            }
        }
    }

    fun startDiscovery() {
        initializeSafely()
        mainHandler.post {
            try {
                val router = mediaRouter ?: return@post
                val selector = routeSelector ?: return@post
                if (!isDiscoveryActive) {
                    router.addCallback(
                        selector,
                        routeCallback,
                        MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
                    )
                    isDiscoveryActive = true
                }
                publishRoutes(router)
            } catch (t: Throwable) {
                Log.w(TAG, "MediaRouter Scan Fehler: ${t.message}")
            }
        }
    }

    fun stopDiscovery() {
        mainHandler.post {
            try {
                if (isDiscoveryActive) {
                    mediaRouter?.removeCallback(routeCallback)
                    isDiscoveryActive = false
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun castMedia(device: CastDevice, streamUrl: String, media: CastMediaItem) {
        initializeSafely()
        mainHandler.post {
            try {
                pendingDevice = device
                pendingMediaUrl = streamUrl
                pendingMediaItem = media

                val activeSession = castContext?.sessionManager?.currentCastSession
                if (activeSession != null && activeSession.isConnected) {
                    onSessionConnected(device)
                    loadMediaOnSession(activeSession, streamUrl, media)
                    return@post
                }

                val router = mediaRouter
                val targetRoute = router?.routes?.firstOrNull { it.id == device.routeId }
                if (router != null && targetRoute != null) {
                    router.selectRoute(targetRoute)
                } else {
                    onError("Chromecast-Gerät '${device.name}' ist aktuell nicht mehr erreichbar.")
                }
            } catch (t: Throwable) {
                onError("Google Cast Start fehlgeschlagen: ${t.localizedMessage ?: "Unbekannter Fehler"}")
            }
        }
    }

    fun togglePlayPause(shouldPlay: Boolean) {
        mainHandler.post {
            runCatching {
                val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return@post
                if (shouldPlay) client.play() else client.pause()
                onPlaybackStateChanged(shouldPlay)
            }
        }
    }

    fun stopPlayback() {
        mainHandler.post {
            runCatching {
                castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.stop()
                onPlaybackStateChanged(false)
            }
        }
    }

    fun setVolume(volume: Float) {
        mainHandler.post {
            runCatching {
                castContext?.sessionManager?.currentCastSession?.volume = volume.coerceIn(0f, 1f).toDouble()
            }
        }
    }

    fun setMuted(muted: Boolean) {
        mainHandler.post {
            runCatching {
                castContext?.sessionManager?.currentCastSession?.isMute = muted
            }
        }
    }

    fun disconnect() {
        mainHandler.post {
            runCatching {
                castContext?.sessionManager?.endCurrentSession(true)
                mediaRouter?.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
            }
            pendingDevice = null
            pendingMediaUrl = null
            pendingMediaItem = null
        }
    }

    private fun loadMediaOnSession(session: CastSession, streamUrl: String, media: CastMediaItem) {
        val remoteClient = session.remoteMediaClient ?: run {
            onError("Google Cast Media-Client nicht bereit.")
            return
        }

        val metadataType = if (media.isAudioOnly) {
            MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
        } else {
            MediaMetadata.MEDIA_TYPE_MOVIE
        }
        val metadata = MediaMetadata(metadataType).apply {
            putString(MediaMetadata.KEY_TITLE, media.title)
            if (media.subtitle.isNotBlank()) {
                putString(MediaMetadata.KEY_SUBTITLE, media.subtitle)
            }
            if (media.logoUrl.startsWith("http://", ignoreCase = true) ||
                media.logoUrl.startsWith("https://", ignoreCase = true)
            ) {
                addImage(WebImage(Uri.parse(media.logoUrl)))
            }
        }

        val streamType = if (media.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED
        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setStreamType(streamType)
            .setContentType(media.inferredMimeType())
            .setMetadata(metadata)
            .build()

        val loadRequest = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .apply {
                if (!media.isLive && media.positionMs > 0L) {
                    setCurrentTime(media.positionMs)
                }
            }
            .build()

        remoteClient.load(loadRequest)
        onPlaybackStateChanged(true)
    }

    private fun publishRoutes(router: MediaRouter) {
        val selector = routeSelector ?: return
        val devices = router.routes
            .filter { route ->
                !route.isDefault &&
                    route.isEnabled &&
                    route.matchesSelector(selector)
            }
            .map { route ->
                CastDevice(
                    id = "gcast_${route.id}",
                    name = route.name.ifBlank { "Google Cast" },
                    protocol = CastProtocol.GOOGLE_CAST,
                    modelName = route.description?.takeIf { it.isNotBlank() } ?: "Google Cast / Chromecast",
                    routeId = route.id
                )
            }
        onDevicesUpdated(devices)
    }
}
