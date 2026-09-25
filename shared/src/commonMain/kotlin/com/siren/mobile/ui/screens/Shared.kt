package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.ui.components.Avatar
import com.siren.mobile.ui.components.ListRow
import com.siren.mobile.ui.components.StatusChip
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.DateFmt

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Layout.touchTarget)
            .padding(vertical = Space.s, horizontal = if (onBack == null) Space.xs else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * @param onOpenLocation shown only when the student has shared a location for the current,
 *   still-open alert; the row then opens it on a map.
 */
@Composable
fun RosterRow(
    person: LinkedPerson,
    modifier: Modifier = Modifier,
    onOpenLocation: ((LinkedPerson) -> Unit)? = null,
) {
    val detail = when (person.status) {
        ResponseStatus.SAFE -> person.respondedAt?.let { "Confirmed ${DateFmt.clock(it)}" } ?: "Confirmed safe"
        ResponseStatus.NEEDS_HELP -> person.respondedAt?.let { "Asked for help ${DateFmt.clock(it)}" } ?: "Asked for help"
        ResponseStatus.NO_RESPONSE -> person.klass.ifBlank { "Waiting for a response" }
    }
    val location = person.location?.takeIf { onOpenLocation != null }
    ListRow(
        modifier = modifier,
        title = person.name.ifBlank { "Student" },
        subtitle = location?.let { "$detail · Location shared ${DateFmt.clock(it.locatedAt)}" } ?: detail,
        leading = { Avatar(person.initials, size = 40.dp, photo = person.photo) },
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (location != null) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = "Location shared",
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                StatusChip(person.status)
            }
        },
        onClick = if (location != null) ({ onOpenLocation?.invoke(person) }) else null,
    )
}

@Composable
fun DashboardHeader(
    initials: String,
    eyebrow: String,
    name: String,
    modifier: Modifier = Modifier,
    photo: String = "",
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(initials, photo = photo)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(
                eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }
        trailing?.invoke()
    }
}
