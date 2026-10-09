package noryn.launcher.core.model

import android.os.UserHandle

data class LauncherProfile(
    val handle: UserHandle,
    val serial: Long,
    val type: LauncherProfileType,
    val quietMode: Boolean,
    val unlocked: Boolean,
    val privateEntryHiddenWhenLocked: Boolean = false,
) {
    val isAvailable: Boolean get() = !quietMode && unlocked
}
