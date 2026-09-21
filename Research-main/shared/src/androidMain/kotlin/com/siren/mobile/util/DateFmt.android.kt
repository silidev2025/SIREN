package com.siren.mobile.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual object DateFmt {

    private fun fmt(pattern: String, ms: Long): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))

    actual fun clock(epochMillis: Long): String = fmt("h:mm a", epochMillis)

    actual fun clockSeconds(epochMillis: Long): String = fmt("h:mm:ss a", epochMillis)

    actual fun date(epochMillis: Long): String = fmt("MMM d, yyyy", epochMillis)

    actual fun dateTime(epochMillis: Long): String = fmt("MMM d, yyyy · h:mm a", epochMillis)

    actual fun shortDateTime(epochMillis: Long): String = fmt("MMM d · h:mm a", epochMillis)
}
