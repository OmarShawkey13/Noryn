package noryn.launcher.launcher.presentation

import android.app.Application
import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import noryn.launcher.core.model.LauncherProfile
import noryn.launcher.core.model.LauncherProfileLayout
import noryn.launcher.core.model.LauncherProfileType
import noryn.launcher.core.model.NotificationIndicatorStyle
import noryn.launcher.core.model.WidgetInstance
import noryn.launcher.core.model.WidgetProvider
import noryn.launcher.core.model.IconPackInfo
import noryn.launcher.core.model.LauncherApp
import noryn.launcher.core.model.LauncherSettings
import noryn.launcher.core.model.LauncherShortcut
import noryn.launcher.core.model.ThemeChoice
import noryn.launcher.core.model.AppLabelMode
import noryn.launcher.core.model.ClockAlignment
import noryn.launcher.core.model.FontChoice
import noryn.launcher.core.model.HomeGestureAction
import noryn.launcher.core.model.RowSpacing
import noryn.launcher.core.model.SizePreset
import noryn.launcher.platform.DefaultLauncherManager
import noryn.launcher.platform.LauncherAppsManager
import noryn.launcher.platform.NotificationAccessManager
import noryn.launcher.platform.SuggestedAppsManager
import noryn.launcher.platform.WidgetHostManager
import noryn.launcher.platform.WidgetHostStep
import noryn.launcher.notifications.NotificationRepository
import noryn.launcher.media.MediaPlaybackRepository
import noryn.launcher.media.MediaPlaybackSnapshot
import noryn.launcher.storage.LauncherPreferences
import java.text.Collator
import java.util.Locale

enum class LauncherScreen {
    Welcome,
    Home,
    Search,
    Settings,
    AppearanceSettings,
    HomeSettings,
    IconPacks,
    GestureSettings,
    AppSettings,
    HiddenApps,
    FavoriteManagement,
    Widgets,
    Notifications,
    AppEdit,
    IconSelection,
}

sealed interface LauncherEvent {
    data object FavoriteLimitReached : LauncherEvent
    data class LaunchWidgetBind(val intent: Intent) : LauncherEvent
    data class LaunchWidgetConfigure(val intent: Intent, val appWidgetId: Int) : LauncherEvent
}

