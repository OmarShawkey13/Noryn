package noryn.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import noryn.launcher.R
import noryn.launcher.ui.theme.LauncherDimens

@Composable
internal fun WelcomeScreen(onSetHome: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LauncherDimens.ScreenHorizontalPadding),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(LauncherDimens.SectionSpacing))
        Column(horizontalAlignment = Alignment.Start) {
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
        Button(
            onClick = onSetHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(LauncherDimens.WelcomeActionHeight)
                .padding(bottom = LauncherDimens.CompactSpacing),
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
    }
}
