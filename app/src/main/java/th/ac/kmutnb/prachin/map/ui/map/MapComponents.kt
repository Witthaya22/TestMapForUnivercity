package th.ac.kmutnb.prachin.map.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.Poi
import th.ac.kmutnb.prachin.map.data.model.PoiCategory
import th.ac.kmutnb.prachin.map.navigation.NavigationProgress
import th.ac.kmutnb.prachin.map.navigation.TapVerdict
import th.ac.kmutnb.prachin.map.ui.common.Formats
import th.ac.kmutnb.prachin.map.ui.common.labelRes
import kotlin.math.roundToInt

/**
 * The live navigation readout (F6).
 *
 * Everything here is derived from the position projected onto the route, so the remaining
 * distance falls steadily as the user walks instead of jumping when the path bends.
 */
@Composable
fun NavigationPanel(
    progress: NavigationProgress,
    isOffRoute: Boolean,
    onRecalculate: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val next = progress.nextWaypoint
            if (next != null) {
                Text(
                    text = stringResource(R.string.nav_next_waypoint, next.name),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.nav_remaining_to_next,
                        Formats.distance(context, progress.distanceToNextMeters),
                        Formats.duration(context, progress.secondsToNextWaypoint),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    text = stringResource(R.string.nav_arrived_final),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(
                    R.string.nav_total_and_remaining,
                    Formats.distance(context, progress.totalMeters),
                    Formats.distance(context, progress.remainingMeters),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progress.progressFraction.toFloat() },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.nav_progress_percent,
                        (progress.progressFraction * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (isOffRoute) {
                Text(
                    text = stringResource(R.string.nav_off_route),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isOffRoute) {
                    Button(onClick = onRecalculate, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.nav_recalculate))
                    }
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nav_stop))
                }
            }
        }
    }
}

/**
 * Detail sheet for a POI, shown on tap and automatically on arrival (F7).
 *
 * The note field writes straight back to Room, so what the user adds survives an app update
 * that ships a revised `pois.geojson`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiDetailSheet(
    poi: Poi,
    isArrival: Boolean,
    isNavigating: Boolean,
    onDismiss: () -> Unit,
    onNavigateHere: () -> Unit,
    onAddWaypoint: () -> Unit,
    onSaveDetails: (name: String, description: String, note: String, category: PoiCategory) -> Unit,
    onDelete: () -> Unit,
    onRelocate: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by rememberSaveable(poi.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing) {
                PoiEditForm(
                    poi = poi,
                    onCancel = { editing = false },
                    onSave = { name, description, note, category ->
                        onSaveDetails(name, description, note, category)
                        editing = false
                    },
                    onDelete = onDelete,
                    onRelocate = onRelocate,
                )
                return@Column
            }

            Text(
                text = if (isArrival) {
                    stringResource(R.string.nav_arrived_title, poi.name)
                } else {
                    poi.name
                },
                style = MaterialTheme.typography.headlineSmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(stringResource(poi.category.labelRes)) })
                poi.gpsAccuracy?.let { accuracy ->
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(stringResource(R.string.poi_detail_surveyed_accuracy, accuracy))
                        },
                    )
                }
            }

            Text(
                text = stringResource(R.string.poi_detail_description),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = poi.description.ifBlank { stringResource(R.string.poi_detail_no_description) },
                style = MaterialTheme.typography.bodyMedium,
            )

            if (poi.note.isNotBlank()) {
                Text(
                    text = stringResource(R.string.poi_detail_note),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(text = poi.note, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(4.dp))

            if (!isNavigating) {
                Button(onClick = onNavigateHere, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.poi_detail_navigate_here))
                }
                OutlinedButton(onClick = onAddWaypoint, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.poi_detail_add_waypoint))
                }
            }
            TextButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.poi_detail_edit))
            }
        }
    }
}

@Composable
private fun PoiEditForm(
    poi: Poi,
    onCancel: () -> Unit,
    onSave: (String, String, String, PoiCategory) -> Unit,
    onDelete: () -> Unit,
    onRelocate: () -> Unit,
) {
    var name by rememberSaveable(poi.id) { mutableStateOf(poi.name) }
    var description by rememberSaveable(poi.id) { mutableStateOf(poi.description) }
    var note by rememberSaveable(poi.id) { mutableStateOf(poi.note) }
    var category by rememberSaveable(poi.id) { mutableStateOf(poi.category) }
    var confirmingDelete by rememberSaveable(poi.id) { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.poi_edit_title),
        style = MaterialTheme.typography.headlineSmall,
    )

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.poi_edit_name)) },
        isError = name.isBlank(),
        supportingText = if (name.isBlank()) {
            { Text(stringResource(R.string.poi_edit_name_required)) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )

    CategoryPicker(selected = category, onSelected = { category = it })

    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text(stringResource(R.string.poi_edit_description)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text(stringResource(R.string.poi_edit_note)) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onSave(name.trim(), description.trim(), note.trim(), category) },
            enabled = name.isNotBlank(),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.action_save))
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.action_cancel))
        }
    }

    OutlinedButton(onClick = onRelocate, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.poi_edit_move))
    }

    // Deleting a seeded POI is allowed too. The table is the source of truth once seeded,
    // and a place that has been demolished or was never really there is exactly the kind of
    // thing the person walking the campus should be able to remove.
    TextButton(onClick = { confirmingDelete = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            text = {
                Text(
                    if (poi.isUserCreated) {
                        stringResource(R.string.poi_delete_confirm, poi.name)
                    } else {
                        stringResource(R.string.poi_delete_confirm_seeded, poi.name)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Shown while a POI is being moved.
 *
 * Two ways to finish, because the two situations are different: standing at the place, use
 * the GPS reading and the point becomes survey-grade; looking at the map from elsewhere,
 * long-press where it should be. Correcting a point that way is the whole reason the
 * imported OSM data is safe to ship - a wrong gate is a five-second fix rather than a
 * rebuild.
 */
