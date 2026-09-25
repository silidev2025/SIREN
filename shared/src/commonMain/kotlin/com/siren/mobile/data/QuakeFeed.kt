package com.siren.mobile.data

import com.siren.mobile.model.AlertRecord
import com.siren.mobile.model.AlertSource
import com.siren.mobile.model.FeedCheck
import com.siren.mobile.model.GeoPoint
import com.siren.mobile.model.Quake
import com.siren.mobile.model.QuakeCatalog
import com.siren.mobile.model.SensorNodes
import com.siren.mobile.platform.Platform
import com.siren.mobile.util.IsoTime
import com.siren.mobile.util.distanceKm
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Official earthquake data, from the standard FDSN event services.
 *
 * **PHIVOLCS has no public API**, and scraping its bulletin page needs a server and breaks
 * easily, so this reads EMSC (primary, better regional pickup) with USGS as the fallback.
 * Neither is PHIVOLCS and the UI must never call it that. Both are global networks running
 * minutes behind real time, so this is a *confirmation* source, never the warning: most
 * events below M3 that PHIVOLCS reports are missing, magnitudes can differ from PHIVOLCS by
 * several tenths (EMSC had 4.6 for a Bogo event PHIVOLCS put at 4.2, 22 Sep 2026), and
 * values are revised after first publication.
 */
object QuakeFeed {

    private const val EMSC_URL = "https://www.seismicportal.eu/fdsnws/event/1/query"
    private const val USGS_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query"

    /** The Philippines, roughly. */
    private const val PH_AREA =
        "minlatitude=4&maxlatitude=21&minlongitude=116&maxlongitude=127"

    private const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    private const val RECENT_MIN_MAGNITUDE = 3.0
    private const val RECENT_LIMIT = 60

    // ---- Cross-referencing an alert --------------------------------------------------------

    /** Search radius around the node, in degrees (~830 km) — the felt-radius cap below. */
    private const val SEARCH_RADIUS_DEG = 7.5

    /**
     * The query window around the detection. Wide enough to take an event up to ~800 km
     * away (about four minutes of S-wave travel) plus clock error; [bestMatch] then applies
     * the real, distance-aware timing test.
     */
    private const val MATCH_BEFORE_MS = 10L * 60 * 1000
    private const val MATCH_AFTER_MS = 2L * 60 * 1000

    /** Shear waves, which carry the shaking the sensor triggers on, travel about 3.5 km/s. */
    private const val S_WAVE_KM_PER_S = 3.5

    /**
     * How far the detection may sit from the predicted shaking arrival. Early covers a P-wave
     * trigger and the node's own clock; late covers the confirmation window, Firestore
     * timestamps and clock drift the other way.
     */
    private const val ARRIVAL_EARLY_MS = 120_000L
    private const val ARRIVAL_LATE_MS = 180_000L

    /** After this the catalogues have had time to publish; "no match" becomes the verdict. */
    private const val FINAL_AFTER_MS = 30L * 60 * 1000

    /** How often a still-open check is repeated while the catalogues catch up. */
    private const val RECHECK_MS = 3L * 60 * 1000

