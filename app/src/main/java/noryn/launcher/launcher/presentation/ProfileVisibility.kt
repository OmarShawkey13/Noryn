package noryn.launcher.launcher.presentation

import noryn.launcher.core.model.LauncherProfileType

/** Pure profile display rules shared by the app list and profile-group builder. */
internal object ProfileVisibility {
    fun shouldShowProfile(
        type: LauncherProfileType,
        available: Boolean,
        showPrivateSpace: Boolean,
        privateEntryHiddenWhenLocked: Boolean,
    ): Boolean = when (type) {
        LauncherProfileType.Private -> showPrivateSpace && (available || !privateEntryHiddenWhenLocked)
        else -> true
    }

    fun shouldShowApp(
        type: LauncherProfileType,
        profileKnownAndAvailable: Boolean,
        showPrivateSpace: Boolean,
    ): Boolean = profileKnownAndAvailable && (type != LauncherProfileType.Private || showPrivateSpace)

    fun belongsToMixedContainer(type: LauncherProfileType): Boolean = type != LauncherProfileType.Private
}
