package noryn.launcher.platform

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

class SuggestedAppsManager(context: Context) {
    private val appContext = context.applicationContext
    private val appOps = appContext.getSystemService(AppOpsManager::class.java)
    private val usageStats = appContext.getSystemService(UsageStatsManager::class.java)

    fun hasUsageAccess(): Boolean = runCatching {
        appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun recentPackages(limit: Int = 6): List<String> {
        if (!hasUsageAccess()) return emptyList()
        val now = System.currentTimeMillis()
        val recent = runCatching {
            usageStats?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - LOOKBACK_MILLIS,
                now,
            ).orEmpty()
        }.getOrDefault(emptyList())
        return recent.asSequence()
            .filter { it.packageName != appContext.packageName && it.lastTimeUsed > 0L }
            .groupBy { it.packageName }
            .mapNotNull { (packageName, usages) ->
                val lastUsed = usages.maxOfOrNull { it.lastTimeUsed } ?: return@mapNotNull null
                packageName to lastUsed
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    private companion object {
        const val LOOKBACK_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
