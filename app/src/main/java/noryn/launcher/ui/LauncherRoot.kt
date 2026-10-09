package noryn.launcher.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import noryn.launcher.R
import noryn.launcher.launcher.presentation.LauncherScreen
import noryn.launcher.launcher.presentation.LauncherUiState
import noryn.launcher.launcher.presentation.LauncherViewModel

@Composable
fun LauncherRoot(viewModel: LauncherViewModel, state: LauncherUiState) {
    val context = LocalContext.current
    val roleRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onHostResumed()
    }
    val roleUnavailableMessage = stringResource(R.string.role_request_unavailable)

    BackHandler(enabled = state.screen !in setOf(LauncherScreen.Home, LauncherScreen.Welcome)) {
        viewModel.goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        when (state.screen) {
            LauncherScreen.Welcome -> WelcomeScreen(
                onSetHome = {
                    val request = viewModel.createDefaultLauncherRequest()
                    if (request == null) {
                        Toast.makeText(context, roleUnavailableMessage, Toast.LENGTH_LONG).show()
                    } else {
                        try {
                            roleRequestLauncher.launch(request)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, roleUnavailableMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
            LauncherScreen.Home -> HomeScreen(viewModel, state)
            LauncherScreen.Search -> SearchScreen(viewModel, state)
            LauncherScreen.Settings -> SettingsScreen(viewModel, state)
            LauncherScreen.HiddenApps -> HiddenAppsScreen(viewModel, state)
            LauncherScreen.FavoriteManagement -> FavoriteManagementScreen(viewModel, state)
        }
    }
}
