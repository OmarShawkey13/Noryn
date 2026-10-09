package noryn.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import noryn.launcher.BuildConfig
import noryn.launcher.R
import noryn.launcher.core.model.AppLabelMode
import noryn.launcher.core.model.ClockAlignment
import noryn.launcher.core.model.FontChoice
import noryn.launcher.core.model.HomeGestureAction
import noryn.launcher.core.model.LauncherApp
import noryn.launcher.core.model.RowSpacing
import noryn.launcher.core.model.SizePreset
import noryn.launcher.core.model.ThemeChoice
import noryn.launcher.core.model.LauncherProfileLayout
import noryn.launcher.core.model.NotificationIndicatorStyle
import noryn.launcher.core.model.WidgetInstance
import noryn.launcher.core.model.WidgetProvider
import noryn.launcher.core.model.WidgetSizePreset
import noryn.launcher.launcher.presentation.LauncherUiState
import noryn.launcher.launcher.presentation.LauncherViewModel
import noryn.launcher.ui.theme.LauncherDimens

@Composable
internal fun SettingsScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = LauncherDimens.ScreenHorizontalPadding),
        contentPadding = PaddingValues(bottom = LauncherDimens.SectionSpacing),
    ) {
        item { ScreenTopBar(stringResource(R.string.settings), viewModel::goBack) }
        item {
            SettingsLinkRow(stringResource(R.string.appearance_group), stringResource(R.string.appearance_description), viewModel::openAppearanceSettings)
        }
        item {
            SettingsLinkRow(stringResource(R.string.home_group), stringResource(R.string.home_description), viewModel::openHomeSettings)
        }
        item {
            SettingsLinkRow(stringResource(R.string.icon_settings), stringResource(R.string.icons_description), viewModel::openIconPacks)
        }
        item {
            SettingsLinkRow(stringResource(R.string.gesture_settings), stringResource(R.string.gestures_description), viewModel::openGestureSettings)
        }
        item {
            SettingsLinkRow(stringResource(R.string.app_settings), stringResource(R.string.apps_description), viewModel::openAppSettings)
        }
        item {
            SettingsLinkRow(stringResource(R.string.widgets), stringResource(R.string.widgets_description), viewModel::openWidgets)
        }
        item {
            SettingsLinkRow(stringResource(R.string.notifications), stringResource(R.string.notifications_description), viewModel::openNotifications)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = LauncherDimens.SectionSpacing), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.about), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppearanceSettingsScreen(
    viewModel: LauncherViewModel,
    state: LauncherUiState,
    onChangeWallpaper: () -> Unit,
) {
    SettingsPage(stringResource(R.string.appearance_settings), viewModel::goBack) {
        ChoiceRow(
            title = stringResource(R.string.theme),
            selected = state.settings.themeChoice,
            choices = listOf(
                ThemeChoice.System to stringResource(R.string.theme_system),
                ThemeChoice.Light to stringResource(R.string.theme_light),
                ThemeChoice.Dark to stringResource(R.string.theme_dark),
            ),
            onSelect = viewModel::setTheme,
        )
        ChoiceRow(
            title = stringResource(R.string.font_style),
            selected = state.settings.fontChoice,
            choices = listOf(
                FontChoice.System to stringResource(R.string.font_system),
                FontChoice.Noryn to stringResource(R.string.font_noryn),
            ),
            onSelect = viewModel::setFontChoice,
        )
        SettingsLinkRow(stringResource(R.string.change_wallpaper), onClick = onChangeWallpaper)
    }
}

