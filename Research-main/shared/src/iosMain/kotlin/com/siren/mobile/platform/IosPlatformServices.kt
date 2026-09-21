package com.siren.mobile.platform

import com.siren.mobile.model.Intensity
import com.siren.mobile.util.asGSpaced
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
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

    override fun startAlarm(alertId: String, intensity: Intensity, magnitudeG: Double, vibrate: Boolean) {
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
        }
    }

    override fun stopAlarm() {
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
}
