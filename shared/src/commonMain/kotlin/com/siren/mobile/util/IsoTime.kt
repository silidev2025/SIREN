package com.siren.mobile.util

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * UTC timestamps in the form the FDSN event services take and return. Kept separate from
 * [DateFmt], which formats for people in the phone's own time zone.
 */
@OptIn(ExperimentalTime::class)
object IsoTime {

    /** `2026-09-25T02:41:07` — FDSN query parameters are UTC with no zone suffix. */
    fun fdsn(millis: Long): String =
        Instant.fromEpochMilliseconds(millis).toString().substringBefore('.').removeSuffix("Z")

    /** Parses `2026-09-25T02:41:07.3Z` and similar; null if it is not a timestamp. */
    fun parse(text: String): Long? = runCatching {
        val normalised = if (text.endsWith("Z") || text.contains('+')) text else "${text}Z"
        Instant.parse(normalised).toEpochMilliseconds()
    }.getOrNull()
}
