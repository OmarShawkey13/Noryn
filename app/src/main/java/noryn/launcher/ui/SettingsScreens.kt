package noryn.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import noryn.launcher.BuildConfig
import noryn.launcher.R
import noryn.launcher.core.model.LauncherApp
import noryn.launcher.core.model.ThemeChoice
import noryn.launcher.launcher.presentation.LauncherUiState
import noryn.launcher.launcher.presentation.LauncherViewModel
import noryn.launcher.ui.theme.LauncherDimens

@Composable
internal fun SettingsScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LauncherDimens.ScreenHorizontalPadding),
    ) {
        item { ScreenTopBar(stringResource(R.string.settings), viewModel::goBack) }
        item {
            SettingsSectionTitle(stringResource(R.string.appearance))
            ThemeChoiceRow(state.settings.themeChoice, viewModel::setTheme)
            SettingSwitchRow(
                label = stringResource(R.string.show_app_labels),
                checked = state.settings.showAppLabels,
                onCheckedChange = viewModel::setShowAppLabels,
            )
            SettingSwitchRow(
                label = stringResource(R.string.show_alphabet_index),
                checked = state.settings.showAlphabetIndex,
                onCheckedChange = viewModel::setShowAlphabetIndex,
            )
        }
        item {
            SettingsSectionTitle(stringResource(R.string.home_section))
            SettingSwitchRow(
                label = stringResource(R.string.show_clock),
                checked = state.settings.showClock,
                onCheckedChange = viewModel::setShowClock,
            )
            SettingSwitchRow(
                label = stringResource(R.string.show_date),
                checked = state.settings.showDate,
                onCheckedChange = viewModel::setShowDate,
            )
            SettingsLinkRow(
                title = stringResource(R.string.manage_favorites),
                onClick = viewModel::openFavoriteManagement,
            )
        }
        item {
            SettingsSectionTitle(stringResource(R.string.apps_section))
            SettingsLinkRow(
                title = stringResource(R.string.hidden_apps),
                onClick = viewModel::openHiddenApps,
            )
        }
        item {
            SettingsSectionTitle(stringResource(R.string.about))
            Column(modifier = Modifier.padding(vertical = LauncherDimens.CompactSpacing)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(LauncherDimens.SectionSpacing))
        }
    }
}

@Composable
internal fun HiddenAppsScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LauncherDimens.ScreenHorizontalPadding),
    ) {
        ScreenTopBar(stringResource(R.string.hidden_apps), viewModel::goBack)
        if (state.hiddenApps.isEmpty()) {
            EmptyMessage(stringResource(R.string.no_hidden_apps))
        } else {
            LazyColumn {
                itemsIndexed(state.hiddenApps, key = { _, app -> app.id }) { _, app ->
                    HiddenAppRow(app, state, viewModel)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
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
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LauncherDimens.AppRowHeight)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, viewModel)
        Text(
            text = appName,
            modifier = Modifier
                .weight(1f)
                .padding(start = LauncherDimens.CompactSpacing),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(
            onClick = { viewModel.toggleHidden(app) },
            modifier = Modifier.semantics { contentDescription = restoreDescription },
        ) {
            Text(stringResource(R.string.restore_app))
        }
    }
}

@Composable
internal fun FavoriteManagementScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LauncherDimens.ScreenHorizontalPadding),
    ) {
        ScreenTopBar(stringResource(R.string.manage_favorites), viewModel::goBack)
        if (state.favorites.isEmpty()) {
            EmptyMessage(stringResource(R.string.no_favorites))
        } else {
            LazyColumn {
                itemsIndexed(state.favorites, key = { _, app -> app.id }) { index, app ->
                    FavoriteOrderRow(
                        app = app,
                        state = state,
                        viewModel = viewModel,
                        onMoveUp = { viewModel.moveFavorite(app, -1) },
                        onMoveDown = { viewModel.moveFavorite(app, 1) },
                        canMoveUp = index > 0,
                        canMoveDown = index < state.favorites.lastIndex,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun FavoriteOrderRow(
    app: LauncherApp,
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LauncherDimens.AppRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, viewModel)
        Text(
            text = appDisplayName(app, state),
            modifier = Modifier
                .weight(1f)
                .padding(start = LauncherDimens.CompactSpacing),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ReorderButton(
            glyph = NorynGlyph.Up,
            description = stringResource(R.string.move_up),
            enabled = canMoveUp,
            onClick = onMoveUp,
        )
        ReorderButton(
            glyph = NorynGlyph.Down,
            description = stringResource(R.string.move_down),
            enabled = canMoveDown,
            onClick = onMoveDown,
        )
    }
}

@Composable
private fun ReorderButton(glyph: NorynGlyph, description: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(LauncherDimens.TouchTarget),
    ) {
        Glyph(glyph, Modifier.size(LauncherDimens.GlyphSize), description)
    }
}

@Composable
private fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LauncherDimens.SectionHeadingHeight + LauncherDimens.TouchTarget),
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
private fun ThemeChoiceRow(choice: ThemeChoice, onSelect: (ThemeChoice) -> Unit) {
    val choices = listOf(
        ThemeChoice.System to stringResource(R.string.theme_system),
        ThemeChoice.Light to stringResource(R.string.theme_light),
        ThemeChoice.Dark to stringResource(R.string.theme_dark),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LauncherDimens.CompactSpacing),
    ) {
        choices.forEach { (value, label) ->
            FilterChip(
                selected = choice == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LauncherDimens.SettingsRowHeight)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsLinkRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LauncherDimens.SettingsRowHeight)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
