package se.beachlobbyball.app.lobby

import kotlinx.datetime.Instant
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.Player

const val RECENT_LOBBY_LIMIT = 10
const val RECENT_PLAYER_LIMIT = 20

/** Lobbies the user was in that have ended, most recent first. */
fun List<Lobby>.recentlyJoined(now: Instant, limit: Int = RECENT_LOBBY_LIMIT): List<Lobby> =
    filter { it.hasEnded(now) }.sortedByDescending { it.startsAt }.take(limit)

/** People the user played with in ended lobbies, most recent game first, each listed once. */
fun List<Lobby>.recentlyPlayedWith(selfUid: String, now: Instant, limit: Int = RECENT_PLAYER_LIMIT): List<Player> =
    recentlyJoined(now, limit = Int.MAX_VALUE)
        .flatMap { lobby -> lobby.players }
        .filterNot { it.uid == selfUid }
        .distinctBy { it.uid }
        .take(limit)