    /**
     * Upper bound on how far away an event of this magnitude could plausibly shake the
     * sensor above its trigger. A deliberately generous matching heuristic, not a physical
     * attenuation model — its job is to stop a small event on the far side of the archipelago
     * "confirming" a desk knock in Bogo. Tune it here, and state it in the methodology.
     */
    private fun feltRadiusKm(magnitude: Double): Double = when {
        magnitude < 3.0 -> 50.0
        magnitude < 4.0 -> 120.0
        magnitude < 5.0 -> 250.0
        magnitude < 6.0 -> 450.0
        else -> 800.0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Recent {
        data object Idle : Recent
        data object Loading : Recent
        data class Loaded(
            val quakes: List<Quake>,
            val catalog: QuakeCatalog,
            val fetchedAt: Long,
            val refreshing: Boolean = false,
        ) : Recent

        data object Failed : Recent
    }

    private val _recent = MutableStateFlow<Recent>(Recent.Idle)
    val recent: StateFlow<Recent> = _recent.asStateFlow()

    private var recentJob: Job? = null

    private val _checks = MutableStateFlow<Map<String, FeedCheck>>(emptyMap())

    /** Cross-reference results by alert id. Absent until [verify] is asked for that alert. */
    val checks: StateFlow<Map<String, FeedCheck>> = _checks.asStateFlow()

    private val checkJobs = mutableMapOf<String, Job>()

    /** Latest M≥3 events in the Philippine box over the past week. */
    fun refreshRecent() {
        if (recentJob?.isActive == true) return
        _recent.update { current ->
            if (current is Recent.Loaded) current.copy(refreshing = true) else Recent.Loading
        }
        recentJob = scope.launch {
            val now = Platform.services.nowMillis()
            val window = "starttime=${IsoTime.fdsn(now - RECENT_WINDOW_MS)}&$PH_AREA" +
                "&minmagnitude=$RECENT_MIN_MAGNITUDE&orderby=time&limit=$RECENT_LIMIT"

            val emsc = fetch(QuakeCatalog.EMSC, "$EMSC_URL?format=json&$window")
            val (quakes, catalog) = if (emsc != null) {
                emsc to QuakeCatalog.EMSC
            } else {
                fetch(QuakeCatalog.USGS, "$USGS_URL?format=geojson&$window") to QuakeCatalog.USGS
            }

            _recent.value = if (quakes == null) {
                // Keep showing the last good list rather than blanking it on a flaky network.
                (_recent.value as? Recent.Loaded)?.copy(refreshing = false) ?: Recent.Failed
            } else {
                Recent.Loaded(quakes.sortedByDescending { it.time }, catalog, now)
            }
        }
    }

    /**
     * Cross-references [alert] against the catalogues: did an official network record an
     * event whose shaking would have reached the node when it fired? That is the
     * independent ground truth the evaluation needs. Re-checks every few minutes while the
     * answer is still "not yet", and records the verdict once it is final.
     */
    fun verify(alert: AlertRecord) {
        if (alert.source == AlertSource.SIMULATED) {
            _checks.update { it + (alert.id to FeedCheck.NotApplicable) }
            return
        }
        val existing = _checks.value[alert.id]
        if (existing.isFinal()) return
        if (checkJobs[alert.id]?.isActive == true) return
        if (existing == null) _checks.update { it + (alert.id to FeedCheck.Checking) }

        checkJobs[alert.id] = scope.launch {
            while (true) {
                val result = crossCheck(alert)
                _checks.update { it + (alert.id to result) }
                if (result.isFinal()) {
                    SirenRepository.recordFeedCheck(alert, result)
                    break
                }
                // Unreachable feeds are retried the next time a screen asks, not in a loop.
                if (result !is FeedCheck.NoMatch) break
                delay(RECHECK_MS)
            }
        }
    }

    private fun FeedCheck?.isFinal(): Boolean =
        this is FeedCheck.Confirmed || (this is FeedCheck.NoMatch && final) ||
            this is FeedCheck.NotApplicable

    private suspend fun crossCheck(alert: AlertRecord): FeedCheck {
        val node = SensorNodes.locationOf(alert.nodeId)
        val query = "starttime=${IsoTime.fdsn(alert.detectedAt - MATCH_BEFORE_MS)}" +
            "&endtime=${IsoTime.fdsn(alert.detectedAt + MATCH_AFTER_MS)}" +
            "&latitude=${node.lat}&longitude=${node.lng}&maxradius=$SEARCH_RADIUS_DEG" +
            "&orderby=time&limit=100"

        val emsc = fetch(QuakeCatalog.EMSC, "$EMSC_URL?format=json&$query")
        emsc?.let { bestMatch(it, alert, node) }?.let { return it }

        // EMSC had nothing (or was down): USGS may have picked up what EMSC did not.
        val usgs = fetch(QuakeCatalog.USGS, "$USGS_URL?format=geojson&$query")
        usgs?.let { bestMatch(it, alert, node) }?.let { return it }

        if (emsc == null && usgs == null) return FeedCheck.Unavailable
        val age = Platform.services.nowMillis() - alert.detectedAt
        return FeedCheck.NoMatch(final = age >= FINAL_AFTER_MS)
    }

    internal fun bestMatch(quakes: List<Quake>, alert: AlertRecord, node: GeoPoint): FeedCheck.Confirmed? =
        quakes.mapNotNull { quake ->
            val km = distanceKm(node.lat, node.lng, quake.lat, quake.lng)
            if (km > feltRadiusKm(quake.magnitude)) return@mapNotNull null
            val arrival = quake.time + (km / S_WAVE_KM_PER_S * 1000).toLong()
            val residual = alert.detectedAt - arrival
            if (residual < -ARRIVAL_EARLY_MS || residual > ARRIVAL_LATE_MS) return@mapNotNull null
            Triple(quake, km, abs(residual))
        }
            .minByOrNull { it.third }
            ?.let { (quake, km) -> FeedCheck.Confirmed(quake, km) }

    /** Null when the service could not be reached or answered with something unreadable. */
    private suspend fun fetch(catalog: QuakeCatalog, url: String): List<Quake>? =
        Platform.services.httpGet(url)?.let { parse(catalog, it) }

    /** Null when the body is unreadable; empty for a 204 or a feed with no features. */
    internal fun parse(catalog: QuakeCatalog, body: ByteArray): List<Quake>? {
        // FDSN services answer 204 No Content when nothing matches — a valid empty result.
        if (body.isEmpty()) return emptyList()
        val root = runCatching { json.parseToJsonElement(body.decodeToString()) }.getOrNull()
            as? JsonObject ?: return null
        val features = root["features"] as? JsonArray ?: return null
        return features.mapNotNull { (it as? JsonObject)?.let { f -> parseFeature(catalog, f) } }
    }

    private fun parseFeature(catalog: QuakeCatalog, feature: JsonObject): Quake? {
        val props = feature["properties"] as? JsonObject ?: return null
        val coords = (feature["geometry"] as? JsonObject)?.get("coordinates") as? JsonArray
        fun coord(i: Int) = (coords?.getOrNull(i) as? JsonPrimitive)?.doubleOrNull

        val lng = coord(0) ?: props.num("lon") ?: return null
        val lat = coord(1) ?: props.num("lat") ?: return null
        val magnitude = props.num("mag") ?: return null

        return when (catalog) {
            QuakeCatalog.EMSC -> Quake(
                id = feature.str("id") ?: props.str("unid") ?: return null,
                catalog = catalog,
                magnitude = magnitude,
                magnitudeType = props.str("magtype").orEmpty(),
                region = props.str("flynn_region").orEmpty().titleCase(),
                lat = lat,
                lng = lng,
                // EMSC's geometry carries depth as a negative altitude; properties has it plain.
                depthKm = props.num("depth") ?: coord(2)?.let { abs(it) } ?: 0.0,
                time = props.str("time")?.let(IsoTime::parse) ?: return null,
                agency = props.str("auth").orEmpty(),
            )

            QuakeCatalog.USGS -> Quake(
                id = feature.str("id") ?: return null,
                catalog = catalog,
                magnitude = magnitude,
                magnitudeType = props.str("magType").orEmpty(),
                region = props.str("place").orEmpty(),
                lat = lat,
                lng = lng,
                depthKm = coord(2) ?: 0.0,
                time = (props["time"] as? JsonPrimitive)?.longOrNull ?: return null,
                agency = props.str("net").orEmpty().uppercase(),
            )
        }
    }

    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** EMSC's Flinn-Engdahl regions arrive in capitals: "MINDANAO, PHILIPPINES". */
    private fun String.titleCase(): String =
        lowercase().split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
}
