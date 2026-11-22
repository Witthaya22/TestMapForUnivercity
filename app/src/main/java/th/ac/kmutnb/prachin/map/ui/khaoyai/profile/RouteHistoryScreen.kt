package th.ac.kmutnb.prachin.map.ui.khaoyai.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.ui.khaoyai.EmojiBadge
import th.ac.kmutnb.prachin.map.ui.khaoyai.KhaoYaiTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Every route walked to the end, newest first. The map has recorded these all along;
 * until now nothing showed them, and nothing could remove one.
 */
@Composable
fun RouteHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val history by container.routeHistoryDao.observeRecent(limit = HISTORY_LIMIT)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val pois by container.poiRepository.pois.collectAsStateWithLifecycle(initialValue = emptyList())
    val names = pois.associate { it.id to it.name }
    val scope = rememberCoroutineScope()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val unknownPlace = stringResource(R.string.history_unknown_place)

    Column(Modifier.fillMaxSize()) {
        KhaoYaiTopBar(title = stringResource(R.string.history_title), onBack = onBack)
        if (history.isEmpty()) {
            Text(
                stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding(),
        ) {
            items(history, key = { it.id }) { entry ->
                val route = entry.waypointIds.split(',').filter { it.isNotBlank() }
                    .joinToString(" → ") { names[it] ?: unknownPlace }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge("👣", size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(route, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(
                                    R.string.history_item_summary,
                                    entry.distanceMeters / 1000.0,
                                    (entry.durationSeconds + 59) / 60,
                                    DATE_FORMAT.format(Date(entry.completedAt)),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { scope.launch { container.routeHistoryDao.deleteById(entry.id) } }) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            if (history.isNotEmpty()) {
                item {
                    TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.history_clear), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.history_clear_confirm, history.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { container.routeHistoryDao.clear() }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private const val HISTORY_LIMIT = 200

private val DATE_FORMAT = SimpleDateFormat("d/M/yyyy HH:mm", Locale.getDefault())
