package th.ac.kmutnb.prachin.map.ui.khaoyai

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.location.LocationState

/** The GPS as seen from a Khao Yai screen, and the way to ask for it when it is not allowed. */
class LocationUi(val state: LocationState?, val requestPermission: () -> Unit)

/**
 * Watches the GPS for as long as the calling screen is on show (the flow stops with the
 * lifecycle, so a trail page left open in the background does not drain the battery).
 * Granting permission restarts the watch: the source only checks permission when started.
 */
@Composable
fun rememberLocationUi(intervalMs: Long = 2_000): LocationUi {
    val context = LocalContext.current
    val source = remember { (context.applicationContext as MapApplication).container.locationSource }
    var restarts by remember { mutableIntStateOf(0) }
    val flow = remember(restarts) { source.updates(minIntervalMs = intervalMs) }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        restarts++
    }
    return LocationUi(state) {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}
