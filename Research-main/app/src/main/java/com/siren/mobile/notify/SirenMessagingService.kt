package com.siren.mobile.notify

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.siren.mobile.data.SirenRepository
import com.siren.mobile.model.Intensity
import com.siren.mobile.platform.Platform

class SirenMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data

        val alertId = data["alertId"] ?: data["alert_id"]
        if (alertId.isNullOrBlank()) {
            Log.w(
                "SirenMessaging",
                "Push had no alertId (data=${data.keys}). Send a data-only, high-priority " +
                    "message with alertId/intensity/magnitudeG or the alarm cannot fire.",
            )
            return
        }
        val magnitude = (data["magnitudeG"] ?: data["magnitude_g"])?.toDoubleOrNull() ?: 0.0
        val intensity = data["intensity"]
            ?.let { Intensity.fromName(it) }
            ?: Intensity.fromMagnitude(magnitude)

        Log.i("SirenMessaging", "alert $alertId $intensity ${magnitude}g")

        Platform.services.startAlarm(
            alertId,
            intensity,
            magnitude,
            SirenRepository.settings.value.vibration,
        )

        if (intensity != Intensity.GREEN) {
            SirenRepository.showAlertFromPush(alertId, intensity, magnitude)
        }
    }
}
