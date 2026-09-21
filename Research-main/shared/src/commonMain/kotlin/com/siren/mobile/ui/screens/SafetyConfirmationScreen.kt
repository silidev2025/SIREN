package com.siren.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContactSupport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.model.SafetyResponse
import com.siren.mobile.ui.components.ButtonTone
import com.siren.mobile.ui.components.Haptics
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asGSpaced
import com.siren.mobile.util.tabular
import com.siren.mobile.ui.theme.Danger
import com.siren.mobile.ui.theme.DangerTint
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Safe
import com.siren.mobile.ui.theme.SafeTint
import com.siren.mobile.ui.theme.SirenGradients
import com.siren.mobile.ui.theme.Space

@Composable
fun SafetyConfirmationScreen(
    alert: AlertRecord,
    myResponse: SafetyResponse?,
    onRespond: (ResponseStatus) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val stamp = remember(myResponse?.respondedAt) {
        myResponse?.let { "${DateFmt.date(it.respondedAt)} · ${DateFmt.clockSeconds(it.respondedAt)}" }
    }

    Column(
        Modifier
            .fillMaxSize()

            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        ScreenHeader(title = "Safety check", onBack = onBack)

        Pill(
            text = "ACTIVE EVENT · ${alert.intensity.levelText.uppercase()}",
            fg = Danger,
            bg = DangerTint,
        )

        Text(
            "Peak ground acceleration ${alert.magnitudeG.asGSpaced(3)}",
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = InkSubtle,
        )

        if (myResponse == null) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.ContactSupport,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                )
                Text(
                    "Are you safe?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink,
                    modifier = Modifier.padding(top = Space.l),
                )
                Text(
                    "Your answer is sent instantly to your adviser and linked guardians.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSubtle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Space.s),
                )
            }

            PrimaryButton(
                text = "I'm Safe",
                onClick = {
                    Haptics.confirm()
                    onRespond(ResponseStatus.SAFE)
                },
                icon = Icons.Filled.CheckCircle,
                tone = ButtonTone.Safe,
            )
            PrimaryButton(
                text = "I Need Help",
                onClick = {
                    Haptics.confirm()
                    onRespond(ResponseStatus.NEEDS_HELP)
                },
                icon = Icons.Filled.Sos,
                tone = ButtonTone.Danger,
            )
        } else {
            val safe = myResponse.status == ResponseStatus.SAFE
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Layout.card))
                        .background(if (safe) SafeTint else DangerTint)
                        .padding(Space.l),
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.TaskAlt,
                        null,
                        tint = if (safe) Safe else Danger,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Response recorded — ${if (safe) "Safe" else "Needs help"}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (safe) Safe else Danger,
                        )
                        stamp?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = InkSubtle)
                        }
                    }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(Layout.pill))
                            .background(Color.White)
                            .padding(horizontal = Space.s, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = InkSubtle, modifier = Modifier.size(12.dp))
                        Text(
                            "LOCKED",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSubtle,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                InfoBanner(
                    "Responses are locked to keep the record accurate. If your situation changes, escalate below.",
                    Icons.Filled.Info,
                )

                if (safe && !alert.closed) {
                    PrimaryButton(
                        text = "Escalate — I Need Help",
                        onClick = {
                            Haptics.confirm()
                            onRespond(ResponseStatus.NEEDS_HELP)
                        },
                        icon = Icons.Filled.Sos,
                        tone = ButtonTone.Danger,
                    )
                }
            }

            PrimaryButton(text = "Done", onClick = onDone)
        }

        Box(Modifier.padding(bottom = Space.xl))
    }
}
