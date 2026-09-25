package com.siren.mobile

import androidx.compose.ui.window.ComposeUIViewController
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.platform.IosPlatformServices
import com.siren.mobile.platform.Platform
import com.siren.mobile.ui.App
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val services = IosPlatformServices(versionName = "2.4.0")
    Platform.install(services)
    services.requestNotificationPermission()
    SirenRepository.start()

    return ComposeUIViewController { App() }
}
