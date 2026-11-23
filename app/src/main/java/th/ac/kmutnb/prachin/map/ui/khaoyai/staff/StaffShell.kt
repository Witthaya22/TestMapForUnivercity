package th.ac.kmutnb.prachin.map.ui.khaoyai.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import th.ac.kmutnb.prachin.map.ui.khaoyai.GlassNavigationBar
import th.ac.kmutnb.prachin.map.ui.khaoyai.forestNavItemColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.Permission
import th.ac.kmutnb.prachin.map.data.model.Reports
import th.ac.kmutnb.prachin.map.data.model.UserRole
import th.ac.kmutnb.prachin.map.data.model.SosStatus
import th.ac.kmutnb.prachin.map.data.model.UserAccount
import th.ac.kmutnb.prachin.map.ui.khaoyai.ForestBrush
import th.ac.kmutnb.prachin.map.ui.khaoyai.LeafCorner
import th.ac.kmutnb.prachin.map.ui.khaoyai.MenuRow
import th.ac.kmutnb.prachin.map.ui.khaoyai.Pill
import th.ac.kmutnb.prachin.map.ui.khaoyai.admin.MapToolsCard
import th.ac.kmutnb.prachin.map.ui.khaoyai.admin.SosAdminScreen
import th.ac.kmutnb.prachin.map.ui.khaoyai.profile.ProfileTab
import th.ac.kmutnb.prachin.map.ui.theme.HornbillAmberLight

private enum class StaffTab(val labelRes: Int, val iconRes: Int) {
    OVERVIEW(R.string.staff_tab_overview, R.drawable.ic_home),
    SOS(R.string.staff_tab_sos, R.drawable.ic_call),
    TOURISTS(R.string.staff_tab_tourists, R.drawable.ic_person),
    ME(R.string.tab_profile, R.drawable.ic_settings),
}

/**
 * What a park officer or an administrator sees after signing in (§2.3.2, §2.3.3). The
 * tabs follow the account's permissions; an administrator has them all, plus users.
 */
@Composable
fun StaffShell(
    account: UserAccount,
    onOpenTourist: (String) -> Unit,
    onOpenReports: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenContent: () -> Unit,
    onOpenTool: (String) -> Unit,
    onViewAsTourist: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit,
) {
    val tabs = StaffTab.entries.filter {
        when (it) {
            StaffTab.SOS -> account.can(Permission.MANAGE_SOS)
            StaffTab.TOURISTS -> account.can(Permission.VIEW_TOURISTS)
            else -> true
        }
    }
    var tab by rememberSaveable { mutableStateOf(StaffTab.OVERVIEW) }
    if (tab !in tabs) tab = StaffTab.OVERVIEW

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            GlassNavigationBar {
                tabs.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                        colors = forestNavItemColors(),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                StaffTab.OVERVIEW -> StaffDashboard(
                    account = account,
                    onOpenSos = { tab = StaffTab.SOS },
                    onOpenTourists = { tab = StaffTab.TOURISTS },
                    onOpenReports = onOpenReports,
                    onOpenUsers = onOpenUsers,
                    onOpenContent = onOpenContent,
                    onOpenTool = onOpenTool,
                    onViewAsTourist = onViewAsTourist,
                )
                StaffTab.SOS -> SosAdminScreen(onBack = null, onOpenTourist = onOpenTourist, canDelete = account.isAdmin)
                StaffTab.TOURISTS -> TouristSearchTab(onOpenTourist = onOpenTourist)
                StaffTab.ME -> ProfileTab(
                    account = account,
                    contentPadding = PaddingValues(0.dp),
                    onOpenNavigation = null,
                    onOpenAbout = onOpenAbout,
                    onOpenHistory = null,
                    onOpenSos = null,
                    onEditProfile = onEditProfile,
                    onLogout = onLogout,
                )
            }
        }
    }
}

