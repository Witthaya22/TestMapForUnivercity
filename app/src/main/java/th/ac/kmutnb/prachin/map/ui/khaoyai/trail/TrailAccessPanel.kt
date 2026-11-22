package th.ac.kmutnb.prachin.map.ui.khaoyai.trail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.khaoyai.Trail
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailAccess
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailAccessRules
import th.ac.kmutnb.prachin.map.location.LocationState
import th.ac.kmutnb.prachin.map.ui.khaoyai.openLink
import th.ac.kmutnb.prachin.map.ui.khaoyai.rememberLocationUi
import th.ac.kmutnb.prachin.map.ui.theme.HornbillAmber
import th.ac.kmutnb.prachin.map.ui.theme.SuccessGreen
import java.util.Locale

/**
 * The bottom of a trail page: whether the visitor may start it from where they stand, and
 * the start button that follows from that. Far away, the start stays locked and the way to
 * the trailhead is offered instead - by road, in whatever maps app the phone has, because
 * the walking map here covers the trail, not the drive there.
 */
@Composable
fun TrailAccessPanel(trail: Trail, onStart: () -> Unit) {
    val location = rememberLocationUi()
    val state = location.state
    val here = (state as? LocationState.Available)?.fix?.point
    val access = TrailAccessRules.evaluate(trail, here)
    val context = LocalContext.current

    Surface(shadowElevation = 8.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (access) {
                TrailAccess.Demo -> Status(stringResource(R.string.access_demo), MaterialTheme.colorScheme.primary)
                TrailAccess.Closed -> Status(stringResource(R.string.access_closed), MaterialTheme.colorScheme.error)
                TrailAccess.Unmapped -> Status(stringResource(R.string.access_unmapped), MaterialTheme.colorScheme.onSurfaceVariant)
                is TrailAccess.Inside -> Status(
                    stringResource(R.string.access_inside, distanceText(access.distanceMeters)),
                    SuccessGreen,
                )
                is TrailAccess.TooFar -> {
                    Status(stringResource(R.string.access_too_far_title), HornbillAmber, bold = true)
                    Text(
                        stringResource(
                            R.string.access_too_far_body,
                            distanceText(access.distanceMeters),
                            distanceText(access.radiusMeters.toDouble()),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TrailAccess.NoLocation -> when (state) {
                    LocationState.PermissionMissing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.access_need_permission),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = location.requestPermission) { Text(stringResource(R.string.location_allow)) }
                    }
                    LocationState.ProviderDisabled -> Status(stringResource(R.string.access_gps_off), MaterialTheme.colorScheme.error)
                    else -> Status(stringResource(R.string.access_searching), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val destination = TrailAccessRules.destination(trail)
            if (access is TrailAccess.TooFar && destination != null) {
                OutlinedButton(
                    onClick = {
                        // A web link, not an in-app request: Google Maps opens it when
                        // installed, a browser otherwise. This app itself fetches nothing.
                        openLink(
                            context,
                            String.format(
                                Locale.US,
                                "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=driving",
                                destination.lat,
                                destination.lon,
                            ),
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(painterResource(R.drawable.ic_navigation), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.access_directions))
                }
            }

            Button(
                onClick = onStart,
                enabled = access.canStart,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(painterResource(R.drawable.ic_walk), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (access.canStart) R.string.access_start else R.string.access_locked),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun Status(text: String, colour: Color, bold: Boolean = false) {
    Text(
        text,
        color = colour,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
    )
}

@Composable
private fun distanceText(meters: Double): String = if (meters < 1_000) {
    stringResource(R.string.distance_short_m, meters.toInt())
} else {
    stringResource(R.string.distance_short_km, meters / 1_000)
}
