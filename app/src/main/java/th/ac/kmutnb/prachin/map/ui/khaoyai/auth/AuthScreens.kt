package th.ac.kmutnb.prachin.map.ui.khaoyai.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.ui.khaoyai.HeroBanner
import th.ac.kmutnb.prachin.map.ui.khaoyai.LeafCorner

/**
 * Sign in. Nothing is checked yet: a blank form signs in as a visitor and the username
 * "admin" signs in as admin (see `KhaoYaiAuth`).
 */
@Composable
fun LoginScreen(
    onSignIn: (username: String) -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthLayout(
        title = stringResource(R.string.login_title),
        subtitle = stringResource(R.string.login_subtitle),
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.login_username)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_person), contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.login_password),
        )
        TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.login_forgot))
        }
        PrimaryButton(text = stringResource(R.string.login_submit), onClick = { onSignIn(username) })
        Text(
            text = stringResource(R.string.login_demo_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.login_no_account), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onSignUp) {
                Text(stringResource(R.string.login_sign_up), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Sign-up form. shortcut: nothing is stored; it says so and returns to sign in. */
@Composable
fun SignUpScreen(onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && confirm != password
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val doneMessage = stringResource(R.string.signup_done)

    AuthLayout(
        title = stringResource(R.string.signup_title),
        subtitle = stringResource(R.string.signup_subtitle),
        snackbar = snackbar,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.signup_name)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_person), contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        EmailField(value = email, onValueChange = { email = it })
        PasswordField(value = password, onValueChange = { password = it }, label = stringResource(R.string.login_password))
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            label = stringResource(R.string.signup_confirm_password),
            error = if (mismatch) stringResource(R.string.signup_password_mismatch) else null,
        )
        PrimaryButton(
            text = stringResource(R.string.signup_submit),
            enabled = !mismatch,
            onClick = {
                scope.launch {
                    snackbar.showSnackbar(doneMessage)
                    onDone()
                }
            },
        )
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.auth_back_to_login))
        }
    }
}

/** Password reset request. shortcut: no email is sent; it says so and returns to sign in. */
@Composable
fun ForgotPasswordScreen(onDone: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val doneMessage = stringResource(R.string.forgot_done)

    AuthLayout(
        title = stringResource(R.string.forgot_title),
        subtitle = stringResource(R.string.forgot_subtitle),
        snackbar = snackbar,
    ) {
        EmailField(value = email, onValueChange = { email = it })
        PrimaryButton(
            text = stringResource(R.string.forgot_submit),
            onClick = {
                scope.launch {
                    // Long enough to read before the screen goes away.
                    launch { snackbar.showSnackbar(doneMessage) }
                    delay(1_500)
                    onDone()
                }
            },
        )
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.auth_back_to_login))
        }
    }
}

// --------------------------------------------------------------------------------------

/** The landscape on top, the brand over it, and a card of form fields lapping onto it. */
@Composable
private fun AuthLayout(
    title: String,
    subtitle: String,
    snackbar: SnackbarHostState? = null,
    form: @Composable ColumnScope.() -> Unit,
) {
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
            HeroBanner(height = 300.dp) {
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
                        .padding(start = 24.dp, end = 24.dp, bottom = 52.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ky_brand_title),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.ky_brand_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                    )
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
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    form()
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
        snackbar?.let {
            SnackbarHost(
                it,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
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
private fun EmailField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.signup_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String? = null,
) {
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
                    contentDescription = stringResource(
                        if (visible) R.string.login_hide_password else R.string.login_show_password,
                    ),
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
