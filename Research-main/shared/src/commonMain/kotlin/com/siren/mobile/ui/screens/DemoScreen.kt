package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.AlertSource
import com.siren.mobile.model.Intensity
import com.siren.mobile.ui.components.BannerTone
import com.siren.mobile.ui.components.EmptyState
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.ListRow
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.RowDivider
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.components.intensityColor
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asG
import com.siren.mobile.util.tabular

@Composable
fun DemoScreen(
    alerts: List<AlertRecord>,
    onTrigger: (Intensity) -> Unit,
    onBack: () -> Unit,
) {
    val log = alerts.filter { it.source == AlertSource.SIMULATED }.take(10)

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
                title = "Demo mode",
                onBack = onBack,
                trailing = {
                    Pill("DEV ONLY", SirenTheme.status.onWarnContainer, SirenTheme.status.warnContainer)
                },
            )
        }

        item {
            InfoBanner(
                "Simulated events are tagged DEMO everywhere they appear. Use this for the defence demonstration, not during a real drill.",
                Icons.Filled.Warning,
                tone = BannerTone.Warn,
            )
        }

        item { SectionLabel("Trigger a simulated event") }

        item {
            ListGroup {
                TriggerRow(Intensity.GREEN, "${Intensity.GREEN.levelText} · notification only") { onTrigger(Intensity.GREEN) }
                RowDivider()
                TriggerRow(Intensity.YELLOW, "${Intensity.YELLOW.levelText} · full-screen alert") { onTrigger(Intensity.YELLOW) }
                RowDivider()
                TriggerRow(Intensity.RED, "${Intensity.RED.levelText} · alarm until dismissed") { onTrigger(Intensity.RED) }
            }
        }

        item { SectionLabel("Simulation log") }

        if (log.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing simulated yet",
                    subtitle = "Trigger a level above to see it recorded here.",
                    icon = Icons.Filled.Science,
                )
            }
        } else {
            item {
                ListGroup {
                    log.forEachIndexed { i, entry ->
                        ListRow(

                            title = "${entry.intensity.severity} · ${entry.magnitudeG.asG(3)}",
                            subtitle = "${DateFmt.date(entry.detectedAt)} · ${DateFmt.clockSeconds(entry.detectedAt)}",
                            leading = {
                                Box(
                                    Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(Layout.pill),
                                        color = intensityColor(entry.intensity),
                                        modifier = Modifier.size(12.dp),
                                    ) {}
                                }
                            },
                            trailing = {
                                Pill(
                                    "DEMO",
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                            },
                        )
                        if (i < log.lastIndex) RowDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TriggerRow(intensity: Intensity, subtitle: String, onClick: () -> Unit) {
    val tint = intensityColor(intensity)
    ListRow(
        title = "Trigger ${intensity.label} alert",
        subtitle = subtitle,
        onClick = onClick,
        leading = {
            Surface(
                shape = RoundedCornerShape(Layout.tile),
                color = tint.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayCircle, contentDescription = null, Modifier.size(22.dp), tint = tint)
                }
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "L${intensity.level}",
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = tint,
                )
            }
        },
    )
}
