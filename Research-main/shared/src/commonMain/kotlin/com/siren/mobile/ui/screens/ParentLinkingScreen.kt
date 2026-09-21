package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.LinkRequest
import com.siren.mobile.model.LinkRequestStatus
import com.siren.mobile.model.LinkedPerson
import com.siren.mobile.ui.components.EmptyState
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.ListRow
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.RowDivider
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.components.SirenField
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.DateFmt

@Composable
fun ParentLinkingScreen(
    linked: List<LinkedPerson>,
    requests: List<LinkRequest>,
    working: Boolean,
    onLink: (String) -> Unit,
    onUnlink: (String) -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    val pending = requests.filter { it.status == LinkRequestStatus.PENDING }
    val declined = requests.filter { it.status == LinkRequestStatus.DECLINED }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenHeader(title = "Link a student", onBack = onBack)

        SirenField(
            value = code,

            onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
            label = "Linking code",
            leadingIcon = Icons.Filled.Key,
            supportingText = "6 characters, shown in your child's Settings",
        )

        PrimaryButton(
            text = "Send link request",
            onClick = {
                onLink(code)
                code = ""
            },
            enabled = code.length == 6 && !working,
            loading = working,
        )

        InfoBanner(
            "Your child is asked to confirm you are their parent or guardian before you can see their safety status.",
            Icons.Filled.Shield,
        )

        if (pending.isNotEmpty()) {
            SectionLabel("Waiting for confirmation")
            ListGroup {
                pending.forEachIndexed { i, req ->
                    RequestRow(
                        title = req.studentName.ifBlank { "Student" },
                        subtitle = "Asked ${DateFmt.shortDateTime(req.requestedAt)}",
                        pillText = "PENDING",
                        pillFg = SirenTheme.status.onWarnContainer,
                        pillBg = SirenTheme.status.warnContainer,
                        icon = Icons.Filled.HourglassTop,
                    )
                    if (i < pending.lastIndex) RowDivider()
                }
            }
        }

        if (declined.isNotEmpty()) {
            SectionLabel("Declined")
            ListGroup {
                declined.forEachIndexed { i, req ->
                    RequestRow(
                        title = req.studentName.ifBlank { "Student" },
                        subtitle = "Ask them to check the code, then try again.",
                        pillText = "DECLINED",
                        pillFg = SirenTheme.status.onDangerContainer,
                        pillBg = SirenTheme.status.dangerContainer,
                        icon = Icons.Filled.Cancel,
                    )
                    if (i < declined.lastIndex) RowDivider()
                }
            }
        }

        SectionLabel("Linked students")

        if (linked.isEmpty()) {
            EmptyState(
                title = "Nobody linked yet",
                subtitle = "Once a student confirms you, you'll see their safety status here during an event.",
                icon = Icons.Filled.FamilyRestroom,
            )
        } else {
            ListGroup {
                linked.forEachIndexed { i, person ->
                    RosterRow(person)
                    Box(Modifier.padding(start = Space.l, bottom = Space.xs)) {
                        TextButton(onClick = { onUnlink(person.uid) }) { Text("Unlink") }
                    }
                    if (i < linked.lastIndex) RowDivider()
                }
            }
        }

        InfoBanner(
            "Codes are issued by the school registrar. If one does not work, ask the adviser to reissue it.",
            Icons.Filled.Info,
        )

        Box(Modifier.padding(bottom = Space.xxl))
    }
}

@Composable
private fun RequestRow(
    title: String,
    subtitle: String,
    pillText: String,
    pillFg: androidx.compose.ui.graphics.Color,
    pillBg: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = {
            Surface(
                shape = RoundedCornerShape(Layout.tile),
                color = pillBg,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = pillFg)
                }
            }
        },
        trailing = { Pill(pillText, pillFg, pillBg) },
    )
}
