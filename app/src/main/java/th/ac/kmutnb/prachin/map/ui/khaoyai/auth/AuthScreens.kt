package th.ac.kmutnb.prachin.map.ui.khaoyai.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.AccountForm
import th.ac.kmutnb.prachin.map.data.model.AccountProblem
import th.ac.kmutnb.prachin.map.data.model.UserAccount
import th.ac.kmutnb.prachin.map.data.repository.AccountRepository
import th.ac.kmutnb.prachin.map.data.repository.OtpRequest
import th.ac.kmutnb.prachin.map.data.repository.RegisterResult
import th.ac.kmutnb.prachin.map.data.repository.SignInResult
import th.ac.kmutnb.prachin.map.ui.khaoyai.HeroBanner
import th.ac.kmutnb.prachin.map.ui.khaoyai.LeafCorner

@Composable
private fun rememberAccounts(): AccountRepository {
    val context = LocalContext.current
    return remember { (context.applicationContext as MapApplication).container.accountRepository }
}

/** Sign in with a username and password (§2.3.1.1, §2.3.2.1, §2.3.3.1). */
@Composable
fun LoginScreen(
    onSignedIn: (UserAccount) -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val accounts = rememberAccounts()
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        busy = true
        scope.launch {
            when (val result = accounts.signIn(username, password)) {
                is SignInResult.Success -> onSignedIn(result.account)
                SignInResult.NoSuchUser -> error = R.string.login_error_no_user
                SignInResult.WrongPassword -> error = R.string.login_error_password
                SignInResult.Disabled -> error = R.string.login_error_disabled
            }
            busy = false
        }
    }

    AuthLayout(title = stringResource(R.string.login_title), subtitle = stringResource(R.string.login_subtitle)) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; error = null },
            label = { Text(stringResource(R.string.login_username)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_person), contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(password, { password = it; error = null }, stringResource(R.string.login_password))
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.login_forgot))
        }
        PrimaryButton(
            text = stringResource(R.string.login_submit),
            enabled = !busy && username.isNotBlank() && password.isNotBlank(),
            onClick = ::submit,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.login_no_account), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onSignUp) { Text(stringResource(R.string.login_sign_up), fontWeight = FontWeight.Bold) }
        }
        DemoAccounts { u, p ->
            username = u
            password = p
            error = null
        }
    }
}

/**
 * The built-in accounts, one tap to fill in. Here so the three roles can be tried before
 * real accounts exist; remove with the seeded accounts before release.
 */
@Composable
private fun DemoAccounts(onPick: (String, String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.login_demo_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onPick("tourist", "Tourist1234") }, label = { Text(stringResource(R.string.role_tourist)) })
                AssistChip(onClick = { onPick("officer", "Officer1234") }, label = { Text(stringResource(R.string.role_officer)) })
                AssistChip(onClick = { onPick("admin", "Admin1234") }, label = { Text(stringResource(R.string.role_admin)) })
            }
            Text(stringResource(R.string.login_demo_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Tourist registration with the six fields of §2.3.1.1; a Tourist ID is issued on success. */
@Composable
fun SignUpScreen(onRegistered: (UserAccount) -> Unit, onBack: () -> Unit) {
    val accounts = rememberAccounts()
    val scope = rememberCoroutineScope()
    var form by rememberSaveable { mutableStateOf(AccountForm(nationality = "ไทย")) }
    var problems by remember { mutableStateOf<List<AccountProblem>>(emptyList()) }
    var created by remember { mutableStateOf<UserAccount?>(null) }
    var busy by remember { mutableStateOf(false) }

    AuthLayout(title = stringResource(R.string.signup_title), subtitle = stringResource(R.string.signup_subtitle)) {
        AccountFields(form, { form = it; problems = emptyList() }, problems, withUsername = true, withPassword = true)
        PrimaryButton(
            text = stringResource(R.string.signup_submit),
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    when (val result = accounts.register(form)) {
                        is RegisterResult.Success -> created = result.account
                        is RegisterResult.Invalid -> problems = result.problems
                    }
                    busy = false
                }
            },
        )
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.auth_back_to_login))
        }
    }

    created?.let { account ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.signup_done_title)) },
            text = { Text(stringResource(R.string.signup_done_body, account.memberId)) },
            confirmButton = { TextButton(onClick = { onRegistered(account) }) { Text(stringResource(R.string.signup_start)) } },
        )
    }
}

/**
 * Forgotten password by OTP (§2.3.1.1, §2.3.2.1): request a code by email, enter it with
 * a new password. shortcut: no mail server yet, so the code is shown on screen.
 */
