package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import com.siren.mobile.model.Role
import com.siren.mobile.ui.components.BannerTone
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.SirenField
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Space

private const val MIN_PASSWORD = 6
private const val CODE_LENGTH = 6

enum class SignUpMethod(val label: String) { EMAIL("Email"), PHONE("Phone") }

@Composable
fun SignUpScreen(
    role: Role,
    loading: Boolean,
    error: String?,
    phoneSupported: Boolean,
    codeSent: Boolean,
    onSignUp: (name: String, email: String, password: String, phone: String) -> Unit,
    onSendCode: (name: String, phone: String) -> Unit,
    onVerifyCode: (name: String, code: String) -> Unit,
    onCancelPhone: () -> Unit,
    onBack: () -> Unit,
) {
    var method by remember { mutableStateOf(SignUpMethod.EMAIL) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val tooShort = password.isNotEmpty() && password.length < MIN_PASSWORD
    val mismatch = confirm.isNotEmpty() && confirm != password

    // A mobile number is required on every route, not just the phone one. The emergency
    // SMS a student sends by tapping "I need help" goes to the numbers their guardians
    // registered here — an account without one is silently unreachable at the only moment
    // that matters, so it cannot be optional.
    val mobileDigits = phone.count { it.isDigit() }
    val mobileValid = mobileDigits >= 10
    val mobileTyped = phone.isNotBlank()

    val emailValid = name.isNotBlank() && email.isNotBlank() && mobileValid &&
        password.length >= MIN_PASSWORD && confirm == password

    val phoneValid = name.isNotBlank() && mobileValid

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenHeader(
            title = "Create your account",
            onBack = { if (codeSent) onCancelPhone() else onBack() },
        )

        Text(
            "Signing up as ${role.label}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (error != null) {
            InfoBanner(error, Icons.Filled.Warning, tone = BannerTone.Danger)
        }

        if (phoneSupported && !codeSent) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                SignUpMethod.entries.forEach { m ->
                    FilterChip(
                        selected = method == m,
                        onClick = { method = m },
                        label = { Text("Sign up with ${m.label}") },
                        shape = RoundedCornerShape(Layout.pill),
                    )
                }
            }
        }

        SirenField(
            value = name,
            onValueChange = { name = it },
            label = "Full name",
            leadingIcon = Icons.Filled.Person,
        )

        if (method == SignUpMethod.EMAIL || !phoneSupported) {
            SirenField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                leadingIcon = Icons.Filled.Mail,
                keyboardType = KeyboardType.Email,
            )
            SirenField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' } },
                label = "Mobile number",
                placeholder = "09XX XXX XXXX",
                leadingIcon = Icons.Filled.Smartphone,
                keyboardType = KeyboardType.Phone,
                isError = mobileTyped && !mobileValid,
                supportingText = if (mobileTyped && !mobileValid) {
                    "Enter a complete mobile number"
                } else {
                    "Used to reach you if someone you're linked to asks for help"
                },
            )
            SirenField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                leadingIcon = Icons.Filled.Lock,
                isPassword = true,
                isError = tooShort,
                supportingText = if (tooShort) {
                    "Use at least $MIN_PASSWORD characters"
                } else {
                    "At least $MIN_PASSWORD characters"
                },
            )
            SirenField(
                value = confirm,
                onValueChange = { confirm = it },
                label = "Confirm password",
                leadingIcon = Icons.Filled.Lock,
                isPassword = true,
                isError = mismatch,
                supportingText = if (mismatch) "Passwords do not match" else null,
            )
        } else {
            SirenField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' } },
                label = "Mobile number",
                placeholder = "09XX XXX XXXX",
                leadingIcon = Icons.Filled.Smartphone,
                keyboardType = KeyboardType.Phone,
                supportingText = "We'll text you a 6-digit code. Standard rates apply.",

                enabled = !codeSent,
            )

            if (codeSent) {
                SirenField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isDigit() }.take(CODE_LENGTH) },
                    label = "Verification code",
                    leadingIcon = Icons.Filled.Pin,
                    keyboardType = KeyboardType.NumberPassword,
                    supportingText = "Sent to $phone",
                )
            }
        }

        if (role == Role.STUDENT) {
            InfoBanner(
                "You'll get a 6-character linking code after signing up. Give it to your parent or guardian so they can ask to follow your safety status — you'll be asked to confirm it's really them.",
                Icons.Filled.Badge,
            )
        }

        when {
            method == SignUpMethod.PHONE && phoneSupported && codeSent -> {
                PrimaryButton(
                    text = "Verify and create account",
                    onClick = { onVerifyCode(name, code) },
                    enabled = code.length == CODE_LENGTH,
                    loading = loading,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onSendCode(name, phone) }, enabled = !loading) {
                        Text("Resend code")
                    }
                    TextButton(onClick = onCancelPhone, enabled = !loading) {
                        Text("Change number")
                    }
                }
            }

            method == SignUpMethod.PHONE && phoneSupported -> PrimaryButton(
                text = "Send code",
                onClick = { onSendCode(name, phone) },
                enabled = phoneValid,
                loading = loading,
            )

            else -> PrimaryButton(
                text = "Create account",
                onClick = { onSignUp(name, email, password, phone) },
                enabled = emailValid,
                loading = loading,
            )
        }

        Text(
            "Your role is saved now and can be changed later in Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(Modifier.padding(bottom = Space.xxl))
    }
}
