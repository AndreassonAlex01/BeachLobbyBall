package se.beachlobbyball.app.lobby

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.invites.LobbyCodeDocument
import se.beachlobbyball.app.invites.inviteId
import se.beachlobbyball.app.invites.newJoinCode
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.Player
import se.beachlobbyball.app.model.Visibility
import se.beachlobbyball.app.i18n.tr

/** Thrown with a message that can be shown to the user as is. */
class LobbyException(message: String) : Exception(message)

class LobbyRepository {
    private val firestore = Firebase.firestore
    private val lobbies = firestore.collection("lobbies")
    private val codes = firestore.collection("lobbyCodes")
    private val invites = firestore.collection("invites")

    /** All public lobbies, including ones that have ended. Filter and sort in the UI. */
    fun publicLobbies(): Flow<List<Lobby>> =
        lobbies.where { "visibility" equalTo Visibility.Public.name }
            .snapshots
            .map { snapshot -> snapshot.documents.map { it.toLobby() } }

    /** Lobbies the user is in, as host or player. */
    fun lobbiesWithPlayer(uid: String): Flow<List<Lobby>> =
        lobbies.where { "playerIds" contains uid }
            .snapshots
            .map { snapshot -> snapshot.documents.map { it.toLobby() } }

    /** Emits null when the lobby doesn't exist, for example after the host deleted it. */
    fun lobby(id: String): Flow<Lobby?> =
        lobbies.document(id).snapshots.map { if (it.exists) it.toLobby() else null }

    /** Creates the lobby with [host] as its first player and returns its id. */
    suspend fun create(lobby: Lobby, host: AppUser): String {
        val withHost = lobby.copy(
            hostId = host.uid,
            hostName = host.displayName,
            createdAt = Clock.System.now(),
            players = listOf(Player(host.uid, host.displayName)),
        )
        if (withHost.visibility == Visibility.Public) return lobbies.add(withHost.toDocument()).id
        // Private lobbies get a join code. Writing a code that's already taken fails, so try a few.
        repeat(CODE_ATTEMPTS - 1) {
            runCatching { return createWithCode(withHost) }
        }
        return createWithCode(withHost)
    }

    private suspend fun createWithCode(lobby: Lobby): String {
        val ref = lobbies.document
        val code = newJoinCode()
        val batch = firestore.batch()
        batch.set(ref, lobby.copy(joinCode = code).toDocument())
        batch.set(codes.document(code), LobbyCodeDocument(ref.id))
        batch.commit()
        return ref.id
    }

    /**
     * Joins the lobby, or its waitlist when it's full. With [hasInvite], the user's invite
     * is used up in the same write once they get a spot.
     */
    suspend fun join(id: String, user: AppUser, hasInvite: Boolean = false) = change(
        id,
        cleanUp = { updated ->
            invites.document(inviteId(id, user.uid)).takeIf { hasInvite && updated.hasPlayer(user.uid) }
        },
    ) { lobby ->
        val now = Clock.System.now()
        when {
            lobby.hasPlayer(user.uid) -> lobby
            lobby.visibility == Visibility.Private && !hasInvite ->
                throw LobbyException(
                    tr("This lobby is private. You need an invite or its join code.", "Lobbyn är privat. Du behöver en inbjudan eller dess kod."),
                )
            !lobby.canJoin(now) && !lobby.canJoinWaitlist(now) -> throw LobbyException(tr("This lobby is closed", "Lobbyn är stängd"))
            else -> lobby.withJoined(Player(user.uid, user.displayName))
        }
    }

    /** Leaves the lobby or its waitlist. */
    suspend fun leave(id: String, uid: String) = change(id) { lobby ->
        if (lobby.isHost(uid)) throw LobbyException(
            tr("The host can't leave. Make someone else host, or delete the lobby.", "Värden kan inte lämna. Gör någon annan till värd, eller radera lobbyn."),
        )
        lobby.without(uid)
    }

    /** For the host: takes a player or someone waiting out of the lobby. */
    suspend fun remove(id: String, uid: String) = change(id) { it.without(uid) }

    /** For the host: hands the host role to another player. */
    suspend fun makeHost(id: String, uid: String) = change(id) { it.withHost(uid) }

    suspend fun setOpen(id: String, isOpen: Boolean) = change(id) { it.copy(isOpen = isOpen) }

    /**
     * Deletes the lobby with its chat, join code and the host's invites.
     * Firestore doesn't delete subcollections on its own.
     */
    suspend fun delete(lobby: Lobby) {
        val ref = lobbies.document(lobby.id)
        ref.collection("messages").get().documents.forEach { it.reference.delete() }
        invites.where { "fromId" equalTo lobby.hostId }.where { "lobbyId" equalTo lobby.id }
            .get().documents.forEach { it.reference.delete() }
        lobby.joinCode?.let { codes.document(it).delete() }
        ref.delete()
    }

    /** Reads, changes and writes a lobby in one transaction, so two people can't take the last spot. */
    private suspend fun change(
        id: String,
        cleanUp: (Lobby) -> DocumentReference? = { null },
        transform: (Lobby) -> Lobby,
    ) {
        val ref = lobbies.document(id)
        try {
            firestore.runTransaction {
                val snapshot = get(ref)
                if (!snapshot.exists) throw LobbyException(tr("This lobby no longer exists", "Lobbyn finns inte längre"))
                val current = snapshot.toLobby()
                val updated = transform(current)
                if (updated != current) set(ref, updated.toDocument())
                cleanUp(updated)?.let { delete(it) }
                Unit
            }
        } catch (e: Exception) {
            // Firestore may wrap exceptions thrown inside the transaction.
            throw generateSequence<Throwable>(e) { it.cause }.filterIsInstance<LobbyException>().firstOrNull()
                ?: LobbyException(tr("Something went wrong. Check your connection and try again.", "Något gick fel. Kolla din uppkoppling och försök igen."))
        }
    }

    private fun DocumentSnapshot.toLobby(): Lobby = data<LobbyDocument>().toLobby(id)

    private companion object {
        const val CODE_ATTEMPTS = 3
    }
}
