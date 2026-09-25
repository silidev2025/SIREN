package com.siren.mobile.util

import com.siren.mobile.model.AlertSource
import com.siren.mobile.model.Intensity

/**
 * The words spoken over an alert. Composed here, once, so Android and iOS say the same
 * thing.
 *
 * English only. Filipino voices exist on most Android phones but Cebuano generally does
 * not, so an alert in Bisaya would have to be short pre-recorded clips rather than text to
 * speech — a content decision, not a code one.
 */
object VoiceAlert {

    private val NUMBER_WORDS = listOf(
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    )

    /** Null for Green, which is a single chime and a notification, not a take-cover alarm. */
    fun phrase(intensity: Intensity, magnitudeG: Double, source: AlertSource): String? {
        if (intensity == Intensity.GREEN) return null
        val level = NUMBER_WORDS[Intensity.peisFromPga(magnitudeG).coerceIn(1, 10) - 1]
        return buildString {
            // A drill has to say so out loud as well as on screen: the speech is exactly
            // what reaches someone who is not looking at the phone.
            if (source == AlertSource.SIMULATED) append("This is a drill, not a real earthquake. ")
            append("Earthquake. Intensity $level. Drop, cover, and hold on.")
        }
    }
}
