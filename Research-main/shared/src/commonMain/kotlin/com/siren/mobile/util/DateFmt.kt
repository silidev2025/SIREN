package com.siren.mobile.util

expect object DateFmt {

    fun clock(epochMillis: Long): String

    fun clockSeconds(epochMillis: Long): String

    fun date(epochMillis: Long): String

    fun dateTime(epochMillis: Long): String

    fun shortDateTime(epochMillis: Long): String
}
