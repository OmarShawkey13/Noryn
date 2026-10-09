package noryn.launcher.core.model

import android.graphics.Bitmap
import android.content.ComponentName

enum class WidgetSizePreset {
    Compact,
    Standard,
    Tall;

    fun next(): WidgetSizePreset = when (this) {
        Compact -> Standard
        Standard -> Tall
        Tall -> Compact
    }
}

data class WidgetInstance(
    val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val position: Int,
    val size: WidgetSizePreset,
    val providerAvailable: Boolean,
    val pendingPhase: WidgetPendingPhase? = null,
    val providerMinHeightDp: Int = 0,
    val providerLabel: String = providerPackage,
    val supportsVerticalResize: Boolean = false,
    val supportsReconfigure: Boolean = false,
) {
    val provider: ComponentName get() = ComponentName(providerPackage, providerClass)
    val isPending: Boolean get() = pendingPhase != null
}

enum class WidgetPendingPhase {
    Binding,
    Configuring,
    Reconfiguring,
}

data class WidgetProvider(
    val provider: ComponentName,
    val appLabel: String,
    val widgetLabel: String,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val resizeMode: Int,
    val needsConfiguration: Boolean,
    val reconfigurable: Boolean,
    val appIcon: Bitmap?,
    val preview: Bitmap?,
) {
    val supportsVerticalResize: Boolean get() = resizeMode and android.appwidget.AppWidgetProviderInfo.RESIZE_VERTICAL != 0
}
