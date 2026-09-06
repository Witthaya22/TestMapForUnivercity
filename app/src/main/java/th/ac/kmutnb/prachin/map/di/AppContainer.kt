package th.ac.kmutnb.prachin.map.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import th.ac.kmutnb.prachin.map.data.assets.AndroidAssetReader
import th.ac.kmutnb.prachin.map.data.assets.AssetReader
import th.ac.kmutnb.prachin.map.data.local.AppDatabase
import th.ac.kmutnb.prachin.map.data.prefs.AppPreferences
import th.ac.kmutnb.prachin.map.data.repository.CampusRepository
import th.ac.kmutnb.prachin.map.data.repository.OfflineMapRepository
import th.ac.kmutnb.prachin.map.data.repository.PoiRepository
import th.ac.kmutnb.prachin.map.data.repository.RouteNetworkRepository
import th.ac.kmutnb.prachin.map.data.repository.WalkPathRepository
import th.ac.kmutnb.prachin.map.location.GpsLocationSource
import th.ac.kmutnb.prachin.map.map.MapStyleProvider

/**
 * Manual dependency container.
 *
 * The graph is small and entirely singletons, so a hand-written container costs less than
 * adding an injection framework and its build-time processing.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val assetReader: AssetReader = AndroidAssetReader(appContext)

    val preferences = AppPreferences(appContext)

    private val database = AppDatabase.get(appContext)

    val campusRepository = CampusRepository(assetReader)

    val poiRepository = PoiRepository(
        poiDao = database.poiDao(),
        campusRepository = campusRepository,
        preferences = preferences,
    )

    val routeHistoryDao = database.routeHistoryDao()

    val walkPathRepository = WalkPathRepository(database.walkPathDao())

    val routeNetworkRepository = RouteNetworkRepository(
        campusRepository = campusRepository,
        poiRepository = poiRepository,
        walkPathRepository = walkPathRepository,
        preferences = preferences,
        scope = applicationScope,
    )

    val offlineMapRepository = OfflineMapRepository(
        context = appContext,
        campusRepository = campusRepository,
        preferences = preferences,
    )

    val mapStyleProvider = MapStyleProvider(appContext, campusRepository)

    val locationSource = GpsLocationSource(appContext)
}
