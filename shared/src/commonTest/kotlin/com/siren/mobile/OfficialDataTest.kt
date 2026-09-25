package com.siren.mobile

import com.siren.mobile.data.QuakeFeed
import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.AlertSource
import com.siren.mobile.model.Intensity
import com.siren.mobile.model.Quake
import com.siren.mobile.model.QuakeCatalog
import com.siren.mobile.model.SensorNodes
import com.siren.mobile.model.peisNumeral
import com.siren.mobile.ui.components.coordinatesText
import com.siren.mobile.ui.components.isNearSensor
import com.siren.mobile.ui.components.placeName
import com.siren.mobile.util.IsoTime
import com.siren.mobile.util.VoiceAlert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Trimmed from a real EMSC answer (25 Sep 2026) — note `auth: PIVS`, EMSC's code for PHIVOLCS. */
private const val EMSC_SAMPLE = """
{"type":"FeatureCollection","metadata":{"count":1},"features":[{
  "type": "Feature",
  "geometry": {"type": "Point", "coordinates": [124.3400, 5.9500, -10.0]},
  "id": "20260925_0000071",
  "properties": {
    "source_id": "2065260", "source_catalog": "EMSC-RTS",
    "lastupdate": "2026-09-25T07:42:58.33518Z", "time": "2026-09-25T07:24:15.0Z",
    "flynn_region": "MINDANAO, PHILIPPINES", "lat": 5.9500, "lon": 124.3400,
    "depth": 10.0, "evtype": "ke", "auth": "PIVS", "mag": 3.2, "magtype": "m",
    "unid": "20260925_0000071"
  }
}]}
"""

/** Trimmed from a real USGS answer: epoch-millisecond time, depth in the coordinates. */
private const val USGS_SAMPLE = """
{"type":"FeatureCollection","metadata":{"status":200},"features":[{"type":"Feature",
"properties":{"mag":4.2,"place":"21 km ENE of Lais, Philippines","time":1790173172302,
"mmi":null,"net":"us","magType":"mb"},
"geometry":{"type":"Point","coordinates":[125.8,6.4,35.5]},"id":"us6000tx3l"}]}
"""

private const val T_0724 = 1_790_321_055_000L // 2026-09-25T07:24:15Z

class PeisTest {

    @Test
    fun numeralsFollowTheBreakpointsInsideEachBand() {
        assertEquals(1, Intensity.peisFromPga(0.0))
        assertEquals(3, Intensity.peisFromPga(0.005)) // Demo Green
        assertEquals(5, Intensity.peisFromPga(0.050)) // Demo Yellow
        assertEquals(7, Intensity.peisFromPga(0.300)) // Demo Red
        assertEquals(6, Intensity.peisFromPga(0.100))
        assertEquals(8, Intensity.peisFromPga(0.500))
        assertEquals(10, Intensity.peisFromPga(1.5))
    }

    @Test
    fun theBandAlwaysWinsAtItsBoundaries() {
        // Wald alone would say III and VI; the paper's bands say Yellow and Red.
        assertEquals(5, Intensity.peisFromPga(0.011))
        assertEquals(7, Intensity.peisFromPga(0.130))
        for (g in listOf(0.0, 0.004, 0.0099, 0.01, 0.05, 0.119, 0.12, 0.4, 2.0)) {
            assertTrue(Intensity.peisFromPga(g) in Intensity.fromMagnitude(g).peisRange, "g=$g")
        }
    }

    @Test
    fun numeralsAreRoman() {
        assertEquals("V", peisNumeral(5))
        assertEquals("X", peisNumeral(12))
        assertEquals("I", peisNumeral(0))
    }
}

class VoiceAlertTest {

    @Test
    fun greenIsNeverSpoken() {
        assertNull(VoiceAlert.phrase(Intensity.GREEN, 0.005, AlertSource.ESP32))
    }

    @Test
    fun speaksTheNumeralFromTheReading() {
        assertEquals(
            "Earthquake. Intensity seven. Drop, cover, and hold on.",
            VoiceAlert.phrase(Intensity.RED, 0.3, AlertSource.ESP32),
        )
    }

    @Test
    fun aDrillSaysSoOutLoud() {
        val phrase = VoiceAlert.phrase(Intensity.YELLOW, 0.05, AlertSource.SIMULATED)
        assertNotNull(phrase)
        assertTrue(phrase.startsWith("This is a drill, not a real earthquake."))
        assertTrue("Intensity five" in phrase)
    }
}

class IsoTimeTest {

    @Test
    fun parsesEmscTimestamps() {
        assertEquals(T_0724, IsoTime.parse("2026-09-25T07:24:15.0Z"))
        assertEquals(T_0724, IsoTime.parse("2026-09-25T07:24:15"))
        assertNull(IsoTime.parse("not a time"))
    }

    @Test
    fun formatsFdsnQueryTimesWithoutZoneOrFraction() {
        assertEquals("2026-09-25T07:24:15", IsoTime.fdsn(T_0724))
        assertEquals("2026-09-25T07:24:15", IsoTime.fdsn(T_0724 + 450))
    }
}

