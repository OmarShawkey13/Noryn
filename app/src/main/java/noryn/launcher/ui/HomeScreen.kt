package noryn.launcher.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import noryn.launcher.R
import noryn.launcher.core.model.LauncherApp
import noryn.launcher.launcher.presentation.LauncherUiState
import noryn.launcher.launcher.presentation.LauncherViewModel
import noryn.launcher.ui.theme.LauncherDimens
import noryn.launcher.ui.theme.LauncherMotion
import java.util.Date
import java.util.Locale

@Composable
internal fun HomeScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
    val alphabet = remember(locale.language) { alphabetFor(locale) }
    val groups = remember(state.visibleApps, alphabet, locale) {
        state.visibleApps.groupBy { sectionFor(it.label, alphabet, locale) }
            .toSortedMap(compareBy { letter -> alphabet.indexOf(letter).let { if (it < 0) alphabet.size else it } })
    }
    val sectionIndices = remember(groups, state.favorites) {
        buildMap {
            var index = 1
            if (state.favorites.isNotEmpty()) index++
            index++
            groups.forEach { (letter, group) ->
                put(letter, index)
                index += group.size + 1
            }
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedLetter by remember { mutableStateOf<String?>(null) }

    fun selectLetter(letter: String) {
        selectedLetter = letter
        val directIndex = sectionIndices[letter]
        val nearbyIndex = directIndex ?: alphabet.asSequence()
            .dropWhile { it != letter }
            .mapNotNull(sectionIndices::get)
            .firstOrNull()
            ?: alphabet.asReversed().asSequence().mapNotNull(sectionIndices::get).firstOrNull()
        nearbyIndex?.let { index -> scope.launch { listState.scrollToItem(index) } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = LauncherDimens.ScreenHorizontalPadding,
                    end = LauncherDimens.ScreenHorizontalPadding +
                        if (state.settings.showAlphabetIndex) LauncherDimens.AlphabetTrackWidth else 0.dp,
                ),
            contentPadding = PaddingValues(
                top = LauncherDimens.ScreenVerticalPadding,
                bottom = LauncherDimens.SectionSpacing,
            ),
        ) {
            item(key = "home_header") {
                HomeHeader(
                    showClock = state.settings.showClock,
                    showDate = state.settings.showDate,
                    onOpenSearch = viewModel::openSearch,
                    onOpenSettings = viewModel::openSettings,
                )
            }
            if (state.favorites.isNotEmpty()) {
                item(key = "favorites") {
                    FavoritesSection(state, viewModel)
                }
            }
            item(key = "all_apps") {
                SectionTitle(
                    text = stringResource(R.string.all_apps),
                    modifier = Modifier.padding(top = LauncherDimens.SectionSpacing),
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
                item(key = "empty_apps") {
                    EmptyMessage(stringResource(R.string.no_apps))
                }
            } else {
                groups.forEach { (letter, group) ->
                    item(key = "section_$letter") {
                        AlphabetSectionTitle(letter)
                    }
                    items(group, key = LauncherApp::id) { app ->
                        LauncherAppRow(
                            app = app,
                            state = state,
                            viewModel = viewModel,
                            showLabel = state.settings.showAppLabels,
                        )
                        AppRowDivider()
                    }
                }
            }
        }

        if (state.settings.showAlphabetIndex && groups.isNotEmpty()) {
            AlphabetIndex(
                letters = alphabet,
                selectedLetter = selectedLetter,
                onSelectLetter = ::selectLetter,
                onGestureEnd = { selectedLetter = null },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = LauncherDimens.AlphabetSideInset)
                    .width(LauncherDimens.AlphabetTrackWidth)
                    .fillMaxHeight()
                    .padding(vertical = LauncherDimens.ScreenVerticalPadding),
            )
        }

        AnimatedVisibility(
            visible = selectedLetter != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(LauncherMotion.Quick)) + scaleIn(tween(LauncherMotion.Quick)),
            exit = fadeOut(tween(LauncherMotion.Quick)) + scaleOut(tween(LauncherMotion.Quick)),
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
    showClock: Boolean,
    showDate: Boolean,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(context) {
        while (true) {
            now = Date()
            val remainder = System.currentTimeMillis() % MILLIS_PER_MINUTE
            delay((MILLIS_PER_MINUTE - remainder).coerceAtLeast(1L))
        }
    }
    val timeText = remember(now, context, showClock) {
        if (showClock) DateFormat.getTimeFormat(context).format(now) else ""
    }
    val dateText = remember(now, context, showDate) {
        if (showDate) DateFormat.getMediumDateFormat(context).format(now) else ""
    }
    val searchDescription = stringResource(R.string.search)
    val settingsDescription = stringResource(R.string.settings)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = LauncherDimens.CompactSpacing)
            .pointerInput(onOpenSearch) {
                var dragDistance = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onVerticalDrag = { change, amount ->
                        if (amount > 0f) dragDistance += amount
                        if (dragDistance > with(density) { LauncherDimens.AlphabetSwipeThreshold.toPx() }) {
                            dragDistance = 0f
                            onOpenSearch()
                        }
                        change.consume()
                    },
                )
            },
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.product_name),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (showClock) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            if (showDate) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphButton(NorynGlyph.Search, searchDescription, onOpenSearch)
            GlyphButton(NorynGlyph.Settings, settingsDescription, onOpenSettings)
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
                FavoriteTile(app, state, viewModel)
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

    fun letterAt(y: Float, height: Float): String {
        val index = ((y / height.coerceAtLeast(1f)) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        return letters[index]
    }

    Column(
        modifier = modifier
            .semantics { contentDescription = accessibilityLabel }
            .pointerInput(letters) {
                detectDragGestures(
                    onDragStart = { offset -> onSelectLetter(letterAt(offset.y, size.height.toFloat())) },
                    onDragEnd = onGestureEnd,
                    onDragCancel = onGestureEnd,
                    onDrag = { change, _ ->
                        onSelectLetter(letterAt(change.position.y, size.height.toFloat()))
                        change.consume()
                    },
                )
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Text(
                text = letter,
                modifier = Modifier.semantics { contentDescription = letter },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = LauncherDimens.AlphabetLetterFontSize),
                fontWeight = if (letter == selectedLetter) FontWeight.Bold else FontWeight.Medium,
                color = if (letter == selectedLetter) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}

private fun alphabetFor(locale: Locale): List<String> = if (locale.language == "ar") {
    ARABIC_ALPHABET
} else {
    LATIN_ALPHABET
}

private fun sectionFor(label: String, alphabet: List<String>, locale: Locale): String {
    val first = normalizeForInitial(label).firstOrNull()?.toString()?.uppercase(locale).orEmpty()
    if (first in alphabet) return first
    return "#"
}

private const val MILLIS_PER_MINUTE = 60_000L
private val LATIN_ALPHABET = ('A'..'Z').map(Char::toString) + "#"
private val ARABIC_ALPHABET = listOf("ا", "ب", "ت", "ث", "ج", "ح", "خ", "د", "ذ", "ر", "ز", "س", "ش", "ص", "ض", "ط", "ظ", "ع", "غ", "ف", "ق", "ك", "ل", "م", "ن", "ه", "و", "ي", "#")
private fun normalizeForInitial(label: String): String = java.text.Normalizer.normalize(
    label.trimStart(),
    java.text.Normalizer.Form.NFD,
).filterNot { character ->
    val type = Character.getType(character)
    type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt()
}
