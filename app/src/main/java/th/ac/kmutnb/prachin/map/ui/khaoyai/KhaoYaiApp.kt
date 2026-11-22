package th.ac.kmutnb.prachin.map.ui.khaoyai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.ui.AppNavHost
import th.ac.kmutnb.prachin.map.ui.Routes
import th.ac.kmutnb.prachin.map.ui.khaoyai.admin.AdminScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.auth.ForgotPasswordScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.auth.LoginScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.auth.SignUpScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.nature.SpeciesDetailScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.news.NewsDetailScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.profile.AboutScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.trail.TrailDetailScreen

object KhaoYaiRoutes {
    const val LOGIN = "ky/login"
    const val SIGN_UP = "ky/sign_up"
    const val FORGOT_PASSWORD = "ky/forgot_password"
    const val MAIN = "ky/main"
    const val ABOUT = "ky/about"

    /** Reached by signing in as "admin"; see `KhaoYaiAuth`. */
    const val ADMIN = "ky/admin"

    /** The walking navigation built before this shell: map, routing, surveying. */
    const val NAVIGATION = "ky/navigation"

    const val ARG_ID = "id"
    const val TRAIL = "ky/trail/{$ARG_ID}"
    const val SPECIES = "ky/species/{$ARG_ID}"
    const val NEWS = "ky/news/{$ARG_ID}"

    // Content ids are `<prefix>_<uuid>` or hand-written snake_case: path-safe as they stand.
    fun trail(id: String) = "ky/trail/$id"
    fun species(id: String) = "ky/species/$id"
    fun news(id: String) = "ky/news/$id"
}

/**
 * The Khao Yai app: sign in, then the visitor tabs, with the existing walking navigation
 * (`AppNavHost`) as one destination inside it.
 */
@Composable
fun KhaoYaiApp(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as MapApplication).container.khaoYaiContentRepository }
    val content by repository.content.collectAsStateWithLifecycle()

    // Kept across rotation and process death, like the back stack it goes with.
    var username by rememberSaveable { mutableStateOf<String?>(null) }
    val session = KhaoYaiAuth.signIn(username.orEmpty())

    fun openHome(admin: Boolean) =
        navController.navigate(if (admin) KhaoYaiRoutes.ADMIN else KhaoYaiRoutes.MAIN) {
            popUpTo(KhaoYaiRoutes.LOGIN) { inclusive = true }
        }

    fun logout() {
        username = null
        navController.navigate(KhaoYaiRoutes.LOGIN) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = KhaoYaiRoutes.LOGIN) {

        composable(KhaoYaiRoutes.LOGIN) {
            LoginScreen(
                onSignIn = { typed ->
                    username = typed
                    openHome(admin = KhaoYaiAuth.signIn(typed).isAdmin)
                },
                onSignUp = { navController.navigate(KhaoYaiRoutes.SIGN_UP) },
                onForgotPassword = { navController.navigate(KhaoYaiRoutes.FORGOT_PASSWORD) },
            )
        }

        composable(KhaoYaiRoutes.SIGN_UP) {
            SignUpScreen(onDone = { navController.popBackStack() })
        }

        composable(KhaoYaiRoutes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onDone = { navController.popBackStack() })
        }

        composable(KhaoYaiRoutes.MAIN) {
            MainShell(
                session = session,
                content = content,
                onOpenNavigation = { navController.navigate(KhaoYaiRoutes.NAVIGATION) },
                onOpenTrail = { navController.navigate(KhaoYaiRoutes.trail(it)) },
                onOpenSpecies = { navController.navigate(KhaoYaiRoutes.species(it)) },
                onOpenNews = { navController.navigate(KhaoYaiRoutes.news(it)) },
                onOpenAbout = { navController.navigate(KhaoYaiRoutes.ABOUT) },
                onOpenAdmin = { navController.navigate(KhaoYaiRoutes.ADMIN) { popUpTo(KhaoYaiRoutes.ADMIN) { inclusive = true } } },
                onLogout = ::logout,
            )
        }

        composable(KhaoYaiRoutes.ADMIN) {
            if (!session.isAdmin) {
                // Only a restored back stack can land here signed out of admin.
                LaunchedEffect(Unit) { logout() }
                return@composable
            }
            AdminScreen(
                content = content,
                repository = repository,
                // Switching back and forth pops to the earlier copy instead of stacking more.
                onViewAsVisitor = {
                    navController.navigate(KhaoYaiRoutes.MAIN) { popUpTo(KhaoYaiRoutes.MAIN) { inclusive = true } }
                },
                onLogout = ::logout,
            )
        }

        composable(KhaoYaiRoutes.TRAIL, arguments = idArgument) { entry ->
            TrailDetailScreen(
                trail = content.trail(entry.id()),
                onBack = { navController.popBackStack() },
                onStartNavigation = { navController.navigate(KhaoYaiRoutes.NAVIGATION) },
            )
        }

        composable(KhaoYaiRoutes.SPECIES, arguments = idArgument) { entry ->
            SpeciesDetailScreen(
                species = content.speciesItem(entry.id()),
                onBack = { navController.popBackStack() },
            )
        }

        composable(KhaoYaiRoutes.NEWS, arguments = idArgument) { entry ->
            NewsDetailScreen(
                item = content.newsItem(entry.id()),
                onBack = { navController.popBackStack() },
            )
        }

        composable(KhaoYaiRoutes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(KhaoYaiRoutes.NAVIGATION) {
            NavigationFeature()
        }
    }
}

private val idArgument = listOf(navArgument(KhaoYaiRoutes.ARG_ID) { type = NavType.StringType })

private fun androidx.navigation.NavBackStackEntry.id(): String =
    arguments?.getString(KhaoYaiRoutes.ARG_ID).orEmpty()

/**
 * The walking navigation as it was the whole app before: onboarding until an offline pack
 * is downloaded, the map after. Its own NavHost keeps its own back stack; back from its
 * first screen returns to the Khao Yai screens.
 */
@Composable
private fun NavigationFeature() {
    val context = LocalContext.current
    val preferences = remember { (context.applicationContext as MapApplication).container.preferences }
    // Reading the flag is a disk hop: hold an empty surface for that frame or two rather
    // than starting on the wrong screen and jumping.
    val offlineReady by produceState<Boolean?>(initialValue = null) {
        value = preferences.offlineMapReady.first()
    }
    when (offlineReady) {
        null -> Box(Modifier.fillMaxSize())
        true -> AppNavHost(startDestination = Routes.MAP)
        false -> AppNavHost(startDestination = Routes.ONBOARDING)
    }
}
