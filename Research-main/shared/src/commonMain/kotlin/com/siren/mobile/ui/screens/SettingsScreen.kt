package com.siren.mobile.ui.screens

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContactEmergency
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siren.mobile.model.Role
import com.siren.mobile.model.SirenSettings
import com.siren.mobile.model.UserProfile
import com.siren.mobile.platform.Platform
import com.siren.mobile.ui.components.Avatar
import com.siren.mobile.ui.components.BannerTone
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.ListRow
import com.siren.mobile.ui.components.RowDivider
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.tabular

@Composable
fun SettingsScreen(
    user: UserProfile,
    settings: SirenSettings,
    versionName: String,
    fullScreenAlertsAllowed: Boolean,
    notificationsAllowed: Boolean,
    onUpdateSettings: ((SirenSettings) -> SirenSettings) -> Unit,
    onOpenContacts: () -> Unit,
    onOpenGuide: () -> Unit,
    onEditProfile: () -> Unit,
    onChangeRole: (Role) -> Unit,
    onFixFullScreenAlerts: () -> Unit,
    onFixNotifications: () -> Unit,
    onSignOut: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var signOutDialog by remember { mutableStateOf(false) }
    var roleDialog by remember { mutableStateOf(false) }
    var pendingRole by remember { mutableStateOf<Role?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenHeader(title = "Settings", onBack = onBack)

        ListGroup {
            ListRow(
                title = user.name.ifBlank { "Unnamed" },
                subtitle = listOfNotNull(
                    user.role.label,
                    user.classId.ifBlank { null },
                    user.contact.ifBlank { null },
                ).joinToString(" · "),
                onClick = onEditProfile,
                leading = { Avatar(user.initials, photo = user.photo) },
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Edit profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        if (user.role == Role.STUDENT && user.shortCode.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(Layout.cardLarge),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(Space.l),
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Key,
                        contentDescription = null,
                        Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                        Text(
                            "Your linking code",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            user.shortCode,
                            style = MaterialTheme.typography.headlineSmall.tabular(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp,
                        )
                        Text(
                            "Give this to your parent or guardian, or to your adviser. You'll be asked to confirm before a guardian can follow your safety status.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        SectionLabel("Alerts")
        ListGroup {
            ToggleRow(
                icon = Icons.Filled.NotificationsActive,
                title = "Critical alerts",
                subtitle = "Sound even when the phone is silenced",
                checked = settings.criticalAlerts,
            ) { v -> onUpdateSettings { it.copy(criticalAlerts = v) } }
            RowDivider()
            ToggleRow(
                icon = Icons.Filled.Vibration,
                title = "Vibration",
                subtitle = "Escalates with intensity",
                checked = settings.vibration,
            ) { v -> onUpdateSettings { it.copy(vibration = v) } }
            if (Platform.services.directSmsSupported) {
                RowDivider()
                ToggleRow(
                    icon = Icons.Filled.Sms,
                    title = "Text my guardians for help",
                    subtitle = "Sends an SMS automatically when you tap \"I need help\"",
                    checked = settings.alertSmsEnabled,
                ) { v -> onUpdateSettings { it.copy(alertSmsEnabled = v) } }
            }
        }

        // Accounts created before a mobile number was required still have none, and a
        // guardian without one cannot be texted when the student they follow asks for help.
        // Nothing else in the app surfaces that, so it would fail silently at the worst
        // possible moment.
        if (user.phone.isBlank()) {
            InfoBanner(
                if (user.role == Role.STUDENT) {
                    "No mobile number is saved on your account. Add one so your school and " +
                        "guardians can reach you after an earthquake."
                } else {
                    "No mobile number is saved on your account. Without one, you will not " +
                        "receive the emergency text if someone you follow taps \"I need help\"."
                },
                Icons.Filled.Smartphone,
                tone = BannerTone.Danger,
            )
            ListGroup {
                NavRow(
                    Icons.Filled.Smartphone,
                    "Add your mobile number",
                    "Opens Edit profile",
                    onEditProfile,
                )
            }
        }

        if (!notificationsAllowed) {
            InfoBanner(
                "Notifications are switched off for SIREN. You will not be warned of an earthquake until you turn them back on.",
                Icons.Filled.NotificationsOff,
                tone = BannerTone.Danger,
            )
            ListGroup {
                NavRow(
                    Icons.Filled.NotificationsOff,
                    "Turn on notifications",
                    "Opens Android settings",
                    onFixNotifications,
                )
            }
        }

        if (!fullScreenAlertsAllowed) {
            InfoBanner(
                "Full-screen alerts are blocked, so a severe event will sound the alarm without taking over the screen. The alarm's notification buttons still work.",
                Icons.Filled.Warning,
                tone = BannerTone.Warn,
            )
            ListGroup {
                NavRow(
                    Icons.Filled.Fullscreen,
                    "Allow full-screen alerts",
                    "Opens Android settings",
                    onFixFullScreenAlerts,
                )
            }
        }

        SectionLabel("Account")
        ListGroup {
            NavRow(
                Icons.Filled.ManageAccounts,
                "Edit profile",
                "Name, class and mobile number",
                onEditProfile,
            )
            RowDivider()
            NavRow(
                Icons.Filled.SwapHoriz,
                "Switch role",
                "Currently ${user.role.label}",
                { roleDialog = true },
            )
            RowDivider()
            NavRow(
                Icons.Filled.ContactEmergency,
                "Emergency contacts",
                "${settings.contacts.size} saved",
                onOpenContacts,
            )
            RowDivider()
            NavRow(
                Icons.Filled.MenuBook,
                "Safety guide",
                "Drop, cover, hold and 27 more",
                onOpenGuide,
            )
            RowDivider()
            NavRow(Icons.Filled.PrivacyTip, "Privacy & data", "How your responses are stored") {}
            RowDivider()
            NavRow(Icons.Filled.Info, "About SIREN", "Version $versionName") {}
        }

        ListGroup {
            ListRow(
                title = "Sign out",
                onClick = { signOutDialog = true },
                leading = {
                    Icon(
                        Icons.Filled.Logout,
                        contentDescription = null,
                        Modifier.size(24.dp),
                        tint = SirenTheme.status.danger,
                    )
                },
            )
        }

        Text(
            "Seismic Integrated Response and Emergency Notification\nCity of Bogo Senior High School · Practical Research 2",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Box(Modifier.padding(bottom = Space.xxl))
    }

    if (signOutDialog) {
        AlertDialog(
            onDismissRequest = { signOutDialog = false },
            title = { Text("Sign out?") },
            text = { Text("You'll stop receiving alerts on this device until you sign back in.") },
            confirmButton = {
                TextButton(onClick = {
                    signOutDialog = false
                    onSignOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { signOutDialog = false }) { Text("Cancel") } },
        )
    }

    if (roleDialog) {
        AlertDialog(
            onDismissRequest = { roleDialog = false },
            title = { Text("Switch role") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text("Pick the role this account should use.")
                    Role.entries.forEach { r ->
                        ListRow(
                            title = r.label,
                            subtitle = r.blurb,
                            onClick = {
                                roleDialog = false
                                if (r != user.role) pendingRole = r
                            },
                            trailing = {
                                if (r == user.role) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Current role",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { roleDialog = false }) { Text("Cancel") } },
        )
    }

    pendingRole?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRole = null },
            title = { Text("Switch to ${target.label}?") },
            text = {
                Text(
                    when (user.role) {
                        Role.PARENT ->
                            "Your linked children stay linked, but you'll stop seeing their safety status until you switch back."

                        Role.TEACHER ->
                            "You'll lose the roll call for your class until you switch back. Students stay assigned to it."

                        Role.STUDENT ->
                            "Your linking code stays valid, but guardians will not see a safety status from you while you are not a student."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onChangeRole(target)
                    pendingRole = null
                }) { Text("Switch") }
            },
            dismissButton = { TextButton(onClick = { pendingRole = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        leading = {
            Icon(
                icon,
                contentDescription = null,
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leading = {
            Icon(
                icon,
                contentDescription = null,
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
