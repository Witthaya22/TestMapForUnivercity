package th.ac.kmutnb.prachin.map.ui.pathlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.GpsFixQuality
import th.ac.kmutnb.prachin.map.data.model.TrackCaptureMode
import th.ac.kmutnb.prachin.map.data.model.TrackLog
import th.ac.kmutnb.prachin.map.survey.TrackRecorder
import th.ac.kmutnb.prachin.map.ui.common.Formats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The parts of the walked-path log that both recording screens share.
 *
 * There are two ways into the same log - one with a map, one without - and they have to
 * agree about what a track contains, what the save form asks for and what the export
 * writes. Keeping the shared half here is what stops the second screen from slowly
 * becoming a slightly different, slightly wrong copy of the first.
 */

// --------------------------------------------------------------------------------------
// Recording
// --------------------------------------------------------------------------------------

@Composable
internal fun RecordCard(
    state: PathLogUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onSave: (String, Boolean) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * True only when the card floats over a map with a capped height, where the save form
     * would otherwise push its own button off the screen.
     *
     * It must stay false on the readout screen, whose own column already scrolls: a
     * scrollable inside a scrollable is measured with unbounded height, and Compose throws
     * rather than guessing - which is what took that screen down.
     */
    scrollable: Boolean = false,
) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
        Column(
            Modifier
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                state.pendingResult != null -> PendingTrackForm(
                    state = state,
                    onSave = onSave,
                    onDiscard = onDiscard,
                )

                state.isRecording -> RecordingInProgress(state, onStop, onCancel)

                else -> {
                    Button(
                        onClick = onStart,
                        enabled = state.canRecord,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.pathlog_record_start))
                    }
                    Text(
                        text = if (state.canRecord) {
                            stringResource(R.string.pathlog_hint)
                        } else {
                            stringResource(R.string.pathlog_wait_fix)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingInProgress(
    state: PathLogUiState,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    Text(
        text = stringResource(
            R.string.pathlog_recording,
            Formats.distance(context, state.recordedLengthMeters),
        ),
        style = MaterialTheme.typography.titleMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ReadoutColumn(
            stringResource(R.string.pathlog_field_vertices),
            state.recordingPoints.size.toString(),
        )
        ReadoutColumn(
            stringResource(R.string.pathlog_field_duration),
            stringResource(R.string.pathlog_seconds_value, state.recordingSeconds),
        )
        ReadoutColumn(
            stringResource(R.string.gpslog_accuracy),
            stringResource(R.string.gpslog_accuracy_value, state.recordingAccuracyMeters),
        )
    }
    if (state.rejectedFixCount > 0) {
        Text(
            text = stringResource(
                R.string.pathlog_rejected,
                state.rejectedFixCount,
                TrackRecorder.DEFAULT_MAX_ACCURACY_M.toInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onStop,
            enabled = state.canFinish,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.pathlog_record_stop))
        }
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.action_cancel))
        }
    }
    if (!state.canFinish) {
        Text(
            text = stringResource(
                R.string.pathlog_too_short,
                TrackRecorder.MIN_USABLE_LENGTH_M.toInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The form that turns a finished walk into a saved track.
 *
 * Asks two questions and no more: what to call it, and whether to route over it. Anything
 * else - what kind of path, whether it is lit, which faculty it belongs to - is a guess
 * typed while standing outdoors, and none of it is something the receiver measured.
 */
@Composable
private fun PendingTrackForm(
    state: PathLogUiState,
    onSave: (String, Boolean) -> Unit,
    onDiscard: () -> Unit,
) {
    val context = LocalContext.current
    val result = state.pendingResult ?: return
    var note by rememberSaveable(state.pendingCode) { mutableStateOf("") }
    var useForRouting by rememberSaveable(state.pendingCode) { mutableStateOf(true) }

    Text(
        text = stringResource(R.string.pathlog_result_title, state.pendingCode),
        style = MaterialTheme.typography.titleMedium,
    )
    FieldRow(
        stringResource(R.string.pathlog_field_length),
        Formats.distance(context, result.lengthMeters),
    )
    FieldRow(
        stringResource(R.string.pathlog_field_vertices),
        result.points.size.toString(),
    )
    FieldRow(
        stringResource(R.string.gpslog_accuracy),
        stringResource(R.string.gpslog_accuracy_value, result.averageAccuracyMeters),
    )
    FieldRow(
        stringResource(R.string.pathlog_field_worst_accuracy),
        stringResource(R.string.gpslog_accuracy_value, result.worstAccuracyMeters),
    )
    FieldRow(
        stringResource(R.string.gpslog_satellites),
        stringResource(
            R.string.gpslog_satellites_value,
            result.satellites.inUse,
            result.satellites.visible,
        ),
    )

    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text(stringResource(R.string.pathlog_note)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.pathlog_use_for_routing),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.pathlog_use_for_routing_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = useForRouting, onCheckedChange = { useForRouting = it })
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onSave(note, useForRouting) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.pathlog_save))
        }
        OutlinedButton(onClick = onDiscard) {
            Text(stringResource(R.string.gpslog_discard))
        }
    }
}

