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

    companion object {
        fun fromMagnitude(g: Double): Intensity = when {
            g >= 0.120 -> RED
            g >= 0.010 -> YELLOW
            else -> GREEN
        }

        fun fromName(name: String?): Intensity =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: GREEN
    }
}

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
)

data class SafetyResponse(
    val alertId: String,
    val userId: String,
    val name: String,
    val status: ResponseStatus,
    val respondedAt: Long,
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
)
