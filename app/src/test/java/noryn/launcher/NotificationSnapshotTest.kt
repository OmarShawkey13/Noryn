package noryn.launcher

import noryn.launcher.notifications.NotificationEntry
import noryn.launcher.notifications.NotificationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSnapshotTest {
    @Test
    fun visibleEntriesAreScopedToProfileAndPackage() {
        val personal = entry("personal", serial = 1, packageName = "com.example.chat")
        val work = entry("work", serial = 2, packageName = "com.example.chat")
        val otherApp = entry("other", serial = 1, packageName = "com.example.mail")
        val snapshot = NotificationSnapshot(
            byApp = listOf(personal, work, otherApp).groupBy(NotificationEntry::appKey),
        )

        assertEquals(listOf(personal), snapshot.visibleForApp(1, "com.example.chat", showSilent = false))
        assertEquals(listOf(work), snapshot.visibleForApp(2, "com.example.chat", showSilent = false))
    }

    @Test
    fun silentAndNonCountableEntriesAreFilteredByPreference() {
        val active = entry("active")
        val silent = entry("silent", silent = true)
        val ongoing = entry("ongoing", countable = false)
        val snapshot = NotificationSnapshot(byApp = listOf(active, silent, ongoing).groupBy(NotificationEntry::appKey))

        assertEquals(listOf(active), snapshot.visibleForApp(1, "com.example.chat", showSilent = false))
        assertEquals(listOf(active, silent), snapshot.visibleForApp(1, "com.example.chat", showSilent = true))
    }

    private fun entry(
        key: String,
        serial: Long = 1,
        packageName: String = "com.example.chat",
        silent: Boolean = false,
        countable: Boolean = true,
    ) = NotificationEntry(
        key = key,
        packageName = packageName,
        profileSerial = serial,
        title = "",
        text = "",
        postedAtMillis = 0,
        silent = silent,
        countable = countable,
        contentIntent = null,
        actions = emptyList(),
    )
}
