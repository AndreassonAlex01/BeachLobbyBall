package se.beachlobbyball.app.model

import kotlinx.datetime.Instant
import kotlin.time.Duration

enum class Location(val label: String) {
    GBC("GBC"),
    Skatas("Skatås"),
    Other("Other"),
}

enum class LobbyType(val label: String) {
    Men("Men"),
    Women("Women"),
    Mixed("Mixed"),
    Any("Any"),
}

enum class Mode(val label: String) {
    Sparring("Sparring"),
    Casual("Casual"),
    Practice("Practice"),
}

enum class Visibility { Public, Private }

data class Player(val uid: String, val name: String)

data class Lobby(
    val id: String,
    val hostId: String,
    val hostName: String,
    val location: Location,
    /** Free-text name, used when [location] is [Location.Other]. */
    val otherLocationName: String? = null,
    val createdAt: Instant,
    val startsAt: Instant,
    val duration: Duration,
    val type: LobbyType? = null,
    val mode: Mode? = null,
    val maxPlayers: Int? = null,
    val level: String? = null,
    val visibility: Visibility = Visibility.Public,
    /** Whether the lobby is still accepting new players. */
    val isOpen: Boolean = true,
    val isCourtBooked: Boolean = false,
    val courtNumber: String? = null,
    val note: String? = null,
    val players: List<Player> = emptyList(),
    /** Short code that lets people into a private lobby, shared by the host. */
    val joinCode: String? = null,
) {
    val endsAt: Instant get() = startsAt + duration

    val isFull: Boolean get() = maxPlayers != null && players.size >= maxPlayers

    val locationName: String
        get() = if (location == Location.Other) otherLocationName?.takeIf { it.isNotBlank() } ?: "Other"
        else location.label

    fun hasPlayer(uid: String): Boolean = players.any { it.uid == uid }

    fun isHost(uid: String): Boolean = hostId == uid

    fun hasEnded(now: Instant): Boolean = now >= endsAt

    fun canJoin(now: Instant): Boolean = isOpen && !isFull && !hasEnded(now)
}
