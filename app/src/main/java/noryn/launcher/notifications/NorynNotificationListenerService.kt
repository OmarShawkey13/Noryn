package noryn.launcher.notifications

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import noryn.launcher.media.MediaPlaybackRepository

class NorynNotificationListenerService : NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        NotificationRepository.setLauncherPackageName(packageName)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val active = runCatching { activeNotifications }.getOrDefault(emptyArray())
        NotificationRepository.onConnected(this, active)
        NotificationRepository.updateSilence(this)
        MediaPlaybackRepository.onListenerConnected(this)
    }

    override fun onListenerDisconnected() {
        MediaPlaybackRepository.onListenerDisconnected(this)
        NotificationRepository.onDisconnected(this)
        runCatching { requestRebind(ComponentName(this, NorynNotificationListenerService::class.java)) }
        super.onListenerDisconnected()
    }

    fun refreshNotifications() = NotificationRepository.refreshActive()

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val ranking = Ranking()
        val silent = runCatching {
            rankingMap.getRanking(sbn.key, ranking) &&
                (ranking.isAmbient || ranking.importance <= android.app.NotificationManager.IMPORTANCE_LOW)
        }.getOrDefault(false)
        NotificationRepository.onPosted(sbn, silent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        NotificationRepository.onRemoved(sbn.key)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        NotificationRepository.updateSilence(this)
    }

    override fun onDestroy() {
        MediaPlaybackRepository.onListenerDisconnected(this)
        NotificationRepository.onDisconnected(this)
        super.onDestroy()
    }
}
