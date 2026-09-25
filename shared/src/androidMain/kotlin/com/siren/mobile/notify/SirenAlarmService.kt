package com.siren.mobile.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.model.Intensity
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.platform.Platform
import java.util.Locale
import kotlin.math.ceil

class SirenAlarmService : Service() {

    companion object {
        private const val TAG = "SirenAlarm"

        const val ACTION_START = "com.siren.mobile.alarm.START"
        const val ACTION_STOP = "com.siren.mobile.alarm.STOP"
        const val ACTION_SAFE = "com.siren.mobile.alarm.SAFE"
        const val ACTION_HELP = "com.siren.mobile.alarm.HELP"

        const val EXTRA_ALERT_ID = "alertId"
        const val EXTRA_INTENSITY = "intensity"
        const val EXTRA_MAGNITUDE = "magnitude"
        const val EXTRA_TIMEOUT_MS = "timeoutMs"
        const val EXTRA_VIBRATE = "vibrate"
        const val EXTRA_SPEECH = "speech"

        private const val EXTRA_LAUNCH_ALERT_ID = "extra_alert_id"

        private const val CHANNEL_ALARM = "siren_alarm_playback"
        private const val NOTIFICATION_ID = 4102
        private const val WATCHDOG_INTERVAL_MS = 2_000L

        // USAGE_ALARM routes around the ringer and DND, but it still plays at whatever the
        // alarm stream is set to — and a phone left on zero is silent with no error. A real
        // alarm clock raises the stream itself; so does this, and puts it back afterwards.
        private const val VOLUME_FLOOR_RED = 0.9f
        private const val VOLUME_FLOOR_YELLOW = 0.6f

        /** Used when the player cannot report its own length. */
        private const val FALLBACK_LOOP_MS = 8_000L

        /**
         * The siren resumes after this even if the speech engine never reports that it has
         * finished. A spoken alert must never be the thing that silences the alarm.
         */
        private const val SPEECH_MAX_MS = 12_000L

        private const val UTTERANCE_ID = "siren-alert"

        @Volatile
        var soundResId: Int = 0

        @Volatile
        var smallIconResId: Int = 0

        @Volatile
        var activityClass: Class<*>? = null
    }

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var currentAlertId: String? = null
    private var priorAlarmVolume: Int? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var shouldRun = false

    private var wentForeground = false

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    /** True while the siren is paused for the spoken alert; the watchdog must not restart it. */
    private var speaking = false
    private var speechRunnable: Runnable? = null
    private val resumeAfterSpeech = Runnable { finishSpeech() }

    private val watchdog = object : Runnable {
        override fun run() {
            if (!shouldRun) return
            player?.let { p ->
                if (!speaking && !p.isPlaying) runCatching { p.start() }
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)

            ACTION_STOP -> stopEverything()

            ACTION_SAFE -> {
                currentAlertId?.let { SirenRepository.submitMyResponse(it, ResponseStatus.SAFE) }
                stopEverything()
            }

            ACTION_HELP -> {
                currentAlertId?.let { SirenRepository.submitMyResponse(it, ResponseStatus.NEEDS_HELP) }
                stopEverything()
            }

            else -> stopEverything()
        }

        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val alertId = intent.getStringExtra(EXTRA_ALERT_ID).orEmpty()
        val intensity = Intensity.fromName(intent.getStringExtra(EXTRA_INTENSITY))
        val magnitude = intent.getDoubleExtra(EXTRA_MAGNITUDE, 0.0)
        val timeout = intent.getLongExtra(EXTRA_TIMEOUT_MS, 0L)
        val vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)
        val speech = intent.getStringExtra(EXTRA_SPEECH)?.takeIf { it.isNotBlank() }

        // The push and the Firestore listener both start the alarm for the same alert. The
        // siren is paused while the alert is spoken, so "not playing" alone would let the
        // second start cut the speech off and restart everything.
        if (currentAlertId == alertId && (player?.isPlaying == true || speaking)) return

        currentAlertId = alertId
        shouldRun = true
        ensureChannel()
        if (!startInForeground(alertId, intensity, magnitude)) {

            stopSelf()
            return
        }

        acquireWakeLock()
        if (intensity != Intensity.GREEN) {
            wakeScreen()

            raiseAlertScreen(alertId)
        }
        requestFocus(intensity)
        raiseAlarmVolume(intensity)
        cancelSpeech()
        if (speech != null && intensity != Intensity.GREEN) prepareSpeech(speech)
        startPlayback(intensity)
        if (vibrate) startVibration(intensity)

