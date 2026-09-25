package com.siren.mobile.ui.screens

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
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import com.siren.mobile.model.LinkRequest
import com.siren.mobile.model.LinkRequestStatus
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.ui.components.Avatar
import com.siren.mobile.ui.components.BannerTone
import com.siren.mobile.ui.components.EmptyState
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.ListRow
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.RowDivider
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.components.SecondaryButton
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.tabular

@Composable
fun GuardiansScreen(
    shortCode: String,
    guardians: List<LinkedPerson>,
    requests: List<LinkRequest>,
    working: Boolean,
    eventActive: Boolean,
    onRespond: (requestId: String, approve: Boolean) -> Unit,
    onRevoke: (parentUid: String) -> Unit,
    onBack: () -> Unit,
) {
    var revoking by remember { mutableStateOf<LinkedPerson?>(null) }
    val pending = requests.filter { it.status == LinkRequestStatus.PENDING }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Layout.screenPadding,
            end = Layout.screenPadding,
            bottom = Space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item { ScreenHeader(title = "Parents & guardians", onBack = onBack) }

        if (shortCode.isNotBlank()) {
            item {
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
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Space.xxs),
                        ) {
                            Text(
                                "Your linking code",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                shortCode,
                                style = MaterialTheme.typography.headlineSmall.tabular(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 4.sp,
                            )
                        }
                    }
                }
            }
        }

        if (pending.isNotEmpty()) {
            item { SectionLabel("Waiting for your confirmation") }
            pending.forEach { req ->
                item(key = req.id) {
                    ListGroup {
                        ListRow(
                            title = req.parentName.ifBlank { "Someone" },
                            subtitle = buildString {
                                append("Says they are your parent or guardian")
                                if (req.parentContact.isNotBlank()) append(" · ${req.parentContact}")
                                append(" · asked ${DateFmt.shortDateTime(req.requestedAt)}")
                            },
                            leading = {
                                Surface(
                                    shape = RoundedCornerShape(Layout.tile),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.PersonAdd,
                                            contentDescription = null,
                                            Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            },
                        )
                        Column(
                            Modifier.padding(
                                start = Space.l,
                                end = Space.l,
                                bottom = Space.l,
                            ),
                            verticalArrangement = Arrangement.spacedBy(Space.s),
                        ) {
                            PrimaryButton(
                                text = "Yes, this is my guardian",
                                onClick = { onRespond(req.id, true) },
                                enabled = !working,
                            )
                            SecondaryButton(
                                text = "No, decline",
                                onClick = { onRespond(req.id, false) },
                                enabled = !working,
                            )
                        }
                    }
                }
            }
            item {
                InfoBanner(
                    "Only confirm someone you recognise. A confirmed guardian sees your safety status during every event.",
                    Icons.Filled.Shield,
                    tone = BannerTone.Warn,
                )
            }
        }

        item { SectionLabel("Following your safety status") }

        if (guardians.isEmpty()) {
            item {
                EmptyState(
                    title = "No guardians linked",
                    subtitle = "Give the code above to your parent or guardian. You'll be asked to confirm before they can follow you.",
                    icon = Icons.Filled.FamilyRestroom,
                )
            }
        } else {
            item {
                ListGroup {
                    guardians.forEachIndexed { i, person ->

                        if (eventActive) {
                            RosterRow(person)
                        } else {
                            ListRow(
                                title = person.name,
                                subtitle = "Parent / Guardian",
                                leading = { Avatar(person.initials, size = 40.dp, photo = person.photo) },
                            )
                        }
                        Box(Modifier.padding(start = Space.l, bottom = Space.xs)) {
                            TextButton(onClick = { revoking = person }) { Text("Remove") }
                        }
                        if (i < guardians.lastIndex) RowDivider()
                    }
                }
            }
            if (eventActive) {
                item {
                    InfoBanner(
                        "During an event this shows whether your guardian has confirmed they are safe.",
                        Icons.Filled.Shield,
                    )
                }
            }
        }
    }

    revoking?.let { person ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text("Remove ${person.name}?") },
            text = { Text("They will stop seeing your safety status. They can ask again with your code.") },
            confirmButton = {
                TextButton(onClick = {
                    onRevoke(person.uid)
                    revoking = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text("Cancel") } },
        )
    }
}