@Composable
private fun StaffDashboard(
    account: UserAccount,
    onOpenSos: () -> Unit,
    onOpenTourists: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenContent: () -> Unit,
    onOpenTool: (String) -> Unit,
    onViewAsTourist: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val reports by container.sosRepository.reports.collectAsStateWithLifecycle(initialValue = emptyList())
    val accounts by container.accountRepository.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val hazards by container.hazardRepository.activeHazards.collectAsStateWithLifecycle(initialValue = emptyList())
    val unsyncedSos by container.sosRepository.unsynced.collectAsStateWithLifecycle(initialValue = 0)
    val scope = rememberCoroutineScope()
    var confirmDemo by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val now = System.currentTimeMillis()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().background(ForestBrush)) {
                LeafCorner(Modifier.align(Alignment.TopEnd).statusBarsPadding(), size = 64.dp, alpha = 0.3f)
                Column(Modifier.statusBarsPadding().padding(20.dp)) {
                    Text(stringResource(R.string.staff_hello, account.fullName), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(stringResource(account.role.labelRes), HornbillAmberLight)
                        Pill(account.memberId, Color.White)
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.staff_summary_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard("🆘", reports.count { it.status == SosStatus.NEW }, R.string.staff_sum_sos_new, MaterialTheme.colorScheme.errorContainer, Modifier.weight(1f), onOpenSos)
                    SummaryCard("🚑", reports.count { it.status == SosStatus.ACKNOWLEDGED || it.status == SosStatus.IN_PROGRESS }, R.string.staff_sum_sos_active, MaterialTheme.colorScheme.tertiaryContainer, Modifier.weight(1f), onOpenSos)
                    SummaryCard("📅", Reports.today(reports, now), R.string.staff_sum_sos_today, MaterialTheme.colorScheme.surfaceContainerLowest, Modifier.weight(1f), onOpenSos)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard("👥", accounts.count { it.role == UserRole.TOURIST }, R.string.staff_sum_tourists, MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f), onOpenTourists)
                    SummaryCard("⚠️", hazards.size, R.string.staff_sum_hazards, MaterialTheme.colorScheme.surfaceContainerLowest, Modifier.weight(1f)) {
                        onOpenTool(th.ac.kmutnb.prachin.map.ui.Routes.HAZARDS)
                    }
                }
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(
                    stringResource(R.string.staff_sync_pending, unsyncedSos),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                if (account.can(Permission.VIEW_REPORTS)) {
                    MenuRow(R.drawable.ic_article, stringResource(R.string.staff_menu_reports), onOpenReports)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (account.isAdmin) {
                    MenuRow(R.drawable.ic_person, stringResource(R.string.staff_menu_users), onOpenUsers, tint = MaterialTheme.colorScheme.tertiary)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (account.can(Permission.EDIT_TRAILS)) {
                    MenuRow(R.drawable.ic_edit, stringResource(if (account.isAdmin) R.string.staff_menu_content else R.string.staff_menu_trails), onOpenContent)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                MenuRow(R.drawable.ic_eco, stringResource(R.string.admin_view_as_visitor), onViewAsTourist)
            }
        }
        if (account.can(Permission.EDIT_TRAILS)) {
            item { MapToolsCard(onOpenTool) }
        }
        if (account.isAdmin) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.staff_demo_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.staff_demo_body), style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { confirmDemo = false }) { Text(stringResource(R.string.staff_demo_clear), color = MaterialTheme.colorScheme.error) }
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = { confirmDemo = true }) { Text(stringResource(R.string.staff_demo_seed)) }
                        }
                    }
                }
            }
        }
    }

    confirmDemo?.let { seed ->
        AlertDialog(
            onDismissRequest = { confirmDemo = null },
            text = { Text(stringResource(if (seed) R.string.staff_demo_seed_confirm else R.string.staff_demo_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDemo = null
                    scope.launch {
                        container.demoDataSeeder.clear()
                        if (seed) container.demoDataSeeder.seed()
                    }
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { confirmDemo = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SummaryCard(emoji: String, value: Int, labelRes: Int, colour: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colour),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium, maxLines = 2)
        }
    }
}
