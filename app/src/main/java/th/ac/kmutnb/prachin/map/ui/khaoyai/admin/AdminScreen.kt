package th.ac.kmutnb.prachin.map.ui.khaoyai.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
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
import th.ac.kmutnb.prachin.map.data.khaoyai.KhaoYaiContent
import th.ac.kmutnb.prachin.map.data.khaoyai.KhaoYaiContentRepository
import th.ac.kmutnb.prachin.map.data.khaoyai.SpeciesKind
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailDifficulty
import th.ac.kmutnb.prachin.map.data.khaoyai.newContentId
import th.ac.kmutnb.prachin.map.ui.khaoyai.EmojiBadge
import th.ac.kmutnb.prachin.map.ui.khaoyai.ForestBrush
import th.ac.kmutnb.prachin.map.ui.khaoyai.LeafCorner
import th.ac.kmutnb.prachin.map.ui.khaoyai.MenuRow
import th.ac.kmutnb.prachin.map.ui.Routes
import th.ac.kmutnb.prachin.map.ui.khaoyai.difficultyLabel
import th.ac.kmutnb.prachin.map.ui.khaoyai.speciesKindLabel
import th.ac.kmutnb.prachin.map.ui.khaoyai.thaiDate
import th.ac.kmutnb.prachin.map.ui.khaoyai.trailDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AdminSection(val labelRes: Int, val addRes: Int, val editRes: Int, val idPrefix: String) {
    TRAILS(R.string.admin_section_trails, R.string.admin_add_trail, R.string.admin_edit_trail, "trail"),
    NEWS(R.string.admin_section_news, R.string.admin_add_news, R.string.admin_edit_news, "news"),
    SPECIES(R.string.admin_section_species, R.string.admin_add_species, R.string.admin_edit_species, "species"),
}

/** One line of the admin list, whatever kind of item it stands for. */
private data class AdminRowData(val id: String, val emoji: String, val title: String, val subtitle: String)

/**
 * Add, edit and delete the trails, news and species the visitor screens show.
 * shortcut: writes go to [KhaoYaiContentRepository], which keeps them in memory only.
 */
