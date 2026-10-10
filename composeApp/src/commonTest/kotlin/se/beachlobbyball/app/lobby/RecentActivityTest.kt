package se.beachlobbyball.app.lobby

import kotlinx.datetime.Instant
import se.beachlobbyball.app.model.Location
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class RecentActivityTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    private fun lobby(id: String, startsAt: Instant, vararg players: String) = Lobby(
        id = id,
        hostId = players.first(),
        hostName = players.first(),
        location = Location.GBC,
        createdAt = startsAt - 1.days,
        startsAt = startsAt,
        duration = 2.hours,
        players = players.map { Player(it, it.uppercase()) },
    )

    private val lobbies = listOf(
        lobby("old", now - 5.days, "me", "sam", "kim"),
        lobby("upcoming", now + 1.days, "me", "zoe"),
        lobby("recent", now - 1.days, "alex", "me", "sam"),
        lobby("ongoing", now - 1.hours, "me", "ola"),
    )

    @Test
    fun recentlyJoinedSkipsUnfinishedAndSortsNewestFirst() {
        assertEquals(listOf("recent", "old"), lobbies.recentlyJoined(now).map { it.id })
        assertEquals(listOf("recent"), lobbies.recentlyJoined(now, limit = 1).map { it.id })
    }

    @Test
    fun recentlyPlayedWithIsDistinctAndExcludesSelf() {
        assertEquals(listOf("alex", "sam", "kim"), lobbies.recentlyPlayedWith("me", now).map { it.uid })
        assertEquals(listOf("alex"), lobbies.recentlyPlayedWith("me", now, limit = 1).map { it.uid })
    }
}
