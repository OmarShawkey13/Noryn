package noryn.launcher.platform

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

class DefaultLauncherManager(private val context: Context) {
    fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun createRequestIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }

        val candidates = buildList {
            add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Intent(Settings.ACTION_HOME_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        return candidates.firstOrNull { it.resolveActivity(context.packageManager) != null }
    }
}