@Composable
fun AdminScreen(
    content: KhaoYaiContent,
    repository: KhaoYaiContentRepository,
    onViewAsVisitor: () -> Unit,
    /** Opens one of the walking navigation's own screens, by its `Routes` value. */
    onOpenTool: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(AdminSection.TRAILS) }
    // Which form is open: the section, and the item id (null for a new item).
    var editing by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.admin_saved)

    val rows = adminRows(section, content)

    fun saved() {
        editing = false
        editId = null
        scope.launch { snackbar.showSnackbar(savedMessage) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editId = null
                    editing = true
                },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(section.addRes)) },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AdminHeader(
                    onViewAsVisitor = onViewAsVisitor,
                    onReset = { confirmReset = true },
                    onLogout = onLogout,
                )
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Text(
                        stringResource(R.string.admin_memory_notice),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatCard(stringResource(R.string.home_stat_trails, content.trails.size), "🌲", Modifier.weight(1f)) {
                        section = AdminSection.TRAILS
                    }
                    StatCard(stringResource(R.string.home_stat_news, content.news.size), "📢", Modifier.weight(1f)) {
                        section = AdminSection.NEWS
                    }
                    StatCard(stringResource(R.string.home_stat_species, content.species.size), "🐘", Modifier.weight(1f)) {
                        section = AdminSection.SPECIES
                    }
                }
            }
            item { MapToolsCard(onOpenTool) }
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AdminSection.entries.forEach { s ->
                        FilterChip(
                            selected = section == s,
                            onClick = { section = s },
                            label = { Text(stringResource(s.labelRes)) },
                        )
                    }
                }
            }
            if (rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.admin_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(rows, key = { it.id }) { row ->
                AdminRow(
                    row = row,
                    onEdit = {
                        editId = row.id
                        editing = true
                    },
                    onDelete = { pendingDelete = row.id },
                )
            }
        }
    }

    if (editing) {
        val id = editId
        val close = {
            editing = false
            editId = null
        }
        when (section) {
            AdminSection.TRAILS -> TrailEditor(
                initial = id?.let { content.trail(it) }?.let(TrailDraft::from) ?: TrailDraft(),
                title = stringResource(if (id == null) section.addRes else section.editRes),
                onSave = { draft ->
                    draft.toTrail(id ?: newContentId(section.idPrefix))?.let {
                        repository.upsertTrail(it)
                        saved()
                    }
                },
                onDismiss = close,
            )

            AdminSection.NEWS -> NewsEditor(
                initial = id?.let { content.newsItem(it) }?.let(NewsDraft::from) ?: NewsDraft(date = today()),
                title = stringResource(if (id == null) section.addRes else section.editRes),
                onSave = { draft ->
                    draft.toNews(id ?: newContentId(section.idPrefix))?.let {
                        repository.upsertNews(it)
                        saved()
                    }
                },
                onDismiss = close,
            )

            AdminSection.SPECIES -> SpeciesEditor(
                initial = id?.let { content.speciesItem(it) }?.let(SpeciesDraft::from) ?: SpeciesDraft(),
                title = stringResource(if (id == null) section.addRes else section.editRes),
                onSave = { draft ->
                    draft.toSpecies(id ?: newContentId(section.idPrefix))?.let {
                        repository.upsertSpecies(it)
                        saved()
                    }
                },
                onDismiss = close,
            )
        }
    }

    pendingDelete?.let { id ->
        // Null when the item is gone already; the stale id then simply shows nothing.
        val row = rows.firstOrNull { it.id == id }
        if (row != null) {
            val deletedMessage = stringResource(R.string.admin_deleted, row.title)
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.admin_delete_title, row.title)) },
                text = { Text(stringResource(R.string.admin_delete_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        when (section) {
                            AdminSection.TRAILS -> repository.deleteTrail(id)
                            AdminSection.NEWS -> repository.deleteNews(id)
                            AdminSection.SPECIES -> repository.deleteSpecies(id)
                        }
                        pendingDelete = null
                        scope.launch { snackbar.showSnackbar(deletedMessage) }
                    }) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.admin_reset_confirm_title)) },
            text = { Text(stringResource(R.string.admin_reset_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    repository.resetToSample()
                    confirmReset = false
                }) { Text(stringResource(R.string.admin_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@Composable
private fun adminRows(section: AdminSection, content: KhaoYaiContent): List<AdminRowData> = when (section) {
    AdminSection.TRAILS -> content.trails.map {
        AdminRowData(
            it.id,
            it.emoji,
            it.name,
            "${stringResource(R.string.trail_distance, it.distanceKm)} · ${trailDuration(it.durationMinutes)} · " +
                difficultyLabel(it.difficulty),
        )
    }

    AdminSection.NEWS -> content.newsByDate.map {
        AdminRowData(it.id, it.emoji, it.title, "${thaiDate(it.date)} · ${it.category}")
    }

    AdminSection.SPECIES -> content.species.map {
        AdminRowData(it.id, it.emoji, it.name, "${speciesKindLabel(it.kind)} · ${it.scientificName}")
    }
}

@Composable
private fun AdminHeader(onViewAsVisitor: () -> Unit, onReset: () -> Unit, onLogout: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(ForestBrush),
    ) {
        LeafCorner(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding(),
            size = 64.dp,
            alpha = 0.3f,
        )
        Column(
            Modifier
                .statusBarsPadding()
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_shield), contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.admin_header),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onLogout) {
                    Icon(painterResource(R.drawable.ic_logout), stringResource(R.string.profile_logout), tint = Color.White)
                }
            }
            Text(
                stringResource(R.string.admin_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onViewAsVisitor) {
                    Text(stringResource(R.string.admin_view_as_visitor), color = Color.White)
                }
                OutlinedButton(onClick = onReset) {
                    Icon(
                        painterResource(R.drawable.ic_refresh),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.admin_reset), color = Color.White)
                }
            }
        }
    }
}

