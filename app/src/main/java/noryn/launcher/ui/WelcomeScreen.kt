package noryn.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import noryn.launcher.R
import noryn.launcher.ui.theme.LauncherDimens

@Composable
internal fun WelcomeScreen(onSetHome: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LauncherDimens.ScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(LauncherDimens.SectionSpacing),
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Spacer(Modifier.height(LauncherDimens.SectionSpacing))
            Box(
                modifier = Modifier
                    .size(LauncherDimens.WelcomeMarkSize)
                    .clip(RoundedCornerShape(LauncherDimens.PanelCornerRadius))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.product_name).first().uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(LauncherDimens.SectionSpacing))
            Text(
                text = stringResource(R.string.product_name),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.welcome_tagline),
                modifier = Modifier.padding(top = LauncherDimens.CompactSpacing),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(LauncherDimens.CompactSpacing)) {
            Text(
                stringResource(R.string.welcome_setup_intro),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(LauncherDimens.PanelCornerRadius),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    WelcomeStepRow("1", R.string.welcome_step_home, R.string.welcome_step_home_summary)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    WelcomeStepRow("2", R.string.welcome_step_favorites, R.string.welcome_step_favorites_summary)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    WelcomeStepRow("3", R.string.welcome_step_widgets, R.string.welcome_step_widgets_summary)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onSetHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LauncherDimens.WelcomeActionHeight),
                shape = RoundedCornerShape(LauncherDimens.PanelCornerRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.set_as_home),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                stringResource(R.string.welcome_set_home_note),
                modifier = Modifier.padding(top = LauncherDimens.CompactSpacing, bottom = LauncherDimens.CompactSpacing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WelcomeStepRow(number: String, title: Int, summary: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
