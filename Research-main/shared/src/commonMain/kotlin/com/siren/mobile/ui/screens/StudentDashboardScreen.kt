package com.siren.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactEmergency
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.model.SafetyResponse
import com.siren.mobile.model.UserProfile
import com.siren.mobile.platform.Platform
import com.siren.mobile.ui.components.BannerTone
import com.siren.mobile.ui.components.EmptyState
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.ListRow
import com.siren.mobile.ui.components.OfflineBanner
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.RowDivider
import com.siren.mobile.ui.components.SectionHeader
import com.siren.mobile.ui.components.SkeletonList
import com.siren.mobile.ui.components.StatusChip
import com.siren.mobile.ui.components.intensityBrush
import com.siren.mobile.ui.components.intensityColor
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asGSpaced
import com.siren.mobile.util.tabular

private const val ACTIVE_WINDOW_MS = 60L * 60L * 1000L

@Composable
fun StudentDashboardScreen(
    user: UserProfile,
    alerts: List<AlertRecord>,
    myResponses: Map<String, SafetyResponse>,
    guardians: List<LinkedPerson>,
    pendingGuardianRequests: Int,
    online: Boolean,
    loading: Boolean,
    onOpenHistory: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenGuardians: () -> Unit,
    onOpenAlert: (AlertRecord) -> Unit,
) {
    val latest = alerts.firstOrNull()
    val active = latest != null &&
        !latest.closed &&
        (Platform.services.nowMillis() - latest.detectedAt) < ACTIVE_WINDOW_MS

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Layout.screenPadding,
            end = Layout.screenPadding,
            bottom = Space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        item {
            DashboardHeader(
                initials = user.initials,
                eyebrow = listOfNotNull("Student", user.classId.ifBlank { null }).joinToString(" · "),
                name = user.name.ifBlank { "Student" },
                photo = user.photo,
                trailing = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Alert history")
                    }
                },
            )
        }

        if (!online) {
            item { OfflineBanner() }
        }

        if (pendingGuardianRequests > 0) {
            item {
                InfoBanner(
                    text = if (pendingGuardianRequests == 1) {
                        "Someone has asked to follow your safety status. Confirm it is really your parent or guardian."
                    } else {
                        "$pendingGuardianRequests people have asked to follow your safety status. Confirm who they are."
                    },
                    icon = Icons.Filled.PersonAdd,
                    tone = BannerTone.Warn,
                    modifier = Modifier.clickable(onClick = onOpenGuardians),
                )
            }
        }

        item {
            StatusPanel(
                latest = latest,
                active = active,
                online = online,
                myResponse = latest?.let { myResponses[it.id] },
                onRespond = { latest?.let(onOpenAlert) },
            )
        }

        if (active && guardians.isNotEmpty()) {
            item { SectionHeader(title = "Your guardians", actionLabel = "Manage", onAction = onOpenGuardians) }
            item {
                ListGroup {
                    guardians.forEachIndexed { i, person ->
                        RosterRow(person)
                        if (i < guardians.lastIndex) RowDivider()
                    }
                }
            }
        }

        item {
            ListGroup {
                ActionRow(Icons.Filled.History, "Alert history", alertCountLabel(alerts.size), onOpenHistory)
                RowDivider()
                ActionRow(
                    Icons.Filled.FamilyRestroom,
                    "Parents & guardians",
                    guardianLabel(guardians.size, pendingGuardianRequests),
                    onOpenGuardians,
                    badge = if (pendingGuardianRequests > 0) "$pendingGuardianRequests NEW" else null,
                )
                RowDivider()
                ActionRow(Icons.Filled.MenuBook, "Safety guide", "Drop, cover, hold and 27 more", onOpenGuide)
                RowDivider()
                ActionRow(Icons.Filled.ContactEmergency, "Emergency contacts", "Reachable without internet", onOpenContacts)
                RowDivider()
                ActionRow(Icons.Filled.Settings, "Settings", "Alerts, account, privacy", onOpenSettings)
            }
        }

        item {
            SectionHeader(
                title = "Recent alerts",
                actionLabel = if (alerts.isNotEmpty()) "See all" else null,
                onAction = if (alerts.isNotEmpty()) onOpenHistory else null,
            )
        }

        when {
            loading -> item { SkeletonList(rows = 3) }

            alerts.isEmpty() -> item {
                EmptyState(
                    title = "No events recorded",
                    subtitle = "Alerts from the campus sensor will appear here.",
                    icon = Icons.Filled.VerifiedUser,
                )
            }

            else -> item {
                ListGroup {
                    val recent = alerts.take(4)
                    recent.forEachIndexed { i, alert ->
                        AlertRow(alert, myResponses[alert.id]) { onOpenAlert(alert) }
                        if (i < recent.lastIndex) RowDivider()
                    }
                }
            }
        }
    }
}

