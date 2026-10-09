package noryn.launcher.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

internal object NorynHaptics {
    fun sectionTick(feedback: HapticFeedback) {
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun dragStart(feedback: HapticFeedback) {
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun reorder(feedback: HapticFeedback) {
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}
