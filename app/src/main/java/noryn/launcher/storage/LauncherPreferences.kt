package noryn.launcher.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import noryn.launcher.core.model.LauncherSettings
import noryn.launcher.core.model.ThemeChoice
import java.io.IOException

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "noryn_settings")

class LauncherPreferences(context: Context) {
    private val dataStore = context.applicationContext.launcherDataStore

    val settings: Flow<LauncherSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            LauncherSettings(
                themeChoice = preferences[Keys.theme].toThemeChoice(),
                showAppLabels = preferences[Keys.showAppLabels] ?: true,
                showAlphabetIndex = preferences[Keys.showAlphabetIndex] ?: true,
                showClock = preferences[Keys.showClock] ?: true,
                showDate = preferences[Keys.showDate] ?: true,
                favoriteIds = decodeList(preferences[Keys.favorites]),
                hiddenIds = decodeList(preferences[Keys.hiddenApps]).toSet(),
            )
        }

    suspend fun setThemeChoice(choice: ThemeChoice) {
        dataStore.edit { it[Keys.theme] = choice.name }
    }

    suspend fun setShowAppLabels(show: Boolean) = updateBoolean(Keys.showAppLabels, show)
    suspend fun setShowAlphabetIndex(show: Boolean) = updateBoolean(Keys.showAlphabetIndex, show)
    suspend fun setShowClock(show: Boolean) = updateBoolean(Keys.showClock, show)
    suspend fun setShowDate(show: Boolean) = updateBoolean(Keys.showDate, show)

    suspend fun toggleFavorite(id: String) {
        dataStore.edit { preferences ->
            val favoriteIds = decodeList(preferences[Keys.favorites]).toMutableList()
            if (!favoriteIds.remove(id)) favoriteIds.add(id)
            preferences[Keys.favorites] = encodeList(favoriteIds)
        }
    }

    suspend fun moveFavorite(id: String, offset: Int) {
        dataStore.edit { preferences ->
            val favoriteIds = decodeList(preferences[Keys.favorites]).toMutableList()
            val currentIndex = favoriteIds.indexOf(id)
            val destination = currentIndex + offset
            if (currentIndex >= 0 && destination in favoriteIds.indices) {
                val favorite = favoriteIds.removeAt(currentIndex)
                favoriteIds.add(destination, favorite)
                preferences[Keys.favorites] = encodeList(favoriteIds)
            }
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

    private suspend fun updateBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }

    private fun String?.toThemeChoice(): ThemeChoice = when (this) {
        ThemeChoice.Light.name -> ThemeChoice.Light
        ThemeChoice.Dark.name -> ThemeChoice.Dark
        else -> ThemeChoice.System
    }

    private fun decodeList(value: String?): List<String> = value
        ?.lineSequence()
        ?.filter(String::isNotBlank)
        ?.distinct()
        ?.toList()
        .orEmpty()

    private fun encodeList(values: Iterable<String>): String = values.joinToString("\n")

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val showAppLabels = booleanPreferencesKey("show_app_labels")
        val showAlphabetIndex = booleanPreferencesKey("show_alphabet_index")
        val showClock = booleanPreferencesKey("show_clock")
        val showDate = booleanPreferencesKey("show_date")
        val favorites = stringPreferencesKey("favorites")
        val hiddenApps = stringPreferencesKey("hidden_apps")
    }
}
