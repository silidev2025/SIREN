package com.siren.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class StatusColors(
    val safe: Color,
    val safeContainer: Color,
    val onSafeContainer: Color,
    val warn: Color,
    val warnContainer: Color,
    val onWarnContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,

    val safeFill: Color,
    val warnFill: Color,
    val dangerFill: Color,

    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
)

private val LightStatus = StatusColors(
    safe = SafeText, safeContainer = SafeTint, onSafeContainer = Color(0xFF06351A),
    warn = WarnText, warnContainer = WarnTint, onWarnContainer = Color(0xFF432B04),
    danger = DangerText, dangerContainer = DangerTint, onDangerContainer = Color(0xFF4C0F0F),
    safeFill = SafeFill, warnFill = WarnFill, dangerFill = DangerFill,
    hero = Navy, onHero = Color.White, onHeroMuted = Color(0xFFA8B4C8),
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

object SirenTheme {
    val status: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current
}

private val LightColors = lightColorScheme(
    primary = SirenBlue,
    onPrimary = Color.White,
    primaryContainer = SurfaceTint,
    onPrimaryContainer = SirenBlueDeep,
    secondary = SirenBlueDark,
    onSecondary = Color.White,
    secondaryContainer = SurfaceTintAlt,
    onSecondaryContainer = SirenBlueDeep,
    background = AppBg,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = InkSubtle,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = SurfaceLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFFE1E6EF),
    error = DangerText,
    onError = Color.White,
    errorContainer = DangerTint,
    onErrorContainer = Color(0xFF4C0F0F),
    outline = BorderStrong,
    outlineVariant = Border,
    scrim = Color(0x99000000),
)

private val SirenShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object Layout {
    val card = 16.dp
    val cardLarge = 22.dp
    val field = 14.dp
    val pill = 999.dp
    val tile = 12.dp
    val fieldHeight = 56.dp
    val buttonHeight = 52.dp
    val screenPadding = 20.dp
    val rowMinHeight = 56.dp

    val touchTarget = 48.dp
    val hairline = 1.dp
}

object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val xxxl = 40.dp
}

@Composable
fun SirenTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides LightStatus) {
        MaterialTheme(
            colorScheme = LightColors,
            typography = sirenTypography(),
            shapes = SirenShapes,
            content = content,
        )
    }
}
