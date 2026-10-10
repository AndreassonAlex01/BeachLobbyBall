package se.beachlobbyball.app.invites

import kotlinx.datetime.Instant
import se.beachlobbyball.app.friends.Friend
import se.beachlobbyball.app.model.Location
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.Player
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class InviteTest {
    private val now = Instant.fromEpochMilliseconds(1_760_000_000_000)

    private fun invite(lobbyId: String, fromId: String = "a", startsIn: Int = 2) = Invite(
        lobbyId = lobbyId,
        fromId = fromId,
        fromName = "A",
        toId = "me",
        locationName = "GBC",
        startsAt = now + startsIn.hours,
        endsAt = now + (startsIn + 2).hours,
        sentAt = now,
    )

    @Test
    fun pendingHidesEndedJoinedAndCodeInvites() {
        val invites = listOf(
            invite("later", startsIn = 5),
            invite("soon", startsIn = 1),
            invite("ended", startsIn = -4),
            invite("joined"),
            invite("code", fromId = "me"),
        )
        assertEquals(listOf("soon", "later"), invites.pending(now, setOf("joined")).map { it.lobbyId })
    }

    @Test
    fun inviteIdIsLobbyThenInvitee() {
        assertEquals("l1_me", invite("l1").id)
    }

    @Test
    fun inviteForCopiesLobbySummary() {
        val lobby = Lobby(
            id = "l1",
            hostId = "h",
            hostName = "Host",
            location = Location.Skatas,
            createdAt = now,
            startsAt = now + 1.hours,
            duration = 2.hours,
        )
        val doc = inviteFor(lobby, "h", "Host", "b", now)
        assertEquals("Skatås", doc.locationName)
        assertEquals((now + 3.hours).toEpochMilliseconds(), doc.endsAtMillis)
        assertEquals(invite("l1").copy(toId = "b", fromId = "h", fromName = "Host", locationName = "Skatås",
            startsAt = now + 1.hours, endsAt = now + 3.hours), doc.toInvite())
    }

    @Test
    fun invitableSkipsPlayersAndSortsByName() {
        val lobby = Lobby(
            id = "l1",
            hostId = "h",
            hostName = "Host",
            location = Location.GBC,
            createdAt = now,
            startsAt = now,
            duration = 2.hours,
            players = listOf(Player("h", "Host"), Player("b", "Bea")),
        )
        val friends = listOf(Friend("c", "carl"), Friend("b", "Bea"), Friend("a", "Anna"))
        assertEquals(listOf("Anna", "carl"), friends.invitableTo(lobby).map { it.name })
    }

    @Test
    fun newCodesAreValid() {
        val random = Random(42)
        repeat(100) {
            val code = newJoinCode(random)
            assertEquals(code, cleanJoinCode(code))
        }
    }

    @Test
    fun cleanJoinCodeAcceptsTypedVariants() {
        assertEquals("K7PQ2M", cleanJoinCode(" k7p-q2m "))
        assertEquals("K7PQ2M", cleanJoinCode("K7P Q2M"))
    }

    @Test
    fun cleanJoinCodeRejectsWrongCodes() {
        assertNull(cleanJoinCode("K7PQ2"))
        assertNull(cleanJoinCode("K7PQ2MX"))
        // 0, O, 1 and I are never used.
        assertNull(cleanJoinCode("K7PQ20"))
        assertNull(cleanJoinCode("K7PQ2I"))
        assertTrue(JOIN_CODE_ALPHABET.none { it in "0O1IL" })
    }
}
