package se.beachlobbyball.app.model

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class LobbyTest {
    private val start = Instant.parse("2026-10-11T17:00:00Z")

    private fun lobby(maxPlayers: Int? = 4, players: Int = 0, isOpen: Boolean = true) = Lobby(
        id = "l1",
        hostId = "h1",
        location = Location.GBC,
        createdAt = Instant.parse("2026-10-10T08:00:00Z"),
        startsAt = start,
        duration = 2.hours,
        maxPlayers = maxPlayers,
        isOpen = isOpen,
        playerIds = List(players) { "p$it" },
    )

    @Test
    fun openLobbyWithRoomCanBeJoined() {
        assertTrue(lobby(players = 3).canJoin(start))
    }

    @Test
    fun fullLobbyCannotBeJoined() {
        assertFalse(lobby(players = 4).canJoin(start))
    }

    @Test
    fun closedLobbyCannotBeJoined() {
        assertFalse(lobby(isOpen = false).canJoin(start))
    }

    @Test
    fun finishedLobbyCannotBeJoined() {
        assertFalse(lobby().canJoin(start + 3.hours))
    }

    @Test
    fun lobbyWithoutLimitIsNeverFull() {
        assertTrue(lobby(maxPlayers = null, players = 30).canJoin(start))
    }
}
