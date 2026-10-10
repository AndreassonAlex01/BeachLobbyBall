package se.beachlobbyball.app.lobby

import kotlinx.datetime.Instant
import se.beachlobbyball.app.model.Location
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.LobbyType
import se.beachlobbyball.app.model.Mode
import se.beachlobbyball.app.model.Player
import se.beachlobbyball.app.model.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class LobbyDocumentTest {
    private val lobby = Lobby(
        id = "abc",
        hostId = "u1",
        hostName = "Alex",
        location = Location.Other,
        otherLocationName = "Slottsskogen",
        createdAt = Instant.fromEpochMilliseconds(1_760_000_000_000),
        startsAt = Instant.fromEpochMilliseconds(1_760_100_000_000),
        duration = 90.minutes,
        type = LobbyType.Mixed,
        mode = Mode.Sparring,
        maxPlayers = 6,
        level = "Series 3",
        visibility = Visibility.Private,
        isOpen = false,
        isCourtBooked = true,
        courtNumber = "4",
        note = "Bring a ball",
        players = listOf(Player("u1", "Alex"), Player("u2", "Sam")),
        joinCode = "K7PQ2M",
    )

    @Test
    fun roundTripKeepsEveryField() {
        assertEquals(lobby, lobby.toDocument().toLobby("abc"))
    }

    @Test
    fun hostIsFirstPlayer() {
        assertEquals(listOf("u1", "u2"), lobby.toDocument().playerIds)
    }

    @Test
    fun unknownEnumValuesFallBackSafely() {
        val doc = lobby.toDocument().copy(location = "Beach", type = "Kids", mode = null, visibility = "Secret")
        val parsed = doc.toLobby("abc")
        assertEquals(Location.Other, parsed.location)
        assertEquals(null, parsed.type)
        assertEquals(null, parsed.mode)
        assertEquals(Visibility.Private, parsed.visibility)
    }

    @Test
    fun missingPlayerNameShowsPlaceholder() {
        val doc = lobby.toDocument().copy(playerNames = mapOf("u1" to "Alex"))
        assertEquals("Player", doc.toLobby("abc").players[1].name)
    }
}