@Composable
internal fun HomeSettingsScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    SettingsPage(stringResource(R.string.home_settings), viewModel::goBack) {
        ChoiceRow(
            title = stringResource(R.string.app_labels),
            selected = state.settings.appLabelMode,
            choices = listOf(
                AppLabelMode.Always to stringResource(R.string.labels_always),
                AppLabelMode.FavoritesOnly to stringResource(R.string.labels_favorites_only),
                AppLabelMode.Never to stringResource(R.string.labels_never),
            ),
            onSelect = viewModel::setAppLabelMode,
        )
        ChoiceRow(
            title = stringResource(R.string.app_icon_size),
            selected = state.settings.appIconSize,
            choices = sizeChoices(),
            onSelect = viewModel::setAppIconSize,
        )
        ChoiceRow(
            title = stringResource(R.string.row_spacing),
            selected = state.settings.rowSpacing,
            choices = listOf(
                RowSpacing.Compact to stringResource(R.string.compact),
                RowSpacing.Default to stringResource(R.string.default_size),
                RowSpacing.Comfortable to stringResource(R.string.comfortable),
            ),
            onSelect = viewModel::setRowSpacing,
        )
        SettingSwitchRow(stringResource(R.string.show_clock), state.settings.showClock, viewModel::setShowClock)
        if (state.settings.showClock) {
            ChoiceRow(
                title = stringResource(R.string.clock_size),
                selected = state.settings.clockSize,
                choices = sizeChoices(),
                onSelect = viewModel::setClockSize,
            )
            ChoiceRow(
                title = stringResource(R.string.clock_alignment),
                selected = state.settings.clockAlignment,
                choices = listOf(
                    ClockAlignment.Start to stringResource(R.string.alignment_start),
                    ClockAlignment.Center to stringResource(R.string.alignment_center),
                ),
                onSelect = viewModel::setClockAlignment,
            )
        }
        SettingSwitchRow(stringResource(R.string.show_date), state.settings.showDate, viewModel::setShowDate)
        SettingSwitchRow(
            stringResource(R.string.show_alphabet_index),
            state.settings.showAlphabetIndex,
            viewModel::setShowAlphabetIndex,
        )
        SettingSwitchRow(
            stringResource(R.string.search_keyboard_immediately),
            state.settings.searchKeyboardImmediately,
            viewModel::setSearchKeyboardImmediately,
        )
        ChoiceRow(
            title = stringResource(R.string.maximum_favorites),
            selected = state.settings.maxFavorites,
            choices = listOf(4, 6, 8, 12).map { it to it.toString() },
            onSelect = viewModel::setMaxFavorites,
        )
        ChoiceRow(
            title = stringResource(R.string.profile_layout),
            selected = state.settings.profileLayout,
            choices = listOf(
                LauncherProfileLayout.Separate to stringResource(R.string.profile_layout_separate),
                LauncherProfileLayout.Mix to stringResource(R.string.profile_layout_mix),
            ),
            onSelect = viewModel::setProfileLayout,
        )
        if (state.profiles.any { it.type == noryn.launcher.core.model.LauncherProfileType.Private }) {
            SettingSwitchRow(
                label = stringResource(R.string.show_private_space),
                summary = stringResource(R.string.show_private_space_summary),
                checked = state.settings.showPrivateSpace,
                onCheckedChange = viewModel::setShowPrivateSpace,
            )
        }
        SettingsLinkRow(stringResource(R.string.widgets), onClick = viewModel::openWidgets)
        SettingsLinkRow(stringResource(R.string.manage_favorites), onClick = viewModel::openFavoriteManagement)
    }
}

@Composable
internal fun IconPacksScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    SettingsPage(stringResource(R.string.icon_settings), viewModel::goBack) {
        SettingsSectionTitle(stringResource(R.string.installed_icon_packs))
        IconPackChoiceRow(
            title = stringResource(R.string.system_icons),
            subtitle = null,
            selected = state.settings.iconPackPackageName.isBlank(),
            onClick = { viewModel.setIconPack("") },
        )
        if (state.iconPacks.isEmpty()) {
            EmptyMessage(stringResource(R.string.no_icon_packs))
        } else {
            state.iconPacks.forEach { pack ->
                IconPackChoiceRow(
                    title = pack.label,
                    subtitle = pluralStringResource(R.plurals.mapped_icon_count, pack.iconCount, pack.iconCount),
                    selected = state.settings.iconPackPackageName == pack.packageName,
                    onClick = { viewModel.setIconPack(pack.packageName) },
                )
            }
        }
        Spacer(Modifier.height(LauncherDimens.SectionSpacing))
    }
}

