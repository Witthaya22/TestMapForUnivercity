package th.ac.kmutnb.prachin.map.ui.khaoyai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.data.model.Permission
import th.ac.kmutnb.prachin.map.data.model.UserAccount
import th.ac.kmutnb.prachin.map.ui.AppNavHost
import th.ac.kmutnb.prachin.map.ui.Routes
import th.ac.kmutnb.prachin.map.ui.khaoyai.admin.AdminScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.admin.AdminSection
import th.ac.kmutnb.prachin.map.ui.khaoyai.auth.ForgotPasswordScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.auth.LoginScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.auth.SignUpScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.nature.SpeciesDetailScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.news.NewsDetailScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.profile.AboutScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.profile.RouteHistoryScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.sos.SosScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.staff.ProfileEditScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.staff.ReportScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.staff.StaffShell
import th.ac.kmutnb.prachin.map.ui.khaoyai.staff.TouristDetailScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.staff.UserEditScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.staff.UserListScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.trail.TrailDetailScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.trail.TrailInfoScreen

object KhaoYaiRoutes {
    const val LOGIN = "ky/login"
    const val SIGN_UP = "ky/sign_up"
    const val FORGOT_PASSWORD = "ky/forgot_password"
    /** The tourist's five tabs (§2.3.1). */
    const val MAIN = "ky/main"
    /** Officers and administrators (§2.3.2, §2.3.3). */
    const val STAFF = "ky/staff"
    const val ABOUT = "ky/about"
    const val HISTORY = "ky/history"
    const val SOS = "ky/sos"
    const val PROFILE_EDIT = "ky/profile/edit"
    const val TRAIL_INFO = "ky/trail_info"
    const val REPORTS = "ky/staff/reports"
    const val USERS = "ky/staff/users"
    const val CONTENT = "ky/staff/content"

    const val ARG_ID = "id"
    const val ARG_START = "start"

    /**
     * The walking navigation built before this shell: map, routing, surveying. [ARG_START]
     * opens it on one of its own screens (a `Routes` value) instead of the map.
     */
    const val NAVIGATION = "ky/navigation?$ARG_START={$ARG_START}"
    fun navigation(start: String = Routes.MAP) = "ky/navigation?$ARG_START=$start"

    const val TRAIL = "ky/trail/{$ARG_ID}"
    const val SPECIES = "ky/species/{$ARG_ID}"
    const val NEWS = "ky/news/{$ARG_ID}"
    const val TOURIST = "ky/staff/tourist/{$ARG_ID}"
    /** `new` for a new account. */
    const val USER_EDIT = "ky/staff/user/{$ARG_ID}"
    const val NEW_USER = "new"

    // Content ids are `<prefix>_<uuid>` or hand-written snake_case: path-safe as they stand.
    fun trail(id: String) = "ky/trail/$id"
    fun species(id: String) = "ky/species/$id"
    fun news(id: String) = "ky/news/$id"
    fun tourist(id: String) = "ky/staff/tourist/$id"
    fun userEdit(id: String?) = "ky/staff/user/${id ?: NEW_USER}"

    fun home(account: UserAccount) = if (account.isStaff) STAFF else MAIN
}

/** What was restored at launch; [account] null means signed out. */
private class Restored(val account: UserAccount?)

/**
 * The Khao Yai app: sign in, then the tourist tabs or the staff shell by role, with the
 * existing walking navigation (`AppNavHost`) as one destination inside it.
 */
@Composable
fun KhaoYaiApp() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    // Restoring the session reads the database: hold an empty surface for that moment
    // rather than flashing the sign-in screen at someone already signed in.
    val restored by produceState<Restored?>(initialValue = null) {
        runCatching { container.demoDataSeeder.seedOnce() }
        value = Restored(container.accountRepository.restoreSession())
    }
    restored?.let { KhaoYaiNavHost(it.account) } ?: Box(Modifier.fillMaxSize())
}

