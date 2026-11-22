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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * Saved to app storage by [KhaoYaiContentRepository].
 *
 * @param sections what this account may edit: officers the trails (§2.3.2.3), an
 * administrator everything.
 */
@Composable
fun AdminScreen(
    content: KhaoYaiContent,
    repository: KhaoYaiContentRepository,
    sections: List<AdminSection>,
    canReset: Boolean,
    onBack: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(sections.first()) }
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
                ContentTopBar(onBack = onBack, onReset = if (canReset) ({ confirmReset = true }) else null)
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
                    if (AdminSection.TRAILS in sections) {
                        StatCard(stringResource(R.string.home_stat_trails, content.trails.size), "🌲", Modifier.weight(1f)) {
                            section = AdminSection.TRAILS
                        }
                    }
                    if (AdminSection.NEWS in sections) {
                        StatCard(stringResource(R.string.home_stat_news, content.news.size), "📢", Modifier.weight(1f)) {
                            section = AdminSection.NEWS
                        }
                    }
                    if (AdminSection.SPECIES in sections) {
                        StatCard(stringResource(R.string.home_stat_species, content.species.size), "🐘", Modifier.weight(1f)) {
                            section = AdminSection.SPECIES
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sections.forEach { s ->
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
                importPhoto = repository::importPhoto,
                onSave = { draft ->
                    val path = id?.let { content.trail(it) }?.path.orEmpty()
                    draft.toTrail(id ?: newContentId(section.idPrefix), path)?.let {
                        repository.upsertTrail(it)
                        saved()
                    }
                },
                onDismiss = close,
            )

            AdminSection.NEWS -> NewsEditor(
                initial = id?.let { content.newsItem(it) }?.let(NewsDraft::from) ?: NewsDraft(date = today()),
                title = stringResource(if (id == null) section.addRes else section.editRes),
                importPhoto = repository::importPhoto,
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
                importPhoto = repository::importPhoto,
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ContentTopBar(onBack: () -> Unit, onReset: (() -> Unit)?) {
    androidx.compose.material3.TopAppBar(
        title = { Text(stringResource(R.string.admin_title), fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
            }
        },
        actions = {
            if (onReset != null) {
                IconButton(onClick = onReset) {
                    Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.admin_reset))
                }
            }
        },
    )
}

/**
 * The surveying and editing screens of the walking navigation, which visitors never see:
 * walk a trail in, correct it point by point, pin hazards, place POIs, manage the map pack.
 */
@Composable
internal fun MapToolsCard(onOpenTool: (String) -> Unit) {
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
