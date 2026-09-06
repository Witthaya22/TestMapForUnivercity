package th.ac.kmutnb.prachin.map.ui.map

import android.graphics.PointF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.config.CampusConfig
import th.ac.kmutnb.prachin.map.location.LocationState
import th.ac.kmutnb.prachin.map.map.MapGeoJson
import th.ac.kmutnb.prachin.map.map.MapLayerManager
import th.ac.kmutnb.prachin.map.map.rememberMapViewWithLifecycle
import th.ac.kmutnb.prachin.map.ui.common.messageRes
import kotlin.math.roundToInt

@Composable
fun MapScreen(
    onOpenPoiList: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    val layerManager = remember { MapLayerManager(context) }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                MapEffect.VibrateArrival -> vibrate(context)
                is MapEffect.Message -> snackbarHostState.showSnackbar(context.getString(effect.messageRes))
                is MapEffect.CameraTo -> mapLibreMap?.animateCamera(
                    CameraUpdateFactory.newLatLng(LatLng(effect.point.lat, effect.point.lon)),
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        Box(Modifier.fillMaxSize()) {
            val problem = state.configProblem
            val config = state.config
            val styleUri = state.styleUri

            if (problem != null) {
                ConfigProblemOverlay(
                    message = stringResource(problem.messageRes),
                    modifier = Modifier.padding(insets),
                )
                return@Box
            }
            if (config == null || styleUri == null) return@Box

            CampusMapView(
                config = config,
                styleUri = styleUri,
                layerManager = layerManager,
                onMapReady = { mapLibreMap = it },
                onPoiTapped = viewModel::onPoiTapped,
                onLongPressed = viewModel::onMapLongPressed,
            )

            // --- overlays pushed into the map ------------------------------------------
            LaunchedEffect(state.pois, state.network.unroutablePoiIds, layerManager.isAttached) {
                if (layerManager.isAttached) {
                    layerManager.setPois(state.pois, state.network.unroutablePoiIds.toSet())
                }
            }
            LaunchedEffect(state.showWalkingNetwork, state.network.paths, layerManager.isAttached) {
                if (layerManager.isAttached) {
                    layerManager.setWalkingNetwork(state.network.paths, state.showWalkingNetwork)
                }
            }
            LaunchedEffect(state.locationState, layerManager.isAttached) {
                if (!layerManager.isAttached) return@LaunchedEffect
                val available = state.locationState as? LocationState.Available
                layerManager.setUserLocation(
                    point = available?.fix?.point,
                    accuracyMeters = available?.fix?.accuracyMeters ?: 0f,
                )
            }
            LaunchedEffect(state.route, state.progress, layerManager.isAttached) {
                if (!layerManager.isAttached) return@LaunchedEffect
                val progress = state.progress
                val route = state.route
                when {
                    progress != null ->
                        layerManager.setRoute(progress.travelledPolyline, progress.remainingPolyline)

                    route != null -> layerManager.setRoute(emptyList(), route.points)
                    else -> layerManager.clearRoute()
                }
            }
            LaunchedEffect(state.pendingPlacement, layerManager.isAttached) {
                if (!layerManager.isAttached) return@LaunchedEffect
                val near = state.pendingPlacement?.verdict as? th.ac.kmutnb.prachin.map.navigation.TapVerdict.NearPath
                layerManager.setSuggestionLine(near?.point, near?.suggestion)
            }

            // --- chrome ----------------------------------------------------------------
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GpsStatusBanner(state.locationState)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .padding(bottom = if (state.isNavigating) 200.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                SmallFloatingActionButton(onClick = onOpenSettings) {
                    Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title))
                }
                SmallFloatingActionButton(onClick = viewModel::toggleWalkingNetwork) {
                    Icon(painterResource(R.drawable.ic_layers), stringResource(R.string.map_layers))
                }
                FloatingActionButton(onClick = viewModel::recenter) {
                    Icon(painterResource(R.drawable.ic_my_location), stringResource(R.string.map_recenter))
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val progress = state.progress
                if (state.isNavigating && progress != null) {
                    NavigationPanel(
                        progress = progress,
                        isOffRoute = state.isOffRoute,
                        onRecalculate = viewModel::recalculateRoute,
                        onStop = { viewModel.stopNavigation() },
                    )
                } else {
                    RoutePlanBar(
                        state = state,
                        onOpenPoiList = onOpenPoiList,
                        onStart = viewModel::startNavigation,
                        onClear = viewModel::clearWaypoints,
                    )
                }
            }
        }
    }

    // --- sheets and dialogs --------------------------------------------------------------
    state.detailPoi?.let { poi ->
        PoiDetailSheet(
            poi = poi,
            isArrival = state.detailIsArrival,
            isNavigating = state.isNavigating,
            onDismiss = viewModel::dismissDetail,
            onNavigateHere = { viewModel.setSingleDestination(poi) },
            onAddWaypoint = { viewModel.addWaypoint(poi) },
            onSaveDetails = { name, description, note, category ->
                viewModel.saveDetails(poi.id, name, description, note, category)
            },
            onDelete = { viewModel.deletePoi(poi.id) },
        )
    }

    state.pendingPlacement?.let { pending ->
        PlacementDialog(
            verdict = pending.verdict,
            onMoveToPath = viewModel::acceptSuggestedPlacement,
            onKeepAnyway = viewModel::keepPlacementAnyway,
            onDismiss = viewModel::dismissPlacement,
        )
    }

    if (state.namingPoint != null) {
        NamePointDialog(
            onConfirm = viewModel::createPoiAtPendingPoint,
            onDismiss = viewModel::dismissPlacement,
        )
    }
}