// --------------------------------------------------------------------------------------
// Saved tracks
// --------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackDetailSheet(
    track: TrackLog,
    onDismiss: () -> Unit,
    onSaveNote: (String) -> Unit,
    onSetUsedForRouting: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by rememberSaveable(track.id) { mutableStateOf(track.note) }
    var confirmDelete by rememberSaveable(track.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.code,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(track.quality.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = colourOf(track.quality),
                )
            }
            if (track.note.isNotBlank()) {
                Text(track.note, style = MaterialTheme.typography.bodyMedium)
            }

            // The walk itself, with nothing underneath it. The numbers below say how far
            // and how well; only this says what shape it was.
            TrackShape(points = track.points, modifier = Modifier.padding(vertical = 8.dp))

            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            FieldRow(
                stringResource(R.string.pathlog_field_length),
                Formats.distance(context, track.lengthMeters),
            )
            FieldRow(
                stringResource(R.string.pathlog_field_vertices),
                track.vertexCount.toString(),
            )
            FieldRow(
                stringResource(R.string.gpslog_accuracy),
                stringResource(R.string.gpslog_accuracy_value, track.averageAccuracyMeters),
            )
            FieldRow(
                stringResource(R.string.pathlog_field_worst_accuracy),
                stringResource(R.string.gpslog_accuracy_value, track.worstAccuracyMeters),
            )
            FieldRow(
                stringResource(R.string.gpslog_satellites),
                stringResource(
                    R.string.gpslog_satellites_value,
                    track.satellitesUsed,
                    track.satellitesVisible,
                ),
            )
            FieldRow(
                stringResource(R.string.pathlog_field_fixes),
                stringResource(R.string.pathlog_fixes_value, track.fixCount),
            )
            FieldRow(
                stringResource(R.string.gpslog_field_rejected),
                stringResource(R.string.pathlog_fixes_value, track.rejectedCount),
            )
            FieldRow(
                stringResource(R.string.pathlog_field_duration),
                stringResource(R.string.pathlog_seconds_value, track.durationSeconds),
            )
            FieldRow(
                stringResource(R.string.gpslog_field_recorded_at),
                formatMoment(track.recordedAt),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pathlog_use_for_routing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.pathlog_use_for_routing_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = track.isUsedForRouting, onCheckedChange = onSetUsedForRouting)
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.pathlog_note)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSaveNote(note); onDismiss() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_save))
                }
                OutlinedButton(onClick = { confirmDelete = true }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.pathlog_delete_confirm, track.code)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * One row of the list of walks.
 *
 * Leads with the note rather than the code, because after a week in the field "ทางไป
 * น้ำตก" is how somebody finds a track and T007 is not. The code stays beside it, since
 * it is what the exported file and any paper notes use.
 */
