package com.siren.mobile.platform

import com.siren.mobile.model.Intensity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PhoneVerification(
    val phone: String,
    val token: String,
) {
    sealed interface Result {
        data class SignedIn(val uid: String, val phone: String) : Result
        data class Failed(val reason: String) : Result
    }
}

sealed interface PhoneCodeRequest {

    data class Sent(val verification: PhoneVerification) : PhoneCodeRequest

    data class AutoVerified(val uid: String, val phone: String) : PhoneCodeRequest

    data class Failed(val reason: String) : PhoneCodeRequest
}

data class SmsRecipient(val name: String, val phone: String)

/**
 * Outcome of an automatic SMS dispatch. Every field is needed to tell the student
 * something true: an earthquake is the worst moment to imply a message went out when it
 * did not, and "sent" here means handed to the radio, not delivered.
 */
data class SmsDispatchResult(
    val sent: Int = 0,
    val failed: Int = 0,
    val noNumber: Int = 0,
    /** The user was asked for permission and said no. */
    val permissionDenied: Boolean = false,
    /**
     * Permission was missing and could not even be requested — there was no Activity to ask
     * from, because the alert was answered from the notification while the app was not open.
     * Distinct from [permissionDenied]: telling someone they declined something they were
     * never asked is both wrong and unactionable.
     */
    val couldNotAsk: Boolean = false,
    val unsupported: Boolean = false,
) {
    val attempted: Int get() = sent + failed
}

/** One position fix from the phone's own location provider. */
data class GeoFix(
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val time: Long,
)

interface PlatformServices {

    val versionName: String

    fun vibrateForIntensity(intensity: Intensity)
    fun vibrateTap()
    fun vibrateConfirm()
    fun cancelVibration()

    fun showAlertNotification(alertId: String, intensity: Intensity, magnitudeG: Double)
    fun clearNotifications()

    /**
     * @param speech spoken once, after the first full cycle of the siren rather than over it,
     *   or null to stay silent apart from the siren. Composed in common code by `VoiceAlert`
     *   so both platforms say the same words.
     */
    fun startAlarm(
        alertId: String,
        intensity: Intensity,
        magnitudeG: Double,
        vibrate: Boolean,
        speech: String? = null,
    )

    fun stopAlarm()

    fun dial(phone: String)
    fun sendSms(phone: String)

    /**
     * Whether this platform can send an SMS without the user pressing send. Android can,
     * with the SEND_SMS permission. iOS has no such API at all — `MFMessageComposeViewController`
     * always requires a tap — so there it stays false and callers fall back to [sendSms].
     */
    val directSmsSupported: Boolean

    /**
     * Sends [body] to every recipient without further interaction, requesting the SEND_SMS
     * permission first if it has not been granted. Never throws: a failure to reach one
     * guardian must not stop the others, and the caller reports the tally to the student.
     */
    suspend fun sendSmsDirect(recipients: List<SmsRecipient>, body: String): SmsDispatchResult

    /**
     * Acquires the SMS permission ahead of time, while the app is open and an Activity is
     * available to ask from. Returns whether it is now granted.
     *
     * Necessary because the moment the permission is actually needed — a student answering
     * from the alarm notification on a locked phone — is a moment when no Activity exists
     * and no prompt can be shown. Asking only then means never asking.
     */
    suspend fun ensureSmsPermission(): Boolean

    /**
     * Posts a plain notification. Used to report the outcome of an emergency SMS, because
     * the in-app snackbar has no collector when the response came from the notification
     * actions with the app closed — the outcome would otherwise be discarded.
     */
    fun postPlainNotification(title: String, text: String)

    fun readSettingsJson(): String?
    fun writeSettingsJson(json: String)

    fun subscribeToAlertsTopic()

    fun nowMillis(): Long

    val phoneAuthSupported: Boolean

    suspend fun sendPhoneCode(phoneE164: String): PhoneCodeRequest

    suspend fun confirmPhoneCode(verification: PhoneVerification, code: String): PhoneVerification.Result

    fun canUseFullScreenIntent(): Boolean

    fun openFullScreenIntentSettings()

    fun openNotificationSettings()

