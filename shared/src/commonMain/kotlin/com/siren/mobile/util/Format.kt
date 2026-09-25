package com.siren.mobile.util

import androidx.compose.ui.text.TextStyle
import kotlin.math.abs
import kotlin.math.round

fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

fun Double.toFixed(decimals: Int): String {
    if (isNaN()) return "—"
    if (isInfinite()) return if (this > 0) "∞" else "-∞"

    var factor = 1L
    repeat(decimals) { factor *= 10L }

    val scaled = round(abs(this) * factor).toLong()
    val whole = scaled / factor
    val frac = scaled % factor

    return buildString {
        if (this@toFixed < 0 && scaled != 0L) append('-')
        append(whole)
        if (decimals > 0) {
            append('.')
            append(frac.toString().padStart(decimals, '0'))
        }
    }
}

fun Double.asG(decimals: Int = 2): String = "${toFixed(decimals)}g"

fun Double.asGSpaced(decimals: Int = 2): String = "${toFixed(decimals)} g"

fun String.initials(max: Int = 2): String =
    trim()
        .split(' ', '\t', '\n')
        .filter { it.isNotEmpty() }
        .take(max)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
