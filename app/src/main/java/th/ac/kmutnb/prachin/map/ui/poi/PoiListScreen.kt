package th.ac.kmutnb.prachin.map.ui.poi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.model.Poi
import th.ac.kmutnb.prachin.map.ui.common.Formats
import th.ac.kmutnb.prachin.map.ui.common.labelRes
import th.ac.kmutnb.prachin.map.ui.map.MapViewModel

/**
 * Destination picker (F4).
 *
 * POIs that cannot be routed to are still listed but flagged, because hiding them would
 * leave the user hunting for a place they know exists; the badge points at the real fix,
 * which is surveying the missing path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiListScreen(
    viewModel: MapViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    val unroutable = remember(state.network.unroutablePoiIds) {
        state.network.unroutablePoiIds.toSet()
    }

    val visible = remember(state.pois, query, state.currentPoint) {
        val filtered = if (query.isBlank()) {
            state.pois
        } else {
            state.pois.filter { poi ->
                poi.name.contains(query, ignoreCase = true) ||
                    poi.shortName?.contains(query, ignoreCase = true) == true ||
                    poi.description.contains(query, ignoreCase = true)
            }
        }
        val here = state.currentPoint
        // Sorting by distance is only meaningful once there is a fix; otherwise keep the
        // curated order from the data file so gates stay numbered 1..5.
        if (here == null) {
            filtered.sortedWith(compareBy({ it.category.ordinal }, { it.order }, { it.name }))
        } else {
            filtered.sortedBy { GeoUtils.haversineMeters(here, it.point) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.poi_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.poi_list_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            if (state.pois.isEmpty()) {
                Text(
                    text = stringResource(R.string.poi_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                )
                return@Column
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { poi ->
                    PoiRow(
                        poi = poi,
                        distanceText = state.currentPoint?.let { here ->
                            Formats.distance(context, GeoUtils.haversineMeters(here, poi.point))
                        },
                        isUnroutable = poi.id in unroutable,
                        onClick = {
                            viewModel.addWaypoint(poi)
                            onBack()
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PoiRow(
    poi: Poi,
    distanceText: String?,
    isUnroutable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isUnroutable, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = poi.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(poi.category.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (poi.description.isNotBlank()) {
                Text(
                    text = poi.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isUnroutable) {
                Text(
                    text = stringResource(R.string.poi_unroutable_explain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (isUnroutable) {
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.poi_unroutable_badge)) })
        } else if (distanceText != null) {
            Text(
                text = stringResource(R.string.poi_distance_away, distanceText),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
