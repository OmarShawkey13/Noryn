package noryn.launcher.core.model

enum class ThemeChoice {
    System,
    Light,
    Dark,
}

data class LauncherSettings(
    val themeChoice: ThemeChoice = ThemeChoice.System,
    val showAppLabels: Boolean = true,
    val showAlphabetIndex: Boolean = true,
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val favoriteIds: List<String> = emptyList(),
    val hiddenIds: Set<String> = emptySet(),
)
