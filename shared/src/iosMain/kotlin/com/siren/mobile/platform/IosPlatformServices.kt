package com.siren.mobile.platform

import com.siren.mobile.model.Intensity
import com.siren.mobile.util.asGSpaced
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.Foundation.timeIntervalSince1970
import platform.posix.memcpy
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackTypeSuccess
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionCriticalAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

class IosPlatformServices(
    override val versionName: String,
) : PlatformServices {

    private companion object {
        const val KEY_SETTINGS = "siren_settings_json"

        const val SYSTEM_SOUND_VIBRATE: UInt = 4095u
    }

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun vibrateForIntensity(intensity: Intensity) {
        val repeats = when (intensity) {
            Intensity.GREEN -> 1
            Intensity.YELLOW -> 3
            Intensity.RED -> 6
        }
        val style = when (intensity) {
            Intensity.GREEN -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
            Intensity.YELLOW -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
            Intensity.RED -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
        }
        val generator = UIImpactFeedbackGenerator(style)
        generator.prepare()
        repeat(repeats) {
            generator.impactOccurred()
            AudioServicesPlaySystemSound(SYSTEM_SOUND_VIBRATE)
        }
    }

    override fun vibrateTap() {
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
            .also { it.prepare() }
            .impactOccurred()
    }

    override fun vibrateConfirm() {
        UINotificationFeedbackGenerator()
            .also { it.prepare() }
            .notificationOccurred(UINotificationFeedbackTypeSuccess)
    }

    override fun cancelVibration() = Unit

    private var alarmPlayer: AVAudioPlayer? = null

    private val synthesizer = AVSpeechSynthesizer()
    private var speechTimer: NSTimer? = null
    private var restoreTimer: NSTimer? = null

    override fun startAlarm(
        alertId: String,
        intensity: Intensity,
        magnitudeG: Double,
        vibrate: Boolean,
        speech: String?,
    ) {
        showAlertNotification(alertId, intensity, magnitudeG)
        if (vibrate) vibrateForIntensity(intensity)
        if (intensity == Intensity.GREEN) return

        val url = NSBundle.mainBundle.URLForResource("siren_alarm", "wav") ?: return
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
            AVAudioSession.sharedInstance().setActive(true, null)
            alarmPlayer = AVAudioPlayer(contentsOfURL = url, error = null).apply {

                numberOfLoops = if (intensity == Intensity.RED) -1 else 3
                prepareToPlay()
                play()
            }
            Platform.setAlarmActive(true)
            speech?.let { scheduleSpeech(it, alarmPlayer?.duration ?: 0.0) }
        }
    }

    /**
     * Speaks once after the first siren cycle, with the siren ducked underneath rather than
     * talked over. Ducked, not paused as on Android: without a synthesizer delegate there is
     * no reliable "finished" callback to resume on, so the volume comes back on a timer.
     */
    private fun scheduleSpeech(text: String, loopSeconds: Double) {
        cancelSpeech()
        val delay = if (loopSeconds > 0.0) loopSeconds else 8.0
        speechTimer = NSTimer.scheduledTimerWithTimeInterval(delay, repeats = false) { _ ->
            val player = alarmPlayer ?: return@scheduledTimerWithTimeInterval
            player.volume = 0.15f
            val utterance = AVSpeechUtterance.speechUtteranceWithString(text).apply {
                voice = AVSpeechSynthesisVoice.voiceWithLanguage("en-US")
            }
            synthesizer.speakUtterance(utterance)
            restoreTimer = NSTimer.scheduledTimerWithTimeInterval(7.0, repeats = false) { _ ->
                alarmPlayer?.volume = 1.0f
            }
        }
    }

    private fun cancelSpeech() {
        speechTimer?.invalidate()
        speechTimer = null
        restoreTimer?.invalidate()
        restoreTimer = null
        runCatching { synthesizer.stopSpeakingAtBoundary(platform.AVFAudio.AVSpeechBoundary.AVSpeechBoundaryImmediate) }
    }

    override fun stopAlarm() {
        cancelSpeech()
        runCatching {
            alarmPlayer?.stop()
            alarmPlayer = null
            AVAudioSession.sharedInstance().setActive(false, null)
        }
        Platform.setAlarmActive(false)
    }

    fun requestNotificationPermission() {

        val options = UNAuthorizationOptionAlert or
            UNAuthorizationOptionSound or
            UNAuthorizationOptionBadge or
            UNAuthorizationOptionCriticalAlert
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(options) { _, _ -> }
    }

    override fun showAlertNotification(alertId: String, intensity: Intensity, magnitudeG: Double) {

        val level = intensity.levelText
        val reading = magnitudeG.asGSpaced(3)
        val content = UNMutableNotificationContent().apply {
            setTitle(
                if (intensity == Intensity.GREEN) "Minor tremor detected — $level"
                else "Earthquake detected — $level"
            )
            setBody(
                if (intensity == Intensity.GREEN) {
                    "No action needed. Logged for the record. Peak ground acceleration $reading."
                } else {
                    "Drop, cover, hold on. Tap to confirm your status. Peak ground acceleration $reading."
                }
            )
            setSound(
                if (intensity == Intensity.RED) UNNotificationSound.defaultCriticalSound()
                else UNNotificationSound.defaultSound()
            )
            setUserInfo(mapOf("alertId" to alertId))
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = alertId,
            content = content,
            trigger = null,
        )
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request) { _ -> }
    }

    override fun clearNotifications() {
        UNUserNotificationCenter.currentNotificationCenter()
            .removeAllDeliveredNotifications()
    }

    private fun open(scheme: String, phone: String) {
        val digits = phone.filter { it.isDigit() || it == '+' }
        val url = NSURL.URLWithString("$scheme:$digits") ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>()) { }
    }

    override fun dial(phone: String) = open("tel", phone)

    override fun sendSms(phone: String) = open("sms", phone)

    // iOS has no API for sending an SMS without the user pressing send —
    // MFMessageComposeViewController always presents its own UI and requires the tap. There
    // is no entitlement that changes this, so the automatic help text is Android-only and
    // the repository falls back to recording the response in the app.
    override val directSmsSupported: Boolean = false

    override suspend fun sendSmsDirect(
        recipients: List<SmsRecipient>,
        body: String,
    ): SmsDispatchResult = SmsDispatchResult(unsupported = true, failed = recipients.size)

    override suspend fun ensureSmsPermission(): Boolean = false

    override fun postPlainNotification(title: String, text: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(text)
            setSound(UNNotificationSound.defaultSound())
        }
        // Unique per post: an identifier derived from the title would let a later outcome
        // silently replace an earlier one, so "2 guardians texted" could erase "no guardian
        // could be texted".
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = "siren_status_${NSUUID().UUIDString}",
            content = content,
            trigger = null,
        )
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request) { _ -> }
    }

    override fun readSettingsJson(): String? = defaults.stringForKey(KEY_SETTINGS)

    override fun writeSettingsJson(json: String) {
        defaults.setObject(json, KEY_SETTINGS)
    }

    override fun subscribeToAlertsTopic() = Unit

    override fun nowMillis(): Long =
        (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    override val phoneAuthSupported: Boolean = false

    override suspend fun sendPhoneCode(phoneE164: String): PhoneCodeRequest =
        PhoneCodeRequest.Failed("Phone sign-up isn't available on iOS yet. Use email.")

    override suspend fun confirmPhoneCode(
        verification: PhoneVerification,
        code: String,
    ): PhoneVerification.Result =
        PhoneVerification.Result.Failed("Phone sign-up isn't available on iOS yet. Use email.")

    override fun canUseFullScreenIntent(): Boolean = true

    override fun openFullScreenIntentSettings() = openAppSettings()

    override fun openNotificationSettings() = openAppSettings()

    private fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>()) { }
    }

    override fun notificationsEnabled(): Boolean = true

    override fun canLaunchAlertOverOtherApps(): Boolean = true

    override fun openOverlaySettings() = openAppSettings()

    override val photoPickerSupported: Boolean = false

    override suspend fun pickProfilePhoto(): String? = null

    override suspend fun httpGet(url: String): ByteArray? = suspendCancellableCoroutine { cont ->
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest.requestWithURL(nsUrl).apply {
            setTimeoutInterval(15.0)
            // OpenStreetMap's tile policy requires an identifying User-Agent.
            setValue("SIREN/$versionName (school earthquake-alert research)", forHTTPHeaderField = "User-Agent")
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            val status = (response as? NSHTTPURLResponse)?.statusCode ?: 0L
            val body = when {
                error != null -> null
                status == 204L -> ByteArray(0)
                status in 200L..299L -> data?.toByteArray() ?: ByteArray(0)
                else -> null
            }
            if (cont.isActive) cont.resume(body)
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
        return bytes
    }

    // Not implemented on iOS yet: CLLocationManager needs a delegate object and an
    // Info.plist usage string, neither of which can be verified without a Mac. False hides
    // the setting, so an iOS student is never told their location is shared when it is not.
    override val locationSupported: Boolean = false

    override fun locationPermissionGranted(): Boolean = false

    override suspend fun ensureLocationPermission(): Boolean = false

    override suspend fun currentLocation(): GeoFix? = null

    override fun openMap(lat: Double, lng: Double, label: String) {
        val url = NSURL.URLWithString("https://maps.apple.com/?ll=$lat,$lng&q=$lat,$lng") ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>()) { }
    }
}
