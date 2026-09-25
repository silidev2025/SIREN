package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.AlertSource
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.model.SafetyResponse
import com.siren.mobile.ui.components.BannerTone
import com.siren.mobile.ui.components.EmptyState
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.ListRow
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.RowDivider
import com.siren.mobile.ui.components.SkeletonList
import com.siren.mobile.ui.components.intensityColor
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asGSpaced
import com.siren.mobile.util.tabular

private enum class HistoryFilter(val label: String) {
    ALL("All events"),
    SENSOR("Sensor"),
    SIMULATION("Simulation"),
}

@Composable
fun HistoryScreen(
    alerts: List<AlertRecord>,
    myResponses: Map<String, SafetyResponse>,
    loading: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }

    val visible = alerts.filter {
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.SENSOR -> it.source == AlertSource.ESP32
            HistoryFilter.SIMULATION -> it.source == AlertSource.SIMULATED
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Layout.screenPadding,
            end = Layout.screenPadding,
            bottom = Space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item { ScreenHeader(title = "Alert history", onBack = onBack) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                HistoryFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) },
                        shape = RoundedCornerShape(Layout.pill),
                    )
                }
            }
        }

        when {
            loading -> item { SkeletonList(rows = 5) }

            visible.isEmpty() -> item {
                EmptyState(
                    title = if (alerts.isEmpty()) "No events recorded" else "Nothing in this filter",
                    subtitle = if (alerts.isEmpty()) {
                        "Alerts from the sensor or Demo Mode will appear here."
                    } else {
                        "Try a different filter."
                    },
                    icon = Icons.Filled.History,
                )
            }

            else -> {
                item {
                    ListGroup {
                        visible.forEachIndexed { i, alert ->
                            HistoryRow(alert, myResponses[alert.id])
                            if (i < visible.lastIndex) RowDivider()
                        }
                    }
                }
                item {
                    InfoBanner(
                        "Read-only record · retained for the study's evaluation period.",
                        Icons.Filled.Lock,
                        tone = BannerTone.Neutral,
                    )
                }
            }
        }

        item { Box(Modifier.size(Space.s)) }
    }
}

@Composable
private fun HistoryRow(alert: AlertRecord, response: SafetyResponse?) {
    val tint = intensityColor(alert.intensity)
    val status = SirenTheme.status

    ListRow(

        title = "${alert.intensity.severity} · ${alert.intensity.shaking}",
        subtitle = DateFmt.dateTime(alert.detectedAt),
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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    alert.intensity.levelText,
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    color = tint,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    alert.magnitudeG.asGSpaced(3),
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (response?.status) {
                    ResponseStatus.SAFE -> ResponseTag("Safe", Icons.Filled.CheckCircle, status.safe, status.safeContainer)
                    ResponseStatus.NEEDS_HELP -> ResponseTag("Helped", Icons.Filled.Sos, status.danger, status.dangerContainer)
                    else -> if (alert.source == AlertSource.SIMULATED) {
                        Pill(
                            "DEMO",
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    } else {
                        Text(
                            "No reply",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ResponseTag(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    fg: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
) {
    Pill(label, fg, bg, icon = icon)
}
