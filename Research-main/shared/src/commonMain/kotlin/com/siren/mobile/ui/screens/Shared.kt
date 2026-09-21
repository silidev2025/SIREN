package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@Composable
fun RosterRow(person: LinkedPerson, modifier: Modifier = Modifier) {
    val detail = when (person.status) {
        ResponseStatus.SAFE -> person.respondedAt?.let { "Confirmed ${DateFmt.clock(it)}" } ?: "Confirmed safe"
        ResponseStatus.NEEDS_HELP -> person.respondedAt?.let { "Asked for help ${DateFmt.clock(it)}" } ?: "Asked for help"
        ResponseStatus.NO_RESPONSE -> person.klass.ifBlank { "Waiting for a response" }
    }
    ListRow(
        modifier = modifier,
        title = person.name.ifBlank { "Student" },
        subtitle = detail,
        leading = { Avatar(person.initials, size = 40.dp, photo = person.photo) },
        trailing = { StatusChip(person.status) },
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
