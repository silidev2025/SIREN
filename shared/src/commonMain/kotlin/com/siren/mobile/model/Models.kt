package com.siren.mobile.model

enum class Role(val label: String, val blurb: String) {
    STUDENT("Student", "Receive alerts and confirm your safety status."),
    TEACHER("Teacher / School Admin", "Monitor your class roster in real time."),
    PARENT("Parent / Guardian", "Track the safety status of linked children.");

    val wire: String get() = name.lowercase()

    companion object {
        fun fromName(name: String?): Role =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: STUDENT
    }
}

enum class Intensity(
    val label: String,
    val severity: String,
    val level: Int,

    val scale: String,

    val shaking: String,

    val range: String,
    val behaviour: String,
) {
    GREEN(
        "Green", "Minor", 1,
        "I–IV", "Light shaking",
        "0.000 – 0.010 g", "Notification only, single vibration",
    ),
    YELLOW(
        "Yellow", "Moderate", 2,
        "V–VI", "Moderate shaking",
        "0.010 – 0.120 g", "Full-screen alert, repeating vibration",
    ),
    RED(
        "Red", "Severe", 3,
        "VII+", "Destructive shaking",
        "0.120 g and above", "Alarm sound, continuous vibration, status required",
    );

    val wire: String get() = name.lowercase()

    val levelText: String get() = "Intensity $scale"

    val levelWithShaking: String get() = "$levelText · $shaking"

    /** The PEIS numerals this band covers, per the research paper's band table. */
    val peisRange: IntRange
        get() = when (this) {
            GREEN -> 1..4
            YELLOW -> 5..6
            RED -> 7..10
        }

    companion object {
        fun fromMagnitude(g: Double): Intensity = when {
            g >= 0.120 -> RED
            g >= 0.010 -> YELLOW
            else -> GREEN
        }

        fun fromName(name: String?): Intensity =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: GREEN

        /**
         * Lower PGA bound, in g, of each numeral from II to X.
         *
         * These are the Wald et al. (1999) PGA→instrumental-intensity breakpoints that USGS
         * ShakeMap uses (0.17, 1.4, 3.9, 9.2, 18, 34, 65, 124 %g), with the combined II–III
         * bin split at its geometric midpoint. That table is calibrated against Modified
         * Mercalli, not PEIS; the two scales run close enough from I to X to serve as a
         * point estimate, and the paper should say so if it quotes a numeral.
         */
        private val PEIS_BREAKS_G =
            doubleArrayOf(0.0017, 0.0049, 0.014, 0.039, 0.092, 0.18, 0.34, 0.65, 1.24)

        /**
         * Point estimate of the PEIS numeral (1–10) for a measured peak ground acceleration.
         *
         * The one source of truth for "Intensity V" as opposed to the band's "Intensity
         * V–VI". The breakpoints above are clamped into the band [fromMagnitude] assigns, so a
         * numeral can never contradict the colour the alarm used — the paper's bands win at
         * every boundary. A consequence worth knowing: IV is unreachable, because Green ends at
         * 0.010 g and the breakpoint for IV is 0.014 g.
         *
         * This is intensity — shaking where the sensor sits. It is never a magnitude; see
         * [Quake] for where magnitude comes from.
         */
        fun peisFromPga(g: Double): Int {
            val raw = 1 + PEIS_BREAKS_G.count { g >= it }
            return raw.coerceIn(fromMagnitude(g).peisRange)
        }
    }
}

private val PEIS_NUMERALS = arrayOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")

/** Roman numeral for a PEIS level, clamped to I–X. */
fun peisNumeral(level: Int): String = PEIS_NUMERALS[level.coerceIn(1, 10) - 1]

enum class ResponseStatus(val label: String) {
    SAFE("I'm Safe"),
    NEEDS_HELP("I Need Help"),
    NO_RESPONSE("No Response Yet");

    val wire: String get() = name.lowercase()

    companion object {
        fun fromName(name: String?): ResponseStatus =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: NO_RESPONSE
    }
}

enum class AlertSource(val label: String) {
    ESP32("Sensor"),
    SIMULATED("Simulation");

    val wire: String get() = name.lowercase()

    companion object {
        fun fromName(name: String?): AlertSource =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: ESP32
    }
}

enum class LinkRequestStatus(val label: String) {
    PENDING("Awaiting confirmation"),
    APPROVED("Confirmed"),
    DECLINED("Declined");

    val wire: String get() = name.lowercase()

    companion object {
        fun fromName(name: String?): LinkRequestStatus =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: PENDING
    }
}

data class LinkRequest(
    val id: String,
    val studentId: String,
    val studentName: String,
    val parentId: String,
    val parentName: String,
    val parentContact: String = "",

    /**
     * The guardian's mobile number, kept separate from [parentContact] because that one
     * resolves to email whenever an email exists — which is every email/password signup.
     * An emergency SMS needs a number specifically, and it is stored on the request so the
     * student's device has it offline, which is exactly when it will be needed.
     */
    val parentPhone: String = "",
    val status: LinkRequestStatus = LinkRequestStatus.PENDING,
    val requestedAt: Long = 0L,
    val respondedAt: Long? = null,
)

data class AlertRecord(
    val id: String,
    val intensity: Intensity,
    val magnitudeG: Double,
    val detectedAt: Long,
    val source: AlertSource,
    val nodeId: String? = null,
    val closed: Boolean = false,
) {
    /** PEIS numeral estimated from this alert's own PGA reading — "Intensity V". */
    val peisLevel: Int get() = Intensity.peisFromPga(magnitudeG)

    val peisText: String get() = "Intensity ${peisNumeral(peisLevel)}"
}

