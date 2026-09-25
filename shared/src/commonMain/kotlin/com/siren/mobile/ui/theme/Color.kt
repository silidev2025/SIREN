package com.siren.mobile.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val SirenBlue = Color(0xFF2563EB)
val SirenBlueDark = Color(0xFF1D4ED8)
val SirenBlueLight = Color(0xFF3B82F6)
val SirenBlueDeep = Color(0xFF1E3A8A)

val Navy = Color(0xFF0B1220)
val NavySoft = Color(0xFF16223A)

val AppBg = Color(0xFFF5F7FA)
val Surface = Color(0xFFFFFFFF)
val SurfaceLow = Color(0xFFFAFBFD)
val SurfaceContainer = Color(0xFFF0F3F7)
val SurfaceContainerHigh = Color(0xFFE8ECF3)
val SurfaceTint = Color(0xFFEFF5FF)
val SurfaceTintAlt = Color(0xFFDCEAFE)

val Ink = Color(0xFF0F172A)
val InkMuted = Color(0xFF3F4A5C)
val InkSubtle = Color(0xFF5B6676)
val InkFaint = Color(0xFF6B7280)
val Border = Color(0xFFE2E7EF)
val BorderStrong = Color(0xFFC9D1DE)

val SafeFill = Color(0xFF16A34A)
val SafeText = Color(0xFF15803D)
val SafeTint = Color(0xFFDCFCE7)

val WarnFill = Color(0xFFF59E0B)
val WarnText = Color(0xFFB45309)
val WarnTint = Color(0xFFFEF3C7)

val DangerFill = Color(0xFFDC2626)
val DangerText = Color(0xFFB91C1C)
val DangerTint = Color(0xFFFEE2E2)

val Safe = SafeText
val Warn = WarnText
val Danger = DangerText

val IntensityGreen = SafeFill
val IntensityYellow = WarnFill
val IntensityRed = DangerFill

object SirenGradients {
    val brand = Brush.linearGradient(listOf(SirenBlueLight, SirenBlueDark))
    val splash = Brush.linearGradient(listOf(SirenBlueLight, SirenBlue, SirenBlueDeep))
    val night = Brush.linearGradient(listOf(NavySoft, Navy))
    val danger = Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFF991B1B)))
    val warn = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFB45309)))
    val safe = Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF15803D)))
}
