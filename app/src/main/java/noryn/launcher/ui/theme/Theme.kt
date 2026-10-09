package noryn.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import noryn.launcher.core.model.ThemeChoice

@Composable
fun NorynLauncherTheme(themeChoice: ThemeChoice = ThemeChoice.System, content: @Composable () -> Unit) {
    val darkTheme = when (themeChoice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) NorynDarkColors else NorynLightColors,
        typography = NorynTypography,
        content = content,
    )
}
