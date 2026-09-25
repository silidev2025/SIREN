package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PersonAdd
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

@Composable
fun LoginScreen(
    loading: Boolean,
    error: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.isNotBlank()

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

        SecondaryButton(
            text = "Create an account",
            onClick = onCreateAccount,
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
