package noryn.launcher.core.model

import android.graphics.Bitmap

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val iconCount: Int,
)

data class LauncherShortcut(
    val id: String,
    val label: String,
    val rank: Int = 0,
    val icon: Bitmap? = null,
)
