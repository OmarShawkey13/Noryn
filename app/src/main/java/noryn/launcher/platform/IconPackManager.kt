package noryn.launcher.platform

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.createBitmap
import noryn.launcher.core.model.IconPackInfo
import noryn.launcher.core.model.LauncherApp
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class IconPackManager(private val context: Context) {
    private val packageManager = context.packageManager
    private val resourceCache = ConcurrentHashMap<String, PackResources>()
    private val cacheGeneration = AtomicLong()
    private val bitmapCache = object : LruCache<String, Bitmap>(ICON_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun installedPacks(): List<IconPackInfo> = packActions
        .flatMap { action -> queryActivities(Intent(action)) }
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            val resources = resourcesFor(packageName)
            val iconNames = resources?.mappings?.values?.toSet().orEmpty()
            IconPackInfo(
                packageName = packageName,
                label = runCatching { resolveInfo.loadLabel(packageManager).toString() }
                    .getOrDefault(packageName),
                iconCount = iconNames.size,
            )
        }
        .distinctBy(IconPackInfo::packageName)
        .sortedBy(IconPackInfo::label)

    fun iconNames(packageName: String): List<String> = resourcesFor(packageName)
        ?.mappings
        ?.values
        ?.distinct()
        ?.sorted()
        .orEmpty()

    fun loadMappedIcon(app: LauncherApp, packageName: String, sizePx: Int): Bitmap? {
        val drawableName = resourcesFor(packageName)?.mappings?.get(app.componentName) ?: return null
        return loadIcon(packageName, drawableName, sizePx)
    }

    @SuppressLint("DiscouragedApi")
    fun loadIcon(packageName: String, drawableName: String, sizePx: Int): Bitmap? {
        val key = "${cacheGeneration.get()}:$packageName:$drawableName:${context.resources.displayMetrics.densityDpi}:$sizePx"
        synchronized(bitmapCache) { bitmapCache.get(key)?.let { return it } }
        return runCatching {
            val packResources = resourcesFor(packageName) ?: return null
            val resourceId = packResources.resources.getIdentifier(drawableName, "drawable", packageName)
                .takeIf { it != 0 }
                ?: packResources.resources.getIdentifier(drawableName, "mipmap", packageName)
                    .takeIf { it != 0 }
                ?: return null
            val drawable = packResources.resources.getDrawable(resourceId, null)
            drawable.toBitmap(sizePx).also { bitmap ->
                synchronized(bitmapCache) { bitmapCache.put(key, bitmap) }
            }
        }.onFailure { Log.w(TAG, "Could not load $drawableName from icon pack $packageName", it) }.getOrNull()
    }

    fun invalidate() {
        cacheGeneration.incrementAndGet()
        resourceCache.clear()
        synchronized(bitmapCache) { bitmapCache.evictAll() }
    }

    private fun resourcesFor(packageName: String): PackResources? {
        val generation = cacheGeneration.get()
        val key = "$generation:$packageName"
        return resourceCache[key] ?: runCatching {
        val resources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getResourcesForApplication(packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0),
            ))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getResourcesForApplication(packageName)
        }
        val mappings = readMappings(packageName, resources)
            PackResources(resources, mappings).also {
                if (generation == cacheGeneration.get()) resourceCache[key] = it
            }
    }.onFailure { Log.w(TAG, "Could not read icon pack $packageName", it) }.getOrNull()
    }

    @SuppressLint("DiscouragedApi")
    private fun readMappings(packageName: String, resources: android.content.res.Resources): Map<String, String> {
        val xmlId = resources.getIdentifier("appfilter", "xml", packageName)
        if (xmlId == 0) return emptyMap()
        return runCatching {
            val parser = resources.getXml(xmlId)
            val mappings = LinkedHashMap<String, String>()
            try {
                var event = parser.eventType
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "item") {
                        val componentText = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (!componentText.isNullOrBlank() && !drawable.isNullOrBlank()) {
                            val flattened = componentText
                                .removePrefix("ComponentInfo{")
                                .removeSuffix("}")
                            ComponentName.unflattenFromString(flattened)?.flattenToString()?.let { component ->
                                mappings[component] = drawable
                            }
                        }
                    }
                    event = parser.next()
                }
            } finally {
                parser.close()
            }
            mappings
        }.onFailure { Log.w(TAG, "Could not parse appfilter.xml in $packageName", it) }.getOrDefault(emptyMap())
    }

    private fun queryActivities(intent: Intent): List<android.content.pm.ResolveInfo> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }.onFailure { Log.w(TAG, "Could not discover icon packs for ${intent.action}", it) }.getOrDefault(emptyList())

    private fun Drawable.toBitmap(size: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val oldBounds = bounds
        setBounds(0, 0, size, size)
        draw(canvas)
        bounds = oldBounds
        return bitmap
    }

    private data class PackResources(
        val resources: android.content.res.Resources,
        val mappings: Map<String, String>,
    )

    private companion object {
        const val TAG = "NorynIconPack"
        const val ICON_CACHE_KB = 4 * 1024
        val packActions = listOf(
            "org.adw.launcher.THEMES",
            "com.anddoes.launcher.THEME",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "com.dlto.atom.launcher.THEME",
            "com.tsf.shell.themes",
            "com.fede.launcher.THEME",
        )
    }
}
