package noryn.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import noryn.launcher.launcher.presentation.LauncherViewModel
import noryn.launcher.ui.LauncherRoot
import noryn.launcher.ui.theme.NorynLauncherTheme
import noryn.launcher.core.model.ThemeChoice

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<LauncherViewModel> { LauncherViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (state.settings.themeChoice) {
                ThemeChoice.System -> systemDark
                ThemeChoice.Light -> false
                ThemeChoice.Dark -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            NorynLauncherTheme(state.settings.themeChoice) {
                LauncherRoot(viewModel, state)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onHostResumed()
    }
}
