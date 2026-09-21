package com.siren.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.Role
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Space

@Composable
fun RoleSelectionScreen(
    onContinue: (Role) -> Unit,
    onSignIn: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var selected by remember { mutableStateOf(Role.STUDENT) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenHeader(title = "Create your account", onBack = onBack)

        Text(
            "Choose your role. This decides which alerts you receive and what you can do during an event. You can change it later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(Modifier.padding(top = Space.xs))

        RoleCard(
            icon = Icons.Filled.School,
            title = "Student",
            blurb = "Receive alerts and confirm your safety status.",
            selected = selected == Role.STUDENT,
        ) { selected = Role.STUDENT }

        RoleCard(
            icon = Icons.Filled.Groups,
            title = "Teacher / School Admin",
            blurb = "Monitor your class roster in real time and close events.",
            selected = selected == Role.TEACHER,
        ) { selected = Role.TEACHER }

        RoleCard(
            icon = Icons.Filled.FamilyRestroom,
            title = "Parent / Guardian",
            blurb = "Follow the safety status of your linked children.",
            selected = selected == Role.PARENT,
        ) { selected = Role.PARENT }

        Box(Modifier.padding(top = Space.xs))

        PrimaryButton(
            text = "Continue as ${selected.label.substringBefore(" /")}",
            onClick = { onContinue(selected) },
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Already have an account?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onSignIn) { Text("Sign in") }
        }

        Text(
            "Your role is saved to your account when you sign up.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Space.xl),
        )
    }
}

@Composable
private fun RoleCard(
    icon: ImageVector,
    title: String,
    blurb: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,

        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
                this.role = SemanticsRole.RadioButton
            },
        shape = RoundedCornerShape(Layout.card),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 2.dp else Layout.hairline,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            Modifier.padding(Space.l),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(Layout.field),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        Modifier.size(24.dp),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
