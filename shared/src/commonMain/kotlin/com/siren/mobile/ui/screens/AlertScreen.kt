package com.siren.mobile.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TextButton
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.AlertSource
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.model.SafetyResponse
import com.siren.mobile.platform.Platform
import com.siren.mobile.ui.components.ButtonTone
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.Haptics
import com.siren.mobile.ui.components.PrimaryButton
import com.siren.mobile.ui.components.intensityBrush
import com.siren.mobile.ui.components.intensityColor
import com.siren.mobile.util.DateFmt
import com.siren.mobile.util.asGSpaced
import com.siren.mobile.util.tabular
import com.siren.mobile.ui.theme.Danger
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Safe
import com.siren.mobile.ui.theme.Space

@Composable
fun AlertScreen(
    alert: AlertRecord,
    vibrationEnabled: Boolean,
    myResponse: SafetyResponse?,
    /**
     * False when nobody is signed in. The alert itself still shows — it reaches every
     * install through the `alerts` topic — but a status cannot be recorded, so the response
     * buttons are replaced rather than shown and silently ignored.
     */
    canRespond: Boolean = true,
    onRespond: (ResponseStatus) -> Unit,
    onConfirmStatus: () -> Unit,
    onDismiss: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "alert")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )

    val alarmActive by Platform.alarmActive.collectAsState()

    val time = remember(alert.detectedAt) { DateFmt.clockSeconds(alert.detectedAt) }
    val date = remember(alert.detectedAt) { DateFmt.date(alert.detectedAt) }

    Column(
        Modifier
            .fillMaxSize()
            .background(intensityBrush(alert.intensity))
            .padding(horizontal = Layout.screenPadding, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            if (alert.source == AlertSource.SIMULATED) {
                Pill("DEMO — NOT A REAL EVENT", intensityColor(alert.intensity), Color.White)
            }
            if (vibrationEnabled && alarmActive) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(Layout.pill))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = Space.m, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Vibration, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        "ALARM ON",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Box(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.l),
            ) {
                Icon(
                    Icons.Filled.Warning,
                    null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(92.dp)
                        .scale(pulse),
                )
                Text(
                    "EARTHQUAKE DETECTED",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Layout.pill))
                        .background(Color.White.copy(alpha = 0.22f))
                        .padding(horizontal = Space.l, vertical = Space.s)
                ) {
                    Text(
                        "${alert.intensity.severity.uppercase()} · LEVEL ${alert.intensity.level}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Shaking intensity",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        alert.intensity.scale,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 46.sp,
                    )
                    Text(
                        alert.intensity.shaking,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Text(
                        alert.magnitudeG.asGSpaced(3),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        modifier = Modifier.padding(top = Space.xxs),
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AlertFact("Detected at", "$time\n$date")
                    AlertFact("Source", alert.nodeId ?: alert.source.label)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Layout.field))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(
                "Drop · Cover · Hold On, then respond",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (!canRespond) {
            // The alert reaches signed-out devices — the `alerts` topic goes to every
            // install. Offering the buttons here would discard the tap and leave "your
            // adviser is notified" on screen, which is worse than saying nothing can be
            // sent. The alarm and its silence control stay available below regardless.
            Text(
                "Sign in to send your status. The alarm and safety guidance still work.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (myResponse == null) {

            PrimaryButton(
                text = "Yes, I'm safe",
                onClick = {
                    Haptics.cancel()
                    onRespond(ResponseStatus.SAFE)
                },
                icon = Icons.Filled.CheckCircle,
                tone = ButtonTone.OnColor,
                onColorContent = Safe,
            )
            PrimaryButton(
                text = "I need help",
                onClick = {
                    Haptics.cancel()
                    onRespond(ResponseStatus.NEEDS_HELP)
                },
                icon = Icons.Filled.Sos,
                tone = ButtonTone.OnColor,
                onColorContent = Danger,
            )
            Text(
                "Your adviser is notified the moment you respond.",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Layout.field))
                    .background(Color.White.copy(alpha = 0.22f))
                    .padding(Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.TaskAlt, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(
                    "Sent: ${myResponse.status.label}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (myResponse.status == ResponseStatus.SAFE) {
                PrimaryButton(
                    text = "I need help after all",
                    onClick = {
                        Haptics.cancel()
                        onRespond(ResponseStatus.NEEDS_HELP)
                    },
                    icon = Icons.Filled.Sos,
                    tone = ButtonTone.OnColor,
                    onColorContent = Danger,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(Layout.pill))
                    .background(Color.White.copy(alpha = if (alarmActive) 0.22f else 0.10f))
                    .clickable(enabled = alarmActive) { Platform.services.stopAlarm() }
                    .padding(horizontal = Space.m, vertical = Space.s),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Vibration,
                    null,
                    tint = Color.White.copy(alpha = if (alarmActive) 1f else 0.55f),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (alarmActive) "Silence alarm" else "Alarm silenced",
                    color = Color.White.copy(alpha = if (alarmActive) 1f else 0.55f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            TextButton(onClick = onConfirmStatus) {
                Text(
                    "Details",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            TextButton(onClick = {
                Haptics.cancel()
                onDismiss()
            }) {
                Text(
                    "Dismiss",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun AlertFact(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}
