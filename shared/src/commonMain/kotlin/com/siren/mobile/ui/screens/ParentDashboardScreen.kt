package com.siren.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.model.UserProfile
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
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space

@Composable
fun ParentDashboardScreen(
    user: UserProfile,
    children: List<LinkedPerson>,
    pendingRequests: Int,
    online: Boolean,
    loading: Boolean,
    schoolHotline: String = "(082) 227-4410",
    onLinkStudent: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenDemo: () -> Unit,
    onCall: (String) -> Unit,
) {
    val status = SirenTheme.status
    val needsHelp = children.count { it.status == ResponseStatus.NEEDS_HELP }
    val waiting = children.count { it.status == ResponseStatus.NO_RESPONSE }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Layout.screenPadding,
            end = Layout.screenPadding,
            bottom = Space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            DashboardHeader(
                initials = user.initials,
                eyebrow = "Parent / Guardian",
                name = user.name.ifBlank { "Guardian" },
                photo = user.photo,
                trailing = {
                    IconButton(onClick = onLinkStudent) {
                        Icon(Icons.Filled.FamilyRestroom, contentDescription = "Link a student")
                    }
                },
            )
        }

        if (!online) item { OfflineBanner() }

        if (pendingRequests > 0) {
            item {
                InfoBanner(
                    text = if (pendingRequests == 1) {
                        "Waiting for a student to confirm you are their parent or guardian."
                    } else {
                        "$pendingRequests link requests are waiting for the students to confirm."
                    },
                    icon = Icons.Filled.HourglassTop,
                    tone = BannerTone.Warn,
                )
            }
        }

        item {
            val headline = when {
                children.isEmpty() -> "No children linked yet"
                needsHelp > 0 -> if (needsHelp == 1) "1 child needs help" else "$needsHelp children need help"
                waiting > 0 -> "Waiting on $waiting response${if (waiting == 1) "" else "s"}"
                else -> "All children are safe"
            }
            val detail = when {
                children.isEmpty() -> "Add a linking code to start following a student."
                needsHelp > 0 -> "Contact the school immediately."
                online -> "Campus sensors normal"
                else -> "Offline · showing last known status"
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Layout.cardLarge))
                    .background(if (needsHelp > 0) status.dangerFill else status.hero)
                    .padding(Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (needsHelp > 0) Icons.Filled.Warning else Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    Modifier.size(36.dp),
                    tint = if (needsHelp > 0) androidx.compose.ui.graphics.Color.White else status.safeFill,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    Text(
                        headline,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (needsHelp > 0) androidx.compose.ui.graphics.Color.White else status.onHero,
                    )
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (needsHelp > 0) {
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
                        } else {
                            status.onHeroMuted
                        },
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "Linked children",
                actionLabel = "Link",
                onAction = onLinkStudent,
            )
        }

        when {
            loading -> item { SkeletonList(rows = 2) }

            children.isEmpty() -> item {
                EmptyState(
                    title = "No children linked",
                    subtitle = "Ask your child for the 6-character code in their Settings, then tap Link.",
                    icon = Icons.Filled.FamilyRestroom,
                )
            }

            else -> item {
                ListGroup {
                    children.forEachIndexed { i, child ->
                        RosterRow(child)
                        if (i < children.lastIndex) RowDivider()
                    }
                }
            }
        }

        item {
            ListGroup {
                TileRow(
                    icon = Icons.Filled.Call,
                    title = "School emergency line",
                    subtitle = "$schoolHotline · 24/7 hotline",
                    onClick = { onCall(schoolHotline) },
                )
                RowDivider()

                TileRow(
                    icon = Icons.Filled.MenuBook,
                    title = "Safety guide",
                    subtitle = "Drop, cover, hold and 27 more",
                    onClick = onOpenGuide,
                )
                RowDivider()

                TileRow(
                    icon = Icons.Filled.Science,
                    title = "Demo mode",
                    subtitle = "Simulate an alert level",
                    onClick = onOpenDemo,
                    badge = "DEV",
                )
            }
        }
    }
}

@Composable
private fun TileRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                badge?.let {
                    Pill(
                        it,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primaryContainer,
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
