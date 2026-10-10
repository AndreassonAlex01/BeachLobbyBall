package se.beachlobbyball.app.lobby

import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.Player

/** Adds [player] to the lobby, or to the waitlist when there's no free spot or others are already waiting. */
fun Lobby.withJoined(player: Player): Lobby {
    val first = waitlist.firstOrNull()
    return when {
        hasPlayer(player.uid) -> this
        // The first in line takes a spot that has opened up.
        first?.uid == player.uid && !isFull -> copy(players = players + player, waitlist = waitlist.drop(1))
        isWaiting(player.uid) -> this
        isFull || waitlist.isNotEmpty() -> copy(waitlist = waitlist + player)
        else -> copy(players = players + player)
    }
}

/** Removes [uid] as a player or from the waitlist. A freed spot goes to the first in line. */
fun Lobby.without(uid: String): Lobby {
    if (isWaiting(uid)) return copy(waitlist = waitlist.filterNot { it.uid == uid })
    if (!hasPlayer(uid)) return this
    val remaining = copy(players = players.filterNot { it.uid == uid })
    val next = remaining.waitlist.firstOrNull()
    return if (next != null && !remaining.isFull) {
        remaining.copy(players = remaining.players + next, waitlist = remaining.waitlist.drop(1))
    } else {
        remaining
    }
}

/** Makes the player [uid] the host. The host is always listed first. */
fun Lobby.withHost(uid: String): Lobby {
    val newHost = players.firstOrNull { it.uid == uid } ?: return this
    return copy(
        hostId = newHost.uid,
        hostName = newHost.name,
        players = listOf(newHost) + players.filterNot { it.uid == uid },
    )
}
