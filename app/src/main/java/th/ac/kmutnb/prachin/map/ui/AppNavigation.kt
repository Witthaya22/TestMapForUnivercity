package th.ac.kmutnb.prachin.map.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import th.ac.kmutnb.prachin.map.ui.debug.GpsDebugScreen
import th.ac.kmutnb.prachin.map.ui.map.MapScreen
import th.ac.kmutnb.prachin.map.ui.map.MapViewModel
import th.ac.kmutnb.prachin.map.ui.onboarding.OnboardingScreen
import th.ac.kmutnb.prachin.map.ui.poi.PoiListScreen
import th.ac.kmutnb.prachin.map.ui.settings.OfflineMapManagerScreen
import th.ac.kmutnb.prachin.map.ui.settings.SettingsScreen
import th.ac.kmutnb.prachin.map.ui.survey.SurveyorScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAP = "map"
    const val POI_LIST = "poi_list"
    const val SETTINGS = "settings"
    const val OFFLINE_MANAGER = "offline_manager"
    const val SURVEY = "survey"
    const val GPS_DEBUG = "gps_debug"
}

@Composable
fun AppNavHost(
    startDestination: String,
    navController: NavHostController = rememberNavController(),
) {
    // Hoisted above the NavHost so the map and the destination picker share one instance:
    // picking a POI in the list has to reach the same route being planned on the map.
    val mapViewModel: MapViewModel = viewModel(factory = MapViewModel.Factory)

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.MAP) {
            MapScreen(
                viewModel = mapViewModel,
                onOpenPoiList = { navController.navigate(Routes.POI_LIST) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.POI_LIST) {
            PoiListScreen(
                viewModel = mapViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenOfflineManager = { navController.navigate(Routes.OFFLINE_MANAGER) },
                onOpenSurvey = { navController.navigate(Routes.SURVEY) },
                onOpenGpsDebug = { navController.navigate(Routes.GPS_DEBUG) },
            )
        }

        composable(Routes.OFFLINE_MANAGER) {
            OfflineMapManagerScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SURVEY) {
            SurveyorScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.GPS_DEBUG) {
            GpsDebugScreen(onBack = { navController.popBackStack() })
        }
    }
}
