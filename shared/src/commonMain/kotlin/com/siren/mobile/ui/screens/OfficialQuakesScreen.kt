package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siren.mobile.data.QuakeFeed
import com.siren.mobile.model.Quake
import com.siren.mobile.model.SensorNodes
import com.siren.mobile.ui.components.BannerTone
import com.siren.mobile.ui.components.EmptyState
import com.siren.mobile.ui.components.ErrorState
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.ListRow
import com.siren.mobile.ui.components.RowDivider
import com.siren.mobile.ui.components.SkeletonList
import com.siren.mobile.ui.components.sourceText
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asKm
import com.siren.mobile.util.distanceKm
import com.siren.mobile.util.tabular
import com.siren.mobile.util.toFixed

@Composable
fun OfficialQuakesScreen(
    state: QuakeFeed.Recent,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val loaded = state as? QuakeFeed.Recent.Loaded

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
                title = "Recent earthquakes",
                onBack = onBack,
                trailing = {
                    if (state is QuakeFeed.Recent.Loading || loaded?.refreshing == true) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
        }

        item {
            // The label is not optional. These catalogues are not PHIVOLCS and must never be
            // mistaken for it, and their limits are exactly what a panel will ask about.
            InfoBanner(
                "Magnitude 3 and above in and around the Philippines over the past 7 days, " +
                    "from ${loaded?.catalog?.fullName ?: "EMSC, or USGS as a fallback"}. " +
                    "This is not PHIVOLCS data. Global networks run minutes behind, can miss " +
                    "small local events, may differ from PHIVOLCS by a tenth or two, and revise " +
                    "their figures after publication.",
                Icons.Filled.Info,
                tone = BannerTone.Neutral,
            )
        }

        when (state) {
            QuakeFeed.Recent.Idle, QuakeFeed.Recent.Loading -> item { SkeletonList(rows = 6) }

            QuakeFeed.Recent.Failed -> item {
                ErrorState(
                    title = "Couldn't reach EMSC or USGS",
                    subtitle = "Check the connection and try again. SIREN's own alerts do not depend on this.",
                    onRetry = onRefresh,
                )
            }

            is QuakeFeed.Recent.Loaded -> if (state.quakes.isEmpty()) {
                item {
                    EmptyState(
                        title = "No magnitude 3+ events this week",
                        subtitle = "Nothing in the catalogue for the Philippine area.",
                        icon = Icons.Filled.Public,
                    )
                }
            } else {
                item {
                    ListGroup {
                        state.quakes.forEachIndexed { i, quake ->
                            QuakeRow(quake)
                            if (i < state.quakes.lastIndex) RowDivider()
                        }
                    }
                }
                item {
                    Text(
                        "Updated ${DateFmt.clock(state.fetchedAt)} · distances are from the SIREN sensor in Bogo City.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { Box(Modifier.size(Space.s)) }
    }
}

@Composable
private fun QuakeRow(quake: Quake) {
    val node = SensorNodes.BOGO
    val km = distanceKm(node.lat, node.lng, quake.lat, quake.lng)
    val s = SirenTheme.status
    val (fg, bg) = when {
        quake.magnitude >= 6.0 -> s.onDangerContainer to s.dangerContainer
        quake.magnitude >= 4.5 -> s.onWarnContainer to s.warnContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceContainerHigh
    }

    ListRow(
        title = quake.region.ifBlank { "Philippine region" },
        subtitle = "${DateFmt.dateTime(quake.time)} · depth ${quake.depthKm.asKm()} · " +
            "${km.asKm()} away · ${quake.sourceText()}",
        leading = {
            Surface(
                shape = RoundedCornerShape(Layout.tile),
                color = bg,
                modifier = Modifier.size(48.dp),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("M", style = MaterialTheme.typography.labelSmall, color = fg)
                    Text(
                        quake.magnitude.toFixed(1),
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        color = fg,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    )
}
