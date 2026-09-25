package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.siren.mobile.resources.Res
import com.siren.mobile.resources.ic_siren_wave
import com.siren.mobile.ui.components.BannerTone
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.SecondaryButton
import com.siren.mobile.ui.components.SirenField
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Space
import org.jetbrains.compose.resources.painterResource

private const val LOGIN_CODE_LENGTH = 6

/**
 * @param phoneSupported shows the Email / Phone choice. Accounts made with "Sign up with
 *   Phone" have no password, so without it they had no way back in from this screen.
 */
@Composable
fun LoginScreen(
    loading: Boolean,
    error: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
    phoneSupported: Boolean = false,
    codeSent: Boolean = false,
    onSendCode: (phone: String) -> Unit = {},
    onVerifyCode: (code: String) -> Unit = {},
    onCancelPhone: () -> Unit = {},
) {
    var method by remember { mutableStateOf(SignUpMethod.EMAIL) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.isNotBlank()
    val usePhone = phoneSupported && method == SignUpMethod.PHONE
    val phoneValid = phone.count { it.isDigit() } >= 10

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        Box(Modifier.height(Space.xxxl))

        Icon(
            painter = painterResource(Res.drawable.ic_siren_wave),
            contentDescription = null,
            modifier = Modifier
                .widthIn(max = 168.dp)
                .height(84.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            "SIREN (Seismic Integrated Response & Emergency Notification)",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )

        Box(Modifier.height(Space.xs))

        if (error != null) {
            InfoBanner(error, Icons.Filled.Lock, tone = BannerTone.Danger)
        }

        if (phoneSupported && !codeSent) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                SignUpMethod.entries.forEach { m ->
                    FilterChip(
                        selected = method == m,
                        onClick = { method = m },
                        label = { Text("Sign in with ${m.label}") },
                        shape = RoundedCornerShape(Layout.pill),
                    )
                }
            }
        }

        if (usePhone) {
            SirenField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' } },
                label = "Mobile number",
                placeholder = "09XX XXX XXXX",
                leadingIcon = Icons.Filled.Smartphone,
                keyboardType = KeyboardType.Phone,
                supportingText = "The number you signed up with. We'll text you a 6-digit code.",
                enabled = !codeSent,
            )

            if (codeSent) {
                SirenField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isDigit() }.take(LOGIN_CODE_LENGTH) },
                    label = "Verification code",
                    leadingIcon = Icons.Filled.Pin,
                    keyboardType = KeyboardType.NumberPassword,
                    supportingText = "Sent to $phone",
                )
                PrimaryButton(
                    text = "Verify and sign in",
                    onClick = { onVerifyCode(code) },
                    enabled = code.length == LOGIN_CODE_LENGTH,
                    loading = loading,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onSendCode(phone) }, enabled = !loading) {
                        Text("Resend code")
                    }
                    TextButton(
                        onClick = {
                            code = ""
                            onCancelPhone()
                        },
                        enabled = !loading,
                    ) {
                        Text("Change number")
                    }
                }
            } else {
                PrimaryButton(
                    text = "Send code",
                    onClick = { onSendCode(phone) },
                    enabled = phoneValid,
                    loading = loading,
                )
            }
        } else {
            SirenField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                placeholder = "Enter your campus email",
                leadingIcon = Icons.Filled.Mail,
                keyboardType = KeyboardType.Email,
            )

            SirenField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                placeholder = "Enter your password",
                leadingIcon = Icons.Filled.Lock,
                isPassword = true,
            )

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { onForgotPassword(email) }) {
                    Text("Forgot password?")
                }
            }

            PrimaryButton(
                text = "Sign in",
                onClick = { onSignIn(email, password) },
                enabled = canSubmit,
                loading = loading,
            )
        }

        SecondaryButton(
            text = "Create an account",
            onClick = {
                onCancelPhone()
                onCreateAccount()
            },
            icon = Icons.Filled.PersonAdd,
        )

        Text(
            "During shaking: Drop, Cover, Hold On.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = Space.xxl),
        )
    }
}
