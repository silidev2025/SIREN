package com.siren.mobile.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

actual object DateFmt {

    private fun fmt(pattern: String, ms: Long): String {
        val formatter = NSDateFormatter().apply { dateFormat = pattern }
        val date = NSDate.dateWithTimeIntervalSince1970(ms / 1000.0)
        return formatter.stringFromDate(date)
    }

    actual fun clock(epochMillis: Long): String = fmt("h:mm a", epochMillis)

    actual fun clockSeconds(epochMillis: Long): String = fmt("h:mm:ss a", epochMillis)

    actual fun date(epochMillis: Long): String = fmt("MMM d, yyyy", epochMillis)

    actual fun dateTime(epochMillis: Long): String = fmt("MMM d, yyyy · h:mm a", epochMillis)

    actual fun shortDateTime(epochMillis: Long): String = fmt("MMM d · h:mm a", epochMillis)
}
