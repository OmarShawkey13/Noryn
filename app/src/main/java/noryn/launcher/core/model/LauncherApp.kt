package noryn.launcher.core.model

import android.os.UserHandle

data class LauncherApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val normalizedLabel: String,
    val userHandle: UserHandle,
    val userSerial: Long,
    val profileType: LauncherProfileType = LauncherProfileType.Other,
) {
    val componentName: String = "$packageName/$activityName"
    val id: String = "$userSerial:$componentName"
}

enum class LauncherProfileType {
    Personal,
    Work,
    Private,
    Clone,
    Other,
}
