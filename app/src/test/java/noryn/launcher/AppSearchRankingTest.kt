package noryn.launcher

import noryn.launcher.launcher.presentation.AppSearchRanking
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchRankingTest {
    @Test
    fun ranksExactPrefixWordAndSubstringMatches() {
        assertEquals(0, AppSearchRanking.rank("Signal", "", "signal"))
        assertEquals(1, AppSearchRanking.rank("Signal", "", "sig"))
        assertEquals(2, AppSearchRanking.rank("Google Play Store", "", "play"))
        assertEquals(3, AppSearchRanking.rank("Telegram", "", "gram"))
        assertEquals(-1, AppSearchRanking.rank("Telegram", "", "camera"))
    }

    @Test
    fun searchesBothCustomAndOriginalLabels() {
        assertEquals(0, AppSearchRanking.rank("Google Maps", "Places", "places"))
        assertEquals(1, AppSearchRanking.rank("Google Maps", "Places", "goo"))
    }

    @Test
    fun foldsLatinDiacriticsForLocalSearch() {
        assertEquals(0, AppSearchRanking.rank("Café", "", "cafe"))
    }
}
