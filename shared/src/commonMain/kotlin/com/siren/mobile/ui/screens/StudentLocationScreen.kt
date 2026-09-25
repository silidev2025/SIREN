package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.ui.components.BannerTone
import com.siren.mobile.ui.components.EmptyState
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.ListRow
import com.siren.mobile.ui.components.OsmStaticMap
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.StatusChip
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asAccuracy
import com.siren.mobile.util.tabular
import com.siren.mobile.util.toFixed

/**
 * Where one student is, for their confirmed guardians and their adviser — the only two
 * roles the repository ever loads a location for. The location disappears from here the
 * moment the event is closed or the student stops sharing.
 */
@Composable
fun StudentLocationScreen(
    person: LinkedPerson?,
    onOpenMaps: (lat: Double, lng: Double, label: String) -> Unit,
    onBack: () -> Unit,
) {
    val location = person?.location

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenHeader(
            title = person?.name?.ifBlank { null } ?: "Student location",
            onBack = onBack,
            trailing = { person?.let { StatusChip(it.status) } },
        )

        if (person == null || location == null) {
            EmptyState(
                title = "Location no longer shared",
                subtitle = "It is only visible while an alert is active, and the student can stop sharing at any time.",
                icon = Icons.Filled.LocationOff,
            )
            return@Column
        }

        OsmStaticMap(
            lat = location.lat,
            lng = location.lng,
            accuracyM = location.accuracyM,
            modifier = Modifier.fillMaxWidth().height(280.dp),
        )

        ListGroup {
            ListRow(
                title = "Shared ${DateFmt.clock(location.locatedAt)}",
                subtitle = "Accurate to ${location.accuracyM.asAccuracy()} · " +
                    "${location.lat.toFixed(5)}, ${location.lng.toFixed(5)}",
            )
        }

        PrimaryButton(
            text = "Open in Maps",
            onClick = { onOpenMaps(location.lat, location.lng, person.name.ifBlank { "Student" }) },
            icon = Icons.Filled.Map,
        )

        InfoBanner(
            "Shared by the student for this alert only, from their phone's own location. " +
                "Visible to their confirmed guardians and their adviser, and removed when the " +
                "event is closed.",
            Icons.Filled.PrivacyTip,
            tone = BannerTone.Neutral,
        )

        Text(
            "Position taken ${DateFmt.dateTime(location.locatedAt)}",
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Space.xxl),
        )
    }
}
