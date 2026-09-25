package com.siren.mobile.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siren.mobile.resources.*
import com.siren.mobile.ui.theme.SirenGradients
import com.siren.mobile.ui.theme.Space
import org.jetbrains.compose.resources.painterResource

@Composable
fun SplashScreen(versionName: String = "2.4.0") {
    val transition = rememberInfiniteTransition(label = "splash")

    Box(
        Modifier
            .fillMaxSize()
            .background(SirenGradients.splash),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.xxl),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                listOf(0, 900, 1800).forEach { delay ->
                    val p by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2600, delayMillis = delay, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "ring$delay",
                    )
                    Box(
                        Modifier
                            .size(150.dp)
                            .scale(0.6f + p * 0.4f)
                            .alpha((1f - p) * 0.5f)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color.White.copy(alpha = 0.10f))
                    )
                }

                Box(
                    Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_splash_shield),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Text(
                    "SIREN",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 40.sp,
                    letterSpacing = 2.4.sp,
                )
                Box(
                    Modifier
                        .width(56.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.5f))
                )
                Text(
                    "Seismic Integrated Response & Emergency Notification",
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(34.dp),
            ) {
                repeat(5) { i ->
                    val h by transition.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1100, delayMillis = i * 150, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "bar$i",
                    )
                    Box(
                        Modifier
                            .width(5.dp)
                            .height(34.dp * h)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.75f))
                    )
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                "Connecting to ESP32 sensor node…",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "v$versionName · Capstone Build",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
