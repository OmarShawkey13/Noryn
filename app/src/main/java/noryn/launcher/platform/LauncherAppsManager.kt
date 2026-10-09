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
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.util.LruCache
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import noryn.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import noryn.launcher.core.model.LauncherApp
import noryn.launcher.core.model.LauncherProfile
import noryn.launcher.core.model.LauncherProfileType
import noryn.launcher.core.model.IconPackInfo
import noryn.launcher.core.model.LauncherShortcut
import java.text.Normalizer
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

class LauncherAppsManager(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val iconPackManager = IconPackManager(appContext)
    private val densityDpi = appContext.resources.displayMetrics.densityDpi
    private val iconSizePx = (ICON_SIZE_DP * appContext.resources.displayMetrics.density).toInt()
    private val activityInfoById = ConcurrentHashMap<String, LauncherActivityInfo>()
    private val iconGeneration = AtomicLong()
    private val iconLoads = ConcurrentHashMap<String, CompletableFuture<Bitmap?>>()
    private val shortcutCache = object : LruCache<String, List<LauncherShortcut>>(SHORTCUT_CACHE_KB) {
        override fun sizeOf(key: String, value: List<LauncherShortcut>): Int =
            (value.sumOf { it.icon?.byteCount ?: 0 } / 1024).coerceAtLeast(1)
    }
    private val shortcutRevisionMutable = MutableStateFlow(0L)
    val shortcutRevision: StateFlow<Long> = shortcutRevisionMutable.asStateFlow()
    private val iconCache = object : LruCache<String, Bitmap>(ICON_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    data class Snapshot(val apps: List<LauncherApp>, val profiles: List<LauncherProfile>)

    fun observeApps(): Flow<Snapshot> = callbackFlow<Unit> {
        val callback = object : LauncherApps.Callback() {
            private fun refresh() {
                trySend(Unit)
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) { invalidateShortcuts(user, packageName); refresh() }
            override fun onPackageRemoved(packageName: String, user: UserHandle) { invalidateShortcuts(user, packageName); refresh() }
            override fun onPackageChanged(packageName: String, user: UserHandle) { invalidateShortcuts(user, packageName); refresh() }
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                packageNames.forEach { invalidateShortcuts(user, it) }
                refresh()
            }
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                packageNames.forEach { invalidateShortcuts(user, it) }
                refresh()
            }
            override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) {
                packageNames.forEach { invalidateShortcuts(user, it) }
                refresh()
            }
            override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) {
                packageNames.forEach { invalidateShortcuts(user, it) }
                refresh()
            }
            override fun onShortcutsChanged(packageName: String, shortcuts: List<android.content.pm.ShortcutInfo>, user: UserHandle) {
                invalidateShortcuts(user, packageName)
            }
        }

        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        val profileReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val profileFilter = IntentFilter().apply {
            addAction("android.intent.action.PROFILE_AVAILABLE")
            addAction("android.intent.action.PROFILE_UNAVAILABLE")
            addAction("android.intent.action.MANAGED_PROFILE_ADDED")
            addAction("android.intent.action.MANAGED_PROFILE_REMOVED")
            addAction("android.intent.action.MANAGED_PROFILE_AVAILABLE")
            addAction("android.intent.action.MANAGED_PROFILE_UNAVAILABLE")
        }
        ContextCompat.registerReceiver(
            appContext,
            profileReceiver,
            profileFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(Unit)

        awaitClose {
            launcherApps.unregisterCallback(callback)
            runCatching { appContext.unregisterReceiver(profileReceiver) }
        }
    }.buffer(Channel.CONFLATED).map {
        withContext(Dispatchers.IO) { loadSnapshot() }
    }

    fun loadIcon(app: LauncherApp, selectedIconPack: String, customIcon: String?): Bitmap? {
        val generation = iconGeneration.get()
        val cacheKey = "$generation:${app.id}:$densityDpi:$selectedIconPack:${customIcon.orEmpty()}"
        synchronized(iconCache) { iconCache.get(cacheKey)?.let { return it } }

        val pending = CompletableFuture<Bitmap?>()
        val existing = iconLoads.putIfAbsent(cacheKey, pending)
        if (existing != null) return runCatching { existing.join() }.getOrNull()

        return try {
            val bitmap = runCatching {
                val useSystemIcon = customIcon == SYSTEM_ICON_OVERRIDE
                val overrideIcon = if (useSystemIcon) null else customIcon?.split('|', limit = 2)?.takeIf { it.size == 2 }
                    ?.let { (pack, drawable) -> iconPackManager.loadIcon(pack, drawable, iconSizePx) }
                val mappedIcon = if (overrideIcon == null && selectedIconPack.isNotBlank() && !useSystemIcon) {
                    iconPackManager.loadMappedIcon(app, selectedIconPack, iconSizePx)
                } else {
                    null
                }
                overrideIcon ?: mappedIcon ?: loadSystemIcon(app)
            }.onFailure { Log.w(TAG, "Could not load icon for ${app.id}", it) }.getOrNull()
            if (bitmap != null && generation == iconGeneration.get()) {
                synchronized(iconCache) { iconCache.put(cacheKey, bitmap) }
            }
            pending.complete(bitmap)
            bitmap
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            Log.w(TAG, "Could not load icon for ${app.id}", error)
            null
        } finally {
            iconLoads.remove(cacheKey, pending)
        }
    }

    fun loadIconPackIcon(packageName: String, drawableName: String): Bitmap? =
        iconPackManager.loadIcon(packageName, drawableName, iconSizePx)

    fun installedIconPacks(): List<IconPackInfo> = iconPackManager.installedPacks()

    fun iconPackDrawableNames(packageName: String): List<String> = iconPackManager.iconNames(packageName)

    fun shortcuts(app: LauncherApp): List<LauncherShortcut> = shortcutCache[app.id] ?: runCatching {
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setActivity(ComponentName(app.packageName, app.activityName))
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        launcherApps.getShortcuts(query, app.userHandle)
            .orEmpty()
            .filter { shortcut -> shortcut.isEnabled }
            .mapNotNull { shortcut ->
                val label = shortcut.shortLabel?.toString()?.trim().orEmpty()
                if (label.isBlank()) null else {
                    val drawable = runCatching { launcherApps.getShortcutIconDrawable(shortcut, densityDpi) }.getOrNull()
                    LauncherShortcut(
                        id = shortcut.id,
                        label = label,
                        rank = shortcut.rank,
                        icon = drawable?.toIconBitmap((ICON_SIZE_DP * appContext.resources.displayMetrics.density).toInt()),
                    )
                }
            }
            .sortedBy(LauncherShortcut::rank)
            .take(MAX_SHORTCUTS_PER_APP)
            .distinctBy(LauncherShortcut::id)
    }.onFailure { Log.w(TAG, "Could not read shortcuts for ${app.id}", it) }.getOrDefault(emptyList())
        .also { shortcutCache.put(app.id, it) }

    fun launchShortcut(app: LauncherApp, shortcut: LauncherShortcut): Boolean = runCatching {
        launcherApps.startShortcut(app.packageName, shortcut.id, null, null, app.userHandle)
        true
    }.onFailure { Log.w(TAG, "Could not launch shortcut ${shortcut.id} for ${app.id}", it) }.getOrDefault(false)

    fun pinShortcutRequest(intent: Intent): LauncherApps.PinItemRequest? = runCatching {
        if (intent.action != LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) return null
        launcherApps.getPinItemRequest(intent)
            ?.takeIf { it.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT && it.isValid }
    }.onFailure { Log.w(TAG, "Could not read a shortcut pin request", it) }.getOrNull()

    fun refreshShortcuts() {
        shortcutCache.evictAll()
        shortcutRevisionMutable.value += 1L
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

    private fun loadSystemIcon(app: LauncherApp): Bitmap? {
        val activityInfo = activityInfoById[app.id]
            ?: launcherApps.getActivityList(app.packageName, app.userHandle)
                .firstOrNull { it.componentName.className == app.activityName }
            ?: return null
        return activityInfo.getBadgedIcon(densityDpi).toIconBitmap(iconSizePx)
    }

    fun requestProfileAvailability(profile: LauncherProfile, available: Boolean): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        userManager.requestQuietModeEnabled(!available, profile.handle)
    }.onFailure { Log.w(TAG, "Could not change profile state for ${profile.serial}", it) }.getOrDefault(false)

    private fun loadSnapshot(): Snapshot {
        val profiles = runCatching { launcherApps.profiles }
            .onFailure { Log.w(TAG, "Could not list user profiles", it) }
            .getOrDefault(emptyList())
        val launcherProfiles = profiles.mapNotNull { user ->
            runCatching {
                val serial = userManager.getSerialNumberForUser(user)
                val launcherUserInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    launcherApps.getLauncherUserInfo(user)
                } else {
                    null
                }
                val userType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    launcherUserInfo?.userType
                } else {
                    null
                }
                val profileType = when {
                    user == Process.myUserHandle() -> LauncherProfileType.Personal
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && userType == UserManager.USER_TYPE_PROFILE_PRIVATE -> LauncherProfileType.Private
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && userType == UserManager.USER_TYPE_PROFILE_CLONE -> LauncherProfileType.Clone
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && userType == UserManager.USER_TYPE_PROFILE_MANAGED -> LauncherProfileType.Work
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM -> LauncherProfileType.Work
                    else -> LauncherProfileType.Other
                }
                val privateEntryHidden = if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
                    profileType == LauncherProfileType.Private
                ) {
                    launcherUserInfo?.userConfig?.getBoolean(
                        android.content.pm.LauncherUserInfo.PRIVATE_SPACE_ENTRYPOINT_HIDDEN,
                        false,
                    ) ?: false
                } else {
                    false
                }
                LauncherProfile(
                    handle = user,
                    serial = serial,
                    type = profileType,
                    quietMode = runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false),
                    unlocked = user == Process.myUserHandle() || runCatching { userManager.isUserUnlocked(user) }.getOrDefault(false),
                    privateEntryHiddenWhenLocked = privateEntryHidden,
                )
            }.onFailure { Log.w(TAG, "Could not read launcher profile", it) }.getOrNull()
        }
        val discovered = buildList {
            launcherProfiles.forEach { profile ->
                if (profile.type != LauncherProfileType.Personal && !profile.isAvailable) return@forEach
                val profileApps = runCatching {
                    launcherApps.getActivityList(null, profile.handle)
                        .filterNot { info -> info.componentName.packageName == appContext.packageName }
                        .map { info ->
                        val component = info.componentName
                        val label = info.label?.toString()?.trim().orEmpty()
                            .ifBlank { info.applicationInfo.loadLabel(appContext.packageManager).toString().trim() }
                            .ifBlank { appContext.getString(R.string.unnamed_app) }
                        val app = LauncherApp(
                            packageName = component.packageName,
                            activityName = component.className,
                            label = label,
                            normalizedLabel = normalize(label),
                            userHandle = profile.handle,
                            userSerial = profile.serial,
                            profileType = profile.type,
                        )
                        app to info
                    }
                }.onFailure { Log.w(TAG, "Could not enumerate apps for a profile", it) }
                    .getOrDefault(emptyList())
                addAll(profileApps)
            }
        }

        iconGeneration.incrementAndGet()
        activityInfoById.clear()
        iconPackManager.invalidate()
        discovered.forEach { (app, info) -> activityInfoById[app.id] = info }
        synchronized(iconCache) { iconCache.evictAll() }
        val collator = Collator.getInstance(Locale.getDefault())
        val appList = discovered.map { it.first }.sortedWith { left, right ->
            val labelOrder = collator.compare(left.label, right.label)
            if (labelOrder != 0) labelOrder else left.id.compareTo(right.id)
        }
        return Snapshot(appList, launcherProfiles)
    }

    private fun invalidateShortcuts(user: UserHandle, packageName: String) {
        val prefix = "${runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(-1L)}:$packageName/"
        shortcutCache.snapshot().keys.filter { it.startsWith(prefix) }.forEach(shortcutCache::remove)
        shortcutRevisionMutable.value += 1L
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
        const val SHORTCUT_CACHE_KB = 768
        const val MAX_SHORTCUTS_PER_APP = 6
        const val SYSTEM_ICON_OVERRIDE = "@system"
    }
}
