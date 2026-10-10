package se.beachlobbyball.app.lobby

import kotlinx.datetime.Instant
import se.beachlobbyball.app.model.Location
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class LobbyChangesTest {
    private val host = Player("h", "Host")
    private val ann = Player("a", "Ann")
    private val bo = Player("b", "Bo")
    private val cid = Player("c", "Cid")

    private val lobby = Lobby(
        id = "l1",
        hostId = "h",
        hostName = "Host",
        location = Location.GBC,
        createdAt = Instant.fromEpochMilliseconds(0),
        startsAt = Instant.fromEpochMilliseconds(0),
        duration = 2.hours,
        maxPlayers = 2,
        players = listOf(host),
    )

    @Test
    fun joiningTakesAFreeSpot() {
        assertEquals(listOf(host, ann), lobby.withJoined(ann).players)
    }

    @Test
    fun joiningAFullLobbyWaits() {
        val full = lobby.withJoined(ann).withJoined(bo).withJoined(cid)
        assertEquals(listOf(host, ann), full.players)
        assertEquals(listOf(bo, cid), full.waitlist)
    }

    @Test
    fun joiningTwiceChangesNothing() {
        val full = lobby.withJoined(ann).withJoined(bo)
        assertEquals(full, full.withJoined(bo))
        assertEquals(full, full.withJoined(ann))
    }

    @Test
    fun leavingLetsTheFirstInLineIn() {
        val full = lobby.withJoined(ann).withJoined(bo).withJoined(cid)
        val after = full.without("a")
        assertEquals(listOf(host, bo), after.players)
        assertEquals(listOf(cid), after.waitlist)
    }

    @Test
    fun leavingTheWaitlistKeepsTheOrder() {
        val full = lobby.withJoined(ann).withJoined(bo).withJoined(cid)
        val after = full.without("b")
        assertEquals(listOf(host, ann), after.players)
        assertEquals(listOf(cid), after.waitlist)
    }

    @Test
    fun othersQueueBehindTheFirstInLine() {
        // A spot is free but Bo is still first in line, so Cid waits behind him.
        val gap = lobby.copy(waitlist = listOf(bo))
        assertEquals(listOf(bo, cid), gap.withJoined(cid).waitlist)
        val boIn = gap.withJoined(bo)
        assertEquals(listOf(host, bo), boIn.players)
        assertEquals(emptyList(), boIn.waitlist)
    }

    @Test
    fun newHostIsListedFirst() {
        val handed = lobby.withJoined(ann).withHost("a")
        assertEquals("a", handed.hostId)
        assertEquals("Ann", handed.hostName)
        assertEquals(listOf(ann, host), handed.players)
    }

    @Test
    fun onlyPlayersCanBecomeHost() {
        assertEquals(lobby, lobby.withHost("z"))
    }
}
