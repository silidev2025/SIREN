package com.siren.mobile.util

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_KM = 6371.0

private fun Double.rad() = this * PI / 180.0

/** Great-circle distance in kilometres (haversine). Plenty for tens-of-kilometres matching. */
fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = (lat2 - lat1).rad()
    val dLng = (lng2 - lng1).rad()
    val a = sin(dLat / 2).pow(2) + cos(lat1.rad()) * cos(lat2.rad()) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

fun Double.asKm(): String = if (this < 10) "${toFixed(1)} km" else "${toFixed(0)} km"

/** "±12 m" / "±1.4 km" — a position is only as good as its accuracy radius. */
fun Double.asAccuracy(): String =
    if (this < 1000) "±${toFixed(0)} m" else "±${(this / 1000).toFixed(1)} km"
