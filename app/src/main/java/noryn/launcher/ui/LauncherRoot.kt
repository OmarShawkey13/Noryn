package noryn.launcher.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.result.PickVisualMediaRequest
import noryn.launcher.ui.theme.NorynColors
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import noryn.launcher.R
import noryn.launcher.launcher.presentation.LauncherEvent
import noryn.launcher.launcher.presentation.LauncherScreen
import noryn.launcher.launcher.presentation.LauncherUiState
import noryn.launcher.launcher.presentation.LauncherViewModel
import noryn.launcher.ui.theme.NorynMotion

@Composable
fun LauncherRoot(viewModel: LauncherViewModel, state: LauncherUiState, homeSupportsDarkText: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wallpaperAppliedMessage = stringResource(R.string.wallpaper_applied)
    val wallpaperApplyFailedMessage = stringResource(R.string.wallpaper_apply_failed)
    val homeListState = rememberLazyListState()
    val roleRequestLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onHostResumed()
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val applied = withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = context.contentResolver.openInputStream(uri) ?: error("Selected image is unavailable")
                        stream.use { WallpaperManager.getInstance(context).setStream(it, null, true, WallpaperManager.FLAG_SYSTEM) != 0 }
                    }.isSuccess
                }
                Toast.makeText(
                    context,
                    if (applied) wallpaperAppliedMessage else wallpaperApplyFailedMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val roleUnavailableMessage = stringResource(R.string.role_request_unavailable)
    val favoriteLimitMessage = stringResource(R.string.favorite_limit_reached)
    val widgetBindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onWidgetBindResult(result.resultCode)
    }
    var configuredWidgetId by remember { mutableIntStateOf(-1) }
    val widgetConfigureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (configuredWidgetId >= 0) viewModel.onWidgetConfigureResult(configuredWidgetId, result.resultCode)
        configuredWidgetId = -1
    }
    val notifications = viewModel.notificationSnapshot.collectAsState().value
    val shortcutRevision = viewModel.shortcutRevision.collectAsState().value
    val pinShortcutPrompt = viewModel.pinShortcutPrompt.collectAsState().value

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LauncherEvent.FavoriteLimitReached -> Toast.makeText(context, favoriteLimitMessage, Toast.LENGTH_SHORT).show()
                is LauncherEvent.LaunchWidgetBind -> runCatching { widgetBindLauncher.launch(event.intent) }
                    .onFailure { viewModel.onWidgetBindResult(android.app.Activity.RESULT_CANCELED) }
                is LauncherEvent.LaunchWidgetConfigure -> {
                    configuredWidgetId = event.appWidgetId
                    runCatching { widgetConfigureLauncher.launch(event.intent) }
                        .onFailure {
                            viewModel.onWidgetConfigureResult(event.appWidgetId, android.app.Activity.RESULT_CANCELED)
                            configuredWidgetId = -1
                        }
                }
            }
        }
    }

    BackHandler {
        if (state.screen !in setOf(LauncherScreen.Home, LauncherScreen.Welcome)) {
            viewModel.goBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (state.screen == LauncherScreen.Home) androidx.compose.ui.graphics.Color.Transparent
                else MaterialTheme.colorScheme.background,
            )
            .then(if (state.screen == LauncherScreen.Home) Modifier else Modifier.safeDrawingPadding()),
    ) {
        CompositionLocalProvider(
            LocalNotificationSnapshot provides notifications,
            LocalShortcutRevision provides shortcutRevision,
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        ) {
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = {
                val isEntering = targetState != LauncherScreen.Home && initialState == LauncherScreen.Home
                (fadeIn(tween(NorynMotion.Standard)) + slideInHorizontally(tween(NorynMotion.Standard)) { distance ->
                    if (isEntering) distance / 16 else -distance / 24
                }) togetherWith
                    (fadeOut(tween(NorynMotion.Fast)) + slideOutHorizontally(tween(NorynMotion.Fast)) { distance ->
                        if (isEntering) -distance / 24 else distance / 24
                    })
            },
            label = "noryn-navigation",
        ) { destination ->
            when (destination) {
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
                LauncherScreen.Home -> MaterialTheme(
                    colorScheme = NorynColors.home(homeSupportsDarkText),
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        HomeScreen(viewModel, state, homeListState)
                    }
                }
                LauncherScreen.Search -> SearchScreen(viewModel, state)
                LauncherScreen.Settings -> SettingsScreen(viewModel, state)
                LauncherScreen.AppearanceSettings -> AppearanceSettingsScreen(
                    viewModel,
                    state,
                    onChangeWallpaper = {
                        wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                LauncherScreen.HomeSettings -> HomeSettingsScreen(viewModel, state)
                LauncherScreen.IconPacks -> IconPacksScreen(viewModel, state)
                LauncherScreen.GestureSettings -> GestureSettingsScreen(viewModel, state)
                LauncherScreen.AppSettings -> AppSettingsScreen(
                    viewModel,
                    state,
                    onOpenUsageAccess = {
                        runCatching { context.startActivity(viewModel.createUsageAccessIntent()) }
                    },
                )
                LauncherScreen.HiddenApps -> HiddenAppsScreen(viewModel, state)
                LauncherScreen.FavoriteManagement -> FavoriteManagementScreen(viewModel, state)
                LauncherScreen.Widgets -> WidgetSettingsScreen(viewModel, state)
                LauncherScreen.Notifications -> NotificationSettingsScreen(
                    viewModel,
                    state,
                    onOpenListenerSettings = {
                        runCatching { context.startActivity(viewModel.createNotificationAccessIntent()) }
                    },
                )
                LauncherScreen.AppEdit -> AppEditScreen(viewModel, state)
                LauncherScreen.IconSelection -> IconSelectionScreen(viewModel, state)
            }
        }
        if (pinShortcutPrompt != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissPinShortcutRequest,
                title = { Text(stringResource(R.string.pin_shortcut_title)) },
                text = { Text(stringResource(R.string.pin_shortcut_message, pinShortcutPrompt)) },
                confirmButton = {
                    TextButton(onClick = viewModel::acceptPinShortcutRequest) {
                        Text(stringResource(R.string.add))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissPinShortcutRequest) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
        }
    }
}
