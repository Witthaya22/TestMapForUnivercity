package th.ac.kmutnb.prachin.map.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.config.CampusConfig
import th.ac.kmutnb.prachin.map.map.rememberMapViewWithLifecycle
import th.ac.kmutnb.prachin.map.ui.common.messageRes

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel(factory = MapViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        val problem = state.configProblem
        val config = state.config
        val styleUri = state.styleUri

        when {
            problem != null -> ConfigProblemOverlay(stringResource(problem.messageRes))
            config != null && styleUri != null -> CampusMap(config = config, styleUri = styleUri)
            else -> Unit
        }
    }
}

@Composable
private fun CampusMap(config: CampusConfig, styleUri: String) {
    val mapView = rememberMapViewWithLifecycle()

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            view.getMapAsync { map ->
                if (map.style == null) {
                    map.setStyle(Style.Builder().fromUri(styleUri)) {
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(config.center.lat, config.center.lon))
                            .zoom(config.defaultZoom)
                            .build()
                    }
                }

                // Clamp to what the offline pack actually contains, so panning or zooming
                // past the downloaded area shows the campus edge rather than blank tiles.
                map.setMinZoomPreference(config.minZoom.toDouble())
                map.setMaxZoomPreference(config.maxZoom.toDouble())
                map.setLatLngBoundsForCameraTarget(
                    LatLngBounds.from(
                        config.bbox.maxLat,
                        config.bbox.maxLon,
                        config.bbox.minLat,
                        config.bbox.minLon,
                    ),
                )
                map.uiSettings.isRotateGesturesEnabled = true
                map.uiSettings.isTiltGesturesEnabled = false
                map.uiSettings.isAttributionEnabled = true
                map.uiSettings.isLogoEnabled = false
            }
        },
    )
}

@Composable
private fun ConfigProblemOverlay(message: String) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = stringResource(R.string.setup_required_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Card(
            modifier = Modifier.padding(top = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = stringResource(R.string.setup_how_to),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
