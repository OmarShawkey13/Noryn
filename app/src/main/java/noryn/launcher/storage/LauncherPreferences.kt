package noryn.launcher.storage

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import noryn.launcher.core.model.AppLabelMode
import noryn.launcher.core.model.ClockAlignment
import noryn.launcher.core.model.ClockDesign
import noryn.launcher.core.model.FontChoice
import noryn.launcher.core.model.HomeGestureAction
import noryn.launcher.core.model.LauncherProfileLayout
import noryn.launcher.core.model.LauncherSettings
import noryn.launcher.core.model.NotificationIndicatorStyle
import noryn.launcher.core.model.RowSpacing
import noryn.launcher.core.model.SizePreset
import noryn.launcher.core.model.ThemeChoice
import java.io.IOException
import java.nio.charset.StandardCharsets

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "noryn_settings")

class LauncherPreferences(context: Context) {
    private val dataStore = context.applicationContext.launcherDataStore

    val settings: Flow<LauncherSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            val labelMode = preferences[Keys.appLabelMode]?.enumValueOrNull<AppLabelMode>()
                ?: if (preferences[Keys.legacyShowAppLabels] == false) AppLabelMode.Never else AppLabelMode.Always
            LauncherSettings(
                themeChoice = preferences[Keys.theme].enumValueOrNull() ?: ThemeChoice.System,
                appLabelMode = labelMode,
                appIconSize = preferences[Keys.appIconSize].enumValueOrNull() ?: SizePreset.Default,
                rowSpacing = preferences[Keys.rowSpacing].enumValueOrNull() ?: RowSpacing.Default,
                clockSize = preferences[Keys.clockSize].enumValueOrNull() ?: SizePreset.Default,
                clockAlignment = preferences[Keys.clockAlignment].enumValueOrNull() ?: ClockAlignment.Start,
                clockDesign = preferences[Keys.clockDesign].enumValueOrNull() ?: ClockDesign.Stacked,
                showAlphabetIndex = preferences[Keys.showAlphabetIndex] ?: true,
                showClock = preferences[Keys.showClock] ?: true,
                showDate = preferences[Keys.showDate] ?: true,
                maxFavorites = (preferences[Keys.maxFavorites] ?: DEFAULT_MAX_FAVORITES).coerceIn(
                    MIN_FAVORITES,
                    MAX_FAVORITES,
                ),
                searchKeyboardImmediately = preferences[Keys.searchKeyboardImmediately] ?: true,
                swipeDownAction = preferences[Keys.swipeDownAction].enumValueOrNull() ?: HomeGestureAction.OpenSearch,
                swipeUpAction = preferences[Keys.swipeUpAction].enumValueOrNull() ?: HomeGestureAction.OpenAppList,
                doubleTapAction = preferences[Keys.doubleTapAction].enumValueOrNull() ?: HomeGestureAction.NoAction,
                fontChoice = preferences[Keys.fontChoice].enumValueOrNull() ?: FontChoice.System,
                iconPackPackageName = preferences[Keys.iconPackPackageName].orEmpty(),
                suggestionsEnabled = preferences[Keys.suggestionsEnabled] ?: false,
                notificationsEnabled = preferences[Keys.notificationsEnabled] ?: false,
                mediaPlayerEnabled = preferences[Keys.mediaPlayerEnabled] ?: false,
                notificationIndicatorStyle = preferences[Keys.notificationIndicatorStyle].enumValueOrNull()
                    ?: NotificationIndicatorStyle.Off,
                notificationPreviewContent = preferences[Keys.notificationPreviewContent] ?: false,
                showSilentNotifications = preferences[Keys.showSilentNotifications] ?: false,
                profileLayout = preferences[Keys.profileLayout].enumValueOrNull() ?: LauncherProfileLayout.Separate,
                showPrivateSpace = preferences[Keys.showPrivateSpace] ?: true,
                customNames = decodeMap(preferences[Keys.customNames]),
                iconOverrides = decodeMap(preferences[Keys.iconOverrides]),
                favoriteIds = decodeList(preferences[Keys.favorites]),
                hiddenIds = decodeList(preferences[Keys.hiddenApps]).toSet(),
            )
        }

    suspend fun setThemeChoice(choice: ThemeChoice) = updateString(Keys.theme, choice.name)
    suspend fun setAppLabelMode(mode: AppLabelMode) {
        dataStore.edit {
            it[Keys.appLabelMode] = mode.name
            it[Keys.legacyShowAppLabels] = mode != AppLabelMode.Never
        }
    }

    suspend fun setAppIconSize(size: SizePreset) = updateString(Keys.appIconSize, size.name)
    suspend fun setRowSpacing(spacing: RowSpacing) = updateString(Keys.rowSpacing, spacing.name)
    suspend fun setClockSize(size: SizePreset) = updateString(Keys.clockSize, size.name)
    suspend fun setClockAlignment(alignment: ClockAlignment) = updateString(Keys.clockAlignment, alignment.name)
    suspend fun setClockDesign(design: ClockDesign) = updateString(Keys.clockDesign, design.name)
    suspend fun setShowAlphabetIndex(show: Boolean) = updateBoolean(Keys.showAlphabetIndex, show)
    suspend fun setShowClock(show: Boolean) = updateBoolean(Keys.showClock, show)
    suspend fun setShowDate(show: Boolean) = updateBoolean(Keys.showDate, show)
    suspend fun setSearchKeyboardImmediately(show: Boolean) = updateBoolean(Keys.searchKeyboardImmediately, show)
    suspend fun setFontChoice(choice: FontChoice) = updateString(Keys.fontChoice, choice.name)
    suspend fun setIconPack(packageName: String) = updateString(Keys.iconPackPackageName, packageName)
    suspend fun setSuggestionsEnabled(enabled: Boolean) = updateBoolean(Keys.suggestionsEnabled, enabled)
    suspend fun setNotificationsEnabled(enabled: Boolean) = updateBoolean(Keys.notificationsEnabled, enabled)
    suspend fun setMediaPlayerEnabled(enabled: Boolean) = updateBoolean(Keys.mediaPlayerEnabled, enabled)
    suspend fun setNotificationIndicatorStyle(style: NotificationIndicatorStyle) =
        updateString(Keys.notificationIndicatorStyle, style.name)
    suspend fun setNotificationPreviewContent(show: Boolean) = updateBoolean(Keys.notificationPreviewContent, show)
    suspend fun setShowSilentNotifications(show: Boolean) = updateBoolean(Keys.showSilentNotifications, show)
    suspend fun setProfileLayout(layout: LauncherProfileLayout) = updateString(Keys.profileLayout, layout.name)
    suspend fun setShowPrivateSpace(show: Boolean) = updateBoolean(Keys.showPrivateSpace, show)
    suspend fun setSwipeDownAction(action: HomeGestureAction) = updateString(Keys.swipeDownAction, action.name)
    suspend fun setSwipeUpAction(action: HomeGestureAction) = updateString(Keys.swipeUpAction, action.name)
    suspend fun setDoubleTapAction(action: HomeGestureAction) = updateString(Keys.doubleTapAction, action.name)

    suspend fun setMaxFavorites(count: Int) {
        val bounded = count.coerceIn(MIN_FAVORITES, MAX_FAVORITES)
        dataStore.edit { preferences ->
            preferences[Keys.maxFavorites] = bounded
            preferences[Keys.favorites] = encodeList(decodeList(preferences[Keys.favorites]).take(bounded))
        }
    }

    suspend fun toggleFavorite(id: String): Boolean {
        var changed = true
        dataStore.edit { preferences ->
            val limit = (preferences[Keys.maxFavorites] ?: DEFAULT_MAX_FAVORITES)
                .coerceIn(MIN_FAVORITES, MAX_FAVORITES)
            val favoriteIds = decodeList(preferences[Keys.favorites]).take(limit).toMutableList()
            if (!favoriteIds.remove(id)) {
                if (favoriteIds.size >= limit) {
                    changed = false
                } else {
                    favoriteIds.add(id)
                }
            }
            if (changed) preferences[Keys.favorites] = encodeList(favoriteIds)
        }
        return changed
    }

    suspend fun reorderFavorite(id: String, targetIndex: Int) {
        dataStore.edit { preferences ->
            val favoriteIds = decodeList(preferences[Keys.favorites]).toMutableList()
            val currentIndex = favoriteIds.indexOf(id)
            if (currentIndex >= 0 && targetIndex in favoriteIds.indices && currentIndex != targetIndex) {
                val favorite = favoriteIds.removeAt(currentIndex)
                favoriteIds.add(targetIndex, favorite)
                preferences[Keys.favorites] = encodeList(favoriteIds)
            }
        }
    }

    suspend fun saveFavoriteOrder(ids: List<String>) {
        dataStore.edit { preferences ->
            val limit = (preferences[Keys.maxFavorites] ?: DEFAULT_MAX_FAVORITES)
                .coerceIn(MIN_FAVORITES, MAX_FAVORITES)
            preferences[Keys.favorites] = encodeList(ids.distinct().take(limit))
        }
    }

    suspend fun toggleHidden(id: String) {
        dataStore.edit { preferences ->
            val hiddenIds = decodeList(preferences[Keys.hiddenApps]).toMutableSet()
            if (!hiddenIds.add(id)) hiddenIds.remove(id)
            preferences[Keys.hiddenApps] = encodeList(hiddenIds.sorted())
            if (id in hiddenIds) {
                preferences[Keys.favorites] = encodeList(decodeList(preferences[Keys.favorites]).filterNot { it == id })
            }
        }
    }

    suspend fun setCustomName(id: String, name: String) = updateMapValue(Keys.customNames, id, name.trim().take(MAX_CUSTOM_NAME_LENGTH))

    suspend fun setIconOverride(id: String, iconReference: String?) {
        dataStore.edit { preferences ->
            val overrides = decodeMap(preferences[Keys.iconOverrides]).toMutableMap()
            if (iconReference.isNullOrBlank()) overrides.remove(id) else overrides[id] = iconReference
            preferences[Keys.iconOverrides] = encodeMap(overrides)
        }
    }

    private suspend fun updateString(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    private suspend fun updateBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }

    private suspend fun updateMapValue(key: Preferences.Key<String>, id: String, value: String) {
        dataStore.edit { preferences ->
            val values = decodeMap(preferences[key]).toMutableMap()
            if (value.isBlank()) values.remove(id) else values[id] = value
            preferences[key] = encodeMap(values)
        }
    }

    private inline fun <reified T : Enum<T>> String?.enumValueOrNull(): T? =
        this?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

    private fun decodeList(value: String?): List<String> = value
        ?.lineSequence()
        ?.filter(String::isNotBlank)
        ?.distinct()
        ?.toList()
        .orEmpty()

    private fun encodeList(values: Iterable<String>): String = values.joinToString("\n")

    private fun decodeMap(value: String?): Map<String, String> = value
        ?.lineSequence()
        ?.mapNotNull { line ->
            val separator = line.indexOf('.')
            if (separator <= 0 || separator == line.lastIndex) return@mapNotNull null
            runCatching {
                decodeBase64(line.substring(0, separator)) to decodeBase64(line.substring(separator + 1))
            }.getOrNull()
        }
        ?.toMap()
        .orEmpty()

    private fun encodeMap(values: Map<String, String>): String = values.entries.joinToString("\n") { (key, value) ->
        "${encodeBase64(key)}.${encodeBase64(value)}"
    }

    private fun encodeBase64(value: String): String = Base64.encodeToString(
        value.toByteArray(StandardCharsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private fun decodeBase64(value: String): String = String(
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
        StandardCharsets.UTF_8,
    )

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val legacyShowAppLabels = booleanPreferencesKey("show_app_labels")
        val appLabelMode = stringPreferencesKey("app_label_mode")
        val appIconSize = stringPreferencesKey("app_icon_size")
        val rowSpacing = stringPreferencesKey("row_spacing")
        val clockSize = stringPreferencesKey("clock_size")
        val clockAlignment = stringPreferencesKey("clock_alignment")
        val clockDesign = stringPreferencesKey("clock_design")
        val showAlphabetIndex = booleanPreferencesKey("show_alphabet_index")
        val showClock = booleanPreferencesKey("show_clock")
        val showDate = booleanPreferencesKey("show_date")
        val maxFavorites = intPreferencesKey("max_favorites")
        val searchKeyboardImmediately = booleanPreferencesKey("search_keyboard_immediately")
        val swipeDownAction = stringPreferencesKey("swipe_down_action")
        val swipeUpAction = stringPreferencesKey("swipe_up_action")
        val doubleTapAction = stringPreferencesKey("double_tap_action")
        val fontChoice = stringPreferencesKey("font_choice")
        val iconPackPackageName = stringPreferencesKey("icon_pack_package")
        val suggestionsEnabled = booleanPreferencesKey("suggestions_enabled")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val mediaPlayerEnabled = booleanPreferencesKey("media_player_enabled")
        val notificationIndicatorStyle = stringPreferencesKey("notification_indicator_style")
        val notificationPreviewContent = booleanPreferencesKey("notification_preview_content")
        val showSilentNotifications = booleanPreferencesKey("show_silent_notifications")
        val profileLayout = stringPreferencesKey("profile_layout")
        val showPrivateSpace = booleanPreferencesKey("show_private_space")
        val customNames = stringPreferencesKey("custom_names")
        val iconOverrides = stringPreferencesKey("icon_overrides")
        val favorites = stringPreferencesKey("favorites")
        val hiddenApps = stringPreferencesKey("hidden_apps")
    }

    private companion object {
        const val DEFAULT_MAX_FAVORITES = 6
        const val MIN_FAVORITES = 1
        const val MAX_FAVORITES = 12
        const val MAX_CUSTOM_NAME_LENGTH = 48
    }
}