/**
 * The surveying and editing screens of the walking navigation, which visitors never see:
 * walk a trail in, correct it point by point, pin hazards, place POIs, manage the map pack.
 */
@Composable
private fun MapToolsCard(onOpenTool: (String) -> Unit) {
    val tools = listOf(
        Triple(R.drawable.ic_navigation, R.string.admin_tool_map, Routes.MAP),
        Triple(R.drawable.ic_walk, R.string.admin_tool_path_log, Routes.PATH_LOG),
        Triple(R.drawable.ic_schedule, R.string.admin_tool_path_readout, Routes.PATH_READOUT),
        Triple(R.drawable.ic_place, R.string.admin_tool_survey, Routes.SURVEY),
        Triple(R.drawable.ic_my_location, R.string.admin_tool_gps_log, Routes.GPS_LOG),
        Triple(R.drawable.ic_shield, R.string.admin_tool_hazards, Routes.HAZARDS),
        Triple(R.drawable.ic_info, R.string.admin_tool_hazard_sounds, Routes.HAZARD_SOUNDS),
        Triple(R.drawable.ic_layers, R.string.admin_tool_offline, Routes.OFFLINE_MANAGER),
        Triple(R.drawable.ic_settings, R.string.admin_tool_settings, Routes.SETTINGS),
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Text(
            stringResource(R.string.admin_tools_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp),
        )
        Text(
            stringResource(R.string.admin_tools_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        tools.forEach { (icon, label, route) ->
            MenuRow(icon, stringResource(label), onClick = { onOpenTool(route) })
        }
    }
}

@Composable
private fun StatCard(label: String, emoji: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdminRow(row: AdminRowData, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(row.emoji, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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

// --------------------------------------------------------------------------------------
// Editors
// --------------------------------------------------------------------------------------

@Composable
private fun TrailEditor(
    initial: TrailDraft,
    title: String,
    onSave: (TrailDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var d by rememberSaveable { mutableStateOf(initial) }
    EditorDialog(title = title, canSave = d.isValid, onSave = { onSave(d) }, onDismiss = onDismiss) {
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
        FormField(d.summary, { d = d.copy(summary = it) }, R.string.admin_field_summary, singleLine = false)
        FormField(d.description, { d = d.copy(description = it) }, R.string.admin_field_description, singleLine = false, minLines = 3)
        FormField(d.stations, { d = d.copy(stations = it) }, R.string.admin_field_stations, singleLine = false, minLines = 3)
        SwitchRow(R.string.admin_field_needs_guide, d.needsGuide) { d = d.copy(needsGuide = it) }
        SwitchRow(R.string.admin_field_is_open, d.isOpen) { d = d.copy(isOpen = it) }
    }
}

@Composable
private fun NewsEditor(
    initial: NewsDraft,
    title: String,
    onSave: (NewsDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var d by rememberSaveable { mutableStateOf(initial) }
    EditorDialog(title = title, canSave = d.isValid, onSave = { onSave(d) }, onDismiss = onDismiss) {
        FormField(d.title, { d = d.copy(title = it) }, R.string.admin_field_title, required(d.titleMissing))
        FormField(d.emoji, { d = d.copy(emoji = it) }, R.string.admin_field_emoji)
        FormField(
            d.date, { d = d.copy(date = it) }, R.string.admin_field_date,
            if (d.dateInvalid) stringResource(R.string.admin_error_date) else null,
        )
        FormField(d.category, { d = d.copy(category = it) }, R.string.admin_field_category)
        FormField(d.summary, { d = d.copy(summary = it) }, R.string.admin_field_summary, singleLine = false)
        FormField(d.body, { d = d.copy(body = it) }, R.string.admin_field_body, singleLine = false, minLines = 5)
    }
}

@Composable
private fun SpeciesEditor(
    initial: SpeciesDraft,
    title: String,
    onSave: (SpeciesDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var d by rememberSaveable { mutableStateOf(initial) }
    EditorDialog(title = title, canSave = d.isValid, onSave = { onSave(d) }, onDismiss = onDismiss) {
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