private fun alertCountLabel(count: Int): String = when (count) {
    0 -> "No events yet"
    1 -> "1 recorded event"
    else -> "$count recorded events"
}

private fun guardianLabel(linked: Int, pending: Int): String = when {
    pending > 0 && linked > 0 -> "$linked linked · $pending waiting for you"
    pending > 0 -> if (pending == 1) "1 request waiting for you" else "$pending requests waiting for you"
    linked == 0 -> "Share your code to link a guardian"
    linked == 1 -> "1 guardian following you"
    else -> "$linked guardians following you"
}

@Composable
private fun StatusPanel(
    latest: AlertRecord?,
    active: Boolean,
    online: Boolean,
    myResponse: SafetyResponse?,
    onRespond: () -> Unit,
) {
    val status = SirenTheme.status
    val shape = RoundedCornerShape(Layout.cardLarge)

    val bgModifier = if (active && latest != null) {
        Modifier.background(intensityBrush(latest.intensity))
    } else {
        Modifier.background(status.hero)
    }
    val onColor = if (active) Color.White else status.onHero
    val onColorMuted = if (active) Color.White.copy(alpha = 0.82f) else status.onHeroMuted

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(bgModifier)
            .padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (active) "Active event" else "System status",
                style = MaterialTheme.typography.labelMedium,
                color = onColorMuted,
            )
            Pill(
                text = when {
                    !online -> "OFFLINE"
                    active -> "LIVE"
                    else -> "MONITORING"
                },
                fg = onColor,
                bg = Color.White.copy(alpha = 0.18f),
            )
        }

        if (active && latest != null) {
            Text(
                latest.intensity.severity.uppercase() + " · LEVEL " + latest.intensity.level,
                style = MaterialTheme.typography.labelLarge,
                color = onColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                latest.intensity.levelText,
                style = MaterialTheme.typography.displaySmall,
                color = onColor,
            )
            Text(
                latest.intensity.shaking,
                style = MaterialTheme.typography.titleSmall,
                color = onColor,
            )

            Text(
                "Peak ground acceleration ${latest.magnitudeG.asGSpaced(3)}",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = onColorMuted,
            )
            Text(
                "Detected ${DateFmt.clock(latest.detectedAt)} · ${latest.nodeId ?: latest.source.label}",
                style = MaterialTheme.typography.bodySmall,
                color = onColorMuted,
            )

            Spacer(Modifier.height(Space.xs))
            if (myResponse == null) {
                com.siren.mobile.ui.components.PrimaryButton(
                    text = "Confirm your status",
                    onClick = onRespond,
                    tone = com.siren.mobile.ui.components.ButtonTone.OnColor,
                    onColorContent = intensityColor(latest.intensity),
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(myResponse.status)
                    Text(
                        "Recorded ${DateFmt.clock(myResponse.respondedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onColorMuted,
                    )
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    Modifier.size(36.dp),
                    tint = status.safeFill,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    Text(
                        "All clear",
                        style = MaterialTheme.typography.headlineSmall,
                        color = onColor,
                    )
                    Text(
                        if (online) "No seismic activity · sensor online"
                        else "Showing last known status",
                        style = MaterialTheme.typography.bodySmall,
                        color = onColorMuted,
                    )
                }
            }
            Text(
                latest?.let { "Last reading ${it.intensity.levelText} · ${DateFmt.shortDateTime(it.detectedAt)}" }
                    ?: "Awaiting the first sensor report",
                style = MaterialTheme.typography.labelSmall,
                color = onColorMuted,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: String? = null,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leading = {
            Surface(
                shape = RoundedCornerShape(Layout.tile),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        trailing = badge?.let {
            {
                Pill(
                    it,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                )
            }
        },
    )
}

@Composable
private fun AlertRow(
    alert: AlertRecord,
    response: SafetyResponse?,
    onClick: () -> Unit,
) {
    val tint = intensityColor(alert.intensity)
    ListRow(
        title = "${alert.intensity.severity} · ${alert.intensity.levelText}",
        subtitle = buildString {
            append(DateFmt.shortDateTime(alert.detectedAt))
            response?.let {
                append(" · you replied ")
                append(if (it.status == ResponseStatus.SAFE) "Safe" else "Needs help")
            }
        },
        onClick = onClick,
        leading = {
            Surface(
                shape = RoundedCornerShape(Layout.tile),
                color = tint.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Warning, contentDescription = null, Modifier.size(20.dp), tint = tint)
                }
            }
        },
        trailing = {
            if (alert.source.name == "SIMULATED") {
                Pill(
                    "DEMO",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            } else {
                response?.let { StatusChip(it.status) }
            }
        },
    )
}
