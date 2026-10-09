package noryn.launcher.ui

import android.os.Build
import android.widget.TextView
import android.appwidget.AppWidgetHostView
import androidx.compose.ui.viewinterop.AndroidView
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import noryn.launcher.R
import noryn.launcher.core.model.AppLabelMode
import noryn.launcher.core.model.ClockAlignment
import noryn.launcher.core.model.HomeGestureAction
import noryn.launcher.core.model.LauncherApp
import noryn.launcher.core.model.LauncherProfile
import noryn.launcher.core.model.LauncherProfileType
import noryn.launcher.core.model.RowSpacing
import noryn.launcher.core.model.SizePreset
import noryn.launcher.core.model.WidgetInstance
import noryn.launcher.core.model.WidgetSizePreset
import noryn.launcher.launcher.presentation.LauncherUiState
import noryn.launcher.launcher.presentation.LauncherViewModel
import noryn.launcher.launcher.presentation.AppSection
import noryn.launcher.media.MediaPlaybackSnapshot
import noryn.launcher.ui.theme.LauncherDimens
import noryn.launcher.ui.theme.LauncherMotion
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun HomeScreen(viewModel: LauncherViewModel, state: LauncherUiState, listState: LazyListState) {
    val mediaPlayback = viewModel.mediaPlayback.collectAsState().value
    val groups = state.profileGroups
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
    LaunchedEffect(locale) { viewModel.updateDisplayLocale(locale) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var selectedLetter by remember { mutableStateOf<String?>(null) }
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val hasMultipleProfileGroups = groups.count { it.profile != null } > 1
    fun showsProfileHeader(profile: LauncherProfile) = profile.type != LauncherProfileType.Personal || hasMultipleProfileGroups

    val appSectionIndex = remember(state.widgetInstances, state.favorites, state.suggestedApps) {
        2 + (if (state.widgetInstances.isNotEmpty()) 1 else 0) + (if (state.favorites.isNotEmpty()) 1 else 0) +
            (if (state.suggestedApps.isNotEmpty()) 1 else 0)
    }
    val indexLetters = remember(groups) { groups.flatMap { it.sections.map(AppSection::label) }.distinct() }
    val sectionIndices = remember(groups, state.widgetInstances, state.favorites, state.suggestedApps) {
        buildMap {
            var index = 1
            if (state.widgetInstances.isNotEmpty()) index++
            if (state.favorites.isNotEmpty()) index++
            if (state.suggestedApps.isNotEmpty()) index++
            index++
            groups.forEach { group ->
                if (group.profile?.let(::showsProfileHeader) == true) index++
                if (group.sections.isEmpty() && group.profile?.isAvailable == false) index++
                group.sections.forEach { section ->
                    putIfAbsent(section.label, index)
                    index += section.apps.size + 1
                }
            }
        }
    }

    fun scrollToLetter(letter: String) {
        val target = sectionIndices[letter] ?: return
        scope.launch { listState.scrollToItem(target) }
    }

    fun runGesture(action: HomeGestureAction) {
        when (action) {
            HomeGestureAction.OpenSearch -> viewModel.openSearch()
            HomeGestureAction.OpenAppList -> scope.launch { listState.animateScrollToItem(appSectionIndex) }
            HomeGestureAction.NoAction -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = safeInsets.calculateStartPadding(layoutDirection) + LauncherDimens.ScreenHorizontalPadding,
                    end = safeInsets.calculateEndPadding(layoutDirection) + LauncherDimens.ScreenHorizontalPadding +
                        if (state.settings.showAlphabetIndex) LauncherDimens.AlphabetTrackWidth else 0.dp,
                ),
            contentPadding = PaddingValues(
                top = safeInsets.calculateTopPadding() + LauncherDimens.ScreenVerticalPadding,
                bottom = safeInsets.calculateBottomPadding() + LauncherDimens.SectionSpacing,
            ),
        ) {
            item(key = "home_header") {
                HomeHeader(
                    state = state,
                    onOpenSearch = viewModel::openSearch,
                    onOpenSettings = viewModel::openSettings,
                    onOpenWidgets = viewModel::openWidgets,
                    onGesture = ::runGesture,
                    mediaPlayback = mediaPlayback.takeIf { state.settings.mediaPlayerEnabled },
                    onMediaOpen = viewModel::openMediaPlayer,
                    onMediaPlayPause = viewModel::toggleMediaPlayback,
                    onMediaPrevious = viewModel::skipMediaPrevious,
                    onMediaNext = viewModel::skipMediaNext,
                )
            }
            if (state.widgetInstances.isNotEmpty()) {
                item(key = "home_widgets") { HomeWidgetsSection(state, viewModel) }
            }
            if (state.favorites.isNotEmpty()) {
                item(key = "favorites") {
                    FavoritesSection(state, viewModel)
                }
            }
            if (state.suggestedApps.isNotEmpty()) {
                item(key = "suggested_apps") {
                    SuggestedAppsSection(state, viewModel)
                }
            }
            item(key = "all_apps") {
                SectionTitle(
                    text = stringResource(R.string.all_apps),
                    modifier = Modifier.padding(
                        top = if (state.favorites.isEmpty() && state.suggestedApps.isEmpty() && state.widgetInstances.isEmpty()) 12.dp else LauncherDimens.SectionSpacing,
                    ),
                )
            }
            if (state.isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = LauncherDimens.EmptyStateTopPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(LauncherDimens.TouchTarget / 2),
                            strokeWidth = LauncherDimens.LoadingIndicatorStroke,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else if (groups.isEmpty()) {
                item(key = "empty_apps") { EmptyMessage(stringResource(R.string.no_apps)) }
            } else {
                groups.forEach { group ->
                    group.profile?.takeIf(::showsProfileHeader)?.let { profile ->
                        item(key = "profile_heading_${group.key}") {
                            ProfileGroupHeader(profile, viewModel)
                        }
                        if (!profile.isAvailable) {
                            item(key = "profile_unavailable_${group.key}") {
                                ProfileAvailabilityRow(profile, viewModel)
                            }
                        }
                    }
                    group.sections.forEach { section ->
                        item(key = "section_${group.key}_${section.label}") { AlphabetSectionTitle(section.label) }
                        items(section.apps, key = LauncherApp::id) { app ->
                            LauncherAppRow(
                                app = app,
                                state = state,
                                viewModel = viewModel,
                                showLabel = when (state.settings.appLabelMode) {
                                    AppLabelMode.Always -> true
                                    AppLabelMode.FavoritesOnly, AppLabelMode.Never -> false
                                },
                                iconSize = appIconSize(state.settings.appIconSize),
                                rowHeight = appRowHeight(state.settings.rowSpacing),
                            )
                        }
                    }
                }
            }
        }

        if (state.settings.showAlphabetIndex && indexLetters.isNotEmpty()) {
            AlphabetIndex(
                letters = indexLetters,
                selectedLetter = selectedLetter,
                onSelectLetter = { letter ->
                    if (selectedLetter != letter) {
                        NorynHaptics.sectionTick(haptics)
                        selectedLetter = letter
                        scrollToLetter(letter)
                    }
                },
                onGestureEnd = { selectedLetter = null },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = safeInsets.calculateEndPadding(layoutDirection) + LauncherDimens.AlphabetSideInset + 4.dp)
                    .width(LauncherDimens.AlphabetTrackWidth)
                    .fillMaxHeight()
                    .padding(
                        top = safeInsets.calculateTopPadding() + LauncherDimens.ScreenVerticalPadding,
                        bottom = safeInsets.calculateBottomPadding() + LauncherDimens.ScreenVerticalPadding,
                    ),
            )
        }

        AnimatedVisibility(
            visible = selectedLetter != null,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = LauncherDimens.AlphabetTrackWidth + 18.dp),
            enter = fadeIn(tween(LauncherMotion.Fast)) + scaleIn(tween(LauncherMotion.Fast)),
            exit = fadeOut(tween(LauncherMotion.Fast)) + scaleOut(tween(LauncherMotion.Fast)),
        ) {
            Box(
                modifier = Modifier
                    .size(LauncherDimens.LetterIndicatorSize)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = selectedLetter.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    state: LauncherUiState,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWidgets: () -> Unit,
    onGesture: (HomeGestureAction) -> Unit,
    mediaPlayback: MediaPlaybackSnapshot?,
    onMediaOpen: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaPrevious: () -> Unit,
    onMediaNext: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val gestureThresholdPx = with(density) { LauncherDimens.AlphabetSwipeThreshold.toPx() }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(context) {
        while (true) {
            now = Date()
            val remainder = System.currentTimeMillis() % MILLIS_PER_MINUTE
            delay((MILLIS_PER_MINUTE - remainder).coerceAtLeast(1L).milliseconds)
        }
    }
    val timeText = remember(now, context, state.settings.showClock) {
        if (state.settings.showClock) DateFormat.getTimeFormat(context).format(now) else ""
    }
    val dateText = remember(now, context, state.settings.showDate) {
        if (state.settings.showDate) DateFormat.getMediumDateFormat(context).format(now) else ""
    }
    val searchDescription = stringResource(R.string.search)
    val settingsDescription = stringResource(R.string.settings)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = LauncherDimens.SectionSpacing)
            .pointerInput(state.settings.swipeDownAction, state.settings.swipeUpAction) {
                var dragDistance = 0f
                var triggered = false
                detectVerticalDragGestures(
                    onDragStart = { dragDistance = 0f; triggered = false },
                    onVerticalDrag = { change, amount ->
                        if (!triggered) dragDistance += amount
                        if (!triggered && dragDistance >= gestureThresholdPx) {
                            onGesture(state.settings.swipeDownAction)
                            triggered = true
                        } else if (!triggered && dragDistance <= -gestureThresholdPx) {
                            onGesture(state.settings.swipeUpAction)
                            triggered = true
                        }
                        change.consume()
                    },
                    onDragEnd = { dragDistance = 0f },
                    onDragCancel = { dragDistance = 0f },
                )
            }
            .pointerInput(state.settings.doubleTapAction) {
                detectTapGestures(
                    onDoubleTap = { onGesture(state.settings.doubleTapAction) },
                    onLongPress = { onOpenWidgets() },
                )
            },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.product_name),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlyphButton(NorynGlyph.Search, searchDescription, onOpenSearch)
            GlyphButton(NorynGlyph.Settings, settingsDescription, onOpenSettings)
        }
        Spacer(Modifier.height(LauncherDimens.SectionSpacing))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (state.settings.clockAlignment == ClockAlignment.Center) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            if (state.settings.showClock) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = clockFontSize(state.settings.clockSize),
                        lineHeight = clockFontSize(state.settings.clockSize) * 1.1f,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = if (state.settings.clockAlignment == ClockAlignment.Center) TextAlign.Center else TextAlign.Start,
                )
            }
            if (state.settings.showDate) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (state.settings.clockAlignment == ClockAlignment.Center) TextAlign.Center else TextAlign.Start,
                )
            }
        }
        if (mediaPlayback != null) {
            Spacer(Modifier.height(LauncherDimens.CompactSpacing))
            MediaPlayerCard(
                playback = mediaPlayback,
                onOpen = onMediaOpen,
                onPlayPause = onMediaPlayPause,
                onPrevious = onMediaPrevious,
                onNext = onMediaNext,
            )
        }
    }
}