@Composable
private fun KhaoYaiNavHost(
    restoredAccount: UserAccount?,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val repository = container.khaoYaiContentRepository
    val content by repository.content.collectAsStateWithLifecycle()
    val currentUser by container.accountRepository.currentUser.collectAsStateWithLifecycle()
    // The live account, with a fallback for the frames before the database flow catches up:
    // the account restored at launch or just signed in. Cleared on sign-out, so the old user
    // never lingers behind the sign-in screen; set on sign-in, so a fresh sign-in is never
    // bounced back out by a momentary null.
    var fallback by remember { mutableStateOf(restoredAccount) }
    val account = currentUser ?: fallback
    val scope = rememberCoroutineScope()

    val startDestination = remember { restoredAccount?.let(KhaoYaiRoutes::home) ?: KhaoYaiRoutes.LOGIN }

    fun goHome(signedIn: UserAccount) {
        fallback = signedIn
        navController.navigate(KhaoYaiRoutes.home(signedIn)) { popUpTo(navController.graph.id) { inclusive = true } }
    }

    fun logout() {
        fallback = null
        scope.launch {
            repository.endTrail()
            container.accountRepository.signOut()
        }
        navController.navigate(KhaoYaiRoutes.LOGIN) { popUpTo(navController.graph.id) { inclusive = true } }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(KhaoYaiRoutes.LOGIN) {
            LoginScreen(
                onSignedIn = ::goHome,
                onSignUp = { navController.navigate(KhaoYaiRoutes.SIGN_UP) },
                onForgotPassword = { navController.navigate(KhaoYaiRoutes.FORGOT_PASSWORD) },
            )
        }

        composable(KhaoYaiRoutes.SIGN_UP) {
            SignUpScreen(onRegistered = ::goHome, onBack = { navController.popBackStack() })
        }

        composable(KhaoYaiRoutes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onDone = { navController.popBackStack() })
        }

        composable(KhaoYaiRoutes.MAIN) {
            val user = account
            if (user == null) {
                LaunchedEffect(Unit) { logout() }
                return@composable
            }
            MainShell(
                account = user,
                content = content,
                onOpenNavigation = { navController.navigate(KhaoYaiRoutes.navigation()) },
                onOpenTrail = { navController.navigate(KhaoYaiRoutes.trail(it)) },
                onOpenTrailInfo = { navController.navigate(KhaoYaiRoutes.TRAIL_INFO) },
                onOpenSpecies = { navController.navigate(KhaoYaiRoutes.species(it)) },
                onOpenNews = { navController.navigate(KhaoYaiRoutes.news(it)) },
                onOpenAbout = { navController.navigate(KhaoYaiRoutes.ABOUT) },
                onOpenHistory = { navController.navigate(KhaoYaiRoutes.HISTORY) },
                onOpenSos = { navController.navigate(KhaoYaiRoutes.SOS) },
                onEditProfile = { navController.navigate(KhaoYaiRoutes.PROFILE_EDIT) },
                onOpenStaff = if (user.isStaff) ({ goHome(user) }) else null,
                onLogout = ::logout,
            )
        }

        composable(KhaoYaiRoutes.STAFF) {
            StaffGate(account, navController) { user ->
                StaffShell(
                    account = user,
                    onOpenTourist = { navController.navigate(KhaoYaiRoutes.tourist(it)) },
                    onOpenReports = { navController.navigate(KhaoYaiRoutes.REPORTS) },
                    onOpenUsers = { navController.navigate(KhaoYaiRoutes.USERS) },
                    onOpenContent = { navController.navigate(KhaoYaiRoutes.CONTENT) },
                    onOpenTool = { route -> navController.navigate(KhaoYaiRoutes.navigation(route)) },
                    onViewAsTourist = { navController.navigate(KhaoYaiRoutes.MAIN) },
                    onEditProfile = { navController.navigate(KhaoYaiRoutes.PROFILE_EDIT) },
                    onOpenAbout = { navController.navigate(KhaoYaiRoutes.ABOUT) },
                    onLogout = ::logout,
                )
            }
        }

        composable(KhaoYaiRoutes.REPORTS) {
            StaffGate(account, navController, Permission.VIEW_REPORTS) {
                ReportScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(KhaoYaiRoutes.USERS) {
            StaffGate(account, navController, adminOnly = true) {
                UserListScreen(onBack = { navController.popBackStack() }, onOpen = { navController.navigate(KhaoYaiRoutes.userEdit(it)) })
            }
        }

        composable(KhaoYaiRoutes.USER_EDIT, arguments = idArgument) { entry ->
            StaffGate(account, navController, adminOnly = true) { user ->
                val id = entry.id().takeUnless { it == KhaoYaiRoutes.NEW_USER }
                UserEditScreen(userId = id, currentUserId = user.id, onBack = { navController.popBackStack() })
            }
        }

        composable(KhaoYaiRoutes.TOURIST, arguments = idArgument) { entry ->
            StaffGate(account, navController, Permission.VIEW_TOURISTS) {
                TouristDetailScreen(userId = entry.id(), onBack = { navController.popBackStack() })
            }
        }

        composable(KhaoYaiRoutes.CONTENT) {
            StaffGate(account, navController, Permission.EDIT_TRAILS) { user ->
                AdminScreen(
                    content = content,
                    repository = repository,
                    // Officers update trails (§2.3.2.3); the admin also news and species.
                    sections = if (user.isAdmin) AdminSection.entries else listOf(AdminSection.TRAILS),
                    canReset = user.isAdmin,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(KhaoYaiRoutes.PROFILE_EDIT) {
            val user = account ?: return@composable
            ProfileEditScreen(userId = user.id, onBack = { navController.popBackStack() })
        }

        composable(KhaoYaiRoutes.TRAIL, arguments = idArgument) { entry ->
            val trailId = entry.id()
            TrailDetailScreen(
                trail = content.trail(trailId),
                onBack = { navController.popBackStack() },
                onStartNavigation = {
                    // The map picks the walked trail up from here and shows its stations.
                    repository.startTrail(trailId)
                    navController.navigate(KhaoYaiRoutes.navigation())
                },
            )
        }

        composable(KhaoYaiRoutes.TRAIL_INFO) {
            TrailInfoScreen(onBack = { navController.popBackStack() })
        }

        composable(KhaoYaiRoutes.SPECIES, arguments = idArgument) { entry ->
            SpeciesDetailScreen(species = content.speciesItem(entry.id()), onBack = { navController.popBackStack() })
        }

        composable(KhaoYaiRoutes.NEWS, arguments = idArgument) { entry ->
            NewsDetailScreen(item = content.newsItem(entry.id()), onBack = { navController.popBackStack() })
        }

        composable(KhaoYaiRoutes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(KhaoYaiRoutes.HISTORY) {
            RouteHistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(KhaoYaiRoutes.SOS) {
            SosScreen(account = account, onBack = { navController.popBackStack() })
        }

        composable(
            KhaoYaiRoutes.NAVIGATION,
            arguments = listOf(
                navArgument(KhaoYaiRoutes.ARG_START) {
                    type = NavType.StringType
                    defaultValue = Routes.MAP
                },
            ),
        ) { entry ->
            val start = entry.arguments?.getString(KhaoYaiRoutes.ARG_START) ?: Routes.MAP
            // Editing places, walking in trails and the other survey tools are for staff
            // who may edit trails; everyone else only ever gets the map.
            val canEdit = account?.can(Permission.EDIT_TRAILS) == true
            NavigationFeature(
                isAdmin = canEdit,
                start = if (canEdit) start else Routes.MAP,
                onOpenSos = { navController.navigate(KhaoYaiRoutes.SOS) },
                onExit = { navController.popBackStack() },
            )
        }
    }
}

/** Shows [body] only to staff with [permission] (or admins, when [adminOnly]); anyone else is sent back. */
@Composable
private fun StaffGate(
    account: UserAccount?,
    navController: NavHostController,
    permission: Permission? = null,
    adminOnly: Boolean = false,
    body: @Composable (UserAccount) -> Unit,
) {
    val allowed = account != null && account.isStaff && (!adminOnly || account.isAdmin) && (permission == null || account.can(permission))
    if (allowed && account != null) body(account) else LaunchedEffect(Unit) { navController.popBackStack() }
}

private val idArgument = listOf(navArgument(KhaoYaiRoutes.ARG_ID) { type = NavType.StringType })

private fun NavBackStackEntry.id(): String = arguments?.getString(KhaoYaiRoutes.ARG_ID).orEmpty()

/**
 * The walking navigation as it was the whole app before: onboarding until an offline pack
 * is downloaded, then [start]. Its own NavHost keeps its own back stack; back from its
 * first screen returns to the Khao Yai screens.
 */
@Composable
private fun NavigationFeature(isAdmin: Boolean, start: String, onOpenSos: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { (context.applicationContext as MapApplication).container.preferences }
    // Reading the flag is a disk hop: hold an empty surface for that frame or two rather
    // than starting on the wrong screen and jumping.
    val offlineReady by produceState<Boolean?>(initialValue = null) {
        value = preferences.offlineMapReady.first()
    }
    when (offlineReady) {
        null -> Box(Modifier.fillMaxSize())
        true -> AppNavHost(startDestination = start, isAdmin = isAdmin, onOpenSos = onOpenSos, onExit = onExit)
        false -> AppNavHost(startDestination = Routes.ONBOARDING, isAdmin = isAdmin, onOpenSos = onOpenSos, onExit = onExit)
    }
}
