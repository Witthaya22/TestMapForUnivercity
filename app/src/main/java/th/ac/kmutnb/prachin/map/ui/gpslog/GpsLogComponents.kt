package th.ac.kmutnb.prachin.map.ui.gpslog

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.geojson.GpsPointExporter
import th.ac.kmutnb.prachin.map.data.model.GpsCaptureMode
import th.ac.kmutnb.prachin.map.data.model.GpsFixQuality
import th.ac.kmutnb.prachin.map.data.model.GpsPoint
import th.ac.kmutnb.prachin.map.survey.PointSurveySession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The parts of the GPS point log that both collecting screens share.
 *
 * There are two ways into the same log - one with a map, one without - and they must agree
 * about what a measurement contains, what the save form asks for and what the export
 * writes. Keeping the shared half here is what stops the second screen from slowly
 * becoming a slightly different, slightly wrong copy of the first.
 */

/** All / map only / readout only, for the export dialog. */
@Composable
internal fun ExportFilterChip(
    mode: GpsCaptureMode?,
    selected: GpsCaptureMode?,
    onSelect: (GpsCaptureMode?) -> Unit,
) {
    FilterChip(
        selected = mode == selected,
        onClick = { onSelect(mode) },
        label = { Text(stringResource(mode.filterLabelRes)) },
    )
}

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

// --------------------------------------------------------------------------------------
// Capture
// --------------------------------------------------------------------------------------

