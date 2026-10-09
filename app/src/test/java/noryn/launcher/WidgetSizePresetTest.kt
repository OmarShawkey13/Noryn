package noryn.launcher

import noryn.launcher.core.model.WidgetSizePreset
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizePresetTest {
    @Test
    fun cyclesThroughCompactStandardAndTallSizes() {
        assertEquals(WidgetSizePreset.Standard, WidgetSizePreset.Compact.next())
        assertEquals(WidgetSizePreset.Tall, WidgetSizePreset.Standard.next())
        assertEquals(WidgetSizePreset.Compact, WidgetSizePreset.Tall.next())
    }
}