@Composable
internal fun TrackRow(
    track: TrackLog,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = track.note.ifBlank { stringResource(R.string.pathlog_unnamed) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (track.isUsedForRouting) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = stringResource(
                    R.string.pathlog_list_item_summary,
                    track.code,
                    Formats.distance(context, track.lengthMeters),
                    track.averageAccuracyMeters,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colourOf(track.quality),
            )
            if (!track.isUsedForRouting) {
                Text(
                    text = stringResource(R.string.pathlog_not_routing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onSelect) {
            Text(stringResource(R.string.gpslog_list_open))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackListSheet(
    state: PathLogUiState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.pathlog_list_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.pathlog_list_summary,
                    state.tracks.size,
                    state.routableCount,
                    Formats.distance(context, state.totalLengthMeters),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.tracks.isEmpty()) {
                Text(
                    text = stringResource(R.string.pathlog_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                return@Column
            }

            state.tracks.forEach { track ->
                TrackRow(track = track, onSelect = { onSelect(track.id) })
                HorizontalDivider()
            }

            TextButton(onClick = onDeleteAll, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = stringResource(R.string.pathlog_delete_all),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// --------------------------------------------------------------------------------------
// Export
// --------------------------------------------------------------------------------------

/**
 * Choosing what leaves the phone: which walks, and in which format.
 *
 * The same shape as the GPS point log's dialog, because it is the same decision about a
 * different measurement, and two export dialogs that behave differently would be a small
 * trap for whoever uses both.
 */
@Composable
internal fun TrackExportDialog(
    trackCount: Int,
    filter: TrackCaptureMode?,
    onFilterChange: (TrackCaptureMode?) -> Unit,
    onGeoJson: () -> Unit,
    onCsv: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pathlog_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pathlog_export_explain, trackCount))
                Text(
                    text = stringResource(R.string.gpslog_export_filter),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFilterChip(null, filter, onFilterChange)
                    ExportFilterChip(TrackCaptureMode.MAP, filter, onFilterChange)
                    ExportFilterChip(TrackCaptureMode.READOUT, filter, onFilterChange)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onGeoJson) {
                Text(stringResource(R.string.gpslog_export_geojson))
            }
        },
        dismissButton = {
            TextButton(onClick = onCsv) { Text(stringResource(R.string.gpslog_export_csv)) }
        },
    )
}

@Composable
private fun ExportFilterChip(
    mode: TrackCaptureMode?,
    selected: TrackCaptureMode?,
    onSelect: (TrackCaptureMode?) -> Unit,
) {
    FilterChip(
        selected = mode == selected,
        onClick = { onSelect(mode) },
        label = { Text(stringResource(mode.filterLabelRes)) },
    )
}

// --------------------------------------------------------------------------------------
// Small shared pieces
// --------------------------------------------------------------------------------------

@Composable
internal fun ReadoutColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun FieldRow(label: String, value: String) {
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
            modifier = Modifier.weight(0.55f),
        )
    }
}

/** Matches the line colours in `PathLogLayerManager`, so map and list agree. */
@Composable
internal fun colourOf(quality: GpsFixQuality): Color = when (quality) {
    GpsFixQuality.GOOD -> Color(0xFF2E7D32)
    GpsFixQuality.FAIR -> Color(0xFFEF6C00)
    GpsFixQuality.POOR -> Color(0xFFC62828)
}

internal val GpsFixQuality.labelRes: Int
    get() = when (this) {
        GpsFixQuality.GOOD -> R.string.gpslog_quality_good
        GpsFixQuality.FAIR -> R.string.gpslog_quality_fair
        GpsFixQuality.POOR -> R.string.gpslog_quality_poor
    }

internal val TrackCaptureMode?.filterLabelRes: Int
    get() = when (this) {
        null -> R.string.gpslog_export_filter_all
        TrackCaptureMode.MAP -> R.string.gpslog_export_filter_map
        TrackCaptureMode.READOUT -> R.string.gpslog_export_filter_readout
    }

/** Short local date and time; the exported file keeps the unambiguous ISO form. */
internal fun formatMoment(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
