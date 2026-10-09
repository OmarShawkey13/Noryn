package noryn.launcher

import android.os.Bundle
import android.app.WallpaperManager
import android.app.WallpaperColors
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.graphics.ColorUtils
import noryn.launcher.launcher.presentation.LauncherViewModel
import noryn.launcher.ui.LauncherRoot
import noryn.launcher.ui.theme.NorynTheme
import noryn.launcher.core.model.ThemeChoice

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<LauncherViewModel> { LauncherViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.onPinShortcutRequest(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (state.settings.themeChoice) {
                ThemeChoice.System -> systemDark
                ThemeChoice.Light -> false
                ThemeChoice.Dark -> true
            }
            val wallpaperManager = remember { getSystemService(WallpaperManager::class.java) }
            var wallpaperSupportsDarkText by remember { mutableStateOf(readWallpaperSupportsDarkText(wallpaperManager)) }
            DisposableEffect(wallpaperManager) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return@DisposableEffect onDispose { }
                val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
                    if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                        wallpaperSupportsDarkText = wallpaperPrefersDarkText(colors)
                    }
                }
                wallpaperManager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
                onDispose { wallpaperManager.removeOnColorsChangedListener(listener) }
            }
            val homeSupportsDarkText = wallpaperSupportsDarkText ?: !darkTheme
            SideEffect {
                val lightSystemIcons = if (state.screen == noryn.launcher.launcher.presentation.LauncherScreen.Home) {
                    homeSupportsDarkText
                } else {
                    !darkTheme
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightSystemIcons
                    isAppearanceLightNavigationBars = lightSystemIcons
                }
            }
            NorynTheme(state.settings.themeChoice, state.settings.fontChoice) {
                LauncherRoot(viewModel, state, homeSupportsDarkText)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onHostResumed()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.onPinShortcutRequest(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onWidgetHostStarted()
    }

    override fun onStop() {
        viewModel.onWidgetHostStopped()
        super.onStop()
    }

    override fun onDestroy() {
        viewModel.releaseWidgetHostViews()
        super.onDestroy()
    }
}

private fun readWallpaperSupportsDarkText(manager: WallpaperManager): Boolean? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
    return runCatching { wallpaperPrefersDarkText(manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)) }.getOrNull()
}

private fun wallpaperPrefersDarkText(colors: WallpaperColors?): Boolean? {
    colors ?: return null
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> supportsDarkTextFromHints(colors)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 ->
            runCatching { ColorUtils.calculateLuminance(colors.primaryColor.toArgb()) >= 0.5 }.getOrNull()
        else -> null
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun supportsDarkTextFromHints(colors: WallpaperColors): Boolean =
    colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
