package th.ac.kmutnb.prachin.map.ui.khaoyai.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.AccountRules
import th.ac.kmutnb.prachin.map.ui.khaoyai.FloatingBackButton
import th.ac.kmutnb.prachin.map.ui.khaoyai.ForestBrush
import th.ac.kmutnb.prachin.map.ui.khaoyai.HeroBanner
import th.ac.kmutnb.prachin.map.data.model.UserAccount
import th.ac.kmutnb.prachin.map.ui.khaoyai.LeafCorner
import th.ac.kmutnb.prachin.map.ui.khaoyai.MenuRow
import th.ac.kmutnb.prachin.map.ui.khaoyai.Pill
import th.ac.kmutnb.prachin.map.ui.theme.HornbillAmberLight

/** @param onOpenAdmin shown only to an admin; null hides the row. */
/**
 * The signed-in account (§2.3.1.1): member ID, contact details, and the things done to
 * it - edit, sign out. Rows passed as null are not shown, so staff and tourists share it.
 *
 * @param onOpenStaff staff browsing the tourist screens get their own screens back.
 */
@Composable
fun ProfileTab(
    account: UserAccount,
    contentPadding: PaddingValues,
    onOpenNavigation: (() -> Unit)?,
    onOpenAbout: () -> Unit,
    onOpenHistory: (() -> Unit)?,
    onOpenSos: (() -> Unit)?,
    onEditProfile: () -> Unit,
    onLogout: () -> Unit,
    onOpenStaff: (() -> Unit)? = null,
) {
    val name = account.fullName.ifBlank { account.username }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = contentPadding.calculateBottomPadding()),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(ForestBrush),
        ) {
            LeafCorner(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding(),
                size = 72.dp,
                alpha = 0.3f,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(AccountRules.initial(name), fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Text(name, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(account.role.labelRes), HornbillAmberLight)
                    Pill(account.memberId, Color.White)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.profile_contact, account.phone, account.email, account.nationality),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            if (onOpenStaff != null) {
                MenuRow(R.drawable.ic_shield, stringResource(R.string.profile_back_to_staff), onOpenStaff, tint = MaterialTheme.colorScheme.tertiary)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            MenuRow(R.drawable.ic_edit, stringResource(R.string.profile_edit), onEditProfile)
            onOpenNavigation?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MenuRow(R.drawable.ic_navigation, stringResource(R.string.profile_navigation), it)
            }
            onOpenSos?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MenuRow(R.drawable.ic_call, stringResource(R.string.sos_title), it, tint = MaterialTheme.colorScheme.error)
            }
            onOpenHistory?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MenuRow(R.drawable.ic_schedule, stringResource(R.string.history_title), it)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MenuRow(R.drawable.ic_info, stringResource(R.string.profile_about), onOpenAbout)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MenuRow(R.drawable.ic_logout, stringResource(R.string.profile_logout), onLogout, tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** What the park is, a few facts, and the numbers to call when something goes wrong. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun dial(number: String) {
        // ACTION_DIAL only fills in the number; it needs no permission and calls nothing.
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        } catch (_: ActivityNotFoundException) {
            // A tablet with no phone app: the number is on screen to read instead.
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        HeroBanner(height = 240.dp) {
            FloatingBackButton(
                onBack,
                Modifier
                    .statusBarsPadding()
                    .padding(12.dp),
            )
            Text(
                stringResource(R.string.home_hero_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
            )
        }
        Column(
            Modifier
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.about_intro), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact(stringResource(R.string.about_fact_founded_label), stringResource(R.string.about_fact_founded), Modifier.weight(1f))
                Fact(stringResource(R.string.about_fact_area_label), stringResource(R.string.about_fact_area), Modifier.weight(1f))
                Fact(stringResource(R.string.about_fact_peak_label), stringResource(R.string.about_fact_peak), Modifier.weight(1f))
            }
            Fact(stringResource(R.string.about_fact_hours_label), stringResource(R.string.about_fact_hours), Modifier.fillMaxWidth())
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🌳 " + stringResource(R.string.about_ecosystem_title), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.about_ecosystem_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                stringResource(R.string.about_emergency_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                EMERGENCY_NUMBERS.forEachIndexed { index, (label, number) ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuRow(
                        R.drawable.ic_call,
                        "${stringResource(label)}  $number",
                        onClick = { dial(number.filter(Char::isDigit)) },
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The park's own lines first - both answer emergencies around the clock according to
 * khaoyainationalpark.com (22 Nov 2026) - then the national hotlines.
 */
private val EMERGENCY_NUMBERS = listOf(
    R.string.about_call_visitor_center to "086-092-6529",
    R.string.about_call_park_office to "086-092-6527",
    R.string.about_call_medical to "1669",
    R.string.about_call_tourist_police to "1155",
    R.string.about_call_dnp to "1362",
)
