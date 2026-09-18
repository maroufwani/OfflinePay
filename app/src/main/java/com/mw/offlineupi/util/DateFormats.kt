package com.mw.offlineupi.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared date/time formatting.
 *
 * Every call site used to build its own [java.text.SimpleDateFormat] inside a composable body, so
 * a `LazyColumn` of transactions re-parsed the pattern and re-loaded locale data for every visible
 * row on every recomposition. [DateTimeFormatter] is immutable and thread-safe (unlike
 * `SimpleDateFormat`), so instances are cached and shared instead — a formatting call is then a
 * hash lookup plus the format itself.
 *
 * The cache is keyed by locale as well as pattern: Android recreates activities on a locale change
 * but keeps the process, so a formatter captured once at class-init would keep formatting in the
 * old locale for the rest of the process's life.
 *
 * `java.time` needs no desugaring here — `minSdk` is 26.
 */
object DateFormats {

    private const val TIME = "hh:mm a"
    private const val DAY_MONTH_TIME = "dd MMM, hh:mm a"
    private const val DAY_MONTH_YEAR = "dd MMM yyyy"
    private const val FULL = "dd MMM yyyy, hh:mm a"

    private val cache = ConcurrentHashMap<String, DateTimeFormatter>()

    private fun formatter(pattern: String): DateTimeFormatter {
        val locale = Locale.getDefault()
        return cache.getOrPut("$pattern|${locale.toLanguageTag()}") {
            DateTimeFormatter.ofPattern(pattern, locale)
        }
    }

    private fun format(pattern: String, epochMillis: Long): String =
        formatter(pattern).format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    /** `09:41 PM` */
    fun time(epochMillis: Long): String = format(TIME, epochMillis)

    /** `07 Sep, 09:41 PM` */
    fun dayMonthTime(epochMillis: Long): String = format(DAY_MONTH_TIME, epochMillis)

    /** `07 Sep 2026` */
    fun dayMonthYear(epochMillis: Long): String = format(DAY_MONTH_YEAR, epochMillis)

    /** `07 Sep 2026, 09:41 PM` */
    fun full(epochMillis: Long): String = format(FULL, epochMillis)
}
