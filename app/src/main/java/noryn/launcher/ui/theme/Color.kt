package noryn.launcher.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object NorynColors {
    val Light = lightColorScheme(
        primary = Color(0xFF356653),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE3ECE7),
        onPrimaryContainer = Color(0xFF193B2E),
        secondary = Color(0xFF52685C),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE6EDE8),
        onSecondaryContainer = Color(0xFF20382C),
        background = Color(0xFFF7F7F4),
        onBackground = Color(0xFF171A18),
        surface = Color(0xFFF7F7F4),
        onSurface = Color(0xFF171A18),
        surfaceVariant = Color(0xFFECEEEB),
        onSurfaceVariant = Color(0xFF6D746F),
        outline = Color(0xFF9EA7A1),
        outlineVariant = Color(0xFFD9DEDA),
        error = Color(0xFFBA4338),
        onError = Color(0xFFFFFFFF),
    )

    val Dark = darkColorScheme(
        primary = Color(0xFF7DB59B),
        onPrimary = Color(0xFF10281D),
        primaryContainer = Color(0xFF294437),
        onPrimaryContainer = Color(0xFFD8EFE3),
        secondary = Color(0xFFB4C9BD),
        onSecondary = Color(0xFF1E342A),
        secondaryContainer = Color(0xFF2B4035),
        onSecondaryContainer = Color(0xFFD8E8DE),
        background = Color(0xFF0E100F),
        onBackground = Color(0xFFF2F4F2),
        surface = Color(0xFF171A18),
        onSurface = Color(0xFFF2F4F2),
        surfaceVariant = Color(0xFF202421),
        onSurfaceVariant = Color(0xFFA5ACA7),
        outline = Color(0xFF818C82),
        outlineVariant = Color(0xFF38413B),
        error = Color(0xFFFFB4A9),
        onError = Color(0xFF690005),
    )

    private val HomeLight = Light.copy(
        background = Color.Transparent,
        surface = Color(0xFFF7F7F4),
        surfaceVariant = Color(0xD9FFFFFF),
        outlineVariant = Color(0x44343D37),
    )

    private val HomeDark = Dark.copy(
        background = Color.Transparent,
        surface = Color(0xFF171A18),
        surfaceVariant = Color(0xD9202421),
        outlineVariant = Color(0x55484F4A),
    )

    fun home(wallpaperSupportsDarkText: Boolean) = if (wallpaperSupportsDarkText) HomeLight else HomeDark
}
