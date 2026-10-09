package noryn.launcher.notifications

import android.app.PendingIntent
import android.app.RemoteInput

data class NotificationAction(
    val label: String,
    val pendingIntent: PendingIntent,
    val remoteInputs: List<RemoteInput>,
) {
    val supportsTextReply: Boolean get() = remoteInputs.any { it.allowFreeFormInput }
}

data class NotificationEntry(
    val key: String,
    val packageName: String,
    val profileSerial: Long,
    val title: String,
    val text: String,
    val postedAtMillis: Long,
    val silent: Boolean,
    val countable: Boolean,
    val contentIntent: PendingIntent?,
    val actions: List<NotificationAction>,
) {
    val appKey: String get() = "$profileSerial:$packageName"
}

data class NotificationSnapshot(
    val byApp: Map<String, List<NotificationEntry>> = emptyMap(),
    val connected: Boolean = false,
) {
    fun forApp(profileSerial: Long, packageName: String): List<NotificationEntry> =
        byApp["$profileSerial:$packageName"].orEmpty()

    fun visibleForApp(profileSerial: Long, packageName: String, showSilent: Boolean): List<NotificationEntry> =
        forApp(profileSerial, packageName).filter { it.countable && (showSilent || !it.silent) }
}