@Composable
private fun CampusMapView(
    config: CampusConfig,
    styleUri: String,
    layerManager: MapLayerManager,
    onMapReady: (MapLibreMap) -> Unit,
    onPoiTapped: (String) -> Unit,
    onLongPressed: (GeoPoint) -> Unit,
) {
    val mapView = rememberMapViewWithLifecycle()

    DisposableEffect(mapView, styleUri) {
        mapView.getMapAsync { map ->
            onMapReady(map)
            map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
                layerManager.attach(style)
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(config.center.lat, config.center.lon))
                    .zoom(config.defaultZoom)
                    .build()
            }

            map.setMinZoomPreference(config.minZoom.toDouble())
            map.setMaxZoomPreference(config.maxZoom.toDouble())
            // Keeps the camera inside the downloaded pack, so panning off it shows the campus
            // edge rather than empty tiles.
            map.setLatLngBoundsForCameraTarget(
                LatLngBounds.from(
                    config.bbox.maxLat,
                    config.bbox.maxLon,
                    config.bbox.minLat,
                    config.bbox.minLon,
                ),
            )
            map.uiSettings.isTiltGesturesEnabled = false
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = true

            map.addOnMapClickListener { latLng ->
                val screenPoint = map.projection.toScreenLocation(latLng)
                val hits = map.queryRenderedFeatures(
                    PointF(screenPoint.x, screenPoint.y),
                    MapLayerManager.LAYER_POIS,
                )
                val id = hits.firstNotNullOfOrNull {
                    it.getStringProperty(MapGeoJson.PROPERTY_ID)
                }
                if (id != null) {
                    onPoiTapped(id)
                    true
                } else {
                    false
                }
            }

            map.addOnMapLongClickListener { latLng ->
                onLongPressed(GeoPoint(lat = latLng.latitude, lon = latLng.longitude))
                true
            }
        }
        onDispose { layerManager.detach() }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

/**
 * Says why the dot is missing.
 *
 * A cold start with no network has no almanac and can take a minute and a half, so leaving
 * the map silently blank would read as a broken app.
 */
@Composable
private fun GpsStatusBanner(locationState: LocationState) {
    val text = when (locationState) {
        LocationState.PermissionMissing -> stringResource(R.string.permission_denied_title)
        LocationState.ProviderDisabled -> stringResource(R.string.map_gps_disabled)
        is LocationState.Searching -> locationState.lastAccuracyMeters?.let {
            stringResource(R.string.map_searching_gps_accuracy, it.roundToInt())
        } ?: stringResource(R.string.map_searching_gps)

        is LocationState.Available ->
            if (locationState.fix.accuracyMeters > WEAK_SIGNAL_METERS) {
                stringResource(R.string.map_gps_weak)
            } else {
                null
            }
    } ?: return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RoutePlanBar(
    state: MapUiState,
    onOpenPoiList: () -> Unit,
    onStart: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val route = state.route

    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.selectedWaypoints.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onOpenPoiList,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(painterResource(R.drawable.ic_place), null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.poi_list_title))
                }
            } else {
                Text(
                    text = state.selectedWaypoints.joinToString(" → ") { it.label },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (route != null) {
                    Text(
                        text = stringResource(
                            R.string.nav_route_summary,
                            th.ac.kmutnb.prachin.map.ui.common.Formats.distance(
                                context,
                                route.totalDistanceMeters,
                            ),
                            th.ac.kmutnb.prachin.map.ui.common.Formats.duration(
                                context,
                                route.totalDurationSeconds,
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Button(
                        onClick = onStart,
                        enabled = route != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.nav_start))
                    }
                    androidx.compose.material3.OutlinedButton(onClick = onOpenPoiList) {
                        Icon(painterResource(R.drawable.ic_place), stringResource(R.string.nav_add_waypoint))
                    }
                    androidx.compose.material3.OutlinedButton(onClick = onClear) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.nav_clear_all))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigProblemOverlay(message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp)) {
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

private const val WEAK_SIGNAL_METERS = 15f

private fun vibrate(context: android.content.Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    } ?: return
    vibrator.vibrate(VibrationEffect.createOneShot(500L, VibrationEffect.DEFAULT_AMPLITUDE))
}
