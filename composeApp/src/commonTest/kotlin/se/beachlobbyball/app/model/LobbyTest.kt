package se.beachlobbyball.app.model

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class LobbyTest {
    private val start = Instant.parse("2026-10-11T17:00:00Z")

    private fun lobby(maxPlayers: Int? = 4, players: Int = 1, isOpen: Boolean = true) = Lobby(
        id = "l1",
        hostId = "p0",
        hostName = "Host",
        location = Location.GBC,
        createdAt = Instant.parse("2026-10-10T08:00:00Z"),
        startsAt = start,
        duration = 2.hours,
        maxPlayers = maxPlayers,
        isOpen = isOpen,
        players = List(players) { Player("p$it", "Player $it") },
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

    @Test
    fun hostAndPlayersAreRecognized() {
        val l = lobby(players = 2)
        assertTrue(l.isHost("p0"))
        assertFalse(l.isHost("p1"))
        assertTrue(l.hasPlayer("p1"))
        assertFalse(l.hasPlayer("p2"))
    }

    @Test
    fun otherLocationUsesItsName() {
        assertEquals("Skatås", lobby().copy(location = Location.Skatas).locationName)
        assertEquals("Slottsskogen", lobby().copy(location = Location.Other, otherLocationName = "Slottsskogen").locationName)
        assertEquals("Other", lobby().copy(location = Location.Other, otherLocationName = " ").locationName)
    }
}
