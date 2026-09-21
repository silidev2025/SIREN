package com.siren.mobile.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.siren.mobile.data.LinkResult
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.model.LinkRequestStatus
import com.siren.mobile.model.Role
import com.siren.mobile.platform.Platform
import com.siren.mobile.platform.PhoneCodeRequest
import com.siren.mobile.platform.PhoneVerification
import com.siren.mobile.ui.screens.AlertScreen
import com.siren.mobile.ui.screens.DemoScreen
import com.siren.mobile.ui.screens.EditProfileScreen
import com.siren.mobile.ui.screens.EmergencyContactsScreen
import com.siren.mobile.ui.screens.GuardiansScreen
import com.siren.mobile.ui.screens.HistoryScreen
import com.siren.mobile.ui.screens.LiveSafetyDashboardScreen
import com.siren.mobile.ui.screens.LoginScreen
import com.siren.mobile.ui.screens.ParentDashboardScreen
import com.siren.mobile.ui.screens.ParentLinkingScreen
import com.siren.mobile.ui.screens.RoleSelectionScreen
import com.siren.mobile.ui.screens.SafetyConfirmationScreen
import com.siren.mobile.ui.screens.SafetyGuideScreen
import com.siren.mobile.ui.screens.SettingsScreen
import com.siren.mobile.ui.screens.SignUpScreen
import com.siren.mobile.ui.screens.SplashScreen
import com.siren.mobile.ui.screens.StudentDashboardScreen
import com.siren.mobile.ui.screens.TeacherDashboardScreen
import com.siren.mobile.ui.theme.SirenTheme
import kotlinx.coroutines.launch

private sealed interface Dest {
    data object Home : Dest
    data object People : Dest
    data object History : Dest
    data object Settings : Dest
    data object Link : Dest
    data object Demo : Dest
    data object Contacts : Dest
    data object Guide : Dest
    data object Guardians : Dest
    data object Profile : Dest
    data class Live(val alertId: String) : Dest
}

private data class NavItem(val dest: Dest, val label: String, val icon: ImageVector)

private data class AlertPermissions(val notifications: Boolean, val fullScreen: Boolean)

private enum class NavDirection { Forward, Back, Tab }

@Composable
fun App() {
    val settings by SirenRepository.settings.collectAsState()
    SirenTheme {
        AppContent()
    }
}

@Composable
private fun AppContent() {
    // The alert is drawn over everything and deliberately sits OUTSIDE the auth gates in
    // AppShell. A full-screen intent wakes a locked phone into a cold start, where
    // `authResolved` is false and the user document has not arrived yet — so the shell
    // renders the splash, then a spinner. That is what an earthquake used to look like on a
    // locked phone: the alarm sounding with nothing on screen to explain it or stop it.
    Box(Modifier.fillMaxSize()) {
        AppShell()
        AlertOverlay()
    }
}

