package th.ac.kmutnb.prachin.map.ui.gpslog

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.geojson.GpsPointExporter
import th.ac.kmutnb.prachin.map.data.model.GpsCaptureMode
import th.ac.kmutnb.prachin.map.data.model.GpsFixQuality
import th.ac.kmutnb.prachin.map.location.LocationState

/**
 * The second way into the GPS point log: the numbers, with no map (F11c).
 *
 * Same log, same averaging, same export as [GpsLogScreen] - the shared half lives in
 * `GpsLogComponents.kt`, and each saved reading records which of the two screens took it.
 *
 * It exists because the map is not always what the surveyor needs. Rendering a basemap and
 * following the dot is the most expensive thing this app does, and someone standing at a
 * known spot to take a careful reading is not looking at the map anyway: they are watching
 * the accuracy figure settle. Without the map there is room to show the whole fix - speed,
 * bearing, provider, how old it is - which is what tells them whether the receiver has
 * actually settled or has just stopped reporting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsReadoutScreen(
    onBack: () -> Unit,
    viewModel: GpsLogViewModel = viewModel(factory = GpsLogViewModel.ReadoutFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var exportFilter by rememberSaveable { mutableStateOf<GpsCaptureMode?>(null) }
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }

    fun report(success: Boolean) {
        val message = if (success) {
            context.getString(R.string.export_success)
        } else {
            context.getString(R.string.export_failed, "")
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val exportGeoJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json"),
    ) { uri ->
        uri?.let { viewModel.export(it, GpsLogExportFormat.GEOJSON, exportFilter, ::report) }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let { viewModel.export(it, GpsLogExportFormat.CSV, exportFilter, ::report) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gpsreadout_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (state.points.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.gpslog_export_empty),
                                    )
                                }
                            } else {
                                showExportDialog = true
                            }
                        },
                    ) {
                        Text(stringResource(R.string.gpslog_menu_export))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.gpsreadout_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FixCard(state)

            CaptureCard(
                state = state,
                onStart = viewModel::startCapture,
                onStop = viewModel::stopCapture,
                onCancel = viewModel::cancelCapture,
                onSave = viewModel::savePending,
                onDiscard = viewModel::discardPending,
            )

            RecordedList(
                state = state,
                onDelete = viewModel::deletePoint,
                onDeleteAll = { confirmDeleteAll = true },
            )
        }
    }

    if (showExportDialog) {
        GpsExportDialog(
            pointCount = state.points.size,
            filter = exportFilter,
            onFilterChange = { exportFilter = it },
            onGeoJson = {
                showExportDialog = false
                exportGeoJsonLauncher.launch(GpsPointExporter.defaultFileName("geojson"))
            },
            onCsv = {
                showExportDialog = false
                exportCsvLauncher.launch(GpsPointExporter.defaultFileName("csv"))
            },
            onDismiss = { showExportDialog = false },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            text = { Text(stringResource(R.string.gpslog_delete_all_confirm, state.points.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                        viewModel.deleteAllPoints()
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Everything the last fix said.
 *
 * Deliberately more than the map screen's summary. With no map to judge the position
 * against, the numbers are the only evidence, and the ones that matter are not only the
 * coordinate: an accuracy that looks fine on a fix that arrived forty seconds ago means
 * the receiver has stopped reporting, not that it is doing well.
 */
@Composable
private fun FixCard(state: GpsLogUiState) {
    val accuracy = state.currentAccuracyMeters
    val quality = accuracy?.let { GpsFixQuality.of(it) }
    val fix = state.fix

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = state.currentPoint?.format()
                    ?: stringResource(R.string.gpsreadout_no_fix),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = accuracy
                        ?.let { stringResource(R.string.gpslog_accuracy_value, it) }
                        ?: stringResource(R.string.gpslog_value_none),
                    style = MaterialTheme.typography.headlineSmall,
                    color = quality?.let { colourOf(it) } ?: MaterialTheme.colorScheme.onSurface,
                )
                quality?.let {
                    Text(
                        text = stringResource(it.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = colourOf(it),
                    )
                }
            }

            HorizontalDivider()

            FieldRow(
                stringResource(R.string.gpslog_satellites),
                stringResource(
                    R.string.gpslog_satellites_value,
                    state.satellites.inUse,
                    state.satellites.visible,
                ),
            )
            FieldRow(
                stringResource(R.string.gpslog_elevation),
                fix?.altitudeMeters
                    ?.let { stringResource(R.string.gpslog_elevation_value, it) }
                    ?: stringResource(R.string.gpslog_value_none),
            )
            FieldRow(
                stringResource(R.string.gpslog_field_vertical_accuracy),
                fix?.verticalAccuracyMeters
                    ?.let { stringResource(R.string.gpslog_accuracy_value, it) }
                    ?: stringResource(R.string.gpslog_value_none),
            )
            FieldRow(
                stringResource(R.string.gpsreadout_field_speed),
                fix?.speedMps
                    ?.let { stringResource(R.string.gpsreadout_speed_value, it) }
                    ?: stringResource(R.string.gpslog_value_none),
            )
            FieldRow(
                stringResource(R.string.gpsreadout_field_bearing),
                fix?.bearingDegrees
                    ?.let { stringResource(R.string.gpsreadout_bearing_value, it) }
                    ?: stringResource(R.string.gpslog_value_none),
            )
            FieldRow(
                stringResource(R.string.gpsreadout_field_fix_age),
                if (fix == null) {
                    stringResource(R.string.gpslog_value_none)
                } else {
                    stringResource(
                        R.string.gpsreadout_fix_age_value,
                        ((SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) /
                            1_000_000_000L).toInt().coerceAtLeast(0),
                    )
                },
            )
            FieldRow(
                stringResource(R.string.gpsreadout_field_rejected_live),
                ((state.locationState as? LocationState.Available)?.rejectedCount ?: 0).toString(),
            )
        }
    }
}

/** The log so far, newest first: the one just taken is the one most likely to be wrong. */
@Composable
private fun RecordedList(
    state: GpsLogUiState,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    Text(
        text = stringResource(R.string.gpslog_points_count, state.points.size),
        style = MaterialTheme.typography.titleSmall,
    )

    if (state.points.isEmpty()) {
        Text(
            text = stringResource(R.string.gpslog_list_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    state.points.asReversed().forEach { point ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${point.code}  ${point.point.format()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = stringResource(
                        R.string.gpslog_list_item_summary,
                        point.accuracyMeters,
                        point.satellitesUsed,
                        formatMoment(point.recordedAt),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colourOf(point.quality),
                )
            }
            TextButton(onClick = { onDelete(point.id) }) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        HorizontalDivider()
    }

    OutlinedButton(onClick = onDeleteAll, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.gpslog_delete_all),
            color = MaterialTheme.colorScheme.error,
        )
    }
}
