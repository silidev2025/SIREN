package com.siren.mobile.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.ui.components.EmptyState
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.RowDivider
import com.siren.mobile.ui.components.RosterBreakdown
import com.siren.mobile.ui.components.SectionHeader
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asGSpaced
import com.siren.mobile.util.tabular

@Composable
fun LiveSafetyDashboardScreen(
    alert: AlertRecord,
    roster: List<LinkedPerson>,
    onCloseEvent: () -> Unit,
    onBack: () -> Unit,
) {
    val status = SirenTheme.status
    val responded = roster.count { it.status != ResponseStatus.NO_RESPONSE }
    val safe = roster.count { it.status == ResponseStatus.SAFE }
    val help = roster.count { it.status == ResponseStatus.NEEDS_HELP }
    val noReply = roster.count { it.status == ResponseStatus.NO_RESPONSE }
    val fraction = if (roster.isEmpty()) 0f else responded.toFloat() / roster.size
    val pct = (fraction * 100).toInt()

    val animated by animateFloatAsState(targetValue = fraction, animationSpec = tween(400), label = "progress")

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
            ScreenHeader(
                title = "Live safety check",
                onBack = onBack,
                trailing = {
                    if (alert.closed) {
                        Pill(
                            "CLOSED",
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    } else {
                        Pill("LIVE", Color.White, status.dangerFill)
                    }
                },
            )
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Layout.cardLarge))
                    .background(status.hero)
                    .padding(Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Text(
                    "${alert.intensity.levelText} · started ${DateFmt.clock(alert.detectedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = status.onHeroMuted,
                )
                Text(
                    "Peak ground acceleration ${alert.magnitudeG.asGSpaced(3)}",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = status.onHeroMuted,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text(
                        "$responded",
                        style = MaterialTheme.typography.displaySmall.tabular(),
                        color = status.onHero,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "of ${roster.size} responded · $pct%",
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = status.onHeroMuted,
                        modifier = Modifier.padding(bottom = Space.s),
                    )
                }
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(Layout.pill)),
                    color = status.safeFill,
                    trackColor = Color.White.copy(alpha = 0.18f),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }

        if (roster.isNotEmpty()) {
            item { RosterBreakdown(safe = safe, needsHelp = help, noReply = noReply) }
        }

        item { SectionHeader(title = "Sorted by urgency") }

        if (roster.isEmpty()) {
            item {
                EmptyState(
                    title = "No students in this roster",
                    subtitle = "Students appear once their account shares your classId.",
                    icon = Icons.Filled.Groups,
                )
            }
        } else {
            item {
                ListGroup {
                    roster.forEachIndexed { i, person ->
                        RosterRow(person)
                        if (i < roster.lastIndex) RowDivider()
                    }
                }
            }
        }

        if (!alert.closed) {
            item { PrimaryButton(text = "Close this event", onClick = onCloseEvent) }
            item {
                Text(
                    "Closing locks every response and marks the roll call complete.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
