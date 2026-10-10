package se.beachlobbyball.app.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.beachlobbyball.app.i18n.languageOverride
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class FormatTest {
    @AfterTest
    fun backToEnglish() {
        languageOverride = null
    }

    @Test
    fun dateTimeIsShortAndLocal() {
        val start = Instant.parse("2026-10-11T16:05:00Z")
        assertEquals("Sun 11 Oct, 18:05", formatDateTime(start, TimeZone.of("Europe/Stockholm")))
    }

    @Test
    fun durations() {
        assertEquals("2 h", formatDuration(2.hours))
        assertEquals("1 h 30 min", formatDuration(90.minutes))
        assertEquals("45 min", formatDuration(45.minutes))
    }

    @Test
    fun swedish() {
        languageOverride = "sv"
        val start = Instant.parse("2026-10-11T16:05:00Z")
        assertEquals("sön 11 okt, 18:05", formatDateTime(start, TimeZone.of("Europe/Stockholm")))
        assertEquals("1 tim 30 min", formatDuration(90.minutes))
    }
}
