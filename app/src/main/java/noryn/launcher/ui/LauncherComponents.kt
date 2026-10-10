package noryn.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement.spacedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import noryn.launcher.R
import noryn.launcher.core.model.LauncherApp
import noryn.launcher.core.model.LauncherSettings
import noryn.launcher.core.model.LauncherShortcut
import noryn.launcher.core.model.LauncherProfileType
import noryn.launcher.core.model.NotificationIndicatorStyle
import noryn.launcher.notifications.NotificationEntry
import noryn.launcher.notifications.NotificationRepository
import noryn.launcher.notifications.NotificationSnapshot
import noryn.launcher.launcher.presentation.LauncherUiState
import noryn.launcher.launcher.presentation.LauncherViewModel
import noryn.launcher.ui.theme.LauncherDimens
import java.text.DateFormat
import java.util.Date

internal val LocalNotificationSnapshot = compositionLocalOf { NotificationSnapshot() }
internal val LocalShortcutRevision = compositionLocalOf { 0L }

@Composable
internal fun appDisplayName(app: LauncherApp, state: LauncherUiState): String {
    return state.settings.customNames[app.id]?.takeIf(String::isNotBlank) ?: app.label
}

@Composable
private fun appAccessibilityDescription(app: LauncherApp, state: LauncherUiState, appName: String): String {
    val profileLabel = if (app.profileType != LauncherProfileType.Personal) {
        when (app.profileType) {
            LauncherProfileType.Work -> stringResource(R.string.profile_work)
            LauncherProfileType.Private -> stringResource(R.string.profile_private)
            LauncherProfileType.Clone -> stringResource(R.string.profile_clone)
            LauncherProfileType.Other -> stringResource(R.string.profile_other)
            LauncherProfileType.Personal -> null
        }
    } else {
        null
    }
    val indicatorEnabled = state.settings.notificationsEnabled &&
        state.settings.notificationIndicatorStyle != NotificationIndicatorStyle.Off
    val notificationCount = if (indicatorEnabled) {
        LocalNotificationSnapshot.current.visibleForApp(
            app.userSerial,
            app.packageName,
            state.settings.showSilentNotifications,
        ).size
    } else {
        0
    }
    val notificationLabel = if (notificationCount > 0) {
        pluralStringResource(R.plurals.notification_count_description, notificationCount, notificationCount)
    } else {
        null
    }
    return listOfNotNull(appName, profileLabel, notificationLabel).joinToString(", ")
}

