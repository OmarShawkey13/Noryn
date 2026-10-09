package noryn.launcher.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import noryn.launcher.core.model.LauncherApp
import java.text.Normalizer
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class LauncherAppsManager(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val densityDpi = appContext.resources.displayMetrics.densityDpi
    private val iconSizePx = (ICON_SIZE_DP * appContext.resources.displayMetrics.density).toInt()
    private val activityInfoById = ConcurrentHashMap<String, LauncherActivityInfo>()
    private val iconCache = object : LruCache<String, Bitmap>(ICON_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun observeApps(): Flow<List<LauncherApp>> = callbackFlow<Unit> {
        val callback = object : LauncherApps.Callback() {
            private fun refresh() {
                trySend(Unit)
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
            override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
            override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
            override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = refresh()
            override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = refresh()
            override fun onShortcutsChanged(packageName: String, shortcuts: List<android.content.pm.ShortcutInfo>, user: UserHandle) = Unit
        }

        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        trySend(Unit)

        awaitClose {
            launcherApps.unregisterCallback(callback)
        }
    }.buffer(Channel.CONFLATED).map {
        withContext(Dispatchers.IO) { loadApps() }
    }

    fun loadIcon(app: LauncherApp): Bitmap? {
        val cacheKey = "${app.id}:$densityDpi"
        synchronized(iconCache) { iconCache.get(cacheKey)?.let { return it } }

        return runCatching {
            val activityInfo = activityInfoById[app.id]
                ?: launcherApps.getActivityList(app.packageName, app.userHandle)
                    .firstOrNull { it.componentName.className == app.activityName }
                ?: return null
            val drawable = activityInfo.getBadgedIcon(densityDpi)
            drawable.toIconBitmap(iconSizePx).also { bitmap ->
                synchronized(iconCache) { iconCache.put(cacheKey, bitmap) }
            }
        }.onFailure { Log.w(TAG, "Could not load icon for ${app.id}", it) }.getOrNull()
    }

    fun launch(app: LauncherApp): Boolean = runCatching {
        launcherApps.startMainActivity(
            ComponentName(app.packageName, app.activityName),
            app.userHandle,
            null,
            null,
        )
        true
    }.onFailure { Log.w(TAG, "Could not launch ${app.id}", it) }.getOrDefault(false)

    fun openAppInfo(app: LauncherApp): Boolean = runCatching {
        launcherApps.startAppDetailsActivity(
            ComponentName(app.packageName, app.activityName),
            app.userHandle,
            null,
            null,
        )
        true
    }.onFailure { Log.w(TAG, "Could not open app details for ${app.id}", it) }.getOrDefault(false)

    private fun loadApps(): List<LauncherApp> {
        val profiles = runCatching { launcherApps.profiles }
            .onFailure { Log.w(TAG, "Could not list user profiles", it) }
            .getOrDefault(emptyList())
        val discovered = buildList {
            profiles.forEach { user ->
                val profileApps = runCatching {
                    val serial = userManager.getSerialNumberForUser(user)
                    launcherApps.getActivityList(null, user).map { info ->
                        val component = info.componentName
                        val label = info.label?.toString().orEmpty().ifBlank { component.packageName }
                        val app = LauncherApp(
                            packageName = component.packageName,
                            activityName = component.className,
                            label = label,
                            normalizedLabel = normalize(label),
                            userHandle = user,
                            userSerial = serial,
                        )
                        app to info
                    }
                }.onFailure { Log.w(TAG, "Could not enumerate apps for a profile", it) }
                    .getOrDefault(emptyList())
                addAll(profileApps)
            }
        }

        activityInfoById.clear()
        discovered.forEach { (app, info) -> activityInfoById[app.id] = info }
        synchronized(iconCache) { iconCache.evictAll() }
        val collator = Collator.getInstance(Locale.getDefault())
        return discovered.map { it.first }.sortedWith { left, right ->
            val labelOrder = collator.compare(left.label, right.label)
            if (labelOrder != 0) labelOrder else left.id.compareTo(right.id)
        }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .filterNot { character ->
            val type = Character.getType(character)
            type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
        }
        .trim()
        .lowercase(Locale.ROOT)

    private fun Drawable.toIconBitmap(size: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val previousBounds = bounds
        setBounds(0, 0, size, size)
        draw(canvas)
        bounds = previousBounds
        return bitmap
    }

    private companion object {
        const val TAG = "NorynLauncher"
        const val ICON_SIZE_DP = 48
        const val ICON_CACHE_KB = 8 * 1024
    }
}
