package com.siren.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.siren.mobile.platform.Platform
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Space
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

private const val TILE_PX = 256
private const val TILE_SERVER = "https://tile.openstreetmap.org"

/** Metres per tile pixel at zoom 0 on the equator (Web Mercator). */
private const val METRES_PER_PX_Z0 = 156543.03392

/**
 * Tiles already fetched this session. OpenStreetMap's tile policy asks clients to cache and
 * never bulk-download; a handful of tiles per shared location, kept in memory, is well
 * inside it. Only ever touched from the composition's own dispatcher.
 */
private val tileCache = mutableMapOf<String, ImageBitmap>()

/**
 * A small, static OpenStreetMap view centred on one point, with its accuracy radius.
 *
 * Deliberately not a map library: OpenStreetMap needs no API key or billing (Google Maps
 * does), and a few tiles drawn on a canvas work identically on both platforms without
 * pulling in a dependency that would have to be verified on iOS. Panning and zooming are
 * the phone's own maps app's job — callers pair this with an "Open in Maps" action.
 */
@Composable
fun OsmStaticMap(
    lat: Double,
    lng: Double,
    accuracyM: Double?,
    modifier: Modifier = Modifier,
    zoom: Int = 16,
) {
    val marker = MaterialTheme.colorScheme.primary
    val tiles = remember(lat, lng, zoom) { mutableStateMapOf<Pair<Int, Int>, ImageBitmap>() }
    var failed by remember(lat, lng, zoom) { mutableStateOf(false) }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(Layout.card))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        val density = LocalDensity.current
        // Each 256-px tile is drawn 128 dp across: crisp on a 2x screen, ~600 m wide at z16.
        val scale = with(density) { 128.dp.toPx() } / TILE_PX
        val halfW = with(density) { maxWidth.toPx() } / 2 / scale
        val halfH = with(density) { maxHeight.toPx() } / 2 / scale

        val worldSize = (1 shl zoom).toDouble() * TILE_PX
        val latRad = lat * PI / 180.0
        val centreX = (lng + 180.0) / 360.0 * worldSize
        val centreY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * worldSize

        val minTx = floor((centreX - halfW) / TILE_PX).toInt()
        val maxTx = floor((centreX + halfW) / TILE_PX).toInt()
        val minTy = floor((centreY - halfH) / TILE_PX).toInt()
        val maxTy = floor((centreY + halfH) / TILE_PX).toInt()
        val tilesPerSide = 1 shl zoom

        LaunchedEffect(lat, lng, zoom, minTx, maxTx, minTy, maxTy) {
            var anyMissing = false
            for (ty in minTy..maxTy) {
                if (ty !in 0 until tilesPerSide) continue
                for (tx in minTx..maxTx) {
                    // Longitude wraps; latitude does not (the loop above skips off-world rows).
                    val wrappedX = ((tx % tilesPerSide) + tilesPerSide) % tilesPerSide
                    val key = "$zoom/$wrappedX/$ty"
                    val bitmap = tileCache[key] ?: Platform.services.httpGet("$TILE_SERVER/$key.png")
                        ?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
                        ?.also { tileCache[key] = it }
                    if (bitmap == null) anyMissing = true else tiles[tx to ty] = bitmap
                }
            }
            failed = anyMissing && tiles.isEmpty()
        }

        Canvas(Modifier.fillMaxSize()) {
            val tileDrawn = (TILE_PX * scale).roundToInt()
            tiles.forEach { (pos, image) ->
                val left = (pos.first * TILE_PX - centreX) * scale + size.width / 2
                val top = (pos.second * TILE_PX - centreY) * scale + size.height / 2
                drawImage(
                    image = image,
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(tileDrawn, tileDrawn),
                )
            }

            val centre = Offset(size.width / 2, size.height / 2)
            accuracyM?.let { metres ->
                val metresPerPx = METRES_PER_PX_Z0 * cos(latRad) / (1 shl zoom)
                val radius = (metres / metresPerPx * scale).toFloat()
                if (radius > 6f) {
                    drawCircle(marker.copy(alpha = 0.16f), radius, centre)
                    drawCircle(marker.copy(alpha = 0.55f), radius, centre, style = Stroke(1.dp.toPx()))
                }
            }
            drawCircle(Color.White, 9.dp.toPx(), centre)
            drawCircle(marker, 6.dp.toPx(), centre)
        }

        if (failed) {
            Text(
                "Map tiles couldn't load. The coordinates below still work in any maps app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(Space.l),
            )
        }

        // Required by the OpenStreetMap licence wherever its tiles are shown.
        Text(
            "© OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = Space.s, vertical = Space.xxs),
        )
    }
}