@Composable
internal fun AppIcon(
    app: LauncherApp,
    viewModel: LauncherViewModel,
    settings: LauncherSettings,
    iconRevision: Long,
    size: Dp = LauncherDimens.AppIconSize,
) {
    val iconStateKey = "$iconRevision:${settings.iconPackPackageName}:${settings.iconOverrides[app.id].orEmpty()}"
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = app.id, key2 = iconStateKey) {
        value = withContext(Dispatchers.IO) { viewModel.loadIcon(app, settings) }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(LauncherDimens.IconCornerRadius))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        } ?: Text(
            text = (settings.customNames[app.id]?.takeIf(String::isNotBlank) ?: app.label)
                .firstOrNull()?.uppercase() ?: "•",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        val snapshot = LocalNotificationSnapshot.current
        val notifications = snapshot.visibleForApp(app.userSerial, app.packageName, settings.showSilentNotifications)
        if (settings.notificationsEnabled && settings.notificationIndicatorStyle != NotificationIndicatorStyle.Off && notifications.isNotEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(1.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                if (settings.notificationIndicatorStyle == NotificationIndicatorStyle.Dot) {
                    Box(Modifier.size(9.dp))
                } else {
                    Text(
                        text = notifications.size.coerceAtMost(99).toString(),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
        val profileBadge = when (app.profileType) {
            LauncherProfileType.Work -> "W"
            LauncherProfileType.Private -> "P"
            LauncherProfileType.Clone -> "C"
            else -> null
        }
        profileBadge?.let {
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(1.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(it, modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun IconPackPreview(
    packageName: String,
    drawableName: String,
    viewModel: LauncherViewModel,
    iconRevision: Long,
    size: Dp = LauncherDimens.AppIconSize,
) {
    val iconStateKey = "$iconRevision:$drawableName"
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = packageName, key2 = iconStateKey) {
        value = withContext(Dispatchers.IO) { viewModel.loadIconPackIcon(packageName, drawableName) }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(LauncherDimens.IconCornerRadius))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun AppActionSurface(
    app: LauncherApp,
    appName: String,
    isFavorite: Boolean,
    state: LauncherUiState,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenInfo: () -> Unit,
    onHide: () -> Unit,
    onEdit: () -> Unit,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var menuExpanded by remember(app.id) { mutableStateOf(false) }
    var shortcuts by remember(app.id) { mutableStateOf<List<LauncherShortcut>>(emptyList()) }
    var notificationsOpen by remember(app.id) { mutableStateOf(false) }
    val notificationSnapshot = LocalNotificationSnapshot.current
    val shortcutRevision = LocalShortcutRevision.current
    val appNotifications = notificationSnapshot.visibleForApp(
        app.userSerial,
        app.packageName,
        state.settings.showSilentNotifications,
    )
    val interactionSource = remember(app.id) { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (isPressed) 0.985f else 1f, label = "app-press")
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(menuExpanded, app.id, shortcutRevision) {
        if (menuExpanded) shortcuts = withContext(Dispatchers.IO) { viewModel.shortcuts(app) }
    }
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpen,
                onLongClick = {
                    NorynHaptics.dragStart(haptics)
                    menuExpanded = true
                },
            ),
    ) {
        content()
        if (menuExpanded) {
            ModalBottomSheet(
                onDismissRequest = { menuExpanded = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .heightIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app, viewModel, state.settings, state.iconRevision, 48.dp)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(appName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            when (app.profileType) {
                                LauncherProfileType.Personal -> Unit
                                LauncherProfileType.Work -> Text(stringResource(R.string.profile_work), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LauncherProfileType.Private -> Text(stringResource(R.string.profile_private), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LauncherProfileType.Clone -> Text(stringResource(R.string.profile_clone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LauncherProfileType.Other -> Text(stringResource(R.string.profile_other), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (shortcuts.isNotEmpty()) {
                        NorynSheetSectionTitle(stringResource(R.string.app_shortcuts))
                        shortcuts.forEach { shortcut ->
                            NorynSheetActionRow(
                                title = shortcut.label,
                                leading = {
                                    shortcut.icon?.let { bitmap ->
                                        Image(bitmap.asImageBitmap(), null, Modifier.size(24.dp))
                                    } ?: AppIcon(app, viewModel, state.settings, state.iconRevision, 24.dp)
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.launchShortcut(app, shortcut)
                                },
                            )
                        }
                    }
                    if (appNotifications.isNotEmpty() && state.settings.notificationsEnabled) {
                        NorynSheetSectionTitle(stringResource(R.string.notifications))
                        NorynSheetActionRow(
                            title = stringResource(R.string.notification_preview_count, appNotifications.size),
                            leading = {
                                TablerIcon(
                                    TablerIconName.Bell,
                                    Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = { menuExpanded = false; notificationsOpen = true },
                        )
                    }
                    NorynSheetSectionTitle(stringResource(R.string.actions))
                    NorynSheetActionRow(
                        title = stringResource(if (isFavorite) R.string.remove_favorite else R.string.add_favorite),
                        leading = {
                            TablerIcon(
                                if (isFavorite) TablerIconName.StarFilled else TablerIconName.Star,
                                Modifier.size(22.dp),
                            )
                        },
                        onClick = { menuExpanded = false; onToggleFavorite() },
                    )
                    NorynSheetActionRow(
                        title = stringResource(R.string.edit_app),
                        leading = { TablerIcon(TablerIconName.Pencil, Modifier.size(22.dp)) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    NorynSheetActionRow(
                        title = stringResource(R.string.app_info),
                        leading = { TablerIcon(TablerIconName.InfoCircle, Modifier.size(22.dp)) },
                        onClick = { menuExpanded = false; onOpenInfo() },
                    )
                    NorynSheetActionRow(
                        title = stringResource(R.string.hide_app),
                        leading = { TablerIcon(TablerIconName.EyeOff, Modifier.size(22.dp)) },
                        onClick = { menuExpanded = false; onHide() },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
    if (notificationsOpen) {
        NotificationPreviewDialog(
            appName = appName,
            entries = appNotifications,
            showContent = state.settings.notificationPreviewContent,
            onOpen = { entry ->
                notificationsOpen = false
                viewModel.openNotification(entry)
            },
            onDismissNotification = NotificationRepository::dismiss,
            onAction = { _, action, reply -> viewModel.sendNotificationAction(action, reply) },
            onClose = { notificationsOpen = false },
        )
    }
}

@Composable
private fun NorynSheetSectionTitle(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun NorynSheetActionRow(
    title: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { leading() }
        Text(
            title,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NotificationPreviewDialog(
    appName: String,
    entries: List<NotificationEntry>,
    showContent: Boolean,
    onOpen: (NotificationEntry) -> Unit,
    onDismissNotification: (String) -> Boolean,
    onAction: (NotificationEntry, noryn.launcher.notifications.NotificationAction, String?) -> Boolean,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.notification_preview_title, appName)) },
        text = {
            Column(
                modifier = Modifier.widthIn(max = 420.dp).heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = spacedBy(LauncherDimens.CompactSpacing),
            ) {
                entries.forEach { entry ->
                    var reply by remember(entry.key) { mutableStateOf("") }
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(entry) },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(LauncherDimens.PanelCornerRadius),
                    ) {
                        Column(Modifier.padding(LauncherDimens.CompactSpacing)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (showContent) entry.title.ifBlank { appName } else stringResource(R.string.notification_generic_title),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                )
                                TextButton(onClick = { onDismissNotification(entry.key) }) {
                                    Text(stringResource(R.string.dismiss))
                                }
                            }
                            if (showContent && entry.text.isNotBlank()) {
                                Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text(stringResource(R.string.notification_content_hidden), style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.postedAtMillis)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            entry.actions.forEach { action ->
                                if (action.supportsTextReply) {
                                    OutlinedTextField(
                                        value = reply,
                                        onValueChange = { reply = it.take(220) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(action.label) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                                        trailingIcon = {
                                            TextButton(onClick = { if (onAction(entry, action, reply)) reply = "" }) {
                                                Text(stringResource(R.string.send))
                                            }
                                        },
                                    )
                                } else {
                                    TextButton(onClick = { onAction(entry, action, null) }) { Text(action.label) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
internal fun LauncherAppRow(
    app: LauncherApp,
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    showLabel: Boolean = true,
    iconSize: Dp = LauncherDimens.AppIconSize,
    rowHeight: Dp = LauncherDimens.AppRowHeight,
    isFavorite: Boolean = app.id in state.settings.favoriteIds,
    onOpen: () -> Unit = { viewModel.launch(app) },
    onToggleFavorite: () -> Unit = { viewModel.toggleFavorite(app) },
    onOpenInfo: () -> Unit = { viewModel.openAppInfo(app) },
    onHide: () -> Unit = { viewModel.toggleHidden(app) },
) {
    val appName = appDisplayName(app, state)
    val accessibilityDescription = appAccessibilityDescription(app, state, appName)
    AppActionSurface(
        app = app,
        appName = appName,
        isFavorite = isFavorite,
        state = state,
        onOpen = onOpen,
        onToggleFavorite = onToggleFavorite,
        onOpenInfo = onOpenInfo,
        onHide = onHide,
        onEdit = { viewModel.openAppEditor(app) },
        viewModel = viewModel,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rowHeight)
            .semantics(mergeDescendants = true) { contentDescription = accessibilityDescription },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = rowHeight)
                .padding(vertical = LauncherDimens.CompactSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app, viewModel, state.settings, state.iconRevision, iconSize)
            if (showLabel) {
                Spacer(Modifier.width(LauncherDimens.SectionSpacing / 2))
                Text(
                    text = appName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun FavoriteTile(
    app: LauncherApp,
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    iconSize: Dp = LauncherDimens.FavoriteIconSize,
    showLabel: Boolean = state.settings.showAppLabels,
    isFavorite: Boolean = true,
) {
    val appName = appDisplayName(app, state)
    val accessibilityDescription = appAccessibilityDescription(app, state, appName)
    AppActionSurface(
        app = app,
        appName = appName,
        isFavorite = isFavorite,
        state = state,
        onOpen = { viewModel.launch(app) },
        onToggleFavorite = { viewModel.toggleFavorite(app) },
        onOpenInfo = { viewModel.openAppInfo(app) },
        onHide = { viewModel.toggleHidden(app) },
        onEdit = { viewModel.openAppEditor(app) },
        viewModel = viewModel,
        modifier = Modifier
            .width(LauncherDimens.FavoriteTileWidth)
            .heightIn(min = LauncherDimens.FavoriteTileHeight)
            .semantics(mergeDescendants = true) { contentDescription = accessibilityDescription },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = LauncherDimens.CompactSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LauncherDimens.CompactSpacing),
        ) {
            AppIcon(app, viewModel, state.settings, state.iconRevision, iconSize)
            if (showLabel) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = LauncherDimens.FavoriteLabelFontSize,
                        letterSpacing = 0.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal enum class NorynGlyph {
    Search,
    Clear,
    MediaPrevious,
    MediaPlay,
    MediaPause,
    MediaNext,
    Settings,
    Back,
    Up,
    Down,
    More,
}

@Composable
internal fun GlyphButton(
    glyph: NorynGlyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(LauncherDimens.TouchTarget)) {
        Glyph(glyph, Modifier.size(LauncherDimens.GlyphSize), description)
    }
}

@Composable
internal fun Glyph(
    glyph: NorynGlyph,
    modifier: Modifier = Modifier,
    description: String? = null,
    colorOverride: Color? = null,
) {
    val icon = when (glyph) {
        NorynGlyph.Search -> TablerIconName.Search
        NorynGlyph.Clear -> TablerIconName.Close
        NorynGlyph.MediaPrevious -> TablerIconName.PlayerTrackPrevious
        NorynGlyph.MediaPlay -> TablerIconName.PlayerPlay
        NorynGlyph.MediaPause -> TablerIconName.PlayerPause
        NorynGlyph.MediaNext -> TablerIconName.PlayerTrackNext
        NorynGlyph.Settings -> TablerIconName.Adjustments
        NorynGlyph.Back -> TablerIconName.ArrowLeft
        NorynGlyph.Up -> TablerIconName.ArrowUp
        NorynGlyph.Down -> TablerIconName.ArrowDown
        NorynGlyph.More -> TablerIconName.MoreVertical
    }
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    TablerIcon(
        name = icon,
        modifier = modifier.graphicsLayer {
            scaleX = if (glyph == NorynGlyph.Back && direction == androidx.compose.ui.unit.LayoutDirection.Rtl) -1f else 1f
        },
        contentDescription = description,
        tint = colorOverride ?: MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
