package com.siren.mobile.ui.components

import com.siren.mobile.model.Intensity
import com.siren.mobile.platform.Platform

object Haptics {
    fun forIntensity(intensity: Intensity) = Platform.services.vibrateForIntensity(intensity)
    fun tap() = Platform.services.vibrateTap()
    fun confirm() = Platform.services.vibrateConfirm()
    fun cancel() = Platform.services.cancelVibration()
}