@Composable
fun ForgotPasswordScreen(onDone: () -> Unit) {
    val accounts = rememberAccounts()
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var request by remember { mutableStateOf<OtpRequest?>(null) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<Int?>(null) }
    var success by remember { mutableStateOf(false) }

    AuthLayout(title = stringResource(R.string.forgot_title), subtitle = stringResource(R.string.forgot_subtitle)) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.signup_email)) },
            singleLine = true,
            enabled = !requested,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!requested) {
            PrimaryButton(stringResource(R.string.forgot_submit), enabled = email.isNotBlank(), onClick = {
                scope.launch {
                    request = accounts.requestOtp(email)
                    requested = true
                }
            })
        } else {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.forgot_sent, request?.maskedEmail ?: email),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    request?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.forgot_demo_code, it.code), fontWeight = FontWeight.Bold)
                    }
                }
            }
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text(stringResource(R.string.forgot_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(password, { password = it }, stringResource(R.string.forgot_new_password))
            PasswordField(
                confirm, { confirm = it }, stringResource(R.string.signup_confirm_password),
                error = if (confirm.isNotEmpty() && confirm != password) stringResource(R.string.signup_password_mismatch) else null,
            )
            message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            PrimaryButton(
                stringResource(R.string.forgot_reset),
                enabled = code.length == 6 && password.isNotEmpty() && password == confirm,
                onClick = {
                    scope.launch {
                        when (accounts.resetWithOtp(email, code, password)) {
                            AccountRepository.ResetResult.OK -> success = true
                            AccountRepository.ResetResult.WRONG_CODE -> message = R.string.forgot_error_code
                            AccountRepository.ResetResult.EXPIRED -> message = R.string.forgot_error_expired
                            AccountRepository.ResetResult.WEAK_PASSWORD -> message = R.string.account_error_password
                        }
                    }
                },
            )
        }
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.auth_back_to_login))
        }
    }

    if (success) {
        AlertDialog(
            onDismissRequest = onDone,
            text = { Text(stringResource(R.string.forgot_done)) },
            confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.auth_back_to_login)) } },
        )
    }
}

// --------------------------------------------------------------------------------------

/**
 * The account form shared by sign-up, profile editing and the admin's user editor.
 * Problems from [AccountRules][th.ac.kmutnb.prachin.map.data.model.AccountRules] show
 * under the field they belong to.
 */
@Composable
fun AccountFields(
    form: AccountForm,
    onChange: (AccountForm) -> Unit,
    problems: List<AccountProblem>,
    withUsername: Boolean,
    withPassword: Boolean,
) {
    @Composable
    fun err(vararg which: AccountProblem): String? =
        which.firstOrNull { it in problems }?.let { stringResource(it.messageRes) }

    if (withUsername) {
        Field(form.username, { onChange(form.copy(username = it)) }, R.string.login_username, err(AccountProblem.USERNAME_FORMAT, AccountProblem.USERNAME_TAKEN))
    }
    if (withPassword) {
        PasswordField(form.password, { onChange(form.copy(password = it)) }, stringResource(R.string.login_password), err(AccountProblem.PASSWORD_WEAK))
        PasswordField(form.confirmPassword, { onChange(form.copy(confirmPassword = it)) }, stringResource(R.string.signup_confirm_password), err(AccountProblem.PASSWORD_MISMATCH))
    }
    Field(form.fullName, { onChange(form.copy(fullName = it)) }, R.string.signup_full_name, err(AccountProblem.NAME_MISSING))
    Field(form.phone, { onChange(form.copy(phone = it)) }, R.string.signup_phone, err(AccountProblem.PHONE_FORMAT), KeyboardType.Phone)
    Field(form.email, { onChange(form.copy(email = it)) }, R.string.signup_email, err(AccountProblem.EMAIL_FORMAT, AccountProblem.EMAIL_TAKEN), KeyboardType.Email)
    Field(form.nationality, { onChange(form.copy(nationality = it)) }, R.string.signup_nationality, err(AccountProblem.NATIONALITY_MISSING))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NATIONALITY_SUGGESTIONS.forEach { n ->
            FilterChip(selected = form.nationality == n, onClick = { onChange(form.copy(nationality = n)) }, label = { Text(n) })
        }
    }
}

private val NATIONALITY_SUGGESTIONS = listOf("ไทย", "จีน", "มาเลเซีย", "อเมริกัน", "อังกฤษ", "เยอรมัน", "ญี่ปุ่น")

@Composable
private fun Field(value: String, onChange: (String) -> Unit, labelRes: Int, error: String?, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The landscape on top, the brand over it, and a card of form fields lapping onto it. */
@Composable
private fun AuthLayout(title: String, subtitle: String, form: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            HeroBanner(height = 260.dp) {
                LeafCorner(
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp),
                    size = 64.dp,
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, end = 24.dp, bottom = 48.dp),
                ) {
                    Text(stringResource(R.string.ky_brand_title), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(stringResource(R.string.ky_brand_subtitle), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-32).dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
                    Spacer(Modifier.height(4.dp))
                    form()
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, error: String? = null) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painter = painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = stringResource(if (visible) R.string.login_hide_password else R.string.login_show_password),
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
