package com.siren.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.siren.mobile.resources.Res
import com.siren.mobile.resources.inter_bold
import com.siren.mobile.resources.inter_extrabold
import com.siren.mobile.resources.inter_medium
import com.siren.mobile.resources.inter_regular
import com.siren.mobile.resources.inter_semibold
import org.jetbrains.compose.resources.Font

@Composable
fun interFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
    Font(Res.font.inter_extrabold, FontWeight.ExtraBold),
)

@Composable
fun sirenTypography(): Typography {
    val inter = interFamily()
    return Typography(
        displaySmall = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.ExtraBold,
            fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = 2.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.ExtraBold,
            fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp, lineHeight = 24.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Bold,
            fontSize = 16.sp, lineHeight = 22.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Normal,
            fontSize = 15.sp, lineHeight = 22.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 20.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Normal,
            fontSize = 13.sp, lineHeight = 18.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, lineHeight = 18.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, lineHeight = 16.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Medium,
            fontSize = 11.sp, lineHeight = 14.sp,
        ),
    )
}