@Composable
private fun AlertOverlay() {
    val repo = SirenRepository
    val incomingAlert by repo.incomingAlert.collectAsState()
    val myResponses by repo.myResponses.collectAsState()
    val settings by repo.settings.collectAsState()

    // The overlay deliberately sits outside the auth gates, which means it can render with
    // nobody signed in — the `alerts` topic reaches every install. A status cannot be
    // recorded in that state, so the screen must not offer to record one.
    //
    // Gated on the auth session ALONE, deliberately. An earlier version also required the
    // Firestore profile document, which hid the response buttons from a genuinely signed-in
    // student for as long as that snapshot took — on a push cold start, the exact scenario
    // this overlay exists for. `submitMyResponse` needs only `auth.currentUser`, which is
    // restored from disk before any network call, so this is the honest condition.
    val signedIn by repo.signedIn.collectAsState()
    val canRespond = signedIn

    val incoming = incomingAlert

    // Without this a Back press finishes the Activity and leaves the alarm looping behind a
    // blank keyguard, with no way back to the alert short of finding the notification. The
    // three deliberate exits are the buttons on the alert itself; Back is not one of them.
    PlatformBackHandler(enabled = incoming != null) { }

    AnimatedVisibility(
        visible = incoming != null,
        enter = fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(140)),
    ) {
        if (incoming != null) {
            var confirming by remember(incoming.id) { mutableStateOf(false) }
            Box(
                Modifier
                    .fillMaxSize()

                    .background(MaterialTheme.colorScheme.background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
            ) {
                if (confirming && canRespond) {
                    SafetyConfirmationScreen(
                        alert = incoming,
                        myResponse = myResponses[incoming.id],
                        onRespond = { repo.submitMyResponse(incoming.id, it) },
                        onDone = { repo.consumeIncomingAlert() },
                        onBack = { confirming = false },
                    )
                } else {
                    AlertScreen(
                        alert = incoming,
                        vibrationEnabled = settings.vibration,
                        myResponse = myResponses[incoming.id],
                        canRespond = canRespond,
                        onRespond = { repo.submitMyResponse(incoming.id, it) },
                        onConfirmStatus = { confirming = true },
                        onDismiss = { repo.consumeIncomingAlert() },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppShell() {
    val repo = SirenRepository
    val scope = rememberCoroutineScope()

    val authResolved by repo.authResolved.collectAsState()
    val signedIn by repo.signedIn.collectAsState()
    val user by repo.user.collectAsState()
    val alerts by repo.alerts.collectAsState()
    val alertsLoaded by repo.alertsLoaded.collectAsState()
    val myResponses by repo.myResponses.collectAsState()
    val roster by repo.roster.collectAsState()
    val settings by repo.settings.collectAsState()
    val online by repo.online.collectAsState()
    val authLoading by repo.authLoading.collectAsState()
    val authError by repo.authError.collectAsState()

    val guardians by repo.guardians.collectAsState()
    val linkRequests by repo.linkRequests.collectAsState()
    val working by repo.working.collectAsState()

    val pendingLinkRequests = linkRequests.count { it.status == LinkRequestStatus.PENDING }

    var phoneVerification by remember { mutableStateOf<PhoneVerification?>(null) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        repo.events.collect { snackbar.showSnackbar(it.text) }
    }

    if (!authResolved) {
        SplashScreen(Platform.services.versionName)
        return
    }

    if (!signedIn) {
        AuthFlow(

            startOnSignUp = !settings.hasAccount,
            loading = authLoading,
            error = authError,
            phoneSupported = Platform.services.phoneAuthSupported,
            codeSent = phoneVerification != null,
            onSignIn = { email, pw -> scope.launch { repo.signIn(email, pw) } },
            onSignUp = { name, email, pw, phone, role ->
                scope.launch { repo.signUp(name, email, pw, phone, role) }
            },
            onSendCode = { name, phone, role ->
                scope.launch {
                    when (val result = repo.sendPhoneCode(phone)) {
                        is PhoneCodeRequest.Sent -> phoneVerification = result.verification
                        is PhoneCodeRequest.AutoVerified -> {
                            phoneVerification = null
                            repo.completeAutoVerifiedPhone(result.uid, result.phone, name, role)
                        }

                        is PhoneCodeRequest.Failed -> phoneVerification = null
                    }
                }
            },
            onVerifyCode = { name, code, role ->
                phoneVerification?.let { v ->
                    scope.launch {
                        if (repo.verifyPhoneCode(v, code, name, role)) phoneVerification = null
                    }
                }
            },
            onCancelPhone = {
                phoneVerification = null
                repo.clearAuthError()
            },
            onForgot = { email -> scope.launch { repo.resetPassword(email) } },
            onClearError = { repo.clearAuthError() },
        )
        return
    }

    val profile = user
    if (profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val backStack = remember { mutableStateListOf<Dest>(Dest.Home) }
    var direction by remember { mutableStateOf(NavDirection.Tab) }
    val current = backStack.last()

    fun push(d: Dest) {
        direction = NavDirection.Forward
        backStack.add(d)
    }

    fun pop() {
        if (backStack.size > 1) {
            direction = NavDirection.Back
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun selectTab(d: Dest) {
        direction = NavDirection.Tab
        backStack.clear()
        backStack.add(d)
    }

    // Disabled while an alert is up, so this cannot out-rank the overlay's own swallow-back
    // and quietly navigate the hidden nav stack underneath it.
    val alertShowing by repo.incomingAlert.collectAsState()
    PlatformBackHandler(enabled = backStack.size > 1 && alertShowing == null) { pop() }

    fun addStudentToClass(code: String) {
        scope.launch {
            val msg = when (val result = repo.linkStudentToClass(code)) {
                is LinkResult.Success -> "${result.studentName} added to ${profile.classId}."
                LinkResult.NotFound -> "No student found for that code."
                LinkResult.AlreadyLinked -> "That student is already in your class."
                is LinkResult.Failed -> result.reason

                is LinkResult.Requested, is LinkResult.AlreadyRequested -> "Student added."
            }
            snackbar.showSnackbar(msg)
        }
    }

    // SEND_SMS has to be granted while the app is open, because the moment it is actually
    // needed — a student answering from the alarm notification on a locked phone — there is
    // no Activity to prompt from and the request silently cannot be made. Asked once the
    // student actually has a guardian to text, so it is never a bare first-run prompt.
    //
    // Asked ONCE per install, not once per launch: Android stops showing the dialog after two
    // refusals, so re-asking on every launch spends that budget before the permission is ever
    // needed. And never while an alert is up — prompting then pauses the Activity, which is
    // exactly what makes a concurrent emergency dispatch report that it could not ask.
    val hasGuardianToText = guardians.isNotEmpty()
    val alertOnScreen = alertShowing != null
    LaunchedEffect(hasGuardianToText, settings.alertSmsEnabled, settings.smsPermissionAsked, alertOnScreen) {
        if (profile.role == Role.STUDENT &&
            hasGuardianToText &&
            settings.alertSmsEnabled &&
            !settings.smsPermissionAsked &&
            !alertOnScreen &&
            Platform.services.directSmsSupported
        ) {
            repo.updateSettings { it.copy(smsPermissionAsked = true) }
            Platform.services.ensureSmsPermission()
        }
    }

    val permissionsChecked = remember(current) {
        AlertPermissions(
            notifications = Platform.services.notificationsEnabled(),
            fullScreen = Platform.services.canUseFullScreenIntent(),
        )
    }

    val tabs = when (profile.role) {
        Role.STUDENT -> listOf(
            NavItem(Dest.Home, "Home", Icons.Filled.Home),
            NavItem(Dest.History, "History", Icons.Filled.History),
            NavItem(Dest.Settings, "Settings", Icons.Filled.Settings),
        )

        Role.TEACHER -> listOf(
            NavItem(Dest.Home, "Overview", Icons.Filled.Dashboard),
            NavItem(Dest.People, "Roster", Icons.Filled.Groups),
            NavItem(Dest.History, "History", Icons.Filled.History),
            NavItem(Dest.Settings, "Settings", Icons.Filled.Settings),
        )

        Role.PARENT -> listOf(
            NavItem(Dest.Home, "Home", Icons.Filled.Home),
            NavItem(Dest.People, "Children", Icons.Filled.FamilyRestroom),
            NavItem(Dest.History, "History", Icons.Filled.History),
            NavItem(Dest.Settings, "Settings", Icons.Filled.Settings),
        )
    }

    val showBottomBar = tabs.any { it.dest == current }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { item ->
                        NavigationBarItem(
                            selected = current == item.dest,
                            onClick = { selectTab(item.dest) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            AnimatedContent(
                targetState = current,
                label = "screen",
                transitionSpec = {

                    when (direction) {
                        NavDirection.Tab ->
                            fadeIn(tween(180)) togetherWith fadeOut(tween(120))

                        NavDirection.Forward ->
                            (slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(200))) togetherWith
                                fadeOut(tween(140))

                        NavDirection.Back ->
                            (slideInHorizontally(tween(260)) { -it / 6 } + fadeIn(tween(200))) togetherWith
                                fadeOut(tween(140))
                    }
                },
            ) { dest ->
                when (dest) {
                    Dest.Home -> when (profile.role) {
                        Role.STUDENT -> StudentDashboardScreen(
                            user = profile,
                            alerts = alerts,
                            myResponses = myResponses,
                            guardians = guardians,
                            pendingGuardianRequests = pendingLinkRequests,
                            online = online,
                            loading = !alertsLoaded,
                            onOpenHistory = { selectTab(Dest.History) },
                            onOpenContacts = { push(Dest.Contacts) },
                            onOpenSettings = { selectTab(Dest.Settings) },
                            onOpenGuide = { push(Dest.Guide) },
                            onOpenGuardians = { push(Dest.Guardians) },
                            onOpenAlert = { repo.showAlertById(it.id, userInitiated = true) },
                        )

                        Role.TEACHER -> TeacherDashboardScreen(
                            user = profile,
                            roster = roster,
                            activeAlert = alerts.firstOrNull(),
                            online = online,
                            loading = !alertsLoaded,
                            working = working,
                            onOpenLive = { alerts.firstOrNull()?.let { push(Dest.Live(it.id)) } },
                            onOpenHistory = { selectTab(Dest.History) },
                            onOpenGuide = { push(Dest.Guide) },
                            onOpenDemo = { push(Dest.Demo) },
                            onAddStudent = { code -> addStudentToClass(code) },
                            onRemoveStudent = { repo.removeStudentFromClass(it) },
                            onEditProfile = { push(Dest.Profile) },
                        )

                        Role.PARENT -> ParentDashboardScreen(
                            user = profile,
                            children = roster,
                            pendingRequests = pendingLinkRequests,
                            online = online,
                            loading = !alertsLoaded,
                            onLinkStudent = { push(Dest.Link) },
                            onOpenGuide = { push(Dest.Guide) },
                            onOpenDemo = { push(Dest.Demo) },
                            onCall = { Platform.services.dial(it) },
                        )
                    }

                    Dest.People -> when (profile.role) {
                        Role.TEACHER -> TeacherDashboardScreen(
                            user = profile,
                            roster = roster,
                            activeAlert = alerts.firstOrNull(),
                            online = online,
                            loading = !alertsLoaded,
                            working = working,
                            onOpenLive = { alerts.firstOrNull()?.let { push(Dest.Live(it.id)) } },
                            onOpenHistory = { selectTab(Dest.History) },
                            onOpenGuide = { push(Dest.Guide) },
                            onOpenDemo = { push(Dest.Demo) },
                            onAddStudent = { code -> addStudentToClass(code) },
                            onRemoveStudent = { repo.removeStudentFromClass(it) },
                            onEditProfile = { push(Dest.Profile) },
                        )

                        else -> ParentDashboardScreen(
                            user = profile,
                            children = roster,
                            pendingRequests = pendingLinkRequests,
                            online = online,
                            loading = !alertsLoaded,
                            onLinkStudent = { push(Dest.Link) },
                            onOpenGuide = { push(Dest.Guide) },
                            onOpenDemo = { push(Dest.Demo) },
                            onCall = { Platform.services.dial(it) },
                        )
                    }

                    Dest.History -> HistoryScreen(
                        alerts = alerts,
                        myResponses = myResponses,
                        loading = !alertsLoaded,
                    )

                    Dest.Settings -> SettingsScreen(
                        user = profile,
                        settings = settings,
                        versionName = Platform.services.versionName,
                        fullScreenAlertsAllowed = permissionsChecked.fullScreen,
                        notificationsAllowed = permissionsChecked.notifications,
                        onUpdateSettings = { repo.updateSettings(it) },
                        onOpenContacts = { push(Dest.Contacts) },
                        onOpenGuide = { push(Dest.Guide) },
                        onEditProfile = { push(Dest.Profile) },
                        onChangeRole = { scope.launch { repo.updateRole(it) } },
                        onFixFullScreenAlerts = { Platform.services.openFullScreenIntentSettings() },
                        onFixNotifications = { Platform.services.openNotificationSettings() },
                        onSignOut = { repo.signOut() },
                    )

                    Dest.Profile -> EditProfileScreen(
                        user = profile,
                        working = working,
                        photoPickerSupported = Platform.services.photoPickerSupported,
                        onSave = { name, classId, phone ->
                            scope.launch {
                                if (repo.updateProfile(name, classId, phone)) pop()
                            }
                        },
                        onChangePhoto = { scope.launch { repo.changeProfilePhoto() } },
                        onRemovePhoto = { scope.launch { repo.changeProfilePhoto(remove = true) } },
                        onBack = { pop() },
                    )

                    Dest.Guardians -> GuardiansScreen(
                        shortCode = profile.shortCode,
                        guardians = guardians,
                        requests = linkRequests,
                        working = working,
                        eventActive = alerts.firstOrNull()?.closed == false,
                        onRespond = { id, approve ->
                            scope.launch { repo.respondToLinkRequest(id, approve) }
                        },
                        onRevoke = { repo.revokeGuardian(it) },
                        onBack = { pop() },
                    )

                    Dest.Link -> ParentLinkingScreen(
                        linked = roster,
                        requests = linkRequests,
                        working = working,
                        onLink = { code ->
                            scope.launch {
                                val msg = when (val result = repo.requestLink(code)) {
                                    is LinkResult.Requested ->
                                        "Request sent to ${result.studentName}. They need to confirm it's you."

                                    is LinkResult.AlreadyRequested ->
                                        "${result.studentName} hasn't answered your last request yet."

                                    is LinkResult.Success -> "Linked ${result.studentName}"
                                    LinkResult.NotFound -> "No student found for that code."
                                    LinkResult.AlreadyLinked -> "That student is already linked."
                                    is LinkResult.Failed -> result.reason
                                }
                                snackbar.showSnackbar(msg)
                            }
                        },
                        onUnlink = { repo.unlinkStudent(it) },
                        onBack = { pop() },
                    )

                    Dest.Demo -> DemoScreen(
                        alerts = alerts,
                        onTrigger = { repo.simulateAlert(it) },
                        onBack = { pop() },
                    )

                    Dest.Contacts -> EmergencyContactsScreen(
                        contacts = settings.contacts,
                        onAdd = { repo.addEmergencyContact(it) },
                        onRemove = { repo.removeEmergencyContact(it) },
                        onCall = { Platform.services.dial(it) },
                        onText = { Platform.services.sendSms(it) },
                        onRestoreDefaults = { repo.restoreDefaultContacts() },
                        onBack = { pop() },
                    )

                    Dest.Guide -> SafetyGuideScreen(onBack = { pop() })

                    is Dest.Live -> {
                        val alert = alerts.firstOrNull { it.id == dest.alertId }
                        if (alert == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("That event is no longer available.")
                            }
                        } else {
                            LiveSafetyDashboardScreen(
                                alert = alert,
                                roster = roster,
                                onCloseEvent = { repo.closeEvent(alert.id) },
                                onBack = { pop() },
                            )
                        }
                    }
                }
            }
        }
    }

    var alertVisibilityAsked by remember { mutableStateOf(false) }
    val alertCanTakeOverScreen = remember(current) {
        Platform.services.canUseFullScreenIntent() || Platform.services.canLaunchAlertOverOtherApps()
    }
    if (!alertCanTakeOverScreen && !alertVisibilityAsked) {
        AlertDialog(
            onDismissRequest = { alertVisibilityAsked = true },
            title = { Text("Let earthquake alerts show on screen") },
            text = {
                Text(
                    "Right now an alert can only appear as a notification. Android blocks " +
                        "full-screen alerts by default, so an earthquake would sound the " +
                        "alarm with nothing on screen explaining it — and on a locked " +
                        "phone that is all anyone would see.\n\n" +
                        "Allowing either one below lets the alert itself come up, with " +
                        "\"I'm safe\" and \"I need help\" on it."
                )
            },
            confirmButton = {

                Column {
                    TextButton(onClick = {
                        alertVisibilityAsked = true
                        Platform.services.openFullScreenIntentSettings()
                    }) { Text("Allow full-screen alerts") }
                    TextButton(onClick = {
                        alertVisibilityAsked = true
                        Platform.services.openOverlaySettings()
                    }) { Text("Allow pop-up windows") }
                }
            },
            dismissButton = {
                TextButton(onClick = { alertVisibilityAsked = true }) { Text("Not now") }
            },
        )
    }

}

private const val STEP_LOGIN = 0
private const val STEP_ROLE = 1
private const val STEP_SIGN_UP = 2

@Composable
private fun AuthFlow(
    startOnSignUp: Boolean,
    loading: Boolean,
    error: String?,
    phoneSupported: Boolean,
    codeSent: Boolean,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, String, Role) -> Unit,
    onSendCode: (name: String, phone: String, role: Role) -> Unit,
    onVerifyCode: (name: String, code: String, role: Role) -> Unit,
    onCancelPhone: () -> Unit,
    onForgot: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var step by remember { mutableStateOf(if (startOnSignUp) STEP_ROLE else STEP_LOGIN) }
    var role by remember { mutableStateOf(Role.STUDENT) }

    fun leaveSignUp(target: Int) {
        onCancelPhone()
        onClearError()
        step = target
    }

    AnimatedContent(
        targetState = step,
        label = "auth",
        transitionSpec = {
            if (targetState > initialState) {
                (slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(200))) togetherWith fadeOut(tween(140))
            } else {
                (slideInHorizontally(tween(260)) { -it / 6 } + fadeIn(tween(200))) togetherWith fadeOut(tween(140))
            }
        },
    ) { s ->
        when (s) {
            STEP_LOGIN -> LoginScreen(
                loading = loading,
                error = error,
                onSignIn = onSignIn,
                onCreateAccount = {
                    onClearError()
                    step = STEP_ROLE
                },
                onForgotPassword = onForgot,
            )

            STEP_ROLE -> RoleSelectionScreen(
                onContinue = {
                    role = it
                    onClearError()
                    step = STEP_SIGN_UP
                },
                onSignIn = {
                    onClearError()
                    step = STEP_LOGIN
                },

                onBack = if (startOnSignUp) null else ({ step = STEP_LOGIN }),
            )

            else -> SignUpScreen(
                role = role,
                loading = loading,
                error = error,
                phoneSupported = phoneSupported,
                codeSent = codeSent,
                onSignUp = { name, email, pw, phone -> onSignUp(name, email, pw, phone, role) },
                onSendCode = { name, phone -> onSendCode(name, phone, role) },
                onVerifyCode = { name, code -> onVerifyCode(name, code, role) },
                onCancelPhone = onCancelPhone,
                onBack = { leaveSignUp(STEP_ROLE) },
            )
        }
    }
}
