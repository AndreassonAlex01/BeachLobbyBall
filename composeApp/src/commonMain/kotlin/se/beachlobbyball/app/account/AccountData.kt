package se.beachlobbyball.app.account

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Query
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.first
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.auth.AuthException
import se.beachlobbyball.app.auth.AuthRepository
import se.beachlobbyball.app.auth.GoogleIdTokenSource
import se.beachlobbyball.app.auth.SignInMethod
import se.beachlobbyball.app.lobby.LobbyRepository
import se.beachlobbyball.app.profile.EmailIndexDocument
import se.beachlobbyball.app.profile.emailKey
import se.beachlobbyball.app.i18n.tr

/** Deletes everything stored about a user. Safe to run again if it stops halfway. */
class AccountData(private val lobbies: LobbyRepository) {
    private val firestore = Firebase.firestore

    suspend fun deleteAll(user: AppUser) {
        val uid = user.uid
        // Lobbies they host go away with their chat. They leave the others.
        lobbies.lobbiesWithPlayer(uid).first().forEach { lobby ->
            if (lobby.isHost(uid)) lobbies.delete(lobby) else lobbies.leave(lobby.id, uid)
        }
        deleteAll(firestore.collectionGroup("messages").where { "senderId" equalTo uid })
        deleteAll(firestore.collection("invites").where { "toId" equalTo uid })
        deleteAll(firestore.collection("invites").where { "fromId" equalTo uid })
        deleteAll(firestore.collection("friendRequests").where { "toId" equalTo uid })
        deleteAll(firestore.collection("friendRequests").where { "fromId" equalTo uid })

        val users = firestore.collection("users")
        users.document(uid).collection("friends").get().documents.forEach { friend ->
            // Take them off their friends' lists too.
            users.document(friend.id).collection("friends").document(uid).delete()
            friend.reference.delete()
        }
        deleteAll(users.document(uid).collection("blocked"))

        user.email?.let { email ->
            val index = firestore.collection("emailIndex").document(emailKey(email))
            val snapshot = index.get()
            if (snapshot.exists && snapshot.data<EmailIndexDocument>().uid == uid) index.delete()
        }
        firestore.collection("profiles").document(uid).delete()
    }

    private suspend fun deleteAll(query: Query) {
        query.get().documents.forEach { it.reference.delete() }
    }
}

/**
 * Confirms who the user is, deletes their data and then their sign-in.
 * Returns false if they closed the Google account picker.
 */
suspend fun deleteAccount(
    user: AppUser,
    password: String?,
    auth: AuthRepository,
    data: AccountData,
    google: GoogleIdTokenSource?,
): Boolean {
    when (user.method) {
        SignInMethod.Email -> auth.confirmWithPassword(password.orEmpty())
        SignInMethod.Google -> {
            val source = google ?: throw AuthException(tr("Google sign-in isn't available on this phone.", "Google-inloggning finns inte på den här telefonen."))
            auth.confirmWithGoogle(source.requestIdToken() ?: return false)
        }
        SignInMethod.Guest -> Unit
    }
    try {
        data.deleteAll(user)
    } catch (e: Exception) {
        throw AuthException(
            tr("Couldn't delete everything. Check your connection and try again.", "Kunde inte radera allt. Kolla din uppkoppling och försök igen."),
        )
    }
    try {
        auth.deleteAccount()
    } catch (e: AuthException) {
        // Guests can't sign in again, so with their data gone, signing out is as good as deleting.
        if (user.isGuest) auth.signOut() else throw e
    }
    return true
}
