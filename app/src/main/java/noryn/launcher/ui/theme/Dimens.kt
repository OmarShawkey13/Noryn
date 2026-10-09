package noryn.launcher.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object NorynDimens {
    val ScreenHorizontalPadding = 20.dp
    val ScreenVerticalPadding = 16.dp
    val SectionSpacing = 24.dp
    val CompactSpacing = 8.dp
    val AppRowHeight = 60.dp
    val AppIconSize = 48.dp
    val FavoriteIconSize = 44.dp
    val FavoriteTileWidth = 78.dp
    val FavoriteTileHeight = 84.dp
    val IconCornerRadius = 13.dp
    val SectionHeadingHeight = 36.dp
    val AlphabetTrackWidth = 22.dp
    val AlphabetSideInset = 4.dp
    val AlphabetLetterFontSize = 10.sp
    val AlphabetSectionHeadingFontSize = 12.sp
    val FavoriteLabelFontSize = 11.sp
    val TouchTarget = 48.dp
    val GlyphSize = 22.dp
    val SettingsRowHeight = 64.dp
    val EmptyStateTopPadding = 36.dp
    val SearchFieldHeight = 66.dp
    val LetterIndicatorSize = 78.dp
    val WelcomeActionHeight = 56.dp
    val WelcomeMarkSize = 54.dp
    val PanelCornerRadius = 20.dp
    val AlphabetSwipeThreshold = 44.dp
    val DividerThickness = 0.5.dp
    val LoadingIndicatorStroke = 2.dp
}

object NorynMotion {
    const val Fast = 110
    const val Standard = 160
    const val SpringStiffness = 720f
    const val SearchFocusDelay = 80L
}

typealias LauncherDimens = NorynDimens
typealias LauncherMotion = NorynMotion