@Composable
private fun MediaPlayerCard(
    playback: MediaPlaybackSnapshot,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val pauseLabel = stringResource(R.string.media_pause)
    val playLabel = stringResource(R.string.media_play)
    val nowPlayingLabel = stringResource(R.string.media_now_playing)
    val playbackLabel = if (playback.isPlaying) pauseLabel else playLabel

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    val artwork = playback.artwork
                    if (artwork != null) {
                        Image(
                            bitmap = artwork.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = "♫",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).padding(start = 14.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Text(
                            text = nowPlayingLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                        Text(
                            text = playback.appName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = playback.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        text = playback.artist.ifBlank { playback.appName },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MediaControlButton(
                        glyph = NorynGlyph.MediaPrevious,
                        label = stringResource(R.string.media_previous),
                        enabled = playback.canSkipPrevious,
                        onClick = onPrevious,
                    )
                    MediaControlButton(
                        glyph = if (playback.isPlaying) NorynGlyph.MediaPause else NorynGlyph.MediaPlay,
                        label = playbackLabel,
                        enabled = playback.canTogglePlayback,
                        emphasized = true,
                        onClick = onPlayPause,
                    )
                    MediaControlButton(
                        glyph = NorynGlyph.MediaNext,
                        label = stringResource(R.string.media_next),
                        enabled = playback.canSkipNext,
                        onClick = onNext,
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaControlButton(
    glyph: NorynGlyph,
    label: String,
    enabled: Boolean,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        emphasized -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = if (emphasized) Modifier.size(48.dp) else Modifier,
        shape = CircleShape,
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(if (emphasized) 48.dp else 44.dp)) {
            Glyph(glyph, Modifier.size(if (emphasized) 21.dp else 18.dp), label, colorOverride = contentColor)
        }
    }
}

@Composable
private fun ProfileGroupHeader(profile: LauncherProfile, viewModel: LauncherViewModel) {
    val title = when (profile.type) {
        LauncherProfileType.Personal -> stringResource(R.string.profile_personal)
        LauncherProfileType.Work -> stringResource(R.string.profile_work)
        LauncherProfileType.Private -> stringResource(R.string.profile_private)
        LauncherProfileType.Clone -> stringResource(R.string.profile_clone)
        LauncherProfileType.Other -> stringResource(R.string.profile_other)
    }
    val status = when (profile.type) {
        LauncherProfileType.Private if !profile.isAvailable -> stringResource(R.string.profile_locked)
        LauncherProfileType.Work if !profile.isAvailable -> stringResource(R.string.profile_paused)
        else -> null
    }
    val action = when (profile.type) {
        LauncherProfileType.Work -> if (profile.isAvailable) stringResource(R.string.pause) else null
        LauncherProfileType.Private -> if (profile.isAvailable) stringResource(R.string.lock) else null
        else -> null
    }
    val profileControlsSupported = profile.type != LauncherProfileType.Work || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    Column(Modifier.fillMaxWidth().padding(top = LauncherDimens.SectionSpacing / 2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            action?.let { label ->
                TextButton(
                    onClick = { viewModel.requestProfileAvailability(profile, !profile.isAvailable) },
                    enabled = profileControlsSupported,
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun ProfileAvailabilityRow(profile: LauncherProfile, viewModel: LauncherViewModel) {
    val label = if (profile.type == LauncherProfileType.Private) stringResource(R.string.unlock) else stringResource(R.string.resume)
    val profileControlsSupported = profile.type != LauncherProfileType.Work || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    Row(
        Modifier.fillMaxWidth().padding(vertical = LauncherDimens.CompactSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (profile.type == LauncherProfileType.Private) R.string.private_space_locked_summary else R.string.work_profile_paused_summary),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { viewModel.requestProfileAvailability(profile, true) }, enabled = profileControlsSupported) { Text(label) }
    }
}

@Composable
private fun HomeWidgetsSection(state: LauncherUiState, viewModel: LauncherViewModel) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widgetWidthDp = maxWidth.value.toInt().coerceAtLeast(120)
        Column(Modifier.fillMaxWidth().padding(top = LauncherDimens.CompactSpacing)) {
            state.widgetInstances.sortedBy(WidgetInstance::position).forEachIndexed { index, widget ->
                WidgetHostCard(widget, viewModel, index, state.widgetInstances.size, widgetWidthDp)
            }
        }
    }
}

@Composable
private fun WidgetHostCard(widget: WidgetInstance, viewModel: LauncherViewModel, index: Int, count: Int, widthDp: Int) {
    val unavailableText = stringResource(R.string.widget_provider_unavailable)
    val moveUpLabel = stringResource(R.string.move_up)
    val moveDownLabel = stringResource(R.string.move_down)
    val actionsLabel = stringResource(R.string.widget_actions)
    val unavailableTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    var actionsExpanded by remember(widget.appWidgetId) { mutableStateOf(false) }
    val baseHeight = when (widget.size) {
        WidgetSizePreset.Compact -> 120
        WidgetSizePreset.Standard -> 180
        WidgetSizePreset.Tall -> 248
    }
    val heightDp = maxOf(baseHeight, widget.providerMinHeightDp)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = LauncherDimens.TouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                widget.providerLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Box {
                GlyphButton(NorynGlyph.More, actionsLabel, { actionsExpanded = true })
                DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                    if (index > 0) {
                        DropdownMenuItem(
                            text = { Text(moveUpLabel) },
                            onClick = {
                                actionsExpanded = false
                                viewModel.reorderWidget(widget.appWidgetId, -1)
                            },
                        )
                    }
                    if (index < count - 1) {
                        DropdownMenuItem(
                            text = { Text(moveDownLabel) },
                            onClick = {
                                actionsExpanded = false
                                viewModel.reorderWidget(widget.appWidgetId, 1)
                            },
                        )
                    }
                    if (widget.providerAvailable && widget.supportsVerticalResize) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.resize)) },
                            onClick = {
                                actionsExpanded = false
                                viewModel.resizeWidget(widget.appWidgetId)
                            },
                        )
                    }
                    if (widget.providerAvailable && widget.supportsReconfigure) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.configure)) },
                            onClick = {
                                actionsExpanded = false
                                viewModel.reconfigureWidget(widget.appWidgetId)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove)) },
                        onClick = {
                            actionsExpanded = false
                            viewModel.removeWidget(widget.appWidgetId)
                        },
                    )
                }
            }
        }
        when {
            widget.isPending -> Text(stringResource(R.string.widget_setup_pending, widget.providerLabel), modifier = Modifier.padding(12.dp))
            !widget.providerAvailable -> Text(stringResource(R.string.widget_provider_unavailable), modifier = Modifier.padding(12.dp))
            else -> AndroidView(
                modifier = Modifier.fillMaxWidth().height(heightDp.dp),
                factory = { hostContext ->
                    viewModel.createWidgetHostView(hostContext, widget.appWidgetId)
                        ?: TextView(hostContext).apply {
                            text = unavailableText
                            setTextColor(unavailableTextColor)
                            gravity = android.view.Gravity.CENTER
                        }
                },
                update = { view ->
                    if (view is AppWidgetHostView) viewModel.resizeWidgetHostView(view, widthDp, heightDp)
                },
            )
        }
    }
}

