package se.beachlobbyball.app.invites

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.friends.Friend
import se.beachlobbyball.app.lobby.LobbyException
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.i18n.tr

class InviteRepository {
    // Lazy, so screens can be built in screenshot tests without a Firebase app.
    private val firestore by lazy { Firebase.firestore }
    private val invites by lazy { firestore.collection("invites") }
    private val codes by lazy { firestore.collection("lobbyCodes") }

    /** Invites to [uid], including ones they made with a join code. */
    fun incoming(uid: String): Flow<List<Invite>> =
        invites.where { "toId" equalTo uid }.snapshots.map { snapshot ->
            snapshot.documents.map { it.data<InviteDocument>().toInvite() }
        }

    /** Invites [fromId] has sent for one lobby. */
    fun sent(fromId: String, lobbyId: String): Flow<List<Invite>> =
        invites.where { "fromId" equalTo fromId }.where { "lobbyId" equalTo lobbyId }.snapshots.map { snapshot ->
            snapshot.documents.map { it.data<InviteDocument>().toInvite() }
        }

    /** Invites each friend separately, so one failure doesn't stop the rest. Returns how many failed. */
    suspend fun send(from: AppUser, lobby: Lobby, friends: List<Friend>): Int {
        val now = Clock.System.now()
        return friends.count { friend ->
            runCatching {
                invites.document(inviteId(lobby.id, friend.uid))
                    .set(inviteFor(lobby, from.uid, from.displayName, friend.uid, now))
            }.isFailure
        }
    }

    /** Declines an invite. */
    suspend fun delete(invite: Invite) {
        invites.document(invite.id).delete()
    }

    /** Lets [user] in to the lobby with [code] and returns the lobby's id. */
    suspend fun redeem(user: AppUser, code: String): String {
        val lobbyId = try {
            val snapshot = codes.document(code).get()
            if (!snapshot.exists) throw LobbyException(tr("No lobby has that code. Check it and try again.", "Ingen lobby har den koden. Kolla den och försök igen."))
            snapshot.data<LobbyCodeDocument>().lobbyId
        } catch (e: LobbyException) {
            throw e
        } catch (e: Exception) {
            throw LobbyException(tr("Something went wrong. Check your connection and try again.", "Något gick fel. Kolla din uppkoppling och försök igen."))
        }
        try {
            invites.document(inviteId(lobbyId, user.uid)).set(
                InviteDocument(
                    lobbyId = lobbyId,
                    fromId = user.uid,
                    fromName = user.displayName,
                    toId = user.uid,
                    sentAtMillis = Clock.System.now().toEpochMilliseconds(),
                    code = code,
                )
            )
        } catch (e: Exception) {
            throw LobbyException(tr("Something went wrong. Check your connection and try again.", "Något gick fel. Kolla din uppkoppling och försök igen."))
        }
        return lobbyId
    }
}