        Platform.setAlarmActive(true)

        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)

        timeoutRunnable?.let(handler::removeCallbacks)
        if (timeout > 0L) {

            timeoutRunnable = Runnable { stopEverything() }.also {
                handler.postDelayed(it, timeout)
            }
        }
    }

    private fun startPlayback(intensity: Intensity) {
        if (soundResId == 0) {
            Log.w(TAG, "No alarm sound resource injected; alarm will be silent")
            return
        }
        runCatching {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()

                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                resources.openRawResourceFd(soundResId).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                isLooping = intensity != Intensity.GREEN
                setOnPreparedListener {
                    it.start()
                    scheduleSpeech(it.duration.toLong())
                }
                prepareAsync()
            }
        }.onFailure { Log.e(TAG, "Alarm playback failed", it) }
    }

    /**
     * Starts the speech engine as the alarm starts, so it has finished initialising by the
     * time the first siren cycle ends. Engine start-up takes a second or more on a cold
     * process, which is exactly the process a push has just woken.
     */
    private fun prepareSpeech(text: String) {
        pendingSpeech = text
        if (tts != null) return
        tts = runCatching {
            TextToSpeech(applicationContext) { status ->
                handler.post {
                    val engine = tts ?: return@post
                    if (status != TextToSpeech.SUCCESS) {
                        Log.w(TAG, "Speech engine unavailable; the alert will not be spoken")
                        return@post
                    }
                    // Philippine English first, then any English, then whatever is installed.
                    val locale = listOf(Locale("en", "PH"), Locale.US, Locale.ENGLISH)
                        .firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
                    locale?.let { engine.setLanguage(it) }
                    engine.setAudioAttributes(
                        AudioAttributes.Builder()
                            // The alarm stream, like the siren: audible through silent and DND.
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) {
                            handler.post { finishSpeech() }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            handler.post { finishSpeech() }
                        }
                    })
                    ttsReady = true
                }
            }
        }.onFailure { Log.w(TAG, "Could not create the speech engine", it) }.getOrNull()
    }

    /**
     * Speaks once, after the first full cycle of the siren rather than over it. The siren
     * holds exclusive audio focus on the alarm stream, so talking over it would either be
     * drowned out or fight it for focus; pausing it for a few seconds does neither.
     */
    private fun scheduleSpeech(loopMs: Long) {
        if (pendingSpeech == null) return
        speechRunnable?.let(handler::removeCallbacks)
        val delay = if (loopMs > 0) loopMs else FALLBACK_LOOP_MS
        speechRunnable = Runnable { speakNow() }.also { handler.postDelayed(it, delay) }
    }

    private fun speakNow() {
        val text = pendingSpeech ?: return
        val engine = tts
        if (!shouldRun || engine == null || !ttsReady) {
            // Not ready by the end of the first cycle: skip rather than hold the siren back.
            pendingSpeech = null
            return
        }
        pendingSpeech = null
        speaking = true
        runCatching { player?.pause() }
        val queued = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
        if (!queued) {
            finishSpeech()
            return
        }
        handler.postDelayed(resumeAfterSpeech, SPEECH_MAX_MS)
    }

    private fun finishSpeech() {
        handler.removeCallbacks(resumeAfterSpeech)
        if (!speaking) return
        speaking = false
        if (shouldRun) runCatching { player?.start() }
    }

    private fun cancelSpeech() {
        speechRunnable?.let(handler::removeCallbacks)
        speechRunnable = null
        handler.removeCallbacks(resumeAfterSpeech)
        pendingSpeech = null
        speaking = false
        runCatching { tts?.stop() }
    }

    private fun releaseSpeech() {
        cancelSpeech()
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
    }

    private fun requestFocus(intensity: Intensity) {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener {

                    if (intensity == Intensity.GREEN) stopEverything()
                }
                .build()
                .also { am.requestAudioFocus(it) }
        }
    }

    /**
     * Lifts the alarm stream to an audible floor if the user has it below one, remembering
     * the old value so [stopEverything] can put it back. Nothing is changed when the phone
     * is already loud enough, so the common case leaves the user's setting alone.
     *
     * `setStreamVolume` throws under some DND policies without ACCESS_NOTIFICATION_POLICY;
     * that is caught rather than requested, because playback itself already bypasses DND —
     * a phone that refuses the volume change still sounds at whatever it was set to.
     */
    private fun raiseAlarmVolume(intensity: Intensity) {
        if (intensity == Intensity.GREEN) return
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val fraction = if (intensity == Intensity.RED) VOLUME_FLOOR_RED else VOLUME_FLOOR_YELLOW
            val floor = ceil(max * fraction).toInt().coerceIn(1, max.coerceAtLeast(1))
            val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
            if (max > 0 && current < floor) {
                // Only the FIRST raise records the prior value. A Yellow alert followed by a
                // Red one would otherwise capture the 60% floor as the user's own setting
                // and "restore" them to it, ratcheting their alarm volume up permanently.
                if (priorAlarmVolume == null) priorAlarmVolume = current
                am.setStreamVolume(AudioManager.STREAM_ALARM, floor, 0)
            }
        }.onFailure { Log.w(TAG, "Could not raise the alarm stream volume", it) }
    }

    private fun restoreAlarmVolume() {
        val prior = priorAlarmVolume ?: return
        priorAlarmVolume = null
        runCatching {
            (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                ?.setStreamVolume(AudioManager.STREAM_ALARM, prior, 0)
        }
    }

    private fun startVibration(intensity: Intensity) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return

        val pattern = when (intensity) {
            Intensity.GREEN -> longArrayOf(0, 250)
            Intensity.YELLOW -> longArrayOf(0, 400, 200, 400, 200, 400)
            Intensity.RED -> longArrayOf(0, 800, 200, 800, 200, 1200, 300)
        }

        val repeat = if (intensity == Intensity.GREEN) -1 else 0
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat)) }
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "siren:alarm").apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isInteractive) return
            screenLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "siren:alarm-screen",
            ).apply {
                setReferenceCounted(false)
                acquire(30 * 1000L)
            }
        }
    }

    private fun fullScreenIntentAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { manager.canUseFullScreenIntent() }.getOrDefault(false)
    }

    private fun raiseAlertScreen(alertId: String) {
        val target = activityClass ?: return
        if (fullScreenIntentAllowed()) return
        if (!runCatching { Settings.canDrawOverlays(this) }.getOrDefault(false)) {
            Log.w(
                TAG,
                "Full-screen intent denied and no overlay grant: the alert can only be a " +
                    "notification. Grant either in Settings → Alert visibility.",
            )
            return
        }
        runCatching {
            startActivity(
                Intent(this, target)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_LAUNCH_ALERT_ID, alertId)
            )
        }.onFailure { Log.e(TAG, "Direct alert launch refused", it) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ALARM,
            "Earthquake alarm",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "The alarm that sounds until you confirm your safety status"
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC

            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun command(action: String): PendingIntent {
        val intent = Intent(this, SirenAlarmService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startInForeground(alertId: String, intensity: Intensity, magnitude: Double): Boolean {
        val open = activityClass?.let {
            PendingIntent.getActivity(
                this,
                alertId.hashCode(),
                Intent(this, it)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_LAUNCH_ALERT_ID, alertId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(if (smallIconResId != 0) smallIconResId else android.R.drawable.stat_sys_warning)
            .setContentTitle("Earthquake detected — ${intensity.levelText}")
            .setContentText("Confirm your status to silence the alarm.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)

            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)

            .addAction(0, "I'm safe", command(ACTION_SAFE))
            .addAction(0, "I need help", command(ACTION_HELP))
            .addAction(0, "Stop alarm", command(ACTION_STOP))

        open?.let {
            builder.setContentIntent(it)

            if (intensity != Intensity.GREEN) builder.setFullScreenIntent(it, true)
        }

        val notification = builder.build()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            wentForeground = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Foreground start refused; falling back to a notification", e)
            runCatching {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            }
            false
        }
    }

    private fun stopEverything() {
        shouldRun = false
        handler.removeCallbacks(watchdog)
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null
        releaseSpeech()

        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null

        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.cancel()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { req ->
                (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocusRequest(req)
            }
        }
        focusRequest = null
        restoreAlarmVolume()

        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        runCatching { if (screenLock?.isHeld == true) screenLock?.release() }
        screenLock = null

        currentAlertId = null
        Platform.setAlarmActive(false)

        if (wentForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            wentForeground = false
        } else {

            runCatching { NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID) }
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }
}
