package th.ac.kmutnb.prachin.map.ui.khaoyai.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.khaoyai.SpeciesKind
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailDifficulty
import th.ac.kmutnb.prachin.map.location.LocationState
import th.ac.kmutnb.prachin.map.ui.khaoyai.ContentPhoto
import th.ac.kmutnb.prachin.map.ui.khaoyai.difficultyLabel
import th.ac.kmutnb.prachin.map.ui.khaoyai.rememberLocationUi
import th.ac.kmutnb.prachin.map.ui.khaoyai.speciesKindLabel

/** Copies a picked image into app storage and returns its photo reference, or null. */
typealias PhotoImporter = suspend (Uri) -> String?

@Composable
internal fun TrailEditor(
    initial: TrailDraft,
    title: String,
    importPhoto: PhotoImporter,
    onSave: (TrailDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var d by rememberSaveable { mutableStateOf(initial) }
    // Index of the station being edited, or -1 for a new one; null when none is open.
    var editingStation by rememberSaveable { mutableStateOf<Int?>(null) }

    EditorDialog(title = title, canSave = d.isValid, onSave = { onSave(d) }, onDismiss = onDismiss) {
        PhotoField(d.photo, d.emoji, importPhoto) { d = d.copy(photo = it) }
        FormField(d.name, { d = d.copy(name = it) }, R.string.admin_field_name, required(d.nameMissing))
        FormField(d.emoji, { d = d.copy(emoji = it) }, R.string.admin_field_emoji)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField(
                d.distanceKm, { d = d.copy(distanceKm = it) }, R.string.admin_field_distance,
                number(d.distanceInvalid), KeyboardType.Decimal, Modifier.weight(1f),
            )
            FormField(
                d.durationMinutes, { d = d.copy(durationMinutes = it) }, R.string.admin_field_duration,
                number(d.durationInvalid), KeyboardType.Number, Modifier.weight(1f),
            )
        }
        Text(stringResource(R.string.admin_field_difficulty), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrailDifficulty.entries.forEach { level ->
                FilterChip(
                    selected = d.difficulty == level,
                    onClick = { d = d.copy(difficulty = level) },
                    label = { Text(difficultyLabel(level)) },
                )
            }
        }
        FormField(d.startPoint, { d = d.copy(startPoint = it) }, R.string.admin_field_start)
        FormField(d.endPoint, { d = d.copy(endPoint = it) }, R.string.admin_field_end)
        FormField(d.openNote, { d = d.copy(openNote = it) }, R.string.admin_field_open_note, singleLine = false)
        FormField(d.summary, { d = d.copy(summary = it) }, R.string.admin_field_summary, singleLine = false)
        FormField(d.description, { d = d.copy(description = it) }, R.string.admin_field_description, singleLine = false, minLines = 3)
        FormField(d.sourceUrl, { d = d.copy(sourceUrl = it) }, R.string.admin_field_source_url, keyboardType = KeyboardType.Uri)
        SwitchRow(R.string.admin_field_needs_guide, d.needsGuide) { d = d.copy(needsGuide = it) }
        SwitchRow(R.string.admin_field_is_open, d.isOpen) { d = d.copy(isOpen = it) }

        SectionLabel(R.string.admin_section_access)
        LatLonField(R.string.admin_field_trailhead, d.trailhead) { d = d.copy(trailhead = it) }
        FormField(
            d.entryRadiusMeters, { d = d.copy(entryRadiusMeters = it) }, R.string.admin_field_entry_radius,
            number(d.entryRadiusInvalid), KeyboardType.Number,
        )
        SwitchRow(R.string.admin_field_demo_unlocked, d.demoUnlocked) { d = d.copy(demoUnlocked = it) }
        Text(
            stringResource(R.string.admin_field_demo_unlocked_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel(R.string.admin_section_stations)
        Text(
            stringResource(R.string.admin_stations_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        d.stations.forEachIndexed { index, station ->
            StationRow(
                index = index,
                station = station,
                onEdit = { editingStation = index },
                onDelete = { d = d.copy(stations = d.stations.filterIndexed { i, _ -> i != index }) },
                onMoveUp = if (index > 0) {
                    { d = d.copy(stations = d.stations.swap(index, index - 1)) }
                } else {
                    null
                },
            )
        }
        OutlinedButton(onClick = { editingStation = -1 }, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.admin_station_add))
        }
    }

    editingStation?.let { index ->
        StationEditor(
            initial = d.stations.getOrNull(index) ?: StationDraft(),
            importPhoto = importPhoto,
            onSave = { station ->
                d = d.copy(
                    stations = if (index in d.stations.indices) {
                        d.stations.toMutableList().also { it[index] = station }
                    } else {
                        d.stations + station
                    },
                )
                editingStation = null
            },
            onDismiss = { editingStation = null },
        )
    }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also {
    val t = it[a]
    it[a] = it[b]
    it[b] = t
}

@Composable
private fun StationRow(
    index: Int,
    station: StationDraft,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}.", fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(station.emoji, station.name).filter { it.isNotBlank() }.joinToString(" "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (station.point.point != null) {
                        stringResource(R.string.admin_station_has_point, station.radiusMeters)
                    } else {
                        stringResource(R.string.admin_station_no_point)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (station.point.point != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (onMoveUp != null) {
                TextButton(onClick = onMoveUp) { Text("↑") }
            }
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StationEditor(
    initial: StationDraft,
    importPhoto: PhotoImporter,
    onSave: (StationDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var s by rememberSaveable { mutableStateOf(initial) }
    EditorDialog(
        title = stringResource(R.string.admin_station_title),
        canSave = s.isValid,
        onSave = { onSave(s) },
        onDismiss = onDismiss,
    ) {
        PhotoField(s.photo, s.emoji, importPhoto) { s = s.copy(photo = it) }
        FormField(s.name, { s = s.copy(name = it) }, R.string.admin_field_name, required(s.nameMissing))
        FormField(s.emoji, { s = s.copy(emoji = it) }, R.string.admin_field_emoji)
        FormField(s.description, { s = s.copy(description = it) }, R.string.admin_field_station_story, singleLine = false, minLines = 4)
        LatLonField(R.string.admin_field_station_point, s.point) { s = s.copy(point = it) }
        FormField(
            s.radiusMeters, { s = s.copy(radiusMeters = it) }, R.string.admin_field_station_radius,
            number(s.radiusInvalid), KeyboardType.Number,
        )
    }
}

@Composable
internal fun NewsEditor(
    initial: NewsDraft,
    title: String,
    importPhoto: PhotoImporter,
    onSave: (NewsDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var d by rememberSaveable { mutableStateOf(initial) }
    EditorDialog(title = title, canSave = d.isValid, onSave = { onSave(d) }, onDismiss = onDismiss) {
        PhotoField(d.photo, d.emoji, importPhoto) { d = d.copy(photo = it) }
        FormField(d.title, { d = d.copy(title = it) }, R.string.admin_field_title, required(d.titleMissing))
        FormField(d.emoji, { d = d.copy(emoji = it) }, R.string.admin_field_emoji)
        FormField(
            d.date, { d = d.copy(date = it) }, R.string.admin_field_date,
            if (d.dateInvalid) stringResource(R.string.admin_error_date) else null,
        )
        FormField(d.category, { d = d.copy(category = it) }, R.string.admin_field_category)
        FormField(d.summary, { d = d.copy(summary = it) }, R.string.admin_field_summary, singleLine = false)
        FormField(d.body, { d = d.copy(body = it) }, R.string.admin_field_body, singleLine = false, minLines = 5)
        FormField(d.sourceName, { d = d.copy(sourceName = it) }, R.string.admin_field_source_name)
        FormField(d.sourceUrl, { d = d.copy(sourceUrl = it) }, R.string.admin_field_source_url, keyboardType = KeyboardType.Uri)
    }
}

@Composable
internal fun SpeciesEditor(
    initial: SpeciesDraft,
    title: String,
    importPhoto: PhotoImporter,
    onSave: (SpeciesDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var d by rememberSaveable { mutableStateOf(initial) }
    EditorDialog(title = title, canSave = d.isValid, onSave = { onSave(d) }, onDismiss = onDismiss) {
        PhotoField(d.photo, d.emoji, importPhoto) { d = d.copy(photo = it) }
        Text(stringResource(R.string.admin_field_kind), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeciesKind.entries.forEach { k ->
                FilterChip(
                    selected = d.kind == k,
                    onClick = { d = d.copy(kind = k) },
                    label = { Text(speciesKindLabel(k)) },
                )
            }
        }
        FormField(d.name, { d = d.copy(name = it) }, R.string.admin_field_name, required(d.nameMissing))
        FormField(d.scientificName, { d = d.copy(scientificName = it) }, R.string.admin_field_scientific)
        FormField(d.emoji, { d = d.copy(emoji = it) }, R.string.admin_field_emoji)
        FormField(d.conservationStatus, { d = d.copy(conservationStatus = it) }, R.string.admin_field_status)
        FormField(d.habitat, { d = d.copy(habitat = it) }, R.string.admin_field_habitat, singleLine = false)
        FormField(d.description, { d = d.copy(description = it) }, R.string.admin_field_description, singleLine = false, minLines = 3)
        FormField(d.tip, { d = d.copy(tip = it) }, R.string.admin_field_tip, singleLine = false)
    }
}

// --------------------------------------------------------------------------------------
// Fields
// --------------------------------------------------------------------------------------

/** The photo as it will look on a card, with pick and remove. Uses the system photo picker: no storage permission. */
@Composable
private fun PhotoField(photo: String, emoji: String, importPhoto: PhotoImporter, onChange: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var importing by rememberSaveable { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val ref = importPhoto(uri)
            importing = false
            failed = ref == null
            ref?.let(onChange)
        }
    }
    ContentPhoto(
        photo,
        emoji.ifBlank { "🖼" },
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp)),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !importing,
        ) {
            Text(stringResource(if (importing) R.string.admin_photo_importing else R.string.admin_photo_pick))
        }
        if (photo.isNotBlank()) {
            TextButton(onClick = { onChange("") }) {
                Text(stringResource(R.string.admin_photo_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (failed) {
        Text(stringResource(R.string.admin_photo_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** Latitude and longitude, typed or filled in from the phone's GPS where the admin stands. */
@Composable
private fun LatLonField(labelRes: Int, value: LatLonDraft, onChange: (LatLonDraft) -> Unit) {
    val location = rememberLocationUi()
    Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FormField(
            value.lat, { onChange(value.copy(lat = it)) }, R.string.admin_field_lat,
            if (value.isInvalid) stringResource(R.string.admin_error_point) else null,
            KeyboardType.Decimal, Modifier.weight(1f),
        )
        FormField(value.lon, { onChange(value.copy(lon = it)) }, R.string.admin_field_lon, null, KeyboardType.Decimal, Modifier.weight(1f))
    }
    when (val state = location.state) {
        is LocationState.Available -> OutlinedButton(onClick = { onChange(LatLonDraft.of(state.fix.point)) }) {
            Icon(painterResource(R.drawable.ic_my_location), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.admin_use_here, state.fix.accuracyMeters))
        }
        LocationState.PermissionMissing -> TextButton(onClick = location.requestPermission) {
            Text(stringResource(R.string.location_allow))
        }
        else -> Text(
            stringResource(R.string.location_searching),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(labelRes: Int) {
    HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun required(missing: Boolean): String? =
    if (missing) stringResource(R.string.admin_error_required) else null

@Composable
private fun number(invalid: Boolean): String? =
    if (invalid) stringResource(R.string.admin_error_number) else null

/** A full-screen form with close on the left and save on the right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorDialog(
    title: String,
    canSave: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.imePadding()) {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close))
                        }
                    },
                    actions = {
                        TextButton(onClick = onSave, enabled = canSave) {
                            Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                        }
                    },
                )
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = fields,
                )
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    )
}

@Composable
private fun SwitchRow(labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
