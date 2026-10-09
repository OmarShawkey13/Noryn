package noryn.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import noryn.launcher.R
import noryn.launcher.core.model.LauncherApp
import noryn.launcher.launcher.presentation.LauncherUiState
import noryn.launcher.launcher.presentation.LauncherViewModel
import noryn.launcher.ui.theme.LauncherDimens
import noryn.launcher.ui.theme.LauncherMotion
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun SearchScreen(viewModel: LauncherViewModel, state: LauncherUiState) {
    val focusRequester = remember { FocusRequester() }
    val queryValue = remember {
        mutableStateOf(TextFieldValue(state.query, selection = TextRange(state.query.length)))
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchDescription = stringResource(R.string.search)
    val searchHint = stringResource(R.string.search_apps_hint)
    val clearSearchDescription = stringResource(R.string.clear_search)
    val noResults = stringResource(R.string.no_search_results)
    val searchTextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 18.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    )

    LaunchedEffect(state.query) {
        if (state.query != queryValue.value.text) {
            queryValue.value = TextFieldValue(
                text = state.query,
                selection = TextRange(state.query.length),
            )
        }
    }

    LaunchedEffect(state.settings.searchKeyboardImmediately) {
        if (state.settings.searchKeyboardImmediately) {
            delay(LauncherMotion.SearchFocusDelay.milliseconds)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LauncherDimens.ScreenHorizontalPadding)
            .padding(top = LauncherDimens.ScreenVerticalPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(LauncherDimens.SectionHeadingHeight + LauncherDimens.TouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphButton(NorynGlyph.Back, stringResource(R.string.back), viewModel::goBack)
            Text(
                text = searchDescription,
                modifier = Modifier.padding(start = LauncherDimens.CompactSpacing),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(LauncherDimens.CompactSpacing))
        TextField(
            value = queryValue.value,
            onValueChange = { updatedValue ->
                queryValue.value = updatedValue
                viewModel.updateQuery(updatedValue.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(LauncherDimens.SearchFieldHeight)
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = searchHint,
                    style = searchTextStyle,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = { Glyph(NorynGlyph.Search) },
            trailingIcon = {
                if (queryValue.value.text.isNotEmpty()) {
                    GlyphButton(NorynGlyph.Clear, clearSearchDescription, onClick = {
                        queryValue.value = TextFieldValue()
                        viewModel.updateQuery("")
                    })
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(LauncherDimens.PanelCornerRadius),
            textStyle = searchTextStyle,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
                unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
                disabledIndicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.height(LauncherDimens.CompactSpacing))
        if (state.searchResults.isEmpty() && !state.isLoading) {
            EmptyMessage(if (state.query.isBlank()) stringResource(R.string.no_apps) else noResults)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LauncherDimens.SectionSpacing),
                verticalArrangement = Arrangement.Top,
            ) {
                items(state.searchResults, key = LauncherApp::id) { app ->
                    LauncherAppRow(
                        app = app,
                        state = state,
                        viewModel = viewModel,
                        showLabel = true,
                    )
                }
            }
        }
    }
}
