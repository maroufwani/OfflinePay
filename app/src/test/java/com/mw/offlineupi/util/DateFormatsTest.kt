package com.mw.offlineupi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone

class DateFormatsTest {

    /**
     * 07 Mar 2026, 21:41:00 IST.
     *
     * March, not September: CLDR abbreviates September as either "Sep" or "Sept" depending on the
     * platform's locale data, so asserting on it tests the JDK rather than this code.
     */
    private val sampleMillis: Long =
        ZonedDateTime.of(2026, 3, 7, 21, 41, 0, 0, ZoneId.of("Asia/Kolkata"))
            .toInstant()
            .toEpochMilli()

    private fun <T> withIndia(block: () -> T): T {
        val tz = TimeZone.getDefault()
        val locale = Locale.getDefault()
        return try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            Locale.setDefault(Locale.forLanguageTag("en-IN"))
            block()
        } finally {
            TimeZone.setDefault(tz)
            Locale.setDefault(locale)
        }
    }

    @Test
    fun `each pattern renders its documented shape`() {
        withIndia {
            assertEquals("09:41 pm", DateFormats.time(sampleMillis).lowercase())
            assertEquals("07 mar, 09:41 pm", DateFormats.dayMonthTime(sampleMillis).lowercase())
            assertEquals("07 mar 2026", DateFormats.dayMonthYear(sampleMillis).lowercase())
            assertEquals("07 mar 2026, 09:41 pm", DateFormats.full(sampleMillis).lowercase())
        }
    }

    @Test
    fun `formatting is stable across repeated calls`() {
        withIndia {
            // The cache hands back a shared immutable formatter rather than rebuilding it, which
            // is the whole reason this exists: a LazyColumn row used to construct a
            // SimpleDateFormat per recomposition.
            assertEquals(DateFormats.full(sampleMillis), DateFormats.full(sampleMillis))
        }
    }

    @Test
    fun `a locale change is picked up rather than frozen at first use`() {
        // Android recreates activities on a locale change but keeps the process, so a formatter
        // captured once at class-init would format in the old locale for the process's lifetime.
        // The cache is keyed by locale, so both renderings stay reachable.
        val tz = TimeZone.getDefault()
        val locale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))

            Locale.setDefault(Locale.forLanguageTag("en-IN"))
            val english = DateFormats.dayMonthYear(sampleMillis)

            Locale.setDefault(Locale.JAPAN)
            val japanese = DateFormats.dayMonthYear(sampleMillis)

            Locale.setDefault(Locale.forLanguageTag("en-IN"))
            val englishAgain = DateFormats.dayMonthYear(sampleMillis)

            assertEquals("07 Mar 2026", english)
            assertNotEquals("the cache must not be keyed by pattern alone", english, japanese)
            assertEquals("switching back must not return the other locale's formatter", english, englishAgain)
        } finally {
            TimeZone.setDefault(tz)
            Locale.setDefault(locale)
        }
    }

    @Test
    fun `the epoch is formatted in the device time zone`() {
        withIndia {
            // 1970-01-01T00:00:00Z is 05:30 on the 1st in IST.
            assertEquals("01 jan 1970, 05:30 am", DateFormats.full(0L).lowercase())
        }
    }
}
