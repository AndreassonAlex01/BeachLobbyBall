package se.beachlobbyball.app.lobby

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.Player
import se.beachlobbyball.app.model.Visibility

/** Thrown with a message that can be shown to the user as is. */
class LobbyException(message: String) : Exception(message)

class LobbyRepository {
    private val firestore = Firebase.firestore
    private val lobbies = firestore.collection("lobbies")

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
        return lobbies.add(withHost.toDocument()).id
    }

    suspend fun join(id: String, user: AppUser) = change(id) { lobby ->
        when {
            lobby.hasPlayer(user.uid) -> lobby
            !lobby.canJoin(Clock.System.now()) -> throw LobbyException("This lobby is full or closed")
            else -> lobby.copy(players = lobby.players + Player(user.uid, user.displayName))
        }
    }

    suspend fun leave(id: String, uid: String) = change(id) { lobby ->
        if (lobby.isHost(uid)) throw LobbyException("The host can't leave. Delete the lobby instead.")
        lobby.copy(players = lobby.players.filterNot { it.uid == uid })
    }

    suspend fun setOpen(id: String, isOpen: Boolean) = change(id) { it.copy(isOpen = isOpen) }

    suspend fun delete(id: String) {
        lobbies.document(id).delete()
    }

    /** Reads, changes and writes a lobby in one transaction, so two people can't take the last spot. */
    private suspend fun change(id: String, transform: (Lobby) -> Lobby) {
        val ref = lobbies.document(id)
        try {
            firestore.runTransaction {
                val snapshot = get(ref)
                if (!snapshot.exists) throw LobbyException("This lobby no longer exists")
                val current = snapshot.toLobby()
                val updated = transform(current)
                if (updated != current) set(ref, updated.toDocument())
                Unit
            }
        } catch (e: Exception) {
            // Firestore may wrap exceptions thrown inside the transaction.
            throw generateSequence<Throwable>(e) { it.cause }.filterIsInstance<LobbyException>().firstOrNull()
                ?: LobbyException("Something went wrong. Check your connection and try again.")
        }
    }

    private fun DocumentSnapshot.toLobby(): Lobby = data<LobbyDocument>().toLobby(id)
}
