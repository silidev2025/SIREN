package com.siren.mobile

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.platform.AndroidPlatformServices
import com.siren.mobile.platform.ProfilePhotoEncoder
import com.siren.mobile.platform.ProfilePhotoPicker
import com.siren.mobile.platform.SmsPermissionRequester
import com.siren.mobile.ui.App
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), ProfilePhotoPicker, SmsPermissionRequester {

    companion object {
        const val EXTRA_ALERT_ID = AndroidPlatformServices.EXTRA_ALERT_ID
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var pendingSmsPermission: CancellableContinuation<Boolean>? = null

    // SEND_SMS is asked for at the moment it is first needed — when a student taps
    // "I need help" — rather than at launch. Asking to send texts on first run, before
    // anyone has seen why, is the kind of prompt people refuse out of hand.
    private val smsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val waiting = pendingSmsPermission
            pendingSmsPermission = null
            if (waiting != null && waiting.isActive) waiting.resume(granted)
        }

    private var pendingPhoto: CancellableContinuation<String?>? = null

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val waiting = pendingPhoto
            pendingPhoto = null
            if (waiting == null || !waiting.isActive) return@registerForActivityResult

            lifecycleScope.launch {
                val encoded = uri?.let {
                    withContext(Dispatchers.IO) { ProfilePhotoEncoder.encode(this@MainActivity, it) }
                }
                if (waiting.isActive) waiting.resume(encoded)
            }
        }

    override fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun requestSmsPermission(): Boolean {
        if (hasSmsPermission()) return true
        return suspendCancellableCoroutine { cont ->
            pendingSmsPermission?.takeIf { it.isActive }?.resume(false)
            pendingSmsPermission = cont
            cont.invokeOnCancellation { pendingSmsPermission = null }
            runCatching { smsPermission.launch(android.Manifest.permission.SEND_SMS) }
                .onFailure {
                    pendingSmsPermission = null
                    if (cont.isActive) cont.resume(false)
                }
        }
    }

    override suspend fun pickProfilePhoto(): String? = suspendCancellableCoroutine { cont ->

        pendingPhoto?.takeIf { it.isActive }?.resume(null)
        pendingPhoto = cont
        cont.invokeOnCancellation { pendingPhoto = null }
        runCatching {
            pickPhoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }.onFailure {
            pendingPhoto = null
            if (cont.isActive) cont.resume(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Holding the splash until auth resolves is right on a normal launch and wrong on an
        // alert: a full-screen intent wakes the phone into a cold start, and keeping the
        // splash up would cover the alert screen for as long as Firebase takes to answer —
        // indefinitely if the device woke with no network. An alert paints immediately.
        val launchedForAlert = intent?.hasExtra(EXTRA_ALERT_ID) == true
        splash.setKeepOnScreenCondition { !launchedForAlert && !SirenRepository.authResolved.value }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        handleIntent(intent)

        setContent { App() }

        // handleIntent only fires for an intent that carries the extra, and it runs before
        // the alert has been fetched. Following the alert itself covers every route in —
        // full-screen intent, the service's own launch, a push arriving while the activity
        // is already alive — and, just as importantly, undoes the override afterwards:
        // setShowWhenLocked and FLAG_KEEP_SCREEN_ON persist, so without this the app sits
        // on the lock screen with the display pinned on long after the earthquake.
        lifecycleScope.launch {
            var sawAlert = false
            SirenRepository.incomingAlert.collect { alert ->
                if (alert != null) {
                    sawAlert = true
                    showOverLockScreen()
                } else if (sawAlert) {
                    sawAlert = false
                    clearLockScreenOverride()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val alertId = intent?.getStringExtra(EXTRA_ALERT_ID) ?: return
        showOverLockScreen()
        SirenRepository.showAlertById(alertId)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isKeyguardSecure == false) {
            runCatching { keyguard.requestDismissKeyguard(this, null) }
        }
    }

    private fun clearLockScreenOverride() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
