package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Smartphone
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
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.Role
import com.siren.mobile.model.UserProfile
import com.siren.mobile.ui.components.Avatar
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.SecondaryButton
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.components.SirenField
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Space

@Composable
fun EditProfileScreen(
    user: UserProfile,
    working: Boolean,
    photoPickerSupported: Boolean,
    onSave: (name: String, classId: String, phone: String) -> Unit,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember(user.uid) { mutableStateOf(user.name) }
    var classId by remember(user.uid) { mutableStateOf(user.classId) }
    var phone by remember(user.uid) { mutableStateOf(user.phone) }

    val changed = name.trim() != user.name || classId.trim() != user.classId || phone.trim() != user.phone
    val valid = name.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenHeader(title = "Edit profile", onBack = onBack)

        if (photoPickerSupported) {
            SectionLabel("Profile picture")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(user.initials, size = 72.dp, photo = user.photo)
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    SecondaryButton(
                        text = if (user.photo.isBlank()) "Choose a picture" else "Change picture",
                        onClick = onChangePhoto,
                        enabled = !working,
                        icon = Icons.Filled.PhotoCamera,
                    )
                    if (user.photo.isNotBlank()) {
                        TextButton(onClick = onRemovePhoto, enabled = !working) {
                            Text("Remove picture")
                        }
                    }
                }
            }
            Text(
                "Your picture is resized on this phone before it is saved, and appears wherever your name does — your adviser's roll call, and anyone linked to you.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel("Your details")

        SirenField(
            value = name,
            onValueChange = { name = it },
            label = "Full name",
            leadingIcon = Icons.Filled.Person,
            isError = name.isBlank(),
            supportingText = if (name.isBlank()) {
                "A name is required — it is what appears on the roll call"
            } else {
                null
            },
        )

        SirenField(
            value = classId,
            onValueChange = { classId = it },
            label = if (user.role == Role.TEACHER) "Class you advise" else "Class",
            placeholder = "e.g. Grade 11 - Rizal",
            leadingIcon = Icons.Filled.School,
            supportingText = when (user.role) {
                Role.TEACHER -> "Students you add with a linking code join this class."
                Role.STUDENT -> "Your adviser sets this when they add you to their class."
                Role.PARENT -> "Not used for guardian accounts."
            },
        )

        SirenField(
            value = phone,
            onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' } },
            label = "Mobile number",
            placeholder = "09XX XXX XXXX",
            leadingIcon = Icons.Filled.Smartphone,
            keyboardType = KeyboardType.Phone,
            supportingText = "Optional. Used so the school can reach you during an event.",
        )

        if (user.email.isNotBlank()) {
            SirenField(
                value = user.email,
                onValueChange = {},
                label = "Sign-in email",
                leadingIcon = Icons.Filled.Mail,
                enabled = false,
                supportingText = "Contact your adviser to change the email on your account.",
            )
        }

        PrimaryButton(
            text = "Save changes",
            onClick = { onSave(name, classId, phone) },
            enabled = valid && changed,
            loading = working,
        )

        InfoBanner(
            "Your name and class are visible to your adviser and to anyone linked to your account.",
            Icons.Filled.Info,
        )

        Text(
            "Signed in as ${user.role.label}.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(Modifier.padding(bottom = Space.xxl))
    }
}