    fun notificationsEnabled(): Boolean

    fun canLaunchAlertOverOtherApps(): Boolean

    fun openOverlaySettings()

    val photoPickerSupported: Boolean

    suspend fun pickProfilePhoto(): String?

    /**
     * GETs [url] and returns the body — empty for a 204 — or null on any failure. Never
     * throws. Sends a User-Agent naming SIREN, which the OpenStreetMap tile servers require
     * and the seismological feeds appreciate.
     */
    suspend fun httpGet(url: String): ByteArray?

    /** False where location sharing is not implemented, which hides the setting entirely. */
    val locationSupported: Boolean

    fun locationPermissionGranted(): Boolean

    /**
     * Asks for location permission while the app is open, the same way [ensureSmsPermission]
     * does and for the same reason: during an alert there may be no Activity to ask from.
     */
    suspend fun ensureLocationPermission(): Boolean

    /**
     * One fresh position fix, or null if none arrives within a few seconds. Never prompts —
     * only ever called while an alert is up, which is no moment for a permission dialog.
     */
    suspend fun currentLocation(): GeoFix?

    /** Hands a coordinate to the phone's own maps app. */
    fun openMap(lat: Double, lng: Double, label: String)
}

const val PROFILE_PHOTO_MAX_PX = 256

const val PROFILE_PHOTO_QUALITY = 80

private object NoOpPlatformServices : PlatformServices {
    override val versionName = "2.4.0"
    override fun vibrateForIntensity(intensity: Intensity) = Unit
    override fun vibrateTap() = Unit
    override fun vibrateConfirm() = Unit
    override fun cancelVibration() = Unit
    override fun showAlertNotification(alertId: String, intensity: Intensity, magnitudeG: Double) = Unit
    override fun clearNotifications() = Unit
    override fun startAlarm(
        alertId: String,
        intensity: Intensity,
        magnitudeG: Double,
        vibrate: Boolean,
        speech: String?,
    ) = Unit
    override fun stopAlarm() = Unit
    override fun dial(phone: String) = Unit
    override fun sendSms(phone: String) = Unit
    override val directSmsSupported = false
    override suspend fun sendSmsDirect(
        recipients: List<SmsRecipient>,
        body: String,
    ): SmsDispatchResult = SmsDispatchResult(unsupported = true, failed = recipients.size)

    override suspend fun ensureSmsPermission() = false
    override fun postPlainNotification(title: String, text: String) = Unit

    override fun readSettingsJson(): String? = null
    override fun writeSettingsJson(json: String) = Unit
    override fun subscribeToAlertsTopic() = Unit
    override fun nowMillis(): Long = 0L
    override val phoneAuthSupported = false
    override suspend fun sendPhoneCode(phoneE164: String): PhoneCodeRequest =
        PhoneCodeRequest.Failed("Phone sign-up is not available on this device.")

    override suspend fun confirmPhoneCode(
        verification: PhoneVerification,
        code: String,
    ): PhoneVerification.Result =
        PhoneVerification.Result.Failed("Phone sign-up is not available on this device.")

    override fun canUseFullScreenIntent() = true
    override fun openFullScreenIntentSettings() = Unit
    override fun openNotificationSettings() = Unit
    override fun notificationsEnabled() = true
    override fun canLaunchAlertOverOtherApps() = true
    override fun openOverlaySettings() = Unit
    override val photoPickerSupported = false
    override suspend fun pickProfilePhoto(): String? = null
    override suspend fun httpGet(url: String): ByteArray? = null
    override val locationSupported = false
    override fun locationPermissionGranted() = false
    override suspend fun ensureLocationPermission() = false
    override suspend fun currentLocation(): GeoFix? = null
    override fun openMap(lat: Double, lng: Double, label: String) = Unit
}

object Platform {
    private var impl: PlatformServices = NoOpPlatformServices

    private val _alarmActive = MutableStateFlow(false)

    val alarmActive: StateFlow<Boolean> = _alarmActive.asStateFlow()

    fun install(services: PlatformServices) {
        impl = services
    }

    fun setAlarmActive(active: Boolean) {
        _alarmActive.value = active
    }

    val services: PlatformServices get() = impl
}
