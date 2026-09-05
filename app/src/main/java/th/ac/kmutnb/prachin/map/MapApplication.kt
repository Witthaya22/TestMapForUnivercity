package th.ac.kmutnb.prachin.map

import android.app.Application
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import th.ac.kmutnb.prachin.map.di.AppContainer

/**
 * Application entry point.
 *
 * [MapLibre.getInstance] must run before any MapView is inflated. MapLibre needs no API key
 * and makes no network call here - it only wires up its native renderer and the local
 * ambient/offline tile cache database.
 */
class MapApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        container = AppContainer(this)

        // Copy the POIs shipped in assets into Room on first launch. Failing here must not
        // stop the app: the map and the survey tools still work with an empty POI table.
        container.applicationScope.launch {
            runCatching { container.poiRepository.seedIfNeeded() }
        }
    }
}
