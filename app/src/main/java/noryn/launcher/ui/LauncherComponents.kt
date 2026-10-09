package noryn.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import noryn.launcher.R
import noryn.launcher.core.model.LauncherApp
import noryn.launcher.launcher.presentation.LauncherUiState
import noryn.launcher.launcher.presentation.LauncherViewModel
import noryn.launcher.ui.theme.LauncherDimens

@Composable
internal fun appDisplayName(app: LauncherApp, state: LauncherUiState): String {
    val duplicate = state.duplicateAppDetails[app.id] ?: return app.label
    val profileName = if (duplicate.profileSerial != null) {
        stringResource(R.string.profile_number, duplicate.profileSerial)
    } else {
        null
    }
    val details = listOfNotNull(
        app.packageName.takeIf { duplicate.showPackageName },
        app.activityName.substringAfterLast('.').takeIf { duplicate.showActivityName },
        profileName,
    )
    if (details.isEmpty()) return app.label
    return stringResource(R.string.duplicate_app_label, app.label, details.joinToString(" · "))
}

@Composable
internal fun AppIcon(
    app: LauncherApp,
    viewModel: LauncherViewModel,
    size: Dp = LauncherDimens.AppIconSize,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = app.id, key2 = viewModel) {
        value = withContext(Dispatchers.IO) { viewModel.loadIcon(app) }
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
            text = app.label.firstOrNull()?.uppercase() ?: "•",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppActionSurface(
    app: LauncherApp,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenInfo: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var menuExpanded by remember(app.id) { mutableStateOf(false) }
    Box(
        modifier = modifier.combinedClickable(
            onClick = onOpen,
            onLongClick = { menuExpanded = true },
        ),
    ) {
        content()
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open)) },
                onClick = { menuExpanded = false; onOpen() },
            )
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (isFavorite) R.string.remove_favorite else R.string.add_favorite))
                },
                onClick = { menuExpanded = false; onToggleFavorite() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_info)) },
                onClick = { menuExpanded = false; onOpenInfo() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.hide_app)) },
                onClick = { menuExpanded = false; onHide() },
            )
        }
    }
}

@Composable
internal fun LauncherAppRow(
    app: LauncherApp,
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    showLabel: Boolean = true,
    isFavorite: Boolean = app.id in state.settings.favoriteIds,
    onOpen: () -> Unit = { viewModel.launch(app) },
    onToggleFavorite: () -> Unit = { viewModel.toggleFavorite(app) },
    onOpenInfo: () -> Unit = { viewModel.openAppInfo(app) },
    onHide: () -> Unit = { viewModel.toggleHidden(app) },
) {
    val appName = appDisplayName(app, state)
    AppActionSurface(
        app = app,
        isFavorite = isFavorite,
        onOpen = onOpen,
        onToggleFavorite = onToggleFavorite,
        onOpenInfo = onOpenInfo,
        onHide = onHide,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LauncherDimens.AppRowHeight)
            .semantics(mergeDescendants = true) { contentDescription = appName },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LauncherDimens.AppRowHeight)
                .padding(vertical = LauncherDimens.CompactSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app, viewModel)
            if (showLabel) {
                Spacer(Modifier.width(LauncherDimens.SectionSpacing / 2))
                Text(
                    text = appName,
                    style = MaterialTheme.typography.bodyLarge,
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
) {
    val appName = appDisplayName(app, state)
    AppActionSurface(
        app = app,
        isFavorite = true,
        onOpen = { viewModel.launch(app) },
        onToggleFavorite = { viewModel.toggleFavorite(app) },
        onOpenInfo = { viewModel.openAppInfo(app) },
        onHide = { viewModel.toggleHidden(app) },
        modifier = Modifier
            .width(LauncherDimens.FavoriteTileWidth)
            .heightIn(min = LauncherDimens.FavoriteTileHeight)
            .clip(RoundedCornerShape(LauncherDimens.PanelCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics(mergeDescendants = true) { contentDescription = appName },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = LauncherDimens.CompactSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LauncherDimens.CompactSpacing),
        ) {
            AppIcon(app, viewModel, LauncherDimens.FavoriteIconSize)
            if (state.settings.showAppLabels) {
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

@Composable
internal fun AppRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = LauncherDimens.AppIconSize + LauncherDimens.SectionSpacing / 2),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        thickness = LauncherDimens.DividerThickness,
    )
}

internal enum class NorynGlyph {
    Search,
    Settings,
    Back,
    Up,
    Down,
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
internal fun Glyph(glyph: NorynGlyph, modifier: Modifier = Modifier, description: String? = null) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.background
    val direction = LocalLayoutDirection.current
    Canvas(
        modifier = modifier.then(
            if (description == null) Modifier else Modifier.semantics { contentDescription = description },
        ),
    ) {
        val stroke = size.minDimension * 0.075f
        when (glyph) {
            NorynGlyph.Search -> {
                val radius = size.minDimension * 0.28f
                val center = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.42f)
                drawCircle(color, radius, center, style = Stroke(stroke))
                drawLine(
                    color,
                    androidx.compose.ui.geometry.Offset(center.x + radius * 0.72f, center.y + radius * 0.72f),
                    androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.88f),
                    stroke,
                    cap = StrokeCap.Round,
                )
            }
            NorynGlyph.Settings -> {
                val ys = listOf(0.25f, 0.5f, 0.75f)
                val knobs = listOf(0.38f, 0.68f, 0.48f)
                ys.forEachIndexed { index, y ->
                    drawLine(
                        color,
                        androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * y),
                        androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * y),
                        stroke,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        background,
                        size.minDimension * 0.11f,
                        androidx.compose.ui.geometry.Offset(size.width * knobs[index], size.height * y),
                    )
                    drawCircle(
                        color,
                        size.minDimension * 0.11f,
                        androidx.compose.ui.geometry.Offset(size.width * knobs[index], size.height * y),
                        style = Stroke(stroke),
                    )
                }
            }
            NorynGlyph.Back -> {
                val mirror = direction == LayoutDirection.Rtl
                val left = if (mirror) size.width * 0.82f else size.width * 0.18f
                val right = if (mirror) size.width * 0.18f else size.width * 0.82f
                drawLine(
                    color,
                    androidx.compose.ui.geometry.Offset(right, size.height * 0.5f),
                    androidx.compose.ui.geometry.Offset(left, size.height * 0.5f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                val arrow = Path().apply {
                    moveTo(right - (right - left) * 0.42f, size.height * 0.2f)
                    lineTo(left, size.height * 0.5f)
                    lineTo(right - (right - left) * 0.42f, size.height * 0.8f)
                }
                drawPath(arrow, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            NorynGlyph.Up, NorynGlyph.Down -> {
                val pointsUp = glyph == NorynGlyph.Up
                val centerY = if (pointsUp) size.height * 0.36f else size.height * 0.64f
                val tailY = if (pointsUp) size.height * 0.78f else size.height * 0.22f
                val path = Path().apply {
                    moveTo(size.width * 0.2f, if (pointsUp) centerY + size.height * 0.15f else centerY - size.height * 0.15f)
                    lineTo(size.width * 0.5f, centerY)
                    lineTo(size.width * 0.8f, if (pointsUp) centerY + size.height * 0.15f else centerY - size.height * 0.15f)
                    moveTo(size.width * 0.5f, centerY)
                    lineTo(size.width * 0.5f, tailY)
                }
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}
