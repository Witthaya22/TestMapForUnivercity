package th.ac.kmutnb.prachin.map.ui.khaoyai.staff

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.TouristPosition
import th.ac.kmutnb.prachin.map.data.model.TrackedTourist
import th.ac.kmutnb.prachin.map.ui.khaoyai.KhaoYaiTopBar
import th.ac.kmutnb.prachin.map.ui.khaoyai.NotFound
import th.ac.kmutnb.prachin.map.ui.khaoyai.Pill
import th.ac.kmutnb.prachin.map.ui.khaoyai.openLink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A tourist last seen this recently is drawn as on a trail now. */
private const val RECENT_MS = 3 * 60 * 60 * 1000L

/** Search by name, Tourist ID, username or phone, with everyone's last position on the map. */
@Composable
fun TouristSearchTab(onOpenTourist: (String) -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val tourists by container.trackingRepository.trackedTourists.collectAsStateWithLifecycle(initialValue = emptyList())
    val content by container.khaoYaiContentRepository.content.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var onlyOnTrail by rememberSaveable { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val shown = tourists.filter { it.matches(query) && (!onlyOnTrail || it.isRecent(now)) }

    Column(Modifier.fillMaxSize()) {
        KhaoYaiTopBar(title = stringResource(R.string.tourists_title))
        StaffMap(
            markers = shown.mapNotNull { t ->
                t.latest?.let { MapMarker(t.account.id, it.point, if (t.isRecent(now)) COLOUR_RECENT else COLOUR_OLD) }
            },
            onMarkerTapped = onOpenTourist,
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.tourists_search)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = onlyOnTrail, onClick = { onlyOnTrail = !onlyOnTrail }, label = { Text(stringResource(R.string.tourists_only_on_trail)) })
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.tourists_count, shown.size), style = MaterialTheme.typography.labelMedium)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shown.isEmpty()) item { Text(stringResource(R.string.tourists_empty)) }
            items(shown, key = { it.account.id }) { t ->
                val trailName = t.latest?.let { p -> content.trail(p.trailId)?.name }
                Card(
                    onClick = { onOpenTourist(t.account.id) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.account.fullName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (t.isRecent(now)) Pill(stringResource(R.string.tourists_on_trail), MaterialTheme.colorScheme.primary)
                            if (t.account.isDemo) Pill(stringResource(R.string.demo_badge), MaterialTheme.colorScheme.tertiary)
                        }
                        Text(
                            stringResource(R.string.tourists_row, t.account.memberId, t.account.nationality, t.account.phone),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            t.latest?.let { lastSeen(it, trailName, now) } ?: stringResource(R.string.tourists_never_seen),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** One tourist: who, how to reach them, where they walked, and their calls for help. */
@Composable
fun TouristDetailScreen(userId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val tourists by container.trackingRepository.trackedTourists.collectAsStateWithLifecycle(initialValue = null)
    val history by remember(userId) { container.trackingRepository.history(userId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val reports by container.sosRepository.reports.collectAsStateWithLifecycle(initialValue = emptyList())
    val content by container.khaoYaiContentRepository.content.collectAsStateWithLifecycle()
    val tourist = tourists?.firstOrNull { it.account.id == userId }
    if (tourists != null && tourist == null) {
        NotFound(stringResource(R.string.tourists_not_found), onBack)
        return
    }
    val account = tourist?.account ?: return
    val now = System.currentTimeMillis()
    val calls = reports.filter { it.userId == userId }

    Column(Modifier.fillMaxSize()) {
        KhaoYaiTopBar(title = account.fullName, onBack = onBack)
        StaffMap(
            markers = listOfNotNull(tourist.latest?.let { MapMarker(account.id, it.point, COLOUR_RECENT) }) +
                calls.mapNotNull { c -> c.point?.let { MapMarker(c.id, it, COLOUR_SOS) } },
            line = history.reversed().map { it.point },
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(R.string.tourists_detail_ids, account.memberId, account.username), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.tourists_detail_contact, account.phone, account.email, account.nationality), style = MaterialTheme.typography.bodyMedium)
                Text(
                    tourist.latest?.let { lastSeen(it, content.trail(it.trailId)?.name, now) } ?: stringResource(R.string.tourists_never_seen),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringResource(R.string.tourists_detail_history, history.size), style = MaterialTheme.typography.bodySmall)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${account.phone}"))) }
                    }) { Text(stringResource(R.string.tourists_call)) }
                    tourist.latest?.let { p ->
                        OutlinedButton(onClick = {
                            openLink(context, String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", p.point.lat, p.point.lon))
                        }) { Text(stringResource(R.string.sos_admin_open_map)) }
                    }
                }
            }
            item {
                Text(stringResource(R.string.tourists_detail_calls, calls.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            items(calls, key = { it.id }) { c ->
                Text(
                    "${c.category.emoji} ${stringResource(c.category.labelRes)} · ${stringResource(c.status.labelRes)} · ${TIME.format(Date(c.createdAt))}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun TrackedTourist.isRecent(now: Long) = latest?.let { now - it.recordedAt <= RECENT_MS } == true

@Composable
private fun lastSeen(p: TouristPosition, trailName: String?, now: Long): String {
    val minutes = ((now - p.recordedAt) / 60_000L).coerceAtLeast(0)
    val ago = when {
        minutes < 60 -> stringResource(R.string.ago_minutes, minutes.toInt())
        minutes < 60 * 24 -> stringResource(R.string.ago_hours, (minutes / 60).toInt())
        else -> stringResource(R.string.ago_days, (minutes / (60 * 24)).toInt())
    }
    return stringResource(R.string.tourists_last_seen, ago, trailName ?: "-", p.accuracyMeters)
}

private val TIME = SimpleDateFormat("d/M/yyyy HH:mm", Locale.getDefault())
private const val COLOUR_RECENT = 0xFF1E88E5.toInt()
private const val COLOUR_OLD = 0xFF9E9E9E.toInt()
private const val COLOUR_SOS = 0xFFD32F2F.toInt()
