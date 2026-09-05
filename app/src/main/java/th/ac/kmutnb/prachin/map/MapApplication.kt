package th.ac.kmutnb.prachin.map

import android.app.Application
import org.maplibre.android.MapLibre

/**
 * Application entry point.
 *
 * [MapLibre.getInstance] must run before any MapView is inflated. MapLibre needs no API key
 * and makes no network call here - it only wires up its native renderer and the local
 * ambient/offline tile cache database.
 */
class MapApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
