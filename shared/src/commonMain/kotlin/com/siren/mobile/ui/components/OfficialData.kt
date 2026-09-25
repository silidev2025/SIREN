package com.siren.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.FeedCheck
import com.siren.mobile.model.Quake
import com.siren.mobile.model.SensorNodes
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asGSpaced
import com.siren.mobile.util.asKm
import com.siren.mobile.util.distanceKm
import com.siren.mobile.util.tabular
import com.siren.mobile.util.toFixed
import kotlin.math.abs

/** "M 4.4" — one decimal, as every catalogue publishes it. */
fun Quake.magnitudeText(): String = "Magnitude ${magnitude.toFixed(1)}"

/**
 * Within this distance of the sensor an event is named after the sensor's town instead of
 * the catalogue's region. EMSC's Flinn-Engdahl regions are broad: the sea just east of Bogo
 * falls in the one it calls "Leyte", so an M4 eleven kilometres from the school read as a
 * Leyte earthquake while PHIVOLCS listed it as "11 km N 58° E of City of Bogo".
 */
private const val NEAR_SENSOR_KM = 50.0

fun Quake.distanceFromSensorKm(): Double =
    SensorNodes.BOGO.let { distanceKm(it.lat, it.lng, lat, lng) }

fun Quake.isNearSensor(): Boolean = distanceFromSensorKm() <= NEAR_SENSOR_KM

/** "Near Bogo" close to the sensor, otherwise the catalogue's own region name. */
fun Quake.placeName(): String =
    if (isNearSensor()) "Near Bogo" else region.ifBlank { "Philippine region" }

/**
 * "11.10°N, 124.08°E" — two decimals, the precision PHIVOLCS publishes, so a row can be
 * matched against its bulletin by eye.
 */
fun Quake.coordinatesText(): String =
    "${abs(lat).toFixed(2)}°${if (lat >= 0) "N" else "S"}, " +
        "${abs(lng).toFixed(2)}°${if (lng >= 0) "E" else "W"}"

/** "EMSC · mb" or "EMSC · mb · agency PHIV" — where the number came from, always shown. */
fun Quake.sourceText(): String = listOfNotNull(
    catalog.label,
    magnitudeType.ifBlank { null },
    agency.takeIf { it.isNotBlank() && !it.equals(catalog.label, ignoreCase = true) }?.let { "agency $it" },
).joinToString(" · ")

/**
 * Magnitude and intensity side by side, each labelled with where it came from:
 * **magnitude from the official catalogue, intensity from the SIREN sensor**. The node
 * cannot measure magnitude — that is energy at the source and needs many stations — so the
 * magnitude slot stays empty until a catalogue has the event, rather than being filled with
 * anything the hardware cannot support.
 */
@Composable
fun OfficialDataCard(
    alert: AlertRecord,
    check: FeedCheck?,
    modifier: Modifier = Modifier,
) {
    val s = SirenTheme.status
    SirenCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel("Magnitude & intensity")
            when (check) {
                is FeedCheck.Confirmed -> Pill("CONFIRMED", s.safe, s.safeContainer, icon = Icons.Filled.Verified)
                is FeedCheck.NoMatch -> if (check.final) {
                    Pill("UNCONFIRMED", s.warn, s.warnContainer, icon = Icons.Filled.SearchOff)
                } else {
                    Pill(
                        "CHECKING",
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        icon = Icons.Filled.HourglassTop,
                    )
                }

                FeedCheck.NotApplicable -> Pill(
                    "DRILL",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    icon = Icons.Filled.Science,
                )

                else -> Unit
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(
                    (check as? FeedCheck.Confirmed)?.quake?.magnitudeText() ?: "Magnitude —",
                    style = MaterialTheme.typography.titleLarge.tabular(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    magnitudeCaption(check),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(
                    alert.peisText,
                    style = MaterialTheme.typography.titleLarge.tabular(),
                    fontWeight = FontWeight.Bold,
                    color = intensityColor(alert.intensity),
                )
                Text(
                    "SIREN sensor · ${alert.magnitudeG.asGSpaced(3)}",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (check is FeedCheck.Confirmed) {
            val q = check.quake
            Text(
                listOf(
                    q.placeName(),
                    "${check.distanceKm.asKm()} from the sensor",
                    "depth ${q.depthKm.asKm()}",
                    "origin ${DateFmt.clockSeconds(q.time)}",
                    q.coordinatesText(),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            "Magnitude comes from a global catalogue (EMSC, or USGS as a fallback) — not " +
                "PHIVOLCS — and can differ from PHIVOLCS by several tenths, especially in the " +
                "first hours. Intensity is the shaking the SIREN sensor measured at the school.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun magnitudeCaption(check: FeedCheck?): String = when (check) {
    is FeedCheck.Confirmed -> check.quake.sourceText()
    is FeedCheck.NoMatch ->
        if (check.final) "No matching event in EMSC or USGS"
        else "Not in EMSC or USGS yet — they run minutes behind"
    FeedCheck.Unavailable -> "Couldn't reach EMSC or USGS"
    FeedCheck.NotApplicable -> "Drills are not cross-checked"
    FeedCheck.Checking, null -> "Checking EMSC and USGS…"
}

/**
 * The compact form for a history row: the catalogue magnitude once confirmed, a warning
 * once a check has finally come up empty, nothing while it is still open.
 */
@Composable
fun FeedCheckTag(check: FeedCheck?) {
    val s = SirenTheme.status
    when (check) {
        is FeedCheck.Confirmed -> Pill(
            "${check.quake.catalog.label} M${check.quake.magnitude.toFixed(1)}",
            s.safe,
            s.safeContainer,
            icon = Icons.Filled.Public,
        )

        is FeedCheck.NoMatch -> if (check.final) Pill("Unconfirmed", s.warn, s.warnContainer)
        else -> Unit
    }
}
