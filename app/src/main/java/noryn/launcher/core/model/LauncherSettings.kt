package noryn.launcher.core.model

enum class ThemeChoice {
    System,
    Light,
    Dark,
}

enum class AppLabelMode {
    Always,
    FavoritesOnly,
    Never,
}

enum class SizePreset {
    Small,
    Default,
    Large,
}

enum class RowSpacing {
    Compact,
    Default,
    Comfortable,
}

enum class ClockAlignment {
    Start,
    Center,
}

enum class HomeGestureAction {
    OpenSearch,
    OpenAppList,
    NoAction,
}

enum class FontChoice {
    System,
    Noryn,
}

enum class NotificationIndicatorStyle {
    Off,
    Dot,
    Count,
}

enum class LauncherProfileLayout {
    Mix,
    Separate,
}

data class LauncherSettings(
    val themeChoice: ThemeChoice = ThemeChoice.System,
    val appLabelMode: AppLabelMode = AppLabelMode.Always,
    val appIconSize: SizePreset = SizePreset.Default,
    val rowSpacing: RowSpacing = RowSpacing.Default,
    val clockSize: SizePreset = SizePreset.Default,
    val clockAlignment: ClockAlignment = ClockAlignment.Start,
    val showAlphabetIndex: Boolean = true,
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val maxFavorites: Int = 6,
    val searchKeyboardImmediately: Boolean = true,
    val swipeDownAction: HomeGestureAction = HomeGestureAction.OpenSearch,
    val swipeUpAction: HomeGestureAction = HomeGestureAction.OpenAppList,
    val doubleTapAction: HomeGestureAction = HomeGestureAction.NoAction,
    val fontChoice: FontChoice = FontChoice.System,
    val iconPackPackageName: String = "",
    val suggestionsEnabled: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val mediaPlayerEnabled: Boolean = false,
    val notificationIndicatorStyle: NotificationIndicatorStyle = NotificationIndicatorStyle.Off,
    val notificationPreviewContent: Boolean = false,
    val showSilentNotifications: Boolean = false,
    val profileLayout: LauncherProfileLayout = LauncherProfileLayout.Separate,
    val showPrivateSpace: Boolean = true,
    val customNames: Map<String, String> = emptyMap(),
    val iconOverrides: Map<String, String> = emptyMap(),
    val favoriteIds: List<String> = emptyList(),
    val hiddenIds: Set<String> = emptySet(),
) {
    val showAppLabels: Boolean get() = appLabelMode != AppLabelMode.Never
}
