package th.ac.kmutnb.prachin.map.ui.khaoyai.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.AccountForm
import th.ac.kmutnb.prachin.map.data.model.AccountProblem
import th.ac.kmutnb.prachin.map.data.model.Permission
import th.ac.kmutnb.prachin.map.data.model.UserAccount
import th.ac.kmutnb.prachin.map.data.model.UserRole
import th.ac.kmutnb.prachin.map.data.repository.RegisterResult
import th.ac.kmutnb.prachin.map.ui.khaoyai.KhaoYaiTopBar
import th.ac.kmutnb.prachin.map.ui.khaoyai.Pill
import th.ac.kmutnb.prachin.map.ui.khaoyai.auth.AccountFields
import th.ac.kmutnb.prachin.map.ui.khaoyai.auth.PasswordField

/** Every account, filtered by role and search (§2.3.3.2); tap to edit, + to add. */
@Composable
fun UserListScreen(onBack: () -> Unit, onOpen: (String?) -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val accounts by container.accountRepository.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    var role by rememberSaveable { mutableStateOf<UserRole?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = accounts.filter { a ->
        (role == null || a.role == role) && (query.isBlank() || listOf(a.fullName, a.username, a.memberId, a.email).any { it.contains(query.trim(), true) })
    }

    Scaffold(
        topBar = { KhaoYaiTopBar(title = stringResource(R.string.users_title), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpen(null) },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.users_add)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.users_search)) },
                    shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = role == null, onClick = { role = null }, label = { Text(stringResource(R.string.filter_all)) })
                    UserRole.entries.forEach { r ->
                        FilterChip(selected = role == r, onClick = { role = r }, label = { Text("${stringResource(r.labelRes)} (${accounts.count { it.role == r }})") })
                    }
                }
            }
            items(shown, key = { it.id }) { a ->
                Card(
                    onClick = { onOpen(a.id) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(a.fullName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Pill(stringResource(a.role.labelRes), MaterialTheme.colorScheme.primary)
                            if (!a.isActive) Pill(stringResource(R.string.users_disabled), MaterialTheme.colorScheme.error)
                            if (a.isDemo) Pill(stringResource(R.string.demo_badge), MaterialTheme.colorScheme.tertiary)
                        }
                        Text("${a.memberId} · ${a.username} · ${a.email}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** Add ([userId] null) or edit an account: profile, role, officer permissions, access, password. */
@Composable
fun UserEditScreen(userId: String?, currentUserId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val repo = container.accountRepository
    val accounts by repo.accounts.collectAsStateWithLifecycle(initialValue = null)
    val existing: UserAccount? = accounts?.firstOrNull { it.id == userId }
    val scope = rememberCoroutineScope()

    var form by rememberSaveable { mutableStateOf(AccountForm(nationality = "ไทย")) }
    var role by rememberSaveable { mutableStateOf(UserRole.TOURIST) }
    var permissions by rememberSaveable { mutableStateOf(Permission.entries.toSet()) }
    var active by rememberSaveable { mutableStateOf(true) }
    var loaded by rememberSaveable { mutableStateOf(userId == null) }
    var problems by remember { mutableStateOf<List<AccountProblem>>(emptyList()) }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(existing) {
        if (!loaded && existing != null) {
            form = AccountForm(existing.username, "", "", existing.fullName, existing.phone, existing.email, existing.nationality)
            role = existing.role
            permissions = existing.permissions
            active = existing.isActive
            loaded = true
        }
    }
    val isSelf = userId == currentUserId

    Column(Modifier.fillMaxSize().imePadding()) {
        KhaoYaiTopBar(title = stringResource(if (userId == null) R.string.users_add else R.string.users_edit), onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            existing?.let { Text("${it.memberId} · ${it.username}", fontWeight = FontWeight.Bold) }
            AccountFields(form, { form = it; problems = emptyList() }, problems, withUsername = userId == null, withPassword = userId == null)

            Text(stringResource(R.string.users_role), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                UserRole.entries.forEach { r ->
                    FilterChip(selected = role == r, enabled = !isSelf, onClick = { role = r }, label = { Text(stringResource(r.labelRes)) })
                }
            }
            if (role == UserRole.OFFICER) {
                Text(stringResource(R.string.users_permissions), style = MaterialTheme.typography.labelLarge)
                Permission.entries.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = p in permissions, onCheckedChange = { on -> permissions = if (on) permissions + p else permissions - p })
                        Text(stringResource(p.labelRes))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.users_active), modifier = Modifier.weight(1f))
                Switch(checked = active, enabled = !isSelf, onCheckedChange = { active = it })
            }
            message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }
            Button(onClick = {
                scope.launch {
                    if (userId == null) {
                        when (val r = repo.create(form, role, if (role == UserRole.OFFICER) permissions else emptySet())) {
                            is RegisterResult.Success -> onBack()
                            is RegisterResult.Invalid -> problems = r.problems
                        }
                    } else {
                        val p = repo.updateProfile(userId, form)
                        if (p.isEmpty()) {
                            repo.updateAccess(userId, role, if (role == UserRole.OFFICER) permissions else emptySet(), active)
                            message = R.string.admin_saved
                        } else {
                            problems = p
                        }
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }

            if (userId != null) {
                PasswordField(newPassword, { newPassword = it }, stringResource(R.string.users_new_password))
                OutlinedButton(onClick = {
                    scope.launch { message = if (repo.adminSetPassword(userId, newPassword)) R.string.users_password_set else R.string.account_error_password }
                }, enabled = newPassword.isNotEmpty()) { Text(stringResource(R.string.users_set_password)) }
                if (!isSelf) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.users_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmDelete && userId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.users_delete_confirm, existing?.fullName.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        container.trackingRepository.deleteFor(userId)
                        repo.delete(userId)
                        onBack()
                    }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Anyone editing their own profile and password. */
@Composable
fun ProfileEditScreen(userId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as MapApplication).container.accountRepository }
    val accounts by repo.accounts.collectAsStateWithLifecycle(initialValue = null)
    val me = accounts?.firstOrNull { it.id == userId }
    val scope = rememberCoroutineScope()
    var form by rememberSaveable { mutableStateOf<AccountForm?>(null) }
    var problems by remember { mutableStateOf<List<AccountProblem>>(emptyList()) }
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(me) {
        if (form == null && me != null) form = AccountForm(me.username, "", "", me.fullName, me.phone, me.email, me.nationality)
    }
    val f = form ?: return

    Column(Modifier.fillMaxSize().imePadding()) {
        KhaoYaiTopBar(title = stringResource(R.string.profile_edit), onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            me?.let { Text("${it.memberId} · ${it.username}", fontWeight = FontWeight.Bold) }
            AccountFields(f, { form = it; problems = emptyList() }, problems, withUsername = false, withPassword = false)
            Button(onClick = {
                scope.launch {
                    val p = repo.updateProfile(userId, f)
                    if (p.isEmpty()) message = R.string.admin_saved else problems = p
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
            Text(stringResource(R.string.profile_change_password), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            PasswordField(current, { current = it }, stringResource(R.string.profile_current_password))
            PasswordField(next, { next = it }, stringResource(R.string.forgot_new_password))
            OutlinedButton(onClick = {
                scope.launch { message = if (repo.changePassword(userId, current, next)) R.string.users_password_set else R.string.profile_password_failed }
            }, enabled = current.isNotEmpty() && next.isNotEmpty()) { Text(stringResource(R.string.profile_change_password)) }
            message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }
        }
    }
}
