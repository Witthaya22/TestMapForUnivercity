package th.ac.kmutnb.prachin.map.ui.debug

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.location.LocationState

/**
 * Live receiver diagnostics (docs/ACCURACY.md).
 *
 * The line that matters most is the distance to the nearest walking path: good accuracy
 * combined with a large distance means the surveyed data is wrong, while poor accuracy means
 * the receiver has not settled. Without it, both failures look identical in the field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsDebugScreen(
    onBack: () -> Unit,
    viewModel: GpsDebugViewModel = viewModel(factory = GpsDebugViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gps_debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (val location = state.locationState) {
                LocationState.PermissionMissing ->
                    Text(stringResource(R.string.permission_denied_title))

                LocationState.ProviderDisabled ->
                    Text(stringResource(R.string.map_gps_disabled))

                is LocationState.Searching -> {
                    Text(stringResource(R.string.gps_debug_no_fix))
                    DebugRow(
                        stringResource(R.string.gps_debug_satellites),
                        "${location.satellites.inUse} / ${location.satellites.visible}",
                    )
                    location.lastAccuracyMeters?.let {
                        DebugRow(
                            stringResource(R.string.gps_debug_accuracy),
                            "%.1f m".format(it),
                        )
                    }
                    location.lastRejection?.let {
                        DebugRow(stringResource(R.string.gps_debug_rejected), it.name)
                    }
                }

                is LocationState.Available -> {
                    val fix = location.fix
                    DebugRow(stringResource(R.string.gps_debug_provider), fix.provider)
                    DebugRow(stringResource(R.string.gps_debug_position), fix.point.format())
                    DebugRow(
                        stringResource(R.string.gps_debug_raw_vs_filtered),
                        "${fix.rawPoint.format()}  ->  ${fix.point.format()}",
                    )
                    DebugRow(
                        stringResource(R.string.gps_debug_accuracy),
                        "%.1f m".format(fix.accuracyMeters),
                    )
                    DebugRow(
                        stringResource(R.string.gps_debug_satellites),
                        "${location.satellites.inUse} / ${location.satellites.visible}",
                    )
                    DebugRow(
                        stringResource(R.string.gps_debug_speed),
                        fix.speedMps?.let { "%.2f m/s".format(it) } ?: "-",
                    )
                    DebugRow(
                        stringResource(R.string.gps_debug_bearing),
                        fix.bearingDegrees?.let { "%.0f°".format(it) } ?: "-",
                    )
                    DebugRow(
                        stringResource(R.string.gps_debug_age),
                        stringResource(
                            R.string.gps_debug_seconds_ago,
                            ((SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) /
                                1_000_000_000L).toInt(),
                        ),
                    )
                    DebugRow(
                        stringResource(R.string.gps_debug_rejected),
                        location.rejectedCount.toString(),
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    DebugRow(
                        stringResource(R.string.gps_debug_distance_to_path),
                        state.distanceToPathMeters
                            ?.let { "%.1f m".format(it) }
                            ?: "-",
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.gps_debug_hint),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.55f),
        )
    }
}