@Composable
internal fun CaptureCard(
    state: GpsLogUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val pending = state.pendingResult

            when {
                pending != null -> PendingResultForm(
                    state = state,
                    onSave = onSave,
                    onDiscard = onDiscard,
                )

                state.isCapturing -> {
                    Text(
                        text = stringResource(
                            R.string.gpslog_capturing,
                            state.capturedSamples,
                            state.targetSamples,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LinearProgressIndicator(
                        progress = {
                            state.capturedSamples.toFloat() / state.targetSamples.toFloat()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            R.string.gpslog_accuracy_average,
                            state.averageAccuracyMeters,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.rejectedSamples > 0) {
                        Text(
                            text = stringResource(
                                R.string.gpslog_rejected,
                                state.rejectedSamples,
                                PointSurveySession.DEFAULT_MAX_ACCURACY_M.toInt(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onStop,
                            enabled = state.capturedSamples > 0,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.gpslog_capture_stop))
                        }
                        OutlinedButton(onClick = onCancel) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }

                else -> {
                    Button(
                        onClick = onStart,
                        enabled = state.canCapture,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.gpslog_capture))
                    }
                    Text(
                        text = if (state.canCapture) {
                            stringResource(R.string.gpslog_hint)
                        } else {
                            stringResource(R.string.gpslog_capture_wait_fix)
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
internal fun PendingResultForm(
    state: GpsLogUiState,
    onSave: (String) -> Unit,
    onDiscard: () -> Unit,
) {
    val result = state.pendingResult ?: return
    var note by rememberSaveable(state.pendingCode) { mutableStateOf("") }

    Text(
        text = stringResource(R.string.gpslog_result_title, state.pendingCode),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = result.point.format(),
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
    )
    FieldRow(
        stringResource(R.string.gpslog_accuracy),
        stringResource(R.string.gpslog_accuracy_value, result.averageAccuracyMeters),
    )
    FieldRow(
        stringResource(R.string.gpslog_field_spread),
        stringResource(R.string.gpslog_meters_value, result.spreadMeters),
    )
    FieldRow(
        stringResource(R.string.gpslog_field_samples),
        stringResource(R.string.gpslog_field_samples_value, result.sampleCount),
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
        label = { Text(stringResource(R.string.gpslog_note)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onSave(note) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.gpslog_save))
        }
        OutlinedButton(onClick = onDiscard) {
            Text(stringResource(R.string.gpslog_discard))
        }
    }
}

// --------------------------------------------------------------------------------------
// Recorded points
// --------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordedPointSheet(
    point: GpsPoint,
    onDismiss: () -> Unit,
    onSaveNote: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by rememberSaveable(point.id) { mutableStateOf(point.note) }
    var confirmDelete by rememberSaveable(point.id) { mutableStateOf(false) }

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
                    text = point.code,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(point.quality.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = colourOf(point.quality),
                )
            }

            Text(
                text = point.point.format(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )

            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            FieldRow(
                stringResource(R.string.gpslog_accuracy),
                stringResource(R.string.gpslog_accuracy_value, point.accuracyMeters),
            )
            FieldRow(
                stringResource(R.string.gpslog_field_spread),
                stringResource(R.string.gpslog_meters_value, point.spreadMeters),
            )
            FieldRow(
                stringResource(R.string.gpslog_satellites),
                stringResource(
                    R.string.gpslog_satellites_value,
                    point.satellitesUsed,
                    point.satellitesVisible,
                ),
            )
            FieldRow(
                stringResource(R.string.gpslog_elevation),
                point.elevationMeters
                    ?.let { stringResource(R.string.gpslog_elevation_value, it) }
                    ?: stringResource(R.string.gpslog_value_none),
            )
            FieldRow(
                stringResource(R.string.gpslog_field_vertical_accuracy),
                point.verticalAccuracyMeters
                    ?.let { stringResource(R.string.gpslog_accuracy_value, it) }
                    ?: stringResource(R.string.gpslog_value_none),
            )
            FieldRow(
                stringResource(R.string.gpslog_field_samples),
                stringResource(R.string.gpslog_field_samples_value, point.sampleCount),
            )
            FieldRow(
                stringResource(R.string.gpslog_field_rejected),
                stringResource(R.string.gpslog_field_samples_value, point.rejectedCount),
            )
            FieldRow(
                stringResource(R.string.gpslog_field_duration),
                stringResource(R.string.gpslog_field_duration_value, point.durationSeconds),
            )
            FieldRow(
                stringResource(R.string.gpslog_field_recorded_at),
                formatMoment(point.recordedAt),
            )

            Text(
                text = stringResource(R.string.gpslog_elevation_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.gpslog_note)) },
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
            text = { Text(stringResource(R.string.gpslog_delete_confirm, point.code)) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PointListSheet(
    points: List<GpsPoint>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
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
                text = stringResource(R.string.gpslog_list_title),
                style = MaterialTheme.typography.titleMedium,
            )

            if (points.isEmpty()) {
                Text(
                    text = stringResource(R.string.gpslog_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                return@Column
            }

            // Newest last, matching the running codes and the order they were walked.
            points.forEach { point ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = point.code,
                            style = MaterialTheme.typography.bodyLarge,
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
                    TextButton(onClick = { onSelect(point.id) }) {
                        Text(stringResource(R.string.gpslog_list_open))
                    }
                }
                HorizontalDivider()
            }

            TextButton(
                onClick = onDeleteAll,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.gpslog_delete_all),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
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

/** Matches the marker colours in `GpsLogLayerManager`, so map and list agree. */
@Composable
internal fun colourOf(quality: GpsFixQuality): Color = when (quality) {
    GpsFixQuality.GOOD -> Color(0xFF2E7D32)
    GpsFixQuality.FAIR -> Color(0xFFEF6C00)
    GpsFixQuality.POOR -> Color(0xFFC62828)
}

internal val GpsCaptureMode?.filterLabelRes: Int
    get() = when (this) {
        null -> R.string.gpslog_export_filter_all
        GpsCaptureMode.MAP -> R.string.gpslog_export_filter_map
        GpsCaptureMode.READOUT -> R.string.gpslog_export_filter_readout
    }

internal val GpsFixQuality.labelRes: Int
    get() = when (this) {
        GpsFixQuality.GOOD -> R.string.gpslog_quality_good
        GpsFixQuality.FAIR -> R.string.gpslog_quality_fair
        GpsFixQuality.POOR -> R.string.gpslog_quality_poor
    }

/** Short local date and time; the exported file keeps the unambiguous ISO form. */
internal fun formatMoment(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

/**
 * Choosing what leaves the phone: which readings, and in which format.
 *
 * The filter is part of the dialog rather than a setting because both screens write to one
 * log - the question "do I want everything, or only what I measured with the map in front
 * of me" belongs to the export, not to the app.
 */
@Composable
internal fun GpsExportDialog(
    pointCount: Int,
    filter: GpsCaptureMode?,
    onFilterChange: (GpsCaptureMode?) -> Unit,
    onGeoJson: () -> Unit,
    onCsv: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gpslog_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.gpslog_export_explain, pointCount))
                Text(
                    text = stringResource(R.string.gpslog_export_filter),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFilterChip(null, filter, onFilterChange)
                    ExportFilterChip(GpsCaptureMode.MAP, filter, onFilterChange)
                    ExportFilterChip(GpsCaptureMode.READOUT, filter, onFilterChange)
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
