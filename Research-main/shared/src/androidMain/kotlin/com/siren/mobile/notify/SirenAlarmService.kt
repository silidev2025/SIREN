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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.model.Intensity
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.platform.Platform
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

        private const val EXTRA_LAUNCH_ALERT_ID = "extra_alert_id"

        private const val CHANNEL_ALARM = "siren_alarm_playback"
        private const val NOTIFICATION_ID = 4102
        private const val WATCHDOG_INTERVAL_MS = 2_000L

        // USAGE_ALARM routes around the ringer and DND, but it still plays at whatever the
        // alarm stream is set to — and a phone left on zero is silent with no error. A real
        // alarm clock raises the stream itself; so does this, and puts it back afterwards.
        private const val VOLUME_FLOOR_RED = 0.9f
        private const val VOLUME_FLOOR_YELLOW = 0.6f

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

    private val watchdog = object : Runnable {
        override fun run() {
            if (!shouldRun) return
            player?.let { p ->
                if (!p.isPlaying) runCatching { p.start() }
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

        if (currentAlertId == alertId && player?.isPlaying == true) return

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
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.onFailure { Log.e(TAG, "Alarm playback failed", it) }
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
