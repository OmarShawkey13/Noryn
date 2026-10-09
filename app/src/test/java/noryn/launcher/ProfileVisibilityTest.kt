package noryn.launcher

import noryn.launcher.core.model.LauncherProfileType
import noryn.launcher.launcher.presentation.ProfileVisibility
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileVisibilityTest {
    @Test
    fun hidesPrivateAppsWhenLockedOrHidden() {
        assertFalse(ProfileVisibility.shouldShowApp(LauncherProfileType.Private, false, true))
        assertFalse(ProfileVisibility.shouldShowApp(LauncherProfileType.Private, true, false))
        assertTrue(ProfileVisibility.shouldShowApp(LauncherProfileType.Private, true, true))
    }

    @Test
    fun honorsSystemHiddenPrivateEntrypointOnlyWhileLocked() {
        assertFalse(ProfileVisibility.shouldShowProfile(LauncherProfileType.Private, false, true, true))
        assertTrue(ProfileVisibility.shouldShowProfile(LauncherProfileType.Private, true, true, true))
        assertFalse(ProfileVisibility.shouldShowProfile(LauncherProfileType.Private, true, false, false))
    }

    @Test
    fun privateSpaceStaysSeparateWhenOtherProfilesAreMixed() {
        assertTrue(ProfileVisibility.belongsToMixedContainer(LauncherProfileType.Personal))
        assertTrue(ProfileVisibility.belongsToMixedContainer(LauncherProfileType.Work))
        assertFalse(ProfileVisibility.belongsToMixedContainer(LauncherProfileType.Private))
    }
}