@Composable
fun RelocateBanner(
    poi: Poi,
    hasLocation: Boolean,
    onUseCurrentLocation: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.poi_move_title, poi.name),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.poi_move_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onUseCurrentLocation,
                    enabled = hasLocation,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.poi_move_use_gps))
                }
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
fun CategoryPicker(selected: PoiCategory, onSelected: (PoiCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.poi_edit_category),
            style = MaterialTheme.typography.titleSmall,
        )
        // A plain wrapping row of chips: eight fixed options never need a scrolling picker.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PoiCategory.entries.forEach { category ->
                androidx.compose.material3.FilterChip(
                    selected = category == selected,
                    onClick = { onSelected(category) },
                    label = { Text(stringResource(category.labelRes)) },
                )
            }
        }
    }
}

/**
 * The F9 dialogs shown when a tapped point is not on the walking network.
 *
 * A point 15-40 m off can still be kept, because the user may be marking a building entrance
 * set back from the path. Beyond 40 m there is no "confirm" option at all: no route could
 * reach it, so offering to place it would only produce a failure later.
 */
@Composable
fun PlacementDialog(
    verdict: TapVerdict,
    onMoveToPath: () -> Unit,
    onKeepAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (verdict) {
        is TapVerdict.NearPath -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.add_point_off_path_warning_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.add_point_off_path_warning_body,
                        verdict.distanceMeters.roundToInt(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onMoveToPath) {
                    Text(stringResource(R.string.add_point_move_to_path))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onKeepAnyway) {
                        Text(stringResource(R.string.add_point_keep_anyway))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
        )

        is TapVerdict.FarFromPath -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.add_point_far_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.add_point_far_body,
                        verdict.distanceMeters.roundToInt(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onMoveToPath) {
                    Text(stringResource(R.string.add_point_move_to_path))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onKeepAnyway) {
                        Text(stringResource(R.string.add_point_keep_anyway))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
        )

        TapVerdict.NoNetwork -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.add_point_no_network_title)) },
            text = { Text(stringResource(R.string.add_point_no_network_body)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            },
        )

        // An on-path tap never reaches here: the view model opens the naming dialog straight
        // away rather than asking the user to confirm something that is already fine.
        is TapVerdict.OnPath -> Unit
    }
}

/** Names a point the user placed, before it becomes a POI. */
@Composable
fun NamePointDialog(
    onConfirm: (String, PoiCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var category by remember { mutableStateOf(PoiCategory.CUSTOM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_point_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.poi_edit_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryPicker(selected = category, onSelected = { category = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), category) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
