package noryn.launcher.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.app.NotificationManager
import android.os.UserManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference

/** Holds only small, in-memory notification models; no notification body is persisted or logged. */
object NotificationRepository {
    private const val TAG = "NorynNotifications"
    private const val MAX_TOTAL = 150
    private const val MAX_PER_APP = 12
    private const val MAX_TITLE = 96
    private const val MAX_TEXT = 220

    private val mutableSnapshot = MutableStateFlow(NotificationSnapshot())
    val snapshot: StateFlow<NotificationSnapshot> = mutableSnapshot.asStateFlow()

    @Volatile private var featureEnabled = false
    @Volatile private var allowedProfiles: Set<Long> = emptySet()
    @Volatile private var launcherPackageName: String? = null
    @Volatile private var listener = WeakReference<NorynNotificationListenerService>(null)

    fun setLauncherPackageName(packageName: String) {
        launcherPackageName = packageName
    }

    fun setFeatureEnabled(enabled: Boolean) {
        featureEnabled = enabled
        if (!enabled) clear() else refreshActive()
    }

    fun setAccessibleProfiles(serials: Set<Long>) {
        allowedProfiles = serials
        mutableSnapshot.update { snapshot ->
            snapshot.withEntries(snapshot.byApp.values.flatten().filter { it.profileSerial in serials })
        }
        refreshActive()
    }

    fun refreshActive() {
        val service = listener.get() ?: return
        val active = if (featureEnabled) runCatching { service.activeNotifications.orEmpty().mapNotNull(::entryFrom) }
            .getOrElse { emptyList() } else emptyList()
        mutableSnapshot.value = NotificationSnapshot(groupByApp(active), connected = true)
        updateSilence(service)
    }

    fun onConnected(service: NorynNotificationListenerService, notifications: Array<StatusBarNotification>) {
        listener = WeakReference(service)
        val active = if (featureEnabled) notifications.mapNotNull(::entryFrom) else emptyList()
        mutableSnapshot.value = NotificationSnapshot(groupByApp(active), connected = true)
    }

    fun onDisconnected(service: NorynNotificationListenerService) {
        if (listener.get() === service) listener.clear()
        mutableSnapshot.value = NotificationSnapshot(connected = false)
    }

    fun onPosted(notification: StatusBarNotification, silent: Boolean) {
        if (!featureEnabled) return
        val entry = entryFrom(notification, silent) ?: return
        mutableSnapshot.update { current ->
            val entries = current.byApp.values.flatten().filterNot { it.key == entry.key } + entry
            current.withEntries(entries)
        }
    }

    fun onRemoved(key: String) {
        mutableSnapshot.update { current ->
            current.withEntries(current.byApp.values.flatten().filterNot { it.key == key })
        }
    }

    fun updateSilence(service: NorynNotificationListenerService) {
        val ranking = runCatching { service.currentRanking }.getOrNull() ?: return
        mutableSnapshot.update { current ->
            current.withEntries(current.byApp.values.flatten().map { entry ->
                val itemRanking = NotificationListenerService.Ranking()
                if (runCatching { ranking.getRanking(entry.key, itemRanking) }.getOrDefault(false)) {
                    entry.copy(silent = itemRanking.isAmbient || itemRanking.importance <= NotificationManager.IMPORTANCE_LOW)
                } else {
                    entry
                }
            })
        }
    }

    fun dismiss(key: String): Boolean = runCatching {
        val service = listener.get() ?: return false
        service.cancelNotification(key)
        true
    }.onFailure { Log.w(TAG, "Could not dismiss a notification", it) }.getOrDefault(false)

    fun launchContent(context: Context, entry: NotificationEntry): Boolean = runCatching {
        val pendingIntent = entry.contentIntent ?: return false
        pendingIntent.send(context, 0, null)
        true
    }.onFailure { Log.w(TAG, "Could not open a notification target", it) }.getOrDefault(false)

