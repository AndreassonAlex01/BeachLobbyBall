package se.beachlobbyball.app.lobby

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import se.beachlobbyball.app.model.Format
import se.beachlobbyball.app.model.Location
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.LobbyType
import se.beachlobbyball.app.model.Mode
import se.beachlobbyball.app.model.Player
import se.beachlobbyball.app.model.Visibility
import kotlin.time.Duration.Companion.minutes

/** How a lobby is stored in Firestore, in the `lobbies` collection. */
@Serializable
data class LobbyDocument(
    val hostId: String = "",
    val hostName: String = "",
    val location: String = Location.Other.name,
    val otherLocationName: String? = null,
    val createdAtMillis: Long = 0,
    val startsAtMillis: Long = 0,
    val durationMinutes: Int = 120,
    val type: String? = null,
    val mode: String? = null,
    val format: String? = null,
    val maxPlayers: Int? = null,
    val level: String? = null,
    val visibility: String = Visibility.Public.name,
    val isOpen: Boolean = true,
    val isCourtBooked: Boolean = false,
    val courtNumber: String? = null,
    val note: String? = null,
    /** Player uids in join order. The host is always first. */
    val playerIds: List<String> = emptyList(),
    /** Names of players and people on the waitlist. */
    val playerNames: Map<String, String> = emptyMap(),
    val joinCode: String? = null,
    val waitlistIds: List<String> = emptyList(),
)

fun LobbyDocument.toLobby(id: String) = Lobby(
    id = id,
    hostId = hostId,
    hostName = hostName,
    location = enumOrNull<Location>(location) ?: Location.Other,
    otherLocationName = otherLocationName,
    createdAt = Instant.fromEpochMilliseconds(createdAtMillis),
    startsAt = Instant.fromEpochMilliseconds(startsAtMillis),
    duration = durationMinutes.minutes,
    type = type?.let { enumOrNull<LobbyType>(it) },
    mode = mode?.let { enumOrNull<Mode>(it) },
    format = format?.let { enumOrNull<Format>(it) },
    maxPlayers = maxPlayers,
    level = level,
    visibility = enumOrNull<Visibility>(visibility) ?: Visibility.Private,
    isOpen = isOpen,
    isCourtBooked = isCourtBooked,
    courtNumber = courtNumber,
    note = note,
    players = playerIds.map { Player(it, playerNames[it] ?: "Player") },
    joinCode = joinCode,
    waitlist = waitlistIds.map { Player(it, playerNames[it] ?: "Player") },
)

fun Lobby.toDocument() = LobbyDocument(
    hostId = hostId,
    hostName = hostName,
    location = location.name,
    otherLocationName = otherLocationName,
    createdAtMillis = createdAt.toEpochMilliseconds(),
    startsAtMillis = startsAt.toEpochMilliseconds(),
    durationMinutes = duration.inWholeMinutes.toInt(),
    type = type?.name,
    mode = mode?.name,
    format = format?.name,
    maxPlayers = maxPlayers,
    level = level,
    visibility = visibility.name,
    isOpen = isOpen,
    isCourtBooked = isCourtBooked,
    courtNumber = courtNumber,
    note = note,
    playerIds = players.map { it.uid },
    playerNames = (players + waitlist).associate { it.uid to it.name },
    joinCode = joinCode,
    waitlistIds = waitlist.map { it.uid },
)

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
