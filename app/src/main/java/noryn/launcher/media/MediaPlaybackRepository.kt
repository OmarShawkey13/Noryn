package noryn.launcher.media

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import noryn.launcher.notifications.NorynNotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import androidx.core.graphics.scale

data class MediaPlaybackSnapshot(
    val packageName: String,
    val appName: String,
    val title: String,
    val artist: String,
    val artwork: Bitmap?,
    val isPlaying: Boolean,
    val canTogglePlayback: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
)

/** Reads only the current active media session. Track data stays in memory. */
object MediaPlaybackRepository {
    private val mutableSnapshot = MutableStateFlow<MediaPlaybackSnapshot?>(null)
    val snapshot: StateFlow<MediaPlaybackSnapshot?> = mutableSnapshot.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var service = WeakReference<NorynNotificationListenerService>(null)
    private var manager: MediaSessionManager? = null
    private var selectedController: MediaController? = null
    private var activeListenerRegistered = false

    @Volatile
    private var featureEnabled = false

    private val notificationListener = ComponentName(
        "noryn.launcher",
        NorynNotificationListenerService::class.java.name,
    )

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateControllers(controllers.orEmpty())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refreshActiveSessions()

        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshActiveSessions()

        override fun onSessionDestroyed() = refreshActiveSessions()
    }

    fun setEnabled(enabled: Boolean) {
        featureEnabled = enabled
        if (!enabled) {
            stopListening(clearService = false)
            return
        }
        startListening()
    }

    fun onListenerConnected(listenerService: NorynNotificationListenerService) {
        service = WeakReference(listenerService)
        if (featureEnabled) startListening() else mutableSnapshot.value = null
    }

    fun onListenerDisconnected(listenerService: NorynNotificationListenerService) {
        if (service.get() !== listenerService) return
        stopListening(clearService = true)
    }

    fun togglePlayback() {
        val controller = selectedController ?: return
        val playback = mutableSnapshot.value ?: return
        runCatching {
            if (playback.isPlaying) controller.transportControls.pause()
            else controller.transportControls.play()
        }
    }

    fun skipPrevious() {
        runCatching { selectedController?.transportControls?.skipToPrevious() }
    }

    fun skipNext() {
        runCatching { selectedController?.transportControls?.skipToNext() }
    }

    fun openPlayer(): Boolean {
        val controller = selectedController ?: return false
        val listenerService = service.get() ?: return false
        val sessionActivity = controller.sessionActivity
        val opened = runCatching {
            sessionActivity?.send()
            sessionActivity != null
        }.getOrDefault(false)
        return opened || runCatching {
            val intent = listenerService.packageManager.getLaunchIntentForPackage(controller.packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            listenerService.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun startListening() {
        val listenerService = service.get() ?: return
        val sessionManager = listenerService.getSystemService(MediaSessionManager::class.java) ?: return
        manager = sessionManager
        if (!activeListenerRegistered) {
            val registered = runCatching {
                sessionManager.addOnActiveSessionsChangedListener(
                    activeSessionsListener,
                    notificationListener,
                    handler,
                )
            }.isSuccess
            if (!registered) {
                mutableSnapshot.value = null
                return
            }
            activeListenerRegistered = true
        }
        refreshActiveSessions()
    }

    private fun refreshActiveSessions() {
        if (!featureEnabled) return
        val sessionManager = manager ?: return startListening()
        val controllers = runCatching { sessionManager.getActiveSessions(notificationListener) }
            .getOrDefault(emptyList())
        updateControllers(controllers)
    }

    private fun updateControllers(controllers: List<MediaController>) {
        if (!featureEnabled) {
            mutableSnapshot.value = null
            return
        }

        val candidates = controllers.mapNotNull { controller ->
            runCatching { snapshotFor(controller) }.getOrNull()?.let { controller to it }
        }
        val selected = candidates.firstOrNull { it.second.isPlaying } ?: candidates.firstOrNull()
        val nextController = selected?.first
        if (selectedController?.sessionToken != nextController?.sessionToken) {
            runCatching { selectedController?.unregisterCallback(controllerCallback) }
            selectedController = nextController
            runCatching { nextController?.registerCallback(controllerCallback, handler) }
        }
        mutableSnapshot.value = selected?.second
    }

    private fun snapshotFor(controller: MediaController): MediaPlaybackSnapshot? {
        val metadata = controller.metadata ?: return null
        val playbackState = controller.playbackState ?: return null
        if (playbackState.state == PlaybackState.STATE_NONE ||
            playbackState.state == PlaybackState.STATE_STOPPED ||
            playbackState.state == PlaybackState.STATE_ERROR
        ) return null

        val description = metadata.description
        val title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?.takeIf(String::isNotBlank)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank)
            ?: description.title?.toString()?.takeIf(String::isNotBlank)
            ?: return null
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf(String::isNotBlank)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?.takeIf(String::isNotBlank)
            ?: description.subtitle?.toString()?.takeIf(String::isNotBlank)
            ?: applicationName(controller.packageName)
        val actions = playbackState.actions
        val isPlaying = playbackState.state == PlaybackState.STATE_PLAYING ||
            playbackState.state == PlaybackState.STATE_BUFFERING ||
            playbackState.state == PlaybackState.STATE_CONNECTING
        val playPauseAction = PlaybackState.ACTION_PLAY_PAUSE
        val canToggle = if (isPlaying) {
            (actions and (PlaybackState.ACTION_PAUSE or playPauseAction)) != 0L
        } else {
            (actions and (PlaybackState.ACTION_PLAY or playPauseAction)) != 0L
        }

        return MediaPlaybackSnapshot(
            packageName = controller.packageName,
            appName = applicationName(controller.packageName),
            title = title,
            artist = artist,
            artwork = artwork(metadata, description),
            isPlaying = isPlaying,
            canTogglePlayback = canToggle,
            canSkipPrevious = (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L,
            canSkipNext = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L,
        )
    }

    private fun artwork(metadata: MediaMetadata, description: android.media.MediaDescription?): Bitmap? {
        val image = sequenceOf(
            runCatching { metadata.getBitmap(MediaMetadata.METADATA_KEY_ART) }.getOrNull(),
            runCatching { metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) }.getOrNull(),
            runCatching { metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) }.getOrNull(),
            description?.iconBitmap,
        ).firstOrNull { it != null } ?: return null
        val largestSide = maxOf(image.width, image.height)
        if (largestSide <= MAX_ARTWORK_SIZE) return image
        val scale = MAX_ARTWORK_SIZE.toFloat() / largestSide
        return runCatching {
            image.scale(
                (image.width * scale).toInt().coerceAtLeast(1),
                (image.height * scale).toInt().coerceAtLeast(1),
            )
        }.getOrDefault(image)
    }

    private fun applicationName(packageName: String): String {
        val listenerService = service.get() ?: return packageName.substringAfterLast('.')
        return runCatching {
            listenerService.packageManager.getApplicationLabel(
                listenerService.packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
    }

    private fun stopListening(clearService: Boolean) {
        val sessionManager = manager
        if (activeListenerRegistered && sessionManager != null) {
            runCatching { sessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener) }
        }
        activeListenerRegistered = false
        runCatching { selectedController?.unregisterCallback(controllerCallback) }
        selectedController = null
        manager = null
        mutableSnapshot.value = null
        if (clearService) service.clear()
    }

    private const val MAX_ARTWORK_SIZE = 320
}