@Composable
internal fun GestureSettingsScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    SettingsPage(stringResource(R.string.gesture_settings), viewModel::goBack) {
        val actions = listOf(
            HomeGestureAction.OpenSearch to stringResource(R.string.action_open_search),
            HomeGestureAction.OpenAppList to stringResource(R.string.action_open_app_list),
            HomeGestureAction.NoAction to stringResource(R.string.action_no_action),
        )
        ChoiceRow(stringResource(R.string.swipe_down), state.settings.swipeDownAction, actions, viewModel::setSwipeDownAction)
        ChoiceRow(stringResource(R.string.swipe_up), state.settings.swipeUpAction, actions, viewModel::setSwipeUpAction)
        ChoiceRow(stringResource(R.string.double_tap), state.settings.doubleTapAction, actions, viewModel::setDoubleTapAction)
    }
}

@Composable
internal fun AppSettingsScreen(
    viewModel: LauncherViewModel,
    state: LauncherUiState,
    onOpenUsageAccess: () -> Unit,
) {
    var showUsageDisclosure by remember { mutableStateOf(false) }
    SettingsPage(stringResource(R.string.app_settings), viewModel::goBack) {
        SettingsLinkRow(stringResource(R.string.notifications), onClick = viewModel::openNotifications)
        SettingsLinkRow(stringResource(R.string.manage_favorites), onClick = viewModel::openFavoriteManagement)
        SettingsLinkRow(stringResource(R.string.hidden_apps), onClick = viewModel::openHiddenApps)
        SettingsSectionTitle(stringResource(R.string.advanced_group))
        SettingSwitchRow(
            label = stringResource(R.string.usage_suggestions),
            summary = stringResource(R.string.usage_suggestions_summary),
            checked = state.settings.suggestionsEnabled,
            onCheckedChange = { enabled ->
                viewModel.setSuggestionsEnabled(enabled)
                if (enabled && !state.hasUsageAccess) showUsageDisclosure = true
            },
        )
        if (state.settings.suggestionsEnabled && !state.hasUsageAccess) {
            Text(
                stringResource(R.string.usage_access_not_granted),
                modifier = Modifier.padding(vertical = LauncherDimens.CompactSpacing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { showUsageDisclosure = true }) {
                Text(stringResource(R.string.usage_access_grant))
            }
        }
        Spacer(Modifier.height(LauncherDimens.SectionSpacing))
    }
    if (showUsageDisclosure) {
        AlertDialog(
            onDismissRequest = { showUsageDisclosure = false },
            title = { Text(stringResource(R.string.usage_access_required_title)) },
            text = { Text(stringResource(R.string.usage_access_required_body)) },
            confirmButton = {
                TextButton(onClick = { showUsageDisclosure = false; onOpenUsageAccess() }) {
                    Text(stringResource(R.string.continue_label))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUsageDisclosure = false
                        if (!state.settings.suggestionsEnabled) viewModel.setSuggestionsEnabled(false)
                    },
                ) { Text(stringResource(R.string.not_now)) }
            },
        )
    }
}