    fun sendAction(context: Context, action: NotificationAction, reply: String? = null): Boolean = runCatching {
        val fillInIntent = Intent()
        if (!reply.isNullOrBlank()) {
            val textInputs = action.remoteInputs.filter { it.allowFreeFormInput }
            if (textInputs.isEmpty()) return false
            val results = Bundle().apply { textInputs.forEach { putCharSequence(it.resultKey, reply.take(MAX_TEXT)) } }
            RemoteInput.addResultsToIntent(action.remoteInputs.toTypedArray(), fillInIntent, results)
        }
        action.pendingIntent.send(context, 0, fillInIntent)
        true
    }.onFailure { Log.w(TAG, "Could not invoke a notification action", it) }.getOrDefault(false)

    fun clear() {
        mutableSnapshot.value = NotificationSnapshot(connected = mutableSnapshot.value.connected)
    }

    private fun entryFrom(sbn: StatusBarNotification, silent: Boolean = false): NotificationEntry? {
        if (sbn.packageName == launcherPackageName) return null
        val notification = sbn.notification ?: return null
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val userManager = listener.get()?.getSystemService(UserManager::class.java)
        val profileSerial = runCatching { userManager?.getSerialNumberForUser(sbn.user) ?: return null }
            .getOrNull() ?: return null
        if (profileSerial !in allowedProfiles) return null
        val extras = runCatching { notification.extras }.getOrNull()
        val title = safeText(extras, Notification.EXTRA_TITLE_BIG)
            ?: safeText(extras, Notification.EXTRA_TITLE)
            ?: ""
        val text = safeText(extras, Notification.EXTRA_BIG_TEXT)
            ?: safeInboxLines(extras)
            ?: safeMessagingLine(extras)
            ?: safeText(extras, Notification.EXTRA_TEXT)
            ?: ""
        val actions = notification.actions.orEmpty()
            .mapNotNull { action ->
                val label = runCatching { action.title?.toString()?.trim().orEmpty() }.getOrDefault("")
                val intent = action.actionIntent ?: return@mapNotNull null
                if (label.isBlank()) null else NotificationAction(
                    label = label.take(48),
                    pendingIntent = intent,
                    remoteInputs = action.remoteInputs.orEmpty().toList(),
                )
            }
            .take(3)
        val category = notification.category
        val countable = category !in setOf(
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_TRANSPORT,
        ) && !(notification.flags and Notification.FLAG_ONGOING_EVENT != 0 && category == Notification.CATEGORY_SYSTEM)
        return NotificationEntry(
            key = sbn.key,
            packageName = sbn.packageName,
            profileSerial = profileSerial,
            title = title.take(MAX_TITLE),
            text = text.take(MAX_TEXT),
            postedAtMillis = sbn.postTime,
            silent = silent,
            countable = countable,
            contentIntent = notification.contentIntent,
            actions = actions,
        )
    }

    private fun safeText(extras: Bundle?, key: String): String? = runCatching {
        extras?.getCharSequence(key)?.toString()?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()

    private fun safeInboxLines(extras: Bundle?): String? = runCatching {
        extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
            ?.takeLast(2)
            ?.joinToString(" · ")
            ?.takeIf(String::isNotBlank)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun safeMessagingLine(extras: Bundle?): String? = runCatching {
        extras?.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.asSequence()
            ?.mapNotNull { it as? Bundle }
            ?.mapNotNull { message -> message.getCharSequence("text")?.toString()?.trim() }
            ?.lastOrNull(String::isNotBlank)
    }.getOrNull()

    private fun groupByApp(entries: List<NotificationEntry>): Map<String, List<NotificationEntry>> =
        entries.asSequence()
            .groupBy(NotificationEntry::appKey)
            .mapValues { (_, appEntries) -> appEntries.sortedByDescending(NotificationEntry::postedAtMillis).take(MAX_PER_APP) }

    private fun NotificationSnapshot.withEntries(entries: List<NotificationEntry>): NotificationSnapshot =
        copy(byApp = groupByApp(entries).entries
            .sortedByDescending { (_, values) -> values.maxOfOrNull(NotificationEntry::postedAtMillis) ?: 0L }
            .take(MAX_TOTAL)
            .associate { it.key to it.value })
}
