package th.ac.kmutnb.prachin.map.ui.khaoyai.staff

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.UserAccount
import th.ac.kmutnb.prachin.map.data.model.UserRole
import th.ac.kmutnb.prachin.map.ui.khaoyai.EmojiBadge
import th.ac.kmutnb.prachin.map.ui.khaoyai.KhaoYaiTopBar
import th.ac.kmutnb.prachin.map.ui.khaoyai.NotFound
import th.ac.kmutnb.prachin.map.ui.khaoyai.Pill
import th.ac.kmutnb.prachin.map.ui.khaoyai.SearchField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Find a tourist by typing (§2.3.2.2). No map of everyone: a park with ten thousand
 * visitors would bury it, and positions are not tracked live - only an SOS call carries one.
 */
@Composable
fun TouristSearchTab(onOpenTourist: (String) -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val accounts by container.accountRepository.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val tourists = accounts.filter { it.role == UserRole.TOURIST }
    var query by rememberSaveable { mutableStateOf("") }
    val found = if (query.isBlank()) emptyList() else tourists.filter { it.matches(query) }

    Column(Modifier.fillMaxSize()) {
        KhaoYaiTopBar(title = stringResource(R.string.tourists_title))
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.tourists_search),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    when {
                        query.isBlank() -> stringResource(R.string.tourists_type_to_search, tourists.size)
                        found.isEmpty() -> stringResource(R.string.tourists_empty)
                        found.size > MAX_SHOWN -> stringResource(R.string.tourists_more, MAX_SHOWN, found.size)
                        else -> stringResource(R.string.tourists_count, found.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(found.take(MAX_SHOWN), key = { it.id }) { t ->
                Card(
                    onClick = { onOpenTourist(t.id) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge("🧑", size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.fullName, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.tourists_row, t.memberId, t.nationality, t.phone),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (t.isDemo) Pill(stringResource(R.string.demo_badge), MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

/**
 * One tourist: who, how to reach them, and their calls for help. Officers look and call;
 * only an administrator changes the account (§2.3.3.2) - [onEdit] is null for everyone else.
 */
@Composable
fun TouristDetailScreen(userId: String, onBack: () -> Unit, onEdit: (() -> Unit)?) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val accounts by container.accountRepository.accounts.collectAsStateWithLifecycle(initialValue = null)
    val reports by container.sosRepository.reports.collectAsStateWithLifecycle(initialValue = emptyList())
    val account = accounts?.firstOrNull { it.id == userId }
    if (accounts != null && account == null) {
        NotFound(stringResource(R.string.tourists_not_found), onBack)
        return
    }
    account ?: return
    val calls = reports.filter { it.userId == userId }

    Column(Modifier.fillMaxSize()) {
        KhaoYaiTopBar(title = account.fullName, onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EmojiBadge("🧑", size = 52.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(account.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.tourists_detail_ids, account.memberId, account.username), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(stringResource(R.string.tourists_detail_contact, account.phone, account.email, account.nationality), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { dial(context, account) }) {
                                Icon(painterResource(R.drawable.ic_call), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.tourists_call))
                            }
                            if (onEdit != null) {
                                OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.tourists_edit)) }
                            }
                        }
                        if (onEdit == null) {
                            Text(stringResource(R.string.tourists_view_only), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.tourists_detail_calls, calls.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            items(calls, key = { it.id }) { c ->
                Text(
                    "${c.category.emoji} ${stringResource(c.category.labelRes)} · ${stringResource(c.status.labelRes)} · ${TIME.format(Date(c.createdAt))}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun dial(context: android.content.Context, account: UserAccount) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${account.phone}"))) }
}

/** Typing narrows quickly; drawing thousands of rows for one letter helps no one. */
private const val MAX_SHOWN = 100
private val TIME = SimpleDateFormat("d/M/yyyy HH:mm", Locale.getDefault())
