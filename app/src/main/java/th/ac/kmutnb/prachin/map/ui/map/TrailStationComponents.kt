package th.ac.kmutnb.prachin.map.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.khaoyai.Trail
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailStation
import th.ac.kmutnb.prachin.map.ui.khaoyai.ContentPhoto
import th.ac.kmutnb.prachin.map.ui.khaoyai.PhotoCreditLine
import th.ac.kmutnb.prachin.map.ui.khaoyai.stationKindLabel
import th.ac.kmutnb.prachin.map.navigation.TrailGuideState

/**
 * The trail being walked, and where the walker stands on it (§2.3.1.4 of the proposal):
 * on the trail or off it and by how far, how sure the GPS is, the next station and the
 * distance walked - with SOS and the way out always one tap away.
 */
@Composable
fun ActiveTrailBanner(trail: Trail, guide: TrailGuideState?, onEnd: () -> Unit, onSos: () -> Unit) {
    val off = guide?.isOffTrail == true
    Surface(
        color = if (off) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (off) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.station_walking, trail.name),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSos) {
                    Text(stringResource(R.string.sos_short), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onEnd) { Text(stringResource(R.string.station_end_trail)) }
            }
            val distance = guide?.distanceFromTrail
            Text(
                when {
                    guide == null -> stringResource(R.string.trail_status_waiting)
                    distance == null -> stringResource(R.string.trail_status_no_line, guide.accuracyMeters)
                    off -> stringResource(R.string.trail_status_off, distance, guide.accuracyMeters)
                    guide.isSnapped -> stringResource(R.string.trail_status_on_snapped, distance, guide.accuracyMeters)
                    else -> stringResource(R.string.trail_status_on, distance, guide.accuracyMeters)
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (off) FontWeight.Bold else FontWeight.Normal,
            )
            val next = guide?.nextStation
            Text(
                if (next != null && guide.metersToNextStation != null) {
                    stringResource(
                        R.string.trail_status_next,
                        listOf(next.emoji, next.name).filter { it.isNotBlank() }.joinToString(" "),
                        guide.metersToNextStation,
                        guide.walkedMeters / 1000.0,
                    )
                } else {
                    stringResource(R.string.trail_status_done, (guide?.walkedMeters ?: 0.0) / 1000.0)
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The story of a station, popped up on arrival or opened from its marker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSheet(station: TrailStation, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.station_arrived) + " · " + station.kind.emoji + " " + stationKindLabel(station.kind),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
            if (station.photo.isNotBlank()) {
                ContentPhoto(
                    station.photo,
                    station.emoji,
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentDescription = station.name,
                )
                PhotoCreditLine(station.photo)
            }
            Text(
                listOf(station.emoji, station.name).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (station.description.isNotBlank()) {
                Text(station.description, style = MaterialTheme.typography.bodyLarge)
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.station_continue))
            }
        }
    }
}
