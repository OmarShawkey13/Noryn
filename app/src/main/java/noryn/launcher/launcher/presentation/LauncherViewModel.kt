package noryn.launcher.launcher.presentation

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import noryn.launcher.core.model.LauncherApp
import noryn.launcher.core.model.LauncherSettings
import noryn.launcher.core.model.ThemeChoice
import noryn.launcher.platform.DefaultLauncherManager
import noryn.launcher.platform.LauncherAppsManager
import noryn.launcher.storage.LauncherPreferences
import java.text.Normalizer
import java.util.Locale

enum class LauncherScreen {
    Welcome,
    Home,
    Search,
    Settings,
    HiddenApps,
    FavoriteManagement,
}

data class DuplicateAppDetails(
    val showPackageName: Boolean = false,
    val showActivityName: Boolean = false,
    val profileSerial: Long? = null,
)

data class LauncherUiState(
    val screen: LauncherScreen = LauncherScreen.Welcome,
    val settings: LauncherSettings = LauncherSettings(),
    val apps: List<LauncherApp> = emptyList(),
    val visibleApps: List<LauncherApp> = emptyList(),
    val favorites: List<LauncherApp> = emptyList(),
    val hiddenApps: List<LauncherApp> = emptyList(),
    val searchResults: List<LauncherApp> = emptyList(),
    val duplicateAppDetails: Map<String, DuplicateAppDetails> = emptyMap(),
    val query: String = "",
    val isLoading: Boolean = true,
    val isDefaultLauncher: Boolean = false,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val appManager = LauncherAppsManager(application)
    private val defaultLauncherManager = DefaultLauncherManager(application)
    private val preferences = LauncherPreferences(application)

    private val apps = MutableStateFlow<List<LauncherApp>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val query = MutableStateFlow("")
    private val screen = MutableStateFlow(LauncherScreen.Welcome)
    private val isDefaultLauncher = MutableStateFlow(false)
    private var appObservation: Job? = null
    private var observedLocale = Locale.getDefault()

    private data class CoreState(
        val settings: LauncherSettings,
        val apps: List<LauncherApp>,
        val query: String,
    )

    val uiState = combine(apps, preferences.settings, query) { appList, settings, searchQuery ->
        CoreState(settings, appList, searchQuery)
    }.let { core ->
        combine(core, screen, isDefaultLauncher, loading) { values, currentScreen, isDefault, isLoading ->
            buildUiState(values, currentScreen, isDefault, isLoading)
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    init {
        onHostResumed()
    }

    fun onHostResumed() {
        val currentLocale = Locale.getDefault()
        val localeChanged = currentLocale != observedLocale
        observedLocale = currentLocale
        val isDefault = defaultLauncherManager.isDefaultLauncher()
        isDefaultLauncher.value = isDefault
        if (isDefault) {
            if (screen.value == LauncherScreen.Welcome) screen.value = LauncherScreen.Home
            if (localeChanged) {
                appObservation?.cancel()
                appObservation = null
            }
            observeAppsIfNeeded()
        } else {
            appObservation?.cancel()
            appObservation = null
            apps.value = emptyList()
            loading.value = false
            screen.value = LauncherScreen.Welcome
        }
    }

    fun createDefaultLauncherRequest(): Intent? = defaultLauncherManager.createRequestIntent()

    fun openSearch() {
        query.value = ""
        screen.value = LauncherScreen.Search
    }

    fun updateQuery(value: String) {
        query.value = value
    }

    fun openSettings() {
        screen.value = LauncherScreen.Settings
    }

    fun openHiddenApps() {
        screen.value = LauncherScreen.HiddenApps
    }

    fun openFavoriteManagement() {
        screen.value = LauncherScreen.FavoriteManagement
    }

    fun goBack() {
        when (screen.value) {
            LauncherScreen.Search -> {
                query.value = ""
                screen.value = LauncherScreen.Home
            }
            LauncherScreen.HiddenApps, LauncherScreen.FavoriteManagement -> screen.value = LauncherScreen.Settings
            LauncherScreen.Settings -> screen.value = LauncherScreen.Home
            else -> Unit
        }
    }

    fun launch(app: LauncherApp) {
        if (appManager.launch(app)) returnToHome()
    }

    fun openAppInfo(app: LauncherApp) {
        if (appManager.openAppInfo(app)) returnToHome()
    }

    fun loadIcon(app: LauncherApp) = appManager.loadIcon(app)

    fun toggleFavorite(app: LauncherApp) {
        viewModelScope.launch { preferences.toggleFavorite(app.id) }
    }

    fun moveFavorite(app: LauncherApp, offset: Int) {
        viewModelScope.launch { preferences.moveFavorite(app.id, offset) }
    }

    fun toggleHidden(app: LauncherApp) {
        viewModelScope.launch { preferences.toggleHidden(app.id) }
    }

    fun setTheme(choice: ThemeChoice) {
        viewModelScope.launch { preferences.setThemeChoice(choice) }
    }

    fun setShowAppLabels(show: Boolean) {
        viewModelScope.launch { preferences.setShowAppLabels(show) }
    }

    fun setShowAlphabetIndex(show: Boolean) {
        viewModelScope.launch { preferences.setShowAlphabetIndex(show) }
    }

    fun setShowClock(show: Boolean) {
        viewModelScope.launch { preferences.setShowClock(show) }
    }

    fun setShowDate(show: Boolean) {
        viewModelScope.launch { preferences.setShowDate(show) }
    }

    private fun observeAppsIfNeeded() {
        if (appObservation?.isActive == true) return
        loading.value = true
        appObservation = viewModelScope.launch {
            appManager.observeApps().collect { refreshedApps ->
                apps.value = refreshedApps
                loading.value = false
            }
        }
    }

    private fun buildUiState(
        core: CoreState,
        currentScreen: LauncherScreen,
        isDefault: Boolean,
        isLoading: Boolean,
    ): LauncherUiState {
        val hiddenIds = core.settings.hiddenIds
        val visibleApps = core.apps.filterNot { it.id in hiddenIds }
        val appsById = core.apps.associateBy(LauncherApp::id)
        val favorites = core.settings.favoriteIds.mapNotNull(appsById::get).filterNot { it.id in hiddenIds }
        val normalizedQuery = normalize(core.query)
        val searchResults = if (normalizedQuery.isBlank()) visibleApps else visibleApps.filter { app ->
            app.normalizedLabel.contains(normalizedQuery)
        }
        val duplicateDetails = core.apps
            .groupBy(LauncherApp::normalizedLabel)
            .values
            .filter { it.size > 1 }
            .flatMap { group ->
                val hasMultiplePackages = group.map(LauncherApp::packageName).distinct().size > 1
                val hasMultipleComponents = group.map(LauncherApp::componentName).distinct().size > 1
                val profileSerialsByComponent = group.groupBy(LauncherApp::componentName)
                    .mapValues { (_, sameComponent) -> sameComponent.map(LauncherApp::userSerial).distinct().size }
                group.map { app ->
                    val sameComponentHasMultipleProfiles = (profileSerialsByComponent[app.componentName] ?: 0) > 1
                    app.id to DuplicateAppDetails(
                        showPackageName = hasMultiplePackages,
                        showActivityName = !hasMultiplePackages && hasMultipleComponents,
                        profileSerial = if (sameComponentHasMultipleProfiles) app.userSerial else null,
                    )
                }
            }
            .toMap()

        return LauncherUiState(
            screen = currentScreen,
            settings = core.settings,
            apps = core.apps,
            visibleApps = visibleApps,
            favorites = favorites,
            hiddenApps = core.apps.filter { it.id in hiddenIds },
            searchResults = searchResults,
            duplicateAppDetails = duplicateDetails,
            query = core.query,
            isLoading = isLoading,
            isDefaultLauncher = isDefault,
        )
    }

    private fun returnToHome() {
        query.value = ""
        screen.value = LauncherScreen.Home
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .filterNot { character ->
            val type = Character.getType(character)
            type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
        }
        .trim()
        .lowercase(Locale.ROOT)

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(LauncherViewModel::class.java)) {
                    return LauncherViewModel(application) as T
                }
                error("Unsupported ViewModel: ${modelClass.name}")
            }
        }
    }
}