data class SafetyResponse(
    val alertId: String,
    val userId: String,
    val name: String,
    val status: ResponseStatus,
    val respondedAt: Long,
)

data class GeoPoint(val lat: Double, val lng: Double)

object SensorNodes {

    /**
     * Bogo City, Cebu — where the one deployed node sits. City-centre coordinates, good to a
     * few kilometres, which is far finer than the tens-of-kilometres radius the official
     * catalogue match works at. Give each node its own entry here if more are ever deployed.
     */
    val BOGO = GeoPoint(11.05, 124.00)

    fun locationOf(@Suppress("UNUSED_PARAMETER") nodeId: String?): GeoPoint = BOGO
}

/** An official seismological catalogue. Never PHIVOLCS — it has no public API. */
enum class QuakeCatalog(val label: String, val fullName: String) {
    EMSC("EMSC", "European-Mediterranean Seismological Centre"),
    USGS("USGS", "U.S. Geological Survey"),
}

/**
 * One event from an official catalogue. This is where **magnitude** comes from: energy at
 * the source, computed from many stations. The SIREN node cannot measure it — it measures
 * intensity where it sits — so the app never shows a magnitude from anywhere else.
 */
data class Quake(
    val id: String,
    val catalog: QuakeCatalog,
    val magnitude: Double,
    val magnitudeType: String,
    val region: String,
    val lat: Double,
    val lng: Double,
    val depthKm: Double,
    val time: Long,
    /** The agency the catalogue credits for the solution, e.g. "PHIV" when EMSC relays one. */
    val agency: String = "",
)

/** The outcome of cross-referencing a SIREN alert against the official catalogues. */
sealed interface FeedCheck {
    data object Checking : FeedCheck

    data class Confirmed(val quake: Quake, val distanceKm: Double) : FeedCheck

    /**
     * No catalogued event matches. [final] once the alert is old enough that the catalogues,
     * which run minutes behind, should have caught up — before that it only means "not yet".
     */
    data class NoMatch(val final: Boolean) : FeedCheck

    /** Neither catalogue could be reached. Says nothing about the event itself. */
    data object Unavailable : FeedCheck

    /** A Demo Mode drill: there is no real event to look for. */
    data object NotApplicable : FeedCheck
}

/**
 * A student's position, attached to one alert and only while it is active. Written to
 * `alerts/{alertId}/locations/{userId}` — a subcollection of its own rather than fields on
 * the response, so it can be locked down to guardians and adviser without also hiding the
 * roll call that the whole class reads.
 */
data class SharedLocation(
    val userId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val locatedAt: Long,
)

data class UserProfile(
    val uid: String,
    val name: String,
    val email: String = "",

    val phone: String = "",
    val role: Role = Role.STUDENT,
    val classId: String = "",
    val schoolId: String = "",

    val shortCode: String = "",
    val linkedStudentIds: List<String> = emptyList(),

    val photo: String = "",
) {

    val contact: String get() = email.ifBlank { phone }

    val initials: String
        get() = name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}

data class LinkedPerson(
    val uid: String,
    val name: String,
    val klass: String = "",
    val status: ResponseStatus = ResponseStatus.NO_RESPONSE,
    val respondedAt: Long? = null,
    val pending: Boolean = false,

    val photo: String = "",

    /** Where the student is, if they opted in and the current alert is still active. */
    val location: SharedLocation? = null,
) {
    val initials: String
        get() = name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}

data class EmergencyContact(
    val id: String,
    val name: String,
    val relation: String,
    val phone: String,
    val primary: Boolean = false,
)

val DefaultEmergencyContacts: List<EmergencyContact> = listOf(
    EmergencyContact(
        id = "default_bogo_police",
        name = "Bogo Police Station",
        relation = "Police",
        phone = "0905 600 2028",
        primary = true,
    ),
    EmergencyContact(
        id = "default_emergency_response",
        name = "Emergency Response Unit",
        relation = "Rescue / medical",
        phone = "0919 920 4635",
    ),
    EmergencyContact(
        id = "default_bogo_fire",
        name = "Bogo Fire Department",
        relation = "Fire",
        phone = "0917 127 9158",
    ),
)

data class SirenSettings(
    val criticalAlerts: Boolean = true,
    val vibration: Boolean = true,
    val contacts: List<EmergencyContact> = DefaultEmergencyContacts,

    /**
     * Text the student's linked guardians automatically when they tap "I need help".
     * On by default: someone trapped or injured is in no position to send messages by
     * hand, which is the whole point. It stays switchable because it spends the
     * student's own load and reaches real people.
     */
    val alertSmsEnabled: Boolean = true,

    /**
     * Whether the SEND_SMS prompt has already been shown once. Android stops presenting the
     * dialog after two refusals, so asking on every launch would burn that budget long
     * before the permission is ever needed.
     */
    val smsPermissionAsked: Boolean = false,

    val hasAccount: Boolean = false,

    /**
     * Share this phone's location with the student's confirmed guardians and adviser while an
     * alert is active. Off until the student turns it on: this tracks minors, so it is
     * opt-in, never continuous, and never in the background.
     */
    val shareLocationDuringAlerts: Boolean = false,

    /** Speak the alert once, after the first siren cycle. */
    val voiceAlerts: Boolean = true,
)
