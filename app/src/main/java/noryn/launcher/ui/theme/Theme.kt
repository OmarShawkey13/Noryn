package noryn.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import noryn.launcher.core.model.FontChoice
import noryn.launcher.core.model.ThemeChoice

@Composable
fun NorynTheme(
    themeChoice: ThemeChoice = ThemeChoice.System,
    fontChoice: FontChoice = FontChoice.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeChoice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    val locale = LocalConfiguration.current.locales[0]
    val typography = remember(fontChoice, locale.language) {
        if (fontChoice == FontChoice.Noryn) {
            if (locale.language == "ar") NorynArabicTypography else NorynTypography
        } else {
            androidx.compose.material3.Typography()
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) NorynColors.Dark else NorynColors.Light,
        typography = typography,
        shapes = NorynShapes,
        content = content,
    )
}
