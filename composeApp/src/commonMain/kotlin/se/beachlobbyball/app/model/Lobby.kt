package se.beachlobbyball.app.model

import kotlinx.datetime.Instant
import kotlin.time.Duration

enum class Location { GBC, Skatas, Other }

enum class LobbyType { Men, Women, Mixed, Any }

enum class Mode { Sparring, Casual, Practice }

enum class Visibility { Public, Private }

data class Lobby(
    val id: String,
    val hostId: String,
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
    val playerIds: List<String> = emptyList(),
) {
    val endsAt: Instant get() = startsAt + duration

    val isFull: Boolean get() = maxPlayers != null && playerIds.size >= maxPlayers

    fun canJoin(now: Instant): Boolean = isOpen && !isFull && now < endsAt
}
