package th.ac.kmutnb.prachin.map.ui.pathlog

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
import androidx.compose.ui.Alignment
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
import th.ac.kmutnb.prachin.map.data.geojson.TrackLogExporter
import th.ac.kmutnb.prachin.map.data.model.GpsFixQuality
import th.ac.kmutnb.prachin.map.data.model.TrackCaptureMode
import th.ac.kmutnb.prachin.map.location.LocationState
import th.ac.kmutnb.prachin.map.ui.common.Formats

/**
 * Making a path by walking it, without a map (F11c).
 *
 * The same log as [PathLogScreen] and the same recorder; what is gone is the map. That is
 * worth a screen of its own for the same reason the GPS readout is: rendering a basemap is
 * the most expensive thing this app does, and a walker who already knows where they are is
 * paying for a picture they are not looking at. On a long survey that is the difference
 * between finishing the afternoon and not.
 *
 * With no map to judge the line against, the numbers have to be complete - so this screen
 * shows everything the last fix reported, including how old it is. An accuracy that looks
 * fine on a fix from forty seconds ago means the receiver has stopped reporting, and while
 * recording a path that would quietly draw a straight line across whatever was walked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathReadoutScreen(
    onBack: () -> Unit,
    viewModel: PathLogViewModel = viewModel(
        factory = PathLogViewModel.factory(TrackCaptureMode.READOUT),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    // Exactly which tracks the next export writes. Held here rather than in the dialog so
    // that opening it from one track's detail sheet can arrive with that one ticked.
    var exportIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    // Names the file after the track when there is only one, so a folder of exports stays
    // tellable apart.
    val exportCode = state.tracks.singleOrNull { it.id in exportIds }?.code
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
        uri?.let { viewModel.export(it, PathLogExportFormat.GEOJSON, exportIds, ::report) }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let { viewModel.export(it, PathLogExportFormat.CSV, exportIds, ::report) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pathreadout_title)) },
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
                            if (state.tracks.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.pathlog_export_empty),
                                    )
                                }
                            } else {
                                exportIds = state.tracks.map { it.id }.toSet()
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
                text = stringResource(R.string.pathreadout_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FixCard(state)

            RecordCard(
                state = state,
                onStart = viewModel::startRecording,
                onStop = {
                    if (!viewModel.stopRecording()) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.pathlog_discarded_too_short),
                            )
                        }
                    }
                },
                onCancel = viewModel::cancelRecording,
                onSave = viewModel::savePending,
                onDiscard = viewModel::discardPending,
                modifier = Modifier.fillMaxWidth(),
            )

            RecordedList(
                state = state,
                onSelect = viewModel::select,
                onDeleteAll = { confirmDeleteAll = true },
            )
        }
    }

    state.selectedTrack?.let { track ->
        TrackDetailSheet(
            track = track,
            onDismiss = viewModel::dismissSelection,
            onSaveNote = { note -> viewModel.updateNote(track.id, note) },
            onSetUsedForRouting = { used -> viewModel.setUsedForRouting(track.id, used) },
            onExport = {
                exportIds = setOf(track.id)
                viewModel.dismissSelection()
                showExportDialog = true
            },
            onDelete = { viewModel.delete(track.id) },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            text = { Text(stringResource(R.string.pathlog_delete_all_confirm, state.tracks.size)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmDeleteAll = false; viewModel.deleteAll() },
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

    if (showExportDialog) {
        TrackExportDialog(
            tracks = state.tracks,
            selectedIds = exportIds,
            onToggle = { id ->
                exportIds = if (id in exportIds) exportIds - id else exportIds + id
            },
            onSelectAll = { exportIds = state.tracks.map { it.id }.toSet() },
            onClearAll = { exportIds = emptySet() },
            onGeoJson = {
                showExportDialog = false
                exportGeoJsonLauncher.launch(
                    TrackLogExporter.defaultFileName("geojson", exportCode),
                )
            },
            onCsv = {
                showExportDialog = false
                exportCsvLauncher.launch(TrackLogExporter.defaultFileName("csv", exportCode))
            },
            onDismiss = { showExportDialog = false },
        )
    }
}

/** Everything the last fix said - the only evidence there is with no map on screen. */
@Composable
private fun FixCard(state: PathLogUiState) {
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

/** The walks so far, newest last, in the order they were made. */
@Composable
private fun RecordedList(
    state: PathLogUiState,
    onSelect: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val context = LocalContext.current

    Text(
        text = stringResource(
            R.string.pathlog_list_summary,
            state.tracks.size,
            state.routableCount,
            Formats.distance(context, state.totalLengthMeters),
        ),
        style = MaterialTheme.typography.titleSmall,
    )

    if (state.tracks.isEmpty()) {
        Text(
            text = stringResource(R.string.pathlog_list_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    state.tracks.forEach { track ->
        TrackRow(track = track, onSelect = { onSelect(track.id) })
        HorizontalDivider()
    }

    TextButton(
        onClick = onDeleteAll,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.pathlog_delete_all),
            color = MaterialTheme.colorScheme.error,
        )
    }
}
