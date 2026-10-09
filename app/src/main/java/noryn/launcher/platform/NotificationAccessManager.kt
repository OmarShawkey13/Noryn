package noryn.launcher.platform

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import noryn.launcher.notifications.NorynNotificationListenerService

class NotificationAccessManager(context: Context) {
    private val appContext = context.applicationContext
    private val component = ComponentName(appContext, NorynNotificationListenerService::class.java)

    fun isAccessGranted(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            appContext.getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(component) == true
        } else {
            Settings.Secure.getString(appContext.contentResolver, "enabled_notification_listeners")
                .orEmpty()
                .split(':')
                .mapNotNull(ComponentName::unflattenFromString)
                .contains(component)
        }
    }.getOrDefault(false)

    fun settingsIntent() = android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