class QuakeFeedTest {

    private fun alertAt(detectedAt: Long, g: Double = 0.05) = AlertRecord(
        id = "a",
        intensity = Intensity.fromMagnitude(g),
        magnitudeG = g,
        detectedAt = detectedAt,
        source = AlertSource.ESP32,
    )

    private fun quake(lat: Double, lng: Double, magnitude: Double, time: Long) = Quake(
        id = "q", catalog = QuakeCatalog.EMSC, magnitude = magnitude, magnitudeType = "m",
        region = "", lat = lat, lng = lng, depthKm = 10.0, time = time,
    )

    @Test
    fun parsesEmsc() {
        val quakes = QuakeFeed.parse(QuakeCatalog.EMSC, EMSC_SAMPLE.encodeToByteArray())
        val q = assertNotNull(quakes).single()
        assertEquals("20260925_0000071", q.id)
        assertEquals(3.2, q.magnitude)
        assertEquals("m", q.magnitudeType)
        assertEquals("Mindanao, Philippines", q.region)
        assertEquals(5.95, q.lat)
        assertEquals(124.34, q.lng)
        assertEquals(10.0, q.depthKm)
        assertEquals(T_0724, q.time)
        assertEquals("PIVS", q.agency)
    }

    @Test
    fun parsesUsgs() {
        val q = assertNotNull(QuakeFeed.parse(QuakeCatalog.USGS, USGS_SAMPLE.encodeToByteArray())).single()
        assertEquals("us6000tx3l", q.id)
        assertEquals(4.2, q.magnitude)
        assertEquals("mb", q.magnitudeType)
        assertEquals(35.5, q.depthKm)
        assertEquals(1_790_173_172_302L, q.time)
        assertEquals("US", q.agency)
    }

    @Test
    fun noContentIsAnEmptyResultAndGarbageIsAFailure() {
        assertEquals(emptyList(), QuakeFeed.parse(QuakeCatalog.EMSC, ByteArray(0)))
        assertNull(QuakeFeed.parse(QuakeCatalog.EMSC, "<html>502</html>".encodeToByteArray()))
    }

    @Test
    fun confirmsANearbyEventWhoseShakingArrivedWhenTheNodeFired() {
        // ~35 km from Bogo: shear waves take ~10 s, so a detection 10 s after origin fits.
        val near = quake(11.30, 124.20, 4.5, T_0724)
        val match = QuakeFeed.bestMatch(listOf(near), alertAt(T_0724 + 10_000), SensorNodes.BOGO)
        assertNotNull(match)
        assertTrue(match.distanceKm in 34.0..37.0, "distance ${match.distanceKm}")
    }

    @Test
    fun rejectsAnEventTooFarAwayForItsMagnitude() {
        // The real 07:24 M3.2 was ~568 km away in Mindanao — far beyond an M3's felt radius.
        val far = QuakeFeed.parse(QuakeCatalog.EMSC, EMSC_SAMPLE.encodeToByteArray())!!
        assertNull(QuakeFeed.bestMatch(far, alertAt(T_0724 + 160_000), SensorNodes.BOGO))
    }

    @Test
    fun rejectsANearbyEventAtTheWrongTime() {
        val near = quake(11.30, 124.20, 4.5, T_0724)
        assertNull(QuakeFeed.bestMatch(listOf(near), alertAt(T_0724 + 10 * 60_000), SensorNodes.BOGO))
        assertNull(QuakeFeed.bestMatch(listOf(near), alertAt(T_0724 - 5 * 60_000), SensorNodes.BOGO))
    }

    @Test
    fun eventsNearTheSensorAreNamedAfterBogoNotTheCatalogueRegion() {
        // 22 Sep 2026 M4.2 at PHIVOLCS's 11.10 N 124.07 E, which EMSC files under "Leyte".
        val bogo = quake(11.10, 124.07, 4.6, T_0724).copy(region = "Leyte, Philippines")
        assertTrue(bogo.isNearSensor())
        assertEquals("Near Bogo", bogo.placeName())
        assertEquals("11.10°N, 124.07°E", bogo.coordinatesText())

        val mindanao = QuakeFeed.parse(QuakeCatalog.EMSC, EMSC_SAMPLE.encodeToByteArray())!!.single()
        assertEquals("Mindanao, Philippines", mindanao.placeName())
        assertEquals("5.95°N, 124.34°E", mindanao.coordinatesText())
    }

    @Test
    fun picksTheEventThatFitsTheTimingBest() {
        val early = quake(11.30, 124.20, 4.5, T_0724 - 90_000)
        val onTime = quake(11.10, 124.10, 3.5, T_0724)
        val match = QuakeFeed.bestMatch(listOf(early, onTime), alertAt(T_0724 + 4_000), SensorNodes.BOGO)
        assertEquals(onTime, match?.quake)
    }
}