@Composable
private fun FavoritesSection(state: LauncherUiState, viewModel: LauncherViewModel) {
    Column(modifier = Modifier.padding(top = LauncherDimens.SectionSpacing)) {
        SectionTitle(stringResource(R.string.favorites))
        Spacer(Modifier.height(LauncherDimens.CompactSpacing))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(LauncherDimens.CompactSpacing)) {
            items(state.favorites, key = LauncherApp::id) { app ->
                FavoriteTile(
                    app = app,
                    state = state,
                    viewModel = viewModel,
                    iconSize = favoriteIconSize(state.settings.appIconSize),
                    showLabel = state.settings.appLabelMode != AppLabelMode.Never,
                )
            }
        }
    }
}

@Composable
private fun SuggestedAppsSection(state: LauncherUiState, viewModel: LauncherViewModel) {
    Column(modifier = Modifier.padding(top = LauncherDimens.SectionSpacing)) {
        SectionTitle(stringResource(R.string.suggested_apps))
        Spacer(Modifier.height(LauncherDimens.CompactSpacing))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(LauncherDimens.CompactSpacing)) {
            items(state.suggestedApps, key = LauncherApp::id) { app ->
                FavoriteTile(
                    app = app,
                    state = state,
                    viewModel = viewModel,
                    iconSize = favoriteIconSize(state.settings.appIconSize),
                    showLabel = true,
                    isFavorite = false,
                )
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.height(LauncherDimens.SectionHeadingHeight),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun AlphabetSectionTitle(letter: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LauncherDimens.SectionHeadingHeight)
            .padding(top = LauncherDimens.CompactSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = LauncherDimens.AlphabetSectionHeadingFontSize),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(LauncherDimens.CompactSpacing))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            thickness = LauncherDimens.DividerThickness,
        )
    }
}

