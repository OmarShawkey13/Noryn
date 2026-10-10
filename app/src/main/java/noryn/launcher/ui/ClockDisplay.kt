package noryn.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import noryn.launcher.core.model.ClockAlignment
import noryn.launcher.core.model.ClockDesign
import noryn.launcher.core.model.SizePreset

@Composable
internal fun ClockDisplay(
    timeText: String,
    dateText: String,
    showClock: Boolean,
    showDate: Boolean,
    size: SizePreset,
    alignment: ClockAlignment,
    design: ClockDesign,
    modifier: Modifier = Modifier,
    thumbnail: Boolean = false,
) {
    val horizontalAlignment = if (alignment == ClockAlignment.Center) Alignment.CenterHorizontally else Alignment.Start
    val timeStyle = MaterialTheme.typography.displayLarge.copy(
        fontSize = if (thumbnail) 24.sp else clockDisplaySize(size),
        lineHeight = if (thumbnail) 28.sp else clockDisplaySize(size) * 1.1f,
    )
    val dateStyle = if (thumbnail) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
    val dateColor = MaterialTheme.colorScheme.onSurfaceVariant

    when (design) {
        ClockDesign.Inline -> Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (alignment == ClockAlignment.Center) Arrangement.Center else Arrangement.Start,
        ) {
            if (showClock) {
                Text(timeText, style = timeStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
            if (showClock && showDate) Spacer(Modifier.width(8.dp))
            if (showDate) {
                Text(
                    text = dateText,
                    modifier = Modifier.weight(1f, fill = false).padding(bottom = if (thumbnail) 2.dp else 8.dp),
                    style = dateStyle,
                    color = dateColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ClockDesign.Stacked,
        ClockDesign.DateFirst -> Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(if (thumbnail) 0.dp else 2.dp),
        ) {
            if (design == ClockDesign.DateFirst && showDate) {
                Text(dateText, style = dateStyle, color = dateColor, maxLines = 1)
            }
            if (showClock) {
                Text(
                    timeText,
                    style = timeStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = if (alignment == ClockAlignment.Center) TextAlign.Center else TextAlign.Start,
                )
            }
            if (design == ClockDesign.Stacked && showDate) {
                Text(dateText, style = dateStyle, color = dateColor, maxLines = 1)
            }
        }
    }
}

private fun clockDisplaySize(size: SizePreset) = when (size) {
    SizePreset.Small -> 42.sp
    SizePreset.Default -> 52.sp
    SizePreset.Large -> 60.sp
}