data class LauncherUiState(
    val screen: LauncherScreen = LauncherScreen.Welcome,
    val settings: LauncherSettings = LauncherSettings(),
    val apps: List<LauncherApp> = emptyList(),
    val visibleApps: List<LauncherApp> = emptyList(),
    val favorites: List<LauncherApp> = emptyList(),
    val hiddenApps: List<LauncherApp> = emptyList(),
    val suggestedApps: List<LauncherApp> = emptyList(),
    val sections: List<AppSection> = emptyList(),
    val profiles: List<LauncherProfile> = emptyList(),
    val profileGroups: List<AppSectionGroup> = emptyList(),
    val widgetInstances: List<WidgetInstance> = emptyList(),
    val widgetProviders: List<WidgetProvider> = emptyList(),
    val searchResults: List<LauncherApp> = emptyList(),
    val iconPacks: List<IconPackInfo> = emptyList(),
    val iconRevision: Long = 0,
    val editingApp: LauncherApp? = null,
    val iconChoices: List<String> = emptyList(),
    val isLoadingIconChoices: Boolean = false,
    val query: String = "",
    val isLoading: Boolean = true,
    val isDefaultLauncher: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasNotificationAccess: Boolean = false,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val appManager = LauncherAppsManager(application)
    private val defaultLauncherManager = DefaultLauncherManager(application)
    private val suggestedAppsManager = SuggestedAppsManager(application)
    private val notificationAccessManager = NotificationAccessManager(application)
    private val widgetHostManager = WidgetHostManager(application)
    private val preferences = LauncherPreferences(application)

    private val apps = MutableStateFlow<List<LauncherApp>>(emptyList())
    private val profiles = MutableStateFlow<List<LauncherProfile>>(emptyList())
    private val iconPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    private val iconChoices = MutableStateFlow<List<String>>(emptyList())
    private val loadingIconChoices = MutableStateFlow(false)
    private val suggestedPackageNames = MutableStateFlow<List<String>>(emptyList())
    private val hasUsageAccess = MutableStateFlow(false)
    private val hasNotificationAccess = MutableStateFlow(false)
    private val widgetInstances = MutableStateFlow<List<WidgetInstance>>(emptyList())
    private val widgetProviders = MutableStateFlow<List<WidgetProvider>>(emptyList())
    private val displayLocale = MutableStateFlow(Locale.getDefault())
    private val iconRevision = MutableStateFlow(0L)
    private val loading = MutableStateFlow(true)
    private val query = MutableStateFlow("")
    private val screen = MutableStateFlow(LauncherScreen.Welcome)
    private val editingAppId = MutableStateFlow<String?>(null)
    private val isDefaultLauncher = MutableStateFlow(false)
    private val pinShortcutPromptMutable = MutableStateFlow<String?>(null)
    val pinShortcutPrompt: StateFlow<String?> = pinShortcutPromptMutable
    private val eventsMutable = MutableSharedFlow<LauncherEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<LauncherEvent> = eventsMutable
    val notificationSnapshot = NotificationRepository.snapshot
    val mediaPlayback: StateFlow<MediaPlaybackSnapshot?> = MediaPlaybackRepository.snapshot
    val shortcutRevision: StateFlow<Long> = appManager.shortcutRevision

    private var appObservation: Job? = null
    private var hostResumeJob: Job? = null
    private var observedLocale = Locale.getDefault()
    private var editorReturnScreen = LauncherScreen.Home
    private var iconPackReturnScreen = LauncherScreen.Settings
    private var renameJob: Job? = null
    private var pendingAppName: Pair<String, String>? = null
    private var pendingPinShortcut: LauncherApps.PinItemRequest? = null

    private data class CoreState(
        val settings: LauncherSettings,
        val apps: List<LauncherApp>,
        val profiles: List<LauncherProfile>,
        val iconPacks: List<IconPackInfo>,
        val iconChoices: List<String>,
        val loadingIconChoices: Boolean,
        val suggestedPackageNames: List<String>,
        val hasUsageAccess: Boolean,
        val hasNotificationAccess: Boolean,
        val query: String,
        val editingAppId: String?,
        val locale: Locale,
        val iconRevision: Long,
    )

    private data class DisplayState(
        val settings: LauncherSettings,
        val appSnapshot: AppSnapshot,
        val iconPacks: List<IconPackInfo>,
        val iconChoices: List<String>,
        val loadingIconChoices: Boolean,
    )

    private data class AppSnapshot(val apps: List<LauncherApp>, val profiles: List<LauncherProfile>)

    private data class WidgetState(val instances: List<WidgetInstance>, val providers: List<WidgetProvider>)

    private data class ContextState(
        val suggestedPackageNames: List<String>,
        val hasUsageAccess: Boolean,
        val hasNotificationAccess: Boolean,
        val query: String,
        val editingAppId: String?,
        val locale: Locale,
        val iconRevision: Long,
    )

    private data class ContextMetadata(
        val locale: Locale,
        val iconRevision: Long,
        val hasNotificationAccess: Boolean,
    )

    private val appSnapshot = combine(apps, profiles) { appList, profileList -> AppSnapshot(appList, profileList) }

    private val displayState = combine(
        preferences.settings,
        appSnapshot,
        iconPacks,
        iconChoices,
        loadingIconChoices,
    ) { settings, snapshot, packs, choices, isLoadingChoices ->
        DisplayState(settings, snapshot, packs, choices, isLoadingChoices)
    }

    private val contextMetadata = combine(displayLocale, iconRevision, hasNotificationAccess) { locale, revision, access ->
        ContextMetadata(locale, revision, access)
    }

    private val contextState = combine(suggestedPackageNames, hasUsageAccess, query, editingAppId, contextMetadata) { suggestions, hasAccess, searchQuery, selectedApp, metadata ->
        ContextState(suggestions, hasAccess, metadata.hasNotificationAccess, searchQuery, selectedApp, metadata.locale, metadata.iconRevision)
    }

    private val coreState = combine(displayState, contextState) { display, context ->
        CoreState(
            settings = display.settings,
            apps = display.appSnapshot.apps,
            profiles = display.appSnapshot.profiles,
            iconPacks = display.iconPacks,
            iconChoices = display.iconChoices,
            loadingIconChoices = display.loadingIconChoices,
            suggestedPackageNames = context.suggestedPackageNames,
            hasUsageAccess = context.hasUsageAccess,
            hasNotificationAccess = context.hasNotificationAccess,
            query = context.query,
            editingAppId = context.editingAppId,
            locale = context.locale,
            iconRevision = context.iconRevision,
        )
    }

    private val widgetState = combine(widgetInstances, widgetProviders) { instances, providers ->
        WidgetState(instances, providers)
    }

    val uiState = combine(coreState, screen, isDefaultLauncher, loading, widgetState) { core, currentScreen, isDefault, isLoading, widgets ->
        buildUiState(core, currentScreen, isDefault, isLoading, widgets)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    init {
        widgetHostManager.setProvidersChangedCallback { refreshWidgets() }
        viewModelScope.launch {
            preferences.settings.distinctUntilChanged().collect { settings ->
                NotificationRepository.setFeatureEnabled(
                    settings.notificationsEnabled && hasNotificationAccess.value && isDefaultLauncher.value,
                )
                MediaPlaybackRepository.setEnabled(
                    settings.mediaPlayerEnabled && hasNotificationAccess.value && isDefaultLauncher.value,
                )
                updateNotificationProfiles(profiles.value, settings)
            }
        }
        refreshWidgets()
        onHostResumed()
    }

    fun onHostResumed() {
        val currentLocale = Locale.getDefault()
        val localeChanged = currentLocale != observedLocale
        observedLocale = currentLocale
        hostResumeJob?.cancel()
        hostResumeJob = viewModelScope.launch {
            val isDefault = withContext(Dispatchers.IO) { defaultLauncherManager.isDefaultLauncher() }
            val notificationAccess = withContext(Dispatchers.IO) { notificationAccessManager.isAccessGranted() }
            hasNotificationAccess.value = notificationAccess
            val savedSettings = preferences.settings.first()
            isDefaultLauncher.value = isDefault
            NotificationRepository.setFeatureEnabled(savedSettings.notificationsEnabled && notificationAccess && isDefault)
            MediaPlaybackRepository.setEnabled(savedSettings.mediaPlayerEnabled && notificationAccess && isDefault)
            updateNotificationProfiles(if (isDefault) profiles.value else emptyList(), savedSettings)
            if (isDefault) {
                if (screen.value == LauncherScreen.Welcome) screen.value = LauncherScreen.Home
                if (localeChanged) {
                    appObservation?.cancel()
                    appObservation = null
                }
                observeAppsIfNeeded()
                refreshSuggestedAppsInternal()
                refreshWidgets()
            } else {
                appObservation?.cancel()
                appObservation = null
                apps.value = emptyList()
                profiles.value = emptyList()
                NotificationRepository.clear()
                suggestedPackageNames.value = emptyList()
                iconPacks.value = emptyList()
                loading.value = false
                screen.value = LauncherScreen.Welcome
            }
        }
    }

    fun createDefaultLauncherRequest(): Intent? = defaultLauncherManager.createRequestIntent()

    fun createUsageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun createNotificationAccessIntent(): Intent = notificationAccessManager.settingsIntent()

    fun onPinShortcutRequest(intent: Intent?) {
        if (intent == null) return
        val request = appManager.pinShortcutRequest(intent) ?: return
        val shortcut = runCatching { request.shortcutInfo }.getOrNull() ?: return
        pendingPinShortcut = request
        pinShortcutPromptMutable.value = shortcut.shortLabel?.toString()?.trim()
            ?.takeIf(String::isNotBlank) ?: "Shortcut"
    }

    fun acceptPinShortcutRequest() {
        val request = pendingPinShortcut
        pendingPinShortcut = null
        pinShortcutPromptMutable.value = null
        if (request != null && runCatching { request.isValid && request.accept() }.getOrDefault(false)) {
            appManager.refreshShortcuts()
        }
    }

    fun dismissPinShortcutRequest() {
        pendingPinShortcut = null
        pinShortcutPromptMutable.value = null
    }

    fun openSearch() {
        query.value = ""
        screen.value = LauncherScreen.Search
    }

    fun updateQuery(value: String) {
        query.value = value
    }

    fun updateDisplayLocale(locale: Locale) {
        displayLocale.value = locale
    }

    fun openSettings() {
        screen.value = LauncherScreen.Settings
    }

    fun openAppearanceSettings() = openSettingsScreen(LauncherScreen.AppearanceSettings)
    fun openHomeSettings() = openSettingsScreen(LauncherScreen.HomeSettings)
    fun openIconPacks() {
        iconPackReturnScreen = screen.value
        screen.value = LauncherScreen.IconPacks
    }
    fun openGestureSettings() = openSettingsScreen(LauncherScreen.GestureSettings)
    fun openAppSettings() = openSettingsScreen(LauncherScreen.AppSettings)
    fun openHiddenApps() = openSettingsScreen(LauncherScreen.HiddenApps)
    fun openFavoriteManagement() = openSettingsScreen(LauncherScreen.FavoriteManagement)
    fun openWidgets() {
        refreshWidgets()
        openSettingsScreen(LauncherScreen.Widgets)
    }
    fun openNotifications() = openSettingsScreen(LauncherScreen.Notifications)

    fun openAppEditor(app: LauncherApp) {
        editorReturnScreen = screen.value
        editingAppId.value = app.id
        screen.value = LauncherScreen.AppEdit
    }

    fun openIconSelection() {
        val pack = uiState.value.settings.iconPackPackageName
        screen.value = LauncherScreen.IconSelection
        loadingIconChoices.value = true
        viewModelScope.launch {
            iconChoices.value = if (pack.isBlank()) emptyList() else withContext(Dispatchers.IO) {
                appManager.iconPackDrawableNames(pack)
            }
            loadingIconChoices.value = false
        }
    }

    fun chooseIcon(drawableName: String) {
        val app = uiState.value.editingApp ?: return
        val pack = uiState.value.settings.iconPackPackageName
        if (pack.isBlank()) return
        viewModelScope.launch {
            preferences.setIconOverride(app.id, "$pack|$drawableName")
            screen.value = LauncherScreen.AppEdit
        }
    }

    fun resetAppIcon(app: LauncherApp) {
        viewModelScope.launch { preferences.setIconOverride(app.id, null) }
    }

    fun useSystemAppIcon(app: LauncherApp) {
        viewModelScope.launch { preferences.setIconOverride(app.id, SYSTEM_ICON_OVERRIDE) }
    }

    fun setCustomAppName(app: LauncherApp, name: String) {
        pendingAppName = app.id to name
        renameJob?.cancel()
        renameJob = viewModelScope.launch {
            delay(NAME_SAVE_DEBOUNCE_MS)
            persistPendingAppName()
        }
    }

    fun resetCustomAppName(app: LauncherApp) {
        setCustomAppName(app, "")
    }

    fun goBack() {
        when (screen.value) {
            LauncherScreen.Search -> {
                query.value = ""
                screen.value = LauncherScreen.Home
            }
            LauncherScreen.HiddenApps,
            LauncherScreen.FavoriteManagement,
            LauncherScreen.Widgets,
            LauncherScreen.Notifications,
            LauncherScreen.AppearanceSettings,
            LauncherScreen.HomeSettings,
            LauncherScreen.GestureSettings,
            LauncherScreen.AppSettings -> screen.value = LauncherScreen.Settings
            LauncherScreen.IconSelection -> screen.value = LauncherScreen.AppEdit
            LauncherScreen.IconPacks -> screen.value = iconPackReturnScreen
            LauncherScreen.AppEdit -> {
                flushPendingAppName()
                editingAppId.value = null
                screen.value = editorReturnScreen
            }
            LauncherScreen.Settings -> screen.value = LauncherScreen.Home
            else -> Unit
        }
    }

    fun launch(app: LauncherApp) {
        if (appManager.launch(app)) returnToHome()
    }

    fun launchShortcut(app: LauncherApp, shortcut: LauncherShortcut) {
        if (appManager.launchShortcut(app, shortcut)) returnToHome()
    }

    fun shortcuts(app: LauncherApp): List<LauncherShortcut> = appManager.shortcuts(app)

    fun onWidgetHostStarted() = widgetHostManager.startListening()
    fun onWidgetHostStopped() = widgetHostManager.stopListening()
    fun releaseWidgetHostViews() = widgetHostManager.releaseActivityViews()
    fun createWidgetHostView(context: Context, appWidgetId: Int): AppWidgetHostView? =
        widgetHostManager.createHostView(context, appWidgetId)
    fun resizeWidgetHostView(view: AppWidgetHostView, widthDp: Int, heightDp: Int) =
        widgetHostManager.resizeHostView(view, widthDp, heightDp)

    fun beginAddWidget(provider: WidgetProvider) {
        handleWidgetStep(widgetHostManager.beginAdd(provider.provider))
        refreshWidgets()
    }

    fun onWidgetBindResult(resultCode: Int) {
        handleWidgetStep(widgetHostManager.onBindResult(resultCode))
        refreshWidgets()
    }

    fun onWidgetConfigureResult(appWidgetId: Int, resultCode: Int) {
        handleWidgetStep(widgetHostManager.onConfigureResult(appWidgetId, resultCode))
        refreshWidgets()
    }

    fun removeWidget(appWidgetId: Int) {
        widgetHostManager.remove(appWidgetId)
        refreshWidgets()
    }

    fun resizeWidget(appWidgetId: Int) {
        widgetHostManager.resizeNext(appWidgetId)
        refreshWidgets()
    }

    fun reconfigureWidget(appWidgetId: Int) {
        val intent = widgetHostManager.reconfigureIntent(appWidgetId)
        if (intent == null) refreshWidgets() else eventsMutable.tryEmit(
            LauncherEvent.LaunchWidgetConfigure(intent, appWidgetId),
        )
    }

    fun reorderWidget(appWidgetId: Int, direction: Int) {
        val items = widgetInstances.value.sortedBy(WidgetInstance::position).toMutableList()
        val index = items.indexOfFirst { it.appWidgetId == appWidgetId }
        val target = index + direction
        if (index < 0 || target !in items.indices) return
        val moved = items.removeAt(index)
        items.add(target, moved)
        widgetHostManager.reorder(items.map(WidgetInstance::appWidgetId))
        refreshWidgets()
    }

    fun cancelPendingWidgets() {
        widgetHostManager.cancelPending()
        refreshWidgets()
    }

    fun refreshWidgets() {
        viewModelScope.launch {
            val values = withContext(Dispatchers.IO) {
                widgetHostManager.instances() to widgetHostManager.providers()
            }
            widgetInstances.value = values.first.sortedBy(WidgetInstance::position)
            widgetProviders.value = values.second
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) = updateSettings {
        preferences.setNotificationsEnabled(enabled)
    }
    fun setMediaPlayerEnabled(enabled: Boolean) = updateSettings {
        preferences.setMediaPlayerEnabled(enabled)
    }
    fun setNotificationIndicatorStyle(style: NotificationIndicatorStyle) = updateSettings {
        preferences.setNotificationIndicatorStyle(style)
    }
    fun setNotificationPreviewContent(show: Boolean) = updateSettings {
        preferences.setNotificationPreviewContent(show)
    }
    fun setShowSilentNotifications(show: Boolean) = updateSettings {
        preferences.setShowSilentNotifications(show)
    }

    fun toggleMediaPlayback() = MediaPlaybackRepository.togglePlayback()
    fun skipMediaPrevious() = MediaPlaybackRepository.skipPrevious()
    fun skipMediaNext() = MediaPlaybackRepository.skipNext()
    fun openMediaPlayer() = MediaPlaybackRepository.openPlayer()
    fun setProfileLayout(layout: LauncherProfileLayout) = updateSettings { preferences.setProfileLayout(layout) }
    fun setShowPrivateSpace(show: Boolean) = updateSettings { preferences.setShowPrivateSpace(show) }

    fun requestProfileAvailability(profile: LauncherProfile, available: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            appManager.requestProfileAvailability(profile, available)
            withContext(Dispatchers.Main) { observeAppsIfNeeded() }
        }
    }

    fun openNotification(entry: noryn.launcher.notifications.NotificationEntry) {
        if (NotificationRepository.launchContent(getApplication(), entry)) return
        uiState.value.apps.firstOrNull {
            it.userSerial == entry.profileSerial && it.packageName == entry.packageName
        }?.let(::launch)
    }

    fun sendNotificationAction(
        action: noryn.launcher.notifications.NotificationAction,
        reply: String?,
    ): Boolean = NotificationRepository.sendAction(getApplication(), action, reply)

    private fun handleWidgetStep(step: WidgetHostStep) {
        when (step) {
            is WidgetHostStep.LaunchBind -> eventsMutable.tryEmit(LauncherEvent.LaunchWidgetBind(step.intent))
            is WidgetHostStep.LaunchConfigure -> {
                val id = step.intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                if (id >= 0) eventsMutable.tryEmit(LauncherEvent.LaunchWidgetConfigure(step.intent, id))
            }
            WidgetHostStep.Completed, WidgetHostStep.Failed -> Unit
        }
    }

    private fun updateNotificationProfiles(profileList: List<LauncherProfile>, settings: LauncherSettings) {
        NotificationRepository.setAccessibleProfiles(profileList.asSequence()
            .filter { it.isAvailable && (it.type != LauncherProfileType.Private || settings.showPrivateSpace) }
            .map(LauncherProfile::serial)
            .toSet())
    }

    fun openAppInfo(app: LauncherApp) {
        if (appManager.openAppInfo(app)) returnToHome()
    }

    fun loadIcon(app: LauncherApp, settings: LauncherSettings) = appManager.loadIcon(
        app = app,
        selectedIconPack = settings.iconPackPackageName,
        customIcon = settings.iconOverrides[app.id],
    )

    fun loadIconPackIcon(packageName: String, drawableName: String) =
        appManager.loadIconPackIcon(packageName, drawableName)

    fun toggleFavorite(app: LauncherApp) {
        viewModelScope.launch {
            if (!preferences.toggleFavorite(app.id)) eventsMutable.emit(LauncherEvent.FavoriteLimitReached)
        }
    }

    fun reorderFavorite(app: LauncherApp, targetIndex: Int) {
        viewModelScope.launch { preferences.reorderFavorite(app.id, targetIndex) }
    }

    fun saveFavoriteOrder(appIds: List<String>) {
        viewModelScope.launch { preferences.saveFavoriteOrder(appIds) }
    }

    fun toggleHidden(app: LauncherApp) {
        viewModelScope.launch { preferences.toggleHidden(app.id) }
    }

    fun setTheme(choice: ThemeChoice) = updateSettings { preferences.setThemeChoice(choice) }
    fun setAppLabelMode(mode: AppLabelMode) = updateSettings { preferences.setAppLabelMode(mode) }
    fun setAppIconSize(size: SizePreset) = updateSettings { preferences.setAppIconSize(size) }
    fun setRowSpacing(spacing: RowSpacing) = updateSettings { preferences.setRowSpacing(spacing) }
    fun setClockSize(size: SizePreset) = updateSettings { preferences.setClockSize(size) }
    fun setClockAlignment(alignment: ClockAlignment) = updateSettings { preferences.setClockAlignment(alignment) }
    fun setShowAlphabetIndex(show: Boolean) = updateSettings { preferences.setShowAlphabetIndex(show) }
    fun setShowClock(show: Boolean) = updateSettings { preferences.setShowClock(show) }
    fun setShowDate(show: Boolean) = updateSettings { preferences.setShowDate(show) }
    fun setMaxFavorites(count: Int) = updateSettings { preferences.setMaxFavorites(count) }
    fun setSearchKeyboardImmediately(show: Boolean) = updateSettings {
        preferences.setSearchKeyboardImmediately(show)
    }
    fun setSwipeDownAction(action: HomeGestureAction) = updateSettings { preferences.setSwipeDownAction(action) }
    fun setSwipeUpAction(action: HomeGestureAction) = updateSettings { preferences.setSwipeUpAction(action) }
    fun setDoubleTapAction(action: HomeGestureAction) = updateSettings { preferences.setDoubleTapAction(action) }
    fun setFontChoice(choice: FontChoice) = updateSettings { preferences.setFontChoice(choice) }

    fun setIconPack(packageName: String) {
        viewModelScope.launch {
            preferences.setIconPack(packageName)
            iconChoices.value = if (packageName.isBlank()) emptyList() else withContext(Dispatchers.IO) {
                appManager.iconPackDrawableNames(packageName)
            }
        }
    }

    fun setSuggestionsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setSuggestionsEnabled(enabled)
            if (enabled) refreshSuggestedApps() else suggestedPackageNames.value = emptyList()
        }
    }

    fun refreshSuggestedApps() {
        viewModelScope.launch { refreshSuggestedAppsInternal() }
    }

    private fun openSettingsScreen(destination: LauncherScreen) {
        screen.value = destination
    }

    private fun updateSettings(update: suspend () -> Unit) {
        viewModelScope.launch { update() }
    }

    private fun observeAppsIfNeeded() {
        if (appObservation?.isActive == true) return
        loading.value = true
        appObservation = viewModelScope.launch {
            appManager.observeApps().collect { snapshot ->
                iconRevision.value += 1
                apps.value = snapshot.apps
                profiles.value = snapshot.profiles
                updateNotificationProfiles(snapshot.profiles, preferences.settings.first())
                loading.value = false
                iconPacks.value = withContext(Dispatchers.IO) { appManager.installedIconPacks() }
                refreshSuggestedAppsInternal()
            }
        }
    }

    private suspend fun refreshSuggestedAppsInternal() {
        val usageAccessGranted = withContext(Dispatchers.IO) { suggestedAppsManager.hasUsageAccess() }
        hasUsageAccess.value = usageAccessGranted
        val settings = preferences.settings.first()
        suggestedPackageNames.value = if (settings.suggestionsEnabled && usageAccessGranted) {
            withContext(Dispatchers.IO) { suggestedAppsManager.recentPackages() }
        } else {
            emptyList()
        }
    }

    private fun buildUiState(
        core: CoreState,
        currentScreen: LauncherScreen,
        isDefault: Boolean,
        isLoading: Boolean,
        widgetState: WidgetState,
    ): LauncherUiState {
        val hiddenIds = core.settings.hiddenIds
        val collator = Collator.getInstance(core.locale)
        val profileBySerial = core.profiles.associateBy(LauncherProfile::serial)
        val launcherPackage = getApplication<Application>().packageName
        val permittedApps = core.apps.filter { app ->
            if (app.packageName == launcherPackage) return@filter false
            val profile = profileBySerial[app.userSerial]
            ProfileVisibility.shouldShowApp(
                app.profileType,
                profile?.isAvailable == true,
                core.settings.showPrivateSpace,
            )
        }
        val visibleApps = permittedApps.asSequence()
            .filterNot { it.id in hiddenIds }
            .sortedWith(compareBy(collator) { core.settings.customNames[it.id]?.takeIf(String::isNotBlank) ?: it.label })
            .toList()
        val appsById = permittedApps.associateBy(LauncherApp::id)
        val favorites = core.settings.favoriteIds.asSequence()
            .mapNotNull(appsById::get)
            .filterNot { it.id in hiddenIds }
            .take(core.settings.maxFavorites)
            .toList()
        val normalizedQuery = AppSearchRanking.normalize(core.query)
        val favoriteIds = favorites.mapTo(HashSet(), LauncherApp::id)
        val searchResults = if (normalizedQuery.isBlank()) {
            visibleApps
        } else {
            visibleApps.asSequence()
                .mapNotNull { app ->
                    val customName = core.settings.customNames[app.id].orEmpty()
                    val matchRank = AppSearchRanking.rank(app.label, customName, normalizedQuery)
                    if (matchRank < 0) null else app to matchRank
                }
                .sortedWith(
                    compareBy<Pair<LauncherApp, Int>> { it.second }
                        .thenByDescending { it.first.id in favoriteIds }
                        .thenBy(collator) { core.settings.customNames[it.first.id]?.takeIf(String::isNotBlank) ?: it.first.label },
                )
                .map { it.first }
                .toList()
        }
        val suggestedApps = core.suggestedPackageNames.asSequence()
            .mapNotNull { packageName -> visibleApps.firstOrNull { it.packageName == packageName } }
            .filterNot { it.id in favoriteIds }
            .distinctBy(LauncherApp::id)
            .take(SUGGESTED_APP_LIMIT)
            .toList()

        val profileGroups = buildProfileGroups(visibleApps, core.profiles, core.settings, core.locale)

        return LauncherUiState(
            screen = currentScreen,
            settings = core.settings,
            apps = permittedApps,
            visibleApps = visibleApps,
            sections = buildAppSections(visibleApps, core.settings.customNames, core.locale),
            profiles = core.profiles,
            profileGroups = profileGroups,
            widgetInstances = widgetState.instances,
            widgetProviders = widgetState.providers,
            favorites = favorites,
            hiddenApps = permittedApps.filter { it.id in hiddenIds },
            suggestedApps = suggestedApps,
            searchResults = searchResults,
            iconPacks = core.iconPacks,
            iconRevision = core.iconRevision,
            editingApp = core.editingAppId?.let(appsById::get),
            iconChoices = core.iconChoices,
            isLoadingIconChoices = core.loadingIconChoices,
            query = core.query,
            isLoading = isLoading,
            isDefaultLauncher = isDefault,
            hasUsageAccess = core.hasUsageAccess,
            hasNotificationAccess = core.hasNotificationAccess,
        )
    }

    private fun buildProfileGroups(
        visibleApps: List<LauncherApp>,
        profileList: List<LauncherProfile>,
        settings: LauncherSettings,
        locale: Locale,
    ): List<AppSectionGroup> {
        val profilesToShow = profileList.filter { profile ->
            ProfileVisibility.shouldShowProfile(
                profile.type,
                profile.isAvailable,
                settings.showPrivateSpace,
                profile.privateEntryHiddenWhenLocked,
            )
        }.sortedWith(compareBy<LauncherProfile> { profileOrder(it.type) }.thenBy(LauncherProfile::serial))
        val collator = Collator.getInstance(locale)
        val activeApps = visibleApps.filter { app -> profilesToShow.any { it.serial == app.userSerial && it.isAvailable } }
        if (settings.profileLayout == LauncherProfileLayout.Mix) {
            val mixed = activeApps.filter { ProfileVisibility.belongsToMixedContainer(it.profileType) }.sortedWith(compareBy(collator) {
                settings.customNames[it.id]?.takeIf(String::isNotBlank) ?: it.label
            })
            val groups = ArrayList<AppSectionGroup>()
            if (mixed.isNotEmpty()) {
                groups += AppSectionGroup(
                    key = "mixed",
                    title = "all",
                    profileType = null,
                    profile = null,
                    sections = buildAppSections(mixed, settings.customNames, locale),
                )
            }
            profilesToShow.filter { profile ->
                profile.type == LauncherProfileType.Private ||
                    profile.type == LauncherProfileType.Work
            }.forEach { profile ->
                groups += profileGroup(profile, activeApps.filter { it.userSerial == profile.serial }, settings, locale)
            }
            return groups
        }
        return profilesToShow.asSequence()
            .filter { profile ->
                profile.isAvailable || profile.type in setOf(LauncherProfileType.Work, LauncherProfileType.Private)
            }
            .map { profile ->
                val appsForProfile = activeApps.filter { it.userSerial == profile.serial }
                    .sortedWith(compareBy(collator) { settings.customNames[it.id]?.takeIf(String::isNotBlank) ?: it.label })
                profileGroup(profile, appsForProfile, settings, locale)
            }
            .toList()
    }

    private fun profileGroup(
        profile: LauncherProfile,
        apps: List<LauncherApp>,
        settings: LauncherSettings,
        locale: Locale,
    ) = AppSectionGroup(
        key = "profile_${profile.serial}",
        title = profile.type.name.lowercase(Locale.ROOT),
        profileType = profile.type,
        profile = profile,
        sections = buildAppSections(apps, settings.customNames, locale),
    )

    private fun profileOrder(type: LauncherProfileType): Int = when (type) {
        LauncherProfileType.Personal -> 0
        LauncherProfileType.Work -> 1
        LauncherProfileType.Private -> 2
        LauncherProfileType.Clone -> 3
        LauncherProfileType.Other -> 4
    }

    private fun returnToHome() {
        query.value = ""
        editingAppId.value = null
        screen.value = LauncherScreen.Home
    }

    private fun flushPendingAppName() {
        renameJob?.cancel()
        renameJob = null
        if (pendingAppName != null) viewModelScope.launch { persistPendingAppName() }
    }

    private suspend fun persistPendingAppName() {
        val pending = pendingAppName ?: return
        preferences.setCustomName(pending.first, pending.second)
        if (pendingAppName == pending) pendingAppName = null
    }

    override fun onCleared() {
        widgetHostManager.close()
    }

    companion object {
        private const val SUGGESTED_APP_LIMIT = 6
        private const val NAME_SAVE_DEBOUNCE_MS = 240L
        private const val SYSTEM_ICON_OVERRIDE = "@system"

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
