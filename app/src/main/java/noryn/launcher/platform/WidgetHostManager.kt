package noryn.launcher.platform

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.View
import androidx.core.graphics.createBitmap
import noryn.launcher.R
import noryn.launcher.core.model.WidgetInstance
import noryn.launcher.core.model.WidgetPendingPhase
import noryn.launcher.core.model.WidgetProvider
import noryn.launcher.core.model.WidgetSizePreset
import noryn.launcher.storage.WidgetRepository
import kotlin.math.roundToInt

sealed interface WidgetHostStep {
    data class LaunchBind(val intent: Intent) : WidgetHostStep
    data class LaunchConfigure(val intent: Intent) : WidgetHostStep
    data object Completed : WidgetHostStep
    data object Failed : WidgetHostStep
}

class WidgetHostManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager = AppWidgetManager.getInstance(appContext)
    private val repository = WidgetRepository(appContext)
    private var providersChanged: (() -> Unit)? = null
    private val host = NorynWidgetHost(appContext, HOST_ID)

    private inner class NorynWidgetHost(context: Context, id: Int) : AppWidgetHost(context, id) {
        override fun onProvidersChanged() {
            super.onProvidersChanged()
            providersChanged?.invoke()
        }

        fun releaseViews() = clearViews()
    }

    fun setProvidersChangedCallback(callback: (() -> Unit)?) {
        providersChanged = callback
    }

    fun startListening() {
        runCatching { host.startListening() }
            .onFailure { Log.w(TAG, "Could not start widget host listening", it) }
    }

    fun stopListening() {
        runCatching { host.stopListening() }
            .onFailure { Log.w(TAG, "Could not stop widget host listening", it) }
    }

    fun releaseActivityViews() {
        host.releaseViews()
    }

    fun providers(): List<WidgetProvider> = runCatching {
        manager.getInstalledProvidersForProfile(Process.myUserHandle())
            .asSequence()
            .filter { info ->
                info.widgetCategory == 0 || info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0
            }
            .filterNot { info ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER != 0
            }
            .mapNotNull(::toPickerProvider)
            .sortedWith(compareBy<WidgetProvider> { it.appLabel.lowercase() }.thenBy { it.widgetLabel.lowercase() })
            .toList()
    }.onFailure { Log.w(TAG, "Could not load widget providers", it) }.getOrDefault(emptyList())

    fun loadPreviews(providers: List<WidgetProvider>): Map<ComponentName, Bitmap?> {
        val requested = providers.mapTo(HashSet(), WidgetProvider::provider)
        if (requested.isEmpty()) return emptyMap()
        return runCatching {
            val density = appContext.resources.displayMetrics.densityDpi
            manager.getInstalledProvidersForProfile(Process.myUserHandle())
                .asSequence()
                .filter { it.provider in requested }
                .associate { info ->
                    val preview = info.loadPreviewImage(appContext, density)?.toPreviewBitmapOrNull(280)
                    info.provider to preview
                }
        }.onFailure { Log.w(TAG, "Could not load widget previews", it) }.getOrDefault(emptyMap())
    }

    private fun toPickerProvider(info: AppWidgetProviderInfo): WidgetProvider? = runCatching {
        val packageInfo = appContext.packageManager.getApplicationInfo(info.provider.packageName, 0)
        val appLabel = appContext.packageManager.getApplicationLabel(packageInfo).toString().trim()
            .ifBlank { appContext.getString(R.string.unnamed_app) }
        val icon = packageInfo.loadIcon(appContext.packageManager).toBitmapOrNull(64)
        val optionalConfiguration = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        val reconfigurable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            info.configure != null &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
        WidgetProvider(
            provider = info.provider,
            appLabel = appLabel,
            widgetLabel = info.loadLabel(appContext.packageManager).toString().ifBlank { appLabel },
            minWidthDp = info.minWidth.coerceAtLeast(1),
            minHeightDp = info.minHeight.coerceAtLeast(1),
            resizeMode = info.resizeMode,
            needsConfiguration = info.configure != null && !optionalConfiguration,
            reconfigurable = reconfigurable,
            appIcon = icon,
            preview = null,
        )
    }.onFailure { Log.w(TAG, "Could not read provider ${info.provider}", it) }.getOrNull()

    fun beginAdd(provider: ComponentName): WidgetHostStep = runCatching {
        val id = host.allocateAppWidgetId()
        var recordAdded = false
        try {
            repository.insertPending(id, provider)
            recordAdded = true
            val options = Bundle()
            if (manager.bindAppWidgetIdIfAllowed(id, provider, options)) {
                afterBound(id)
            } else {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
                }
                WidgetHostStep.LaunchBind(intent)
            }
        } catch (error: Throwable) {
            if (recordAdded) runCatching { repository.remove(id) }
            runCatching { host.deleteAppWidgetId(id) }
            throw error
        }
    }.onFailure { Log.w(TAG, "Could not begin widget add for $provider", it) }
        .getOrDefault(WidgetHostStep.Failed)

    fun onBindResult(resultCode: Int): WidgetHostStep {
        val pending = repository.all().firstOrNull { it.pendingPhase == WidgetPendingPhase.Binding }
            ?: return WidgetHostStep.Failed
        if (resultCode != Activity.RESULT_OK) return cancelPending(pending.appWidgetId)
        val boundInfo = runCatching { manager.getAppWidgetInfo(pending.appWidgetId) }.getOrNull()
        val isBound = boundInfo?.provider == pending.provider || runCatching {
            manager.bindAppWidgetIdIfAllowed(pending.appWidgetId, pending.provider)
        }.getOrDefault(false)
        if (!isBound) return cancelPending(pending.appWidgetId)
        return afterBound(pending.appWidgetId)
    }

    fun onConfigureResult(appWidgetId: Int, resultCode: Int): WidgetHostStep {
        val pending = repository.all().firstOrNull { it.appWidgetId == appWidgetId && it.pendingPhase != null }
            ?: return WidgetHostStep.Failed
        if (pending.pendingPhase == WidgetPendingPhase.Reconfiguring) {
            repository.markActive(pending.appWidgetId)
            return WidgetHostStep.Completed
        }
        return if (resultCode == Activity.RESULT_OK) {
            repository.markActive(pending.appWidgetId)
            WidgetHostStep.Completed
        } else {
            cancelPending(pending.appWidgetId)
        }
    }

    fun reconfigureIntent(appWidgetId: Int): Intent? = runCatching {
        val info = manager.getAppWidgetInfo(appWidgetId) ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE == 0
        ) return null
        val configure = info.configure ?: return null
        repository.setPendingPhase(appWidgetId, WidgetPendingPhase.Reconfiguring)
        Intent().setComponent(configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }.onFailure { Log.w(TAG, "Could not reconfigure widget $appWidgetId", it) }.getOrNull()

    fun resizeNext(appWidgetId: Int): Boolean {
        val widget = repository.all().firstOrNull { it.appWidgetId == appWidgetId } ?: return false
        val info = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull() ?: return false
        if (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL == 0) return false
        repository.setSize(appWidgetId, widget.size.next())
        return true
    }

    fun remove(appWidgetId: Int) {
        runCatching { repository.remove(appWidgetId) }
            .onFailure { Log.w(TAG, "Could not remove widget record $appWidgetId", it) }
        runCatching { host.deleteAppWidgetId(appWidgetId) }
            .onFailure { Log.w(TAG, "Could not release widget ID $appWidgetId", it) }
    }

    fun reconfigureCanceled(appWidgetId: Int) {
        repository.markActive(appWidgetId)
    }

    fun createHostView(context: Context, appWidgetId: Int): AppWidgetHostView? {
        val info = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull() ?: return null
        return runCatching { host.createView(context, appWidgetId, info) }
            .onFailure { Log.w(TAG, "Could not create widget host view $appWidgetId", it) }
            .getOrNull()
    }

    fun resizeHostView(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        val safeWidth = widthDp.coerceAtLeast(1)
        val safeHeight = heightDp.coerceAtLeast(1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.updateAppWidgetSize(
                Bundle(),
                listOf(android.util.SizeF(safeWidth.toFloat(), safeHeight.toFloat())),
            )
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(Bundle(), safeWidth, safeHeight, safeWidth, safeHeight)
        }
    }

    fun instances(): List<WidgetInstance> {
        val allocatedIds = runCatching { host.appWidgetIds.toSet() }.getOrDefault(emptySet())
        val records = repository.all()
        (allocatedIds - records.mapTo(HashSet(), WidgetInstance::appWidgetId)).forEach { orphanId ->
            runCatching { host.deleteAppWidgetId(orphanId) }
                .onFailure { Log.w(TAG, "Could not clean orphaned widget ID $orphanId", it) }
        }
        val staleRecords = records.filterNot { it.appWidgetId in allocatedIds }
        staleRecords.forEach { repository.remove(it.appWidgetId) }
        return records.filter { it.appWidgetId in allocatedIds }.map { record ->
            val info = runCatching { manager.getAppWidgetInfo(record.appWidgetId) }.getOrNull()
            val label = runCatching {
                info?.loadLabel(appContext.packageManager)?.trim()?.takeIf(String::isNotBlank)
                    ?: appContext.packageManager.getApplicationLabel(
                        appContext.packageManager.getApplicationInfo(record.providerPackage, 0),
                    ).toString().trim().takeIf(String::isNotBlank)
                    ?: appContext.getString(R.string.unnamed_app)
            }.getOrDefault(appContext.getString(R.string.unnamed_app))
            record.copy(
                providerAvailable = info?.provider == record.provider,
                providerMinHeightDp = info?.minHeight?.coerceAtLeast(0) ?: 0,
                providerLabel = label,
                supportsVerticalResize = ((info?.resizeMode ?: 0) and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0,
                supportsReconfigure = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    info?.configure != null &&
                    (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0,
            )
        }
    }

    fun reorder(appWidgetIds: List<Int>) = repository.reorder(appWidgetIds)

    fun cancelPending() {
        repository.all().filter(WidgetInstance::isPending).forEach { remove(it.appWidgetId) }
    }

    fun close() = repository.close()

    private fun afterBound(appWidgetId: Int): WidgetHostStep {
        val info = manager.getAppWidgetInfo(appWidgetId) ?: return cancelPending(appWidgetId)
        val optionalConfiguration = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (info.configure != null && !optionalConfiguration) {
            repository.setPendingPhase(appWidgetId, WidgetPendingPhase.Configuring)
            return WidgetHostStep.LaunchConfigure(
                Intent().setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
        }
        repository.markActive(appWidgetId)
        return WidgetHostStep.Completed
    }

    private fun cancelPending(appWidgetId: Int): WidgetHostStep {
        remove(appWidgetId)
        return WidgetHostStep.Failed
    }

    private fun Drawable.toBitmapOrNull(size: Int): Bitmap? = runCatching {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val oldBounds = bounds
        try {
            setBounds(0, 0, size, size)
            draw(canvas)
        } finally {
            bounds = oldBounds
        }
        bitmap
    }.getOrNull()

    private fun Drawable.toPreviewBitmapOrNull(maxEdge: Int): Bitmap? = runCatching {
        val sourceWidth = intrinsicWidth.takeIf { it > 0 } ?: maxEdge
        val sourceHeight = intrinsicHeight.takeIf { it > 0 } ?: maxEdge
        val scale = minOf(1f, maxEdge.toFloat() / sourceWidth, maxEdge.toFloat() / sourceHeight)
        val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val oldBounds = bounds
        try {
            setBounds(0, 0, width, height)
            draw(canvas)
        } finally {
            bounds = oldBounds
        }
        bitmap
    }.getOrNull()

    private companion object {
        const val TAG = "NorynWidgetHost"
        const val HOST_ID = 0x4E4F5259
    }
}
