package se.beachlobbyball.app.invites

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import se.beachlobbyball.app.friends.Friend
import se.beachlobbyball.app.model.Lobby
import kotlin.random.Random

const val JOIN_CODE_LENGTH = 6

// No 0/O or 1/I/L, so codes are easy to read out and type.
const val JOIN_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

/** An invite to a lobby, as the invitee sees it. */
data class Invite(
    val lobbyId: String,
    val fromId: String,
    val fromName: String,
    val toId: String,
    val locationName: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val sentAt: Instant,
) {
    val id: String get() = inviteId(lobbyId, toId)

    /** Made by the invitee themselves by entering the lobby's join code. */
    val isFromCode: Boolean get() = fromId == toId
}

/** Stored in `invites/{lobbyId}_{toId}`, so each person has at most one invite per lobby. */
@Serializable
data class InviteDocument(
    val lobbyId: String = "",
    val fromId: String = "",
    val fromName: String = "",
    val toId: String = "",
    val locationName: String = "",
    val startsAtMillis: Long = 0,
    val endsAtMillis: Long = 0,
    val sentAtMillis: Long = 0,
    /** The join code, on invites people make for themselves. */
    val code: String? = null,
)

/** Stored in `lobbyCodes/{code}`. */
@Serializable
data class LobbyCodeDocument(val lobbyId: String = "")

fun inviteId(lobbyId: String, toId: String) = "${lobbyId}_$toId"

fun InviteDocument.toInvite() = Invite(
    lobbyId = lobbyId,
    fromId = fromId,
    fromName = fromName,
    toId = toId,
    locationName = locationName,
    startsAt = Instant.fromEpochMilliseconds(startsAtMillis),
    endsAt = Instant.fromEpochMilliseconds(endsAtMillis),
    sentAt = Instant.fromEpochMilliseconds(sentAtMillis),
)

fun inviteFor(lobby: Lobby, fromId: String, fromName: String, toId: String, now: Instant) = InviteDocument(
    lobbyId = lobby.id,
    fromId = fromId,
    fromName = fromName,
    toId = toId,
    locationName = lobby.locationName,
    startsAtMillis = lobby.startsAt.toEpochMilliseconds(),
    endsAtMillis = lobby.endsAt.toEpochMilliseconds(),
    sentAtMillis = now.toEpochMilliseconds(),
)

/** Invites from friends that still need an answer: not ended, and not joined yet. Soonest first. */
fun List<Invite>.pending(now: Instant, joinedLobbyIds: Set<String>): List<Invite> =
    filter { !it.isFromCode && it.endsAt > now && it.lobbyId !in joinedLobbyIds }.sortedBy { it.startsAt }

/** Friends who aren't in [lobby] yet, by name. */
fun List<Friend>.invitableTo(lobby: Lobby): List<Friend> =
    filterNot { lobby.hasPlayer(it.uid) }.sortedBy { it.name.lowercase() }

fun newJoinCode(random: Random = Random): String =
    (1..JOIN_CODE_LENGTH).map { JOIN_CODE_ALPHABET[random.nextInt(JOIN_CODE_ALPHABET.length)] }.joinToString("")

/** Normalizes a code someone typed. Returns null when it can't be a join code. */
fun cleanJoinCode(input: String): String? {
    val code = input.uppercase().filterNot { it.isWhitespace() || it == '-' }
    return code.takeIf { it.length == JOIN_CODE_LENGTH && it.all { c -> c in JOIN_CODE_ALPHABET } }
}
