package th.ac.kmutnb.prachin.map.ui.khaoyai

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.khaoyai.KhaoYaiContent
import th.ac.kmutnb.prachin.map.data.khaoyai.SpeciesKind
import th.ac.kmutnb.prachin.map.ui.khaoyai.home.HomeTab
import th.ac.kmutnb.prachin.map.ui.khaoyai.nature.NatureTab
import th.ac.kmutnb.prachin.map.ui.khaoyai.news.NewsTab
import th.ac.kmutnb.prachin.map.ui.khaoyai.profile.ProfileTab
import th.ac.kmutnb.prachin.map.ui.khaoyai.trail.TrailsTab

enum class MainTab(val labelRes: Int, val iconRes: Int) {
    HOME(R.string.tab_home, R.drawable.ic_home),
    TRAILS(R.string.tab_trails, R.drawable.ic_walk),
    NATURE(R.string.tab_nature, R.drawable.ic_eco),
    NEWS(R.string.tab_news, R.drawable.ic_article),
    PROFILE(R.string.tab_profile, R.drawable.ic_person),
}

/** What a visitor sees after signing in: five tabs over the bottom navigation bar. */
@Composable
fun MainShell(
    session: KhaoYaiSession,
    content: KhaoYaiContent,
    onOpenNavigation: () -> Unit,
    onOpenTrail: (String) -> Unit,
    onOpenSpecies: (String) -> Unit,
    onOpenNews: (String) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSos: () -> Unit,
    onOpenAdmin: () -> Unit,
    onLogout: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var natureKind by rememberSaveable { mutableStateOf<SpeciesKind?>(null) }

    Scaffold(
        // Each tab handles the status bar itself, so the home picture can run under it.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            MainTab.HOME -> HomeTab(
                session = session,
                content = content,
                contentPadding = padding,
                onOpenNavigation = onOpenNavigation,
                onOpenTrails = { tab = MainTab.TRAILS },
                onOpenTrail = onOpenTrail,
                onOpenNature = { kind ->
                    natureKind = kind
                    tab = MainTab.NATURE
                },
                onOpenNews = { tab = MainTab.NEWS },
                onOpenNewsItem = onOpenNews,
                onOpenAbout = onOpenAbout,
                onOpenSos = onOpenSos,
            )

            MainTab.TRAILS -> TrailsTab(content.trails, padding, onOpenTrail)

            MainTab.NATURE -> NatureTab(
                species = content.species,
                kind = natureKind,
                onKindChange = { natureKind = it },
                contentPadding = padding,
                onOpenSpecies = onOpenSpecies,
            )

            MainTab.NEWS -> NewsTab(content.newsByDate, padding, onOpenNews)

            MainTab.PROFILE -> ProfileTab(
                session = session,
                contentPadding = padding,
                onOpenNavigation = onOpenNavigation,
                onOpenAbout = onOpenAbout,
                onOpenHistory = onOpenHistory,
                onOpenSos = onOpenSos,
                onOpenAdmin = onOpenAdmin.takeIf { session.isAdmin },
                onLogout = onLogout,
            )
        }
    }
}