@Composable
internal fun EmptyMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LauncherDimens.EmptyStateTopPadding),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AlphabetIndex(
    letters: List<String>,
    selectedLetter: String?,
    onSelectLetter: (String) -> Unit,
    onGestureEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accessibilityLabel = stringResource(R.string.alphabet_navigation)
    val layoutDirection = LocalLayoutDirection.current
    val activeIndex = letters.indexOf(selectedLetter)
    val latestOnSelect by rememberUpdatedState(onSelectLetter)

    fun letterAt(y: Float, height: Float): String {
        val index = ((y / height.coerceAtLeast(1f)) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        return letters[index]
    }

    Column(
        modifier = modifier
            .semantics { contentDescription = accessibilityLabel }
            .pointerInput(letters) {
                detectDragGestures(
                    onDragStart = { offset -> latestOnSelect(letterAt(offset.y, size.height.toFloat())) },
                    onDragEnd = onGestureEnd,
                    onDragCancel = onGestureEnd,
                    onDrag = { change, _ ->
                        latestOnSelect(letterAt(change.position.y, size.height.toFloat()))
                        change.consume()
                    },
                )
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEachIndexed { index, letter ->
            val distance = if (activeIndex < 0) Int.MAX_VALUE else kotlin.math.abs(index - activeIndex)
            val emphasis = when (distance) {
                0 -> 1.28f
                1 -> 1.12f
                2 -> 1.04f
                else -> 1f
            }
            val animatedEmphasis by animateFloatAsState(
                targetValue = emphasis,
                animationSpec = spring(stiffness = 720f),
                label = "alphabet-emphasis",
            )
            Text(
                text = letter,
                modifier = Modifier
                    .clickable {
                        latestOnSelect(letter)
                        onGestureEnd()
                    }
                    .graphicsLayer {
                        val towardList = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) -1f else 1f
                        scaleX = animatedEmphasis
                        scaleY = animatedEmphasis
                        translationX = if (distance == 0) 3f * towardList else if (distance == 1) 1f * towardList else 0f
                    }
                    .semantics { contentDescription = letter },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = LauncherDimens.AlphabetLetterFontSize),
                fontWeight = if (letter == selectedLetter) FontWeight.Bold else FontWeight.Medium,
                color = if (letter == selectedLetter) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}

private fun appIconSize(size: SizePreset) = when (size) {
    SizePreset.Small -> 36.dp
    SizePreset.Default -> LauncherDimens.AppIconSize
    SizePreset.Large -> 52.dp
}

private fun favoriteIconSize(size: SizePreset) = when (size) {
    SizePreset.Small -> 34.dp
    SizePreset.Default -> LauncherDimens.FavoriteIconSize
    SizePreset.Large -> 48.dp
}

private fun appRowHeight(spacing: RowSpacing) = when (spacing) {
    RowSpacing.Compact -> 54.dp
    RowSpacing.Default -> LauncherDimens.AppRowHeight
    RowSpacing.Comfortable -> 74.dp
}

private fun clockFontSize(size: SizePreset) = when (size) {
    SizePreset.Small -> 42.sp
    SizePreset.Default -> 52.sp
    SizePreset.Large -> 60.sp
}

private const val MILLIS_PER_MINUTE = 60_000L