@Composable
internal fun WidgetSettingsScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    var expandedWidgetPackage by remember { mutableStateOf<String?>(null) }
    val providersByApp = remember(state.widgetProviders) {
        state.widgetProviders.groupBy { it.provider.packageName }.values.toList()
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = LauncherDimens.ScreenHorizontalPadding)) {
        ScreenTopBar(stringResource(R.string.widgets), viewModel::goBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = LauncherDimens.SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(stringResource(R.string.widgets_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.widgetInstances.isNotEmpty()) {
                item { SettingsSectionTitle(stringResource(R.string.placed_widgets)) }
                items(state.widgetInstances.sortedBy(WidgetInstance::position), key = WidgetInstance::appWidgetId) { widget ->
                    WidgetInstanceRow(widget, viewModel)
                }
            }
            item { SettingsSectionTitle(stringResource(R.string.add_widget)) }
            if (state.isLoadingWidgetProviders) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (state.widgetProviders.isEmpty()) {
                item { Text(stringResource(R.string.no_widget_providers), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                providersByApp.forEach { providers ->
                    val packageName = providers.first().provider.packageName
                    item(key = "widget-app-$packageName") {
                        WidgetAppGroupCard(
                            appLabel = providers.first().appLabel,
                            appIcon = providers.first().appIcon,
                            providers = providers,
                            expanded = expandedWidgetPackage == packageName,
                            onToggle = {
                                if (expandedWidgetPackage == packageName) {
                                    viewModel.clearWidgetPreviews(packageName)
                                    expandedWidgetPackage = null
                                } else {
                                    expandedWidgetPackage?.let(viewModel::clearWidgetPreviews)
                                    expandedWidgetPackage = packageName
                                    viewModel.loadWidgetPreviews(packageName)
                                }
                            },
                            onAddWidget = viewModel::beginAddWidget,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetAppGroupCard(
    appLabel: String,
    appIcon: android.graphics.Bitmap?,
    providers: List<WidgetProvider>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddWidget: (WidgetProvider) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(17.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            appLabel.firstOrNull()?.uppercase() ?: "•",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Text(
                    appLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        providers.size.toString(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    if (expanded) "⌃" else "⌄",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                providers.forEach { provider ->
                    WidgetProviderCard(provider) { onAddWidget(provider) }
                }
            }
        }
    }
}

@Composable
private fun WidgetInstanceRow(widget: WidgetInstance, viewModel: LauncherViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (widget.isPending) stringResource(R.string.widget_setup_pending, widget.providerLabel)
                    else widget.providerLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!widget.isPending && widget.supportsVerticalResize) {
                    Text(
                        stringResource(R.string.widget_current_size, widgetPresetLabel(widget.size)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!widget.isPending) {
                ReorderButton(NorynGlyph.Up, stringResource(R.string.move_up), widget.position > 0) {
                    viewModel.reorderWidget(widget.appWidgetId, -1)
                }
                ReorderButton(NorynGlyph.Down, stringResource(R.string.move_down), true) {
                    viewModel.reorderWidget(widget.appWidgetId, 1)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (widget.isPending) {
                TextButton(onClick = viewModel::cancelPendingWidgets) { Text(stringResource(R.string.cancel)) }
            } else {
                TextButton(onClick = { viewModel.resizeWidget(widget.appWidgetId) }, enabled = widget.supportsVerticalResize) { Text(stringResource(R.string.resize)) }
                if (widget.providerAvailable && widget.supportsReconfigure) {
                    TextButton(onClick = { viewModel.reconfigureWidget(widget.appWidgetId) }) {
                        Text(stringResource(R.string.configure))
                    }
                }
                TextButton(onClick = { viewModel.removeWidget(widget.appWidgetId) }) { Text(stringResource(R.string.remove)) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@Composable
private fun widgetPresetLabel(size: WidgetSizePreset): String = when (size) {
    WidgetSizePreset.Compact -> stringResource(R.string.widget_size_small)
    WidgetSizePreset.Standard -> stringResource(R.string.widget_size_medium)
    WidgetSizePreset.Tall -> stringResource(R.string.widget_size_large)
}

@Composable
private fun WidgetProviderCard(provider: WidgetProvider, onAdd: () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val preview = provider.preview
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val previewRatio = preview?.let { it.width.toFloat() / it.height.coerceAtLeast(1) }
                val providerRatio = provider.minWidthDp.toFloat() / provider.minHeightDp.coerceAtLeast(1)
                val ratio = (previewRatio ?: providerRatio).coerceIn(0.8f, 2.4f)
                val frameHeight = (maxWidth / ratio).coerceIn(120.dp, 184.dp)
                Box(
                    modifier = Modifier.fillMaxWidth().height(frameHeight)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(10.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (provider.appIcon != null) {
                                Image(provider.appIcon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(44.dp))
                            } else {
                                Box(
                                    Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(provider.widgetLabel.take(1), color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Text(
                                stringResource(R.string.widget_preview_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(provider.widgetLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(widgetSizeLabel(provider), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        stringResource(R.string.add),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun widgetSizeLabel(provider: WidgetProvider): String = when {
    provider.minWidthDp >= 360 && provider.minHeightDp < 180 -> stringResource(R.string.widget_size_wide)
    provider.minHeightDp >= 280 || provider.minWidthDp >= 360 -> stringResource(R.string.widget_size_large)
    provider.minWidthDp >= 260 || provider.minHeightDp >= 120 -> stringResource(R.string.widget_size_medium)
    else -> stringResource(R.string.widget_size_small)
}

@Composable
internal fun NotificationSettingsScreen(
    viewModel: LauncherViewModel,
    state: LauncherUiState,
    onOpenListenerSettings: () -> Unit,
) {
    SettingsPage(stringResource(R.string.notifications), viewModel::goBack) {
        Text(stringResource(R.string.notification_access_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingSwitchRow(
            label = stringResource(R.string.enable_notifications),
            summary = stringResource(R.string.enable_notifications_summary),
            checked = state.settings.notificationsEnabled,
            onCheckedChange = viewModel::setNotificationsEnabled,
        )
        SettingSwitchRow(
            label = stringResource(R.string.media_player),
            summary = stringResource(R.string.media_player_summary),
            checked = state.settings.mediaPlayerEnabled,
            onCheckedChange = viewModel::setMediaPlayerEnabled,
        )
        if (state.settings.mediaPlayerEnabled && !state.hasNotificationAccess) {
            Text(
                stringResource(R.string.media_player_access_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(if (state.hasNotificationAccess) R.string.notification_access_granted else R.string.notification_access_not_granted),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenListenerSettings) { Text(stringResource(R.string.open_notification_access_settings)) }
        ChoiceRow(
            title = stringResource(R.string.notification_indicator),
            selected = state.settings.notificationIndicatorStyle,
            choices = listOf(
                NotificationIndicatorStyle.Off to stringResource(R.string.off),
                NotificationIndicatorStyle.Dot to stringResource(R.string.dot),
                NotificationIndicatorStyle.Count to stringResource(R.string.count),
            ),
            onSelect = viewModel::setNotificationIndicatorStyle,
        )
        SettingSwitchRow(
            label = stringResource(R.string.show_notification_content),
            summary = stringResource(R.string.show_notification_content_summary),
            checked = state.settings.notificationPreviewContent,
            onCheckedChange = viewModel::setNotificationPreviewContent,
        )
        SettingSwitchRow(
            label = stringResource(R.string.show_silent_notifications),
            checked = state.settings.showSilentNotifications,
            onCheckedChange = viewModel::setShowSilentNotifications,
        )
        Spacer(Modifier.height(LauncherDimens.SectionSpacing))
    }
}

@Composable
internal fun HiddenAppsScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = LauncherDimens.ScreenHorizontalPadding)) {
        ScreenTopBar(stringResource(R.string.hidden_apps), viewModel::goBack)
        if (state.hiddenApps.isEmpty()) {
            EmptyMessage(stringResource(R.string.no_hidden_apps))
        } else {
            LazyColumn {
                items(state.hiddenApps, key = LauncherApp::id) { app ->
                    HiddenAppRow(app, state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun HiddenAppRow(app: LauncherApp, state: LauncherUiState, viewModel: LauncherViewModel) {
    val appName = appDisplayName(app, state)
    val restoreDescription = stringResource(R.string.restore_hidden_app, appName)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = LauncherDimens.AppRowHeight).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, viewModel, state.settings, state.iconRevision)
        Text(
            text = appName,
            modifier = Modifier.weight(1f).padding(start = LauncherDimens.CompactSpacing),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = { viewModel.toggleHidden(app) }, modifier = Modifier.semantics { contentDescription = restoreDescription }) {
            Text(stringResource(R.string.restore_app))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FavoriteManagementScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    val orderedFavorites = remember(state.favorites) { mutableStateListOf<LauncherApp>().apply { addAll(state.favorites) } }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dragHint = stringResource(R.string.favorite_drag_hint)

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = LauncherDimens.ScreenHorizontalPadding)) {
        ScreenTopBar(stringResource(R.string.manage_favorites), viewModel::goBack)
        if (orderedFavorites.isEmpty()) {
            EmptyMessage(stringResource(R.string.no_favorites))
        } else {
            LazyColumn(state = listState) {
                itemsIndexed(orderedFavorites, key = { _, app -> app.id }) { index, app ->
                    val isDragging = draggingId == app.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = LauncherDimens.AppRowHeight)
                            .animateItem()
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffset else 0f
                                scaleX = if (isDragging) 1.025f else 1f
                                scaleY = if (isDragging) 1.025f else 1f
                                alpha = if (isDragging) 0.88f else 1f
                            }
                            .pointerInput(app.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = app.id
                                        dragOffset = 0f
                                        NorynHaptics.dragStart(haptics)
                                    },
                                    onDragEnd = {
                                        viewModel.saveFavoriteOrder(orderedFavorites.map(LauncherApp::id))
                                        draggingId = null
                                        dragOffset = 0f
                                        NorynHaptics.reorder(haptics)
                                    },
                                    onDragCancel = {
                                        viewModel.saveFavoriteOrder(orderedFavorites.map(LauncherApp::id))
                                        draggingId = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val source = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == app.id }
                                            ?: return@detectDragGesturesAfterLongPress
                                        val draggedCenter = source.offset + dragOffset + source.size / 2f
                                        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                            item.key != app.id && draggedCenter >= item.offset && draggedCenter < item.offset + item.size
                                        } ?: return@detectDragGesturesAfterLongPress
                                        val from = orderedFavorites.indexOfFirst { it.id == app.id }
                                        val to = target.index
                                        if (from in orderedFavorites.indices && to in orderedFavorites.indices && from != to) {
                                            val appToMove = orderedFavorites.removeAt(from)
                                            orderedFavorites.add(to, appToMove)
                                            dragOffset -= (target.offset - source.offset).toFloat()
                                            NorynHaptics.reorder(haptics)
                                        }
                                    },
                                )
                            }
                            .semantics { contentDescription = dragHint },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app, viewModel, state.settings, state.iconRevision)
                        Text(
                            text = appDisplayName(app, state),
                            modifier = Modifier.weight(1f).padding(start = LauncherDimens.CompactSpacing),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ReorderButton(NorynGlyph.Up, stringResource(R.string.move_up), index > 0) {
                            if (index > 0) {
                                val moved = orderedFavorites.removeAt(index)
                                orderedFavorites.add(index - 1, moved)
                                viewModel.saveFavoriteOrder(orderedFavorites.map(LauncherApp::id))
                            }
                        }
                        ReorderButton(NorynGlyph.Down, stringResource(R.string.move_down), index < orderedFavorites.lastIndex) {
                            if (index < orderedFavorites.lastIndex) {
                                val moved = orderedFavorites.removeAt(index)
                                orderedFavorites.add(index + 1, moved)
                                viewModel.saveFavoriteOrder(orderedFavorites.map(LauncherApp::id))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
internal fun AppEditScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    val app = state.editingApp
    if (app == null) {
        SettingsPage(stringResource(R.string.edit_app), viewModel::goBack) {
            EmptyMessage(stringResource(R.string.no_apps))
        }
        return
    }
    val persistedName = state.settings.customNames[app.id].orEmpty()
    var name by remember(app.id, persistedName) { mutableStateOf(persistedName.ifBlank { app.label }) }
    val packName = state.settings.iconPackPackageName
    val packLabel = state.iconPacks.firstOrNull { it.packageName == packName }?.label
        ?: stringResource(R.string.system_icons)
    val override = state.settings.iconOverrides[app.id]
    val usingSystemIcon = override == "@system"
    SettingsPage(stringResource(R.string.edit_app), viewModel::goBack) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(app, viewModel, state.settings, state.iconRevision, 64.dp)
            Text(
                appDisplayName(app, state),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(R.string.custom_app_name),
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        TextField(
            value = name,
            onValueChange = {
                name = it.take(48)
                viewModel.setCustomAppName(app, name)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(LauncherDimens.PanelCornerRadius),
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { name = app.label; viewModel.resetCustomAppName(app) }) { Text(stringResource(R.string.reset_name)) }
        }
        SettingsSectionTitle(stringResource(R.string.icon))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, viewModel, state.settings, state.iconRevision, 48.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.current_icon_pack, packLabel), style = MaterialTheme.typography.bodyLarge)
                if (usingSystemIcon) Text(
                    stringResource(R.string.selected_system_icon),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (packName.isBlank()) {
            SettingsLinkRow(stringResource(R.string.choose_icon_pack), stringResource(R.string.system_icons), viewModel::openIconPacks)
        } else {
            SettingsLinkRow(stringResource(R.string.choose_icon), onClick = viewModel::openIconSelection)
            TextButton(onClick = { viewModel.resetAppIcon(app) }, enabled = override != null && !usingSystemIcon) {
                Text(stringResource(R.string.use_pack_default))
            }
            TextButton(onClick = { viewModel.useSystemAppIcon(app) }, enabled = !usingSystemIcon) {
                Text(stringResource(R.string.use_system_icon))
            }
        }
    }
}

@Composable
internal fun IconSelectionScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    val packName = state.settings.iconPackPackageName
    val selectIconDescription = stringResource(R.string.select_icon)
    SettingsPage(stringResource(R.string.choose_icon), viewModel::goBack, scrollable = false) {
        when {
            packName.isBlank() -> EmptyMessage(stringResource(R.string.choose_icon_pack_first))
            state.isLoadingIconChoices -> EmptyMessage(stringResource(R.string.loading))
            state.iconChoices.isEmpty() -> EmptyMessage(stringResource(R.string.no_pack_icons))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(72.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(LauncherDimens.CompactSpacing),
                verticalArrangement = Arrangement.spacedBy(LauncherDimens.CompactSpacing),
                contentPadding = PaddingValues(bottom = LauncherDimens.SectionSpacing),
            ) {
                items(state.iconChoices, key = { it }) { drawableName ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.chooseIcon(drawableName) }
                            .semantics { contentDescription = selectIconDescription },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconPackPreview(packName, drawableName, viewModel, state.iconRevision, 48.dp)
                        Text(
                            drawableName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = LauncherDimens.ScreenHorizontalPadding)) {
        ScreenTopBar(title, onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = LauncherDimens.SectionSpacing)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(LauncherDimens.CompactSpacing),
            content = content,
        )
    }
}

@Composable
private fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(LauncherDimens.SectionHeadingHeight + LauncherDimens.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(NorynGlyph.Back, stringResource(R.string.back), onBack)
        Text(
            text = title,
            modifier = Modifier.padding(start = LauncherDimens.CompactSpacing),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = LauncherDimens.SectionSpacing, bottom = LauncherDimens.CompactSpacing),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> ChoiceRow(
    title: String,
    selected: T,
    choices: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var sheetOpen by remember(title) { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.first == selected }?.second.orEmpty()
    val layoutDirection = LocalLayoutDirection.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LauncherDimens.SettingsRowHeight)
            .clickable { sheetOpen = true }
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            selectedLabel,
            modifier = Modifier.widthIn(max = 150.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (layoutDirection == LayoutDirection.Rtl) "‹" else "›",
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(horizontal = 24.dp).padding(bottom = 16.dp),
            ) {
                Text(title, modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
                choices.forEachIndexed { index, (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                            onSelect(value)
                            sheetOpen = false
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        RadioButton(selected = selected == value, onClick = {
                            onSelect(value)
                            sheetOpen = false
                        })
                    }
                    if (index < choices.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun sizeChoices() = listOf(
    SizePreset.Small to stringResource(R.string.small),
    SizePreset.Default to stringResource(R.string.default_size),
    SizePreset.Large to stringResource(R.string.large),
)

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LauncherDimens.SettingsRowHeight)
            .clickable { onCheckedChange(!checked) }
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = LauncherDimens.CompactSpacing)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsLinkRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    val layoutDirection = LocalLayoutDirection.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LauncherDimens.SettingsRowHeight)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = LauncherDimens.CompactSpacing)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            if (layoutDirection == LayoutDirection.Rtl) "‹" else "›",
            modifier = Modifier.padding(start = LauncherDimens.CompactSpacing),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IconPackChoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LauncherDimens.SettingsRowHeight)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = LauncherDimens.CompactSpacing)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ReorderButton(glyph: NorynGlyph, description: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(LauncherDimens.TouchTarget)) {
        Glyph(glyph, Modifier.size(LauncherDimens.GlyphSize), description)
    }
}
