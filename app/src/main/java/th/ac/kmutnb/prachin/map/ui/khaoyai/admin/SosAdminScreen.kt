package th.ac.kmutnb.prachin.map.ui.khaoyai.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.model.SosReport
import th.ac.kmutnb.prachin.map.data.model.SosRules
import th.ac.kmutnb.prachin.map.data.model.SosStatus
import th.ac.kmutnb.prachin.map.data.model.TouristPosition
import th.ac.kmutnb.prachin.map.ui.khaoyai.KhaoYaiTopBar
import th.ac.kmutnb.prachin.map.ui.khaoyai.Pill
import th.ac.kmutnb.prachin.map.ui.khaoyai.openLink
import th.ac.kmutnb.prachin.map.ui.khaoyai.staff.MapMarker
import th.ac.kmutnb.prachin.map.ui.khaoyai.staff.StaffMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Emergency calls (§2.3.2.2): where on the map, the caller's latest known position, the
 * time and type, and the handling status - received, in progress, help completed.
 *
 * @param onBack null when shown as a tab, which has no back.
 */
@Composable
fun SosAdminScreen(onBack: (() -> Unit)?, onOpenTourist: (String) -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val reports by container.sosRepository.reports.collectAsStateWithLifecycle(initialValue = emptyList())
    val tourists by container.trackingRepository.trackedTourists.collectAsStateWithLifecycle(initialValue = emptyList())
    val latestByUser = tourists.associate { it.account.id to it.latest }
    var filter by rememberSaveable { mutableStateOf<SosStatus?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val ordered = SosRules.triage(reports).filter { r ->
        (filter == null || r.status == filter) &&
            (query.isBlank() || r.username.contains(query.trim(), true) || r.touristId.contains(query.trim(), true))
    }
    val scope = rememberCoroutineScope()
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        KhaoYaiTopBar(title = stringResource(R.string.sos_admin_title), onBack = onBack)
        StaffMap(
            markers = ordered.mapNotNull { r -> r.point?.let { MapMarker(r.id, it, statusColour(r.status)) } },
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.sos_admin_search)) },
                    shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("${stringResource(R.string.filter_all)} (${reports.size})") })
                    SosStatus.entries.forEach { s ->
                        FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text("${stringResource(s.labelRes)} (${reports.count { it.status == s }})") })
                    }
                }
            }
            item { Text(stringResource(R.string.sos_admin_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (ordered.isEmpty()) item { Text(stringResource(R.string.sos_admin_empty), modifier = Modifier.padding(vertical = 24.dp)) }
            items(ordered, key = { it.id }) { report ->
                ReportCard(
                    report = report,
                    latest = latestByUser[report.userId],
                    onAdvance = { next -> scope.launch { container.sosRepository.setStatus(report.id, next) } },
                    onOpenTourist = report.userId.takeIf { it.isNotBlank() }?.let { id -> { onOpenTourist(id) } },
                    onDelete = { confirmDelete = report.id },
                    onOpenMap = { link -> openLink(context, link) },
                )
            }
            if (reports.any { it.status == SosStatus.RESOLVED }) {
                item {
                    TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sos_admin_clear_resolved), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(R.string.sos_admin_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch { container.sosRepository.delete(id) }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.sos_admin_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { container.sosRepository.deleteResolved() }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ReportCard(
    report: SosReport,
    latest: TouristPosition?,
    onAdvance: (SosStatus) -> Unit,
    onOpenTourist: (() -> Unit)?,
    onDelete: () -> Unit,
    onOpenMap: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${report.category.emoji} ${stringResource(report.category.labelRes)}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (report.isDemo) Pill(stringResource(R.string.demo_badge), MaterialTheme.colorScheme.tertiary)
                Pill(stringResource(report.status.labelRes), Color(statusColour(report.status)))
            }
            Text(
                stringResource(
                    R.string.sos_admin_who_when,
                    listOf(report.username, report.touristId).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "-" },
                    TIME_FORMAT.format(Date(report.createdAt)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.message.isNotBlank()) Text(report.message, style = MaterialTheme.typography.bodyMedium)
            if (report.trailName.isNotBlank()) Text(stringResource(R.string.sos_admin_on_trail, report.trailName), style = MaterialTheme.typography.bodySmall)
            val point = report.point
            Text(
                if (point == null) stringResource(R.string.sos_share_no_fix)
                else stringResource(R.string.sos_admin_called_from, point.lat, point.lon, report.accuracyMeters ?: 0f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            // Where the caller has been seen since the call: what a rescuer walks to.
            if (latest != null && latest.recordedAt > report.createdAt) {
                val moved = point?.let { GeoUtils.haversineMeters(it, latest.point) }
                Text(
                    stringResource(R.string.sos_admin_latest, TIME_FORMAT.format(Date(latest.recordedAt)), moved ?: 0.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                report.status.next?.let { next ->
                    Button(onClick = { onAdvance(next) }) { Text(stringResource(R.string.sos_admin_mark, stringResource(next.labelRes))) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                report.mapLink?.let { link -> TextButton(onClick = { onOpenMap(link) }) { Text(stringResource(R.string.sos_admin_open_map)) } }
                onOpenTourist?.let { TextButton(onClick = it) { Text(stringResource(R.string.sos_admin_open_tourist)) } }
            }
        }
    }
}

private fun statusColour(status: SosStatus): Int = when (status) {
    SosStatus.NEW -> 0xFFD32F2F.toInt()
    SosStatus.ACKNOWLEDGED -> 0xFFE3A72F.toInt()
    SosStatus.IN_PROGRESS -> 0xFF1E88E5.toInt()
    SosStatus.RESOLVED -> 0xFF2E7D32.toInt()
}

private val TIME_FORMAT = SimpleDateFormat("d/M/yyyy HH:mm", Locale.getDefault())
