package se.beachlobbyball.app.lobby

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import se.beachlobbyball.app.model.Location
import se.beachlobbyball.app.model.Lobby
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class LobbyRulesTest {
    private val stockholm = TimeZone.of("Europe/Stockholm")
    private val now = Instant.parse("2026-10-10T08:00:00Z")

    private fun lobby(startsAt: Instant = now + 2.hours) = Lobby(
        id = "",
        hostId = "u1",
        hostName = "Alex",
        location = Location.GBC,
        createdAt = now,
        startsAt = startsAt,
        duration = 2.hours,
    )

    @Test
    fun startInstantUsesPickedDateInLocalTime() {
        val pickedDate = LocalDate(2026, 10, 11).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        // October is summer time in Sweden, UTC+2.
        assertEquals(Instant.parse("2026-10-11T16:00:00Z"), startInstant(pickedDate, 18, 0, stockholm))
    }

    @Test
    fun startInstantHandlesWinterTime() {
        val pickedDate = LocalDate(2026, 12, 1).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        assertEquals(Instant.parse("2026-12-01T17:30:00Z"), startInstant(pickedDate, 18, 30, stockholm))
    }

    @Test
    fun maxPlayersParsing() {
        assertNull(parseMaxPlayers("  ").getOrThrow())
        assertEquals(4, parseMaxPlayers("4").getOrThrow())
        assertTrue(parseMaxPlayers("1").isFailure)
        assertTrue(parseMaxPlayers("41").isFailure)
        assertTrue(parseMaxPlayers("x").isFailure)
    }

    @Test
    fun newLobbyMustStartInFuture() {
        assertNull(validateNewLobby(lobby(), now))
        assertNotNull(validateNewLobby(lobby(startsAt = now - 1.minutes), now))
    }

    @Test
    fun otherLocationNeedsName() {
        assertNotNull(validateNewLobby(lobby().copy(location = Location.Other), now))
        assertNull(validateNewLobby(lobby().copy(location = Location.Other, otherLocationName = "Askim"), now))
    }

    @Test
    fun upcomingDropsEndedAndSortsBySoonest() {
        val later = lobby(startsAt = now + 5.hours).copy(id = "later")
        val soon = lobby(startsAt = now + 1.hours).copy(id = "soon")
        val ongoing = lobby(startsAt = now - 1.hours).copy(id = "ongoing")
        val ended = lobby(startsAt = now - 3.hours).copy(id = "ended")
        assertEquals(listOf("ongoing", "soon", "later"), listOf(later, ended, soon, ongoing).upcoming(now).map { it.id })
    }
}
