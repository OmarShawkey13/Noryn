package noryn.launcher.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import noryn.launcher.R

/** The small Tabler subset used for Noryn's own interface controls. */
internal enum class TablerIconName(@DrawableRes val drawable: Int) {
    Search(R.drawable.tabler_search),
    Close(R.drawable.tabler_x),
    PlayerTrackPrevious(R.drawable.tabler_player_track_prev),
    PlayerPlay(R.drawable.tabler_player_play),
    PlayerPause(R.drawable.tabler_player_pause),
    PlayerTrackNext(R.drawable.tabler_player_track_next),
    Adjustments(R.drawable.tabler_adjustments_horizontal),
    ArrowLeft(R.drawable.tabler_arrow_left),
    ArrowUp(R.drawable.tabler_arrow_up),
    ArrowDown(R.drawable.tabler_arrow_down),
    MoreVertical(R.drawable.tabler_dots_vertical),
    ChevronUp(R.drawable.tabler_chevron_up),
    ChevronDown(R.drawable.tabler_chevron_down),
    ChevronRight(R.drawable.tabler_chevron_right),
    Star(R.drawable.tabler_star),
    StarFilled(R.drawable.tabler_star_filled),
    Pencil(R.drawable.tabler_pencil),
    InfoCircle(R.drawable.tabler_info_circle),
    EyeOff(R.drawable.tabler_eye_off),
    Check(R.drawable.tabler_check),
    Bell(R.drawable.tabler_bell),
}

@Composable
internal fun TablerIcon(
    name: TablerIconName,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(
        painter = painterResource(name.drawable),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
