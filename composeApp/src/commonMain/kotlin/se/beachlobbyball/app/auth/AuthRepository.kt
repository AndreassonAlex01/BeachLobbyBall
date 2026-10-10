package se.beachlobbyball.app.auth

import kotlinx.coroutines.flow.Flow

enum class SignInMethod { Guest, Email, Google }

data class AppUser(
    val uid: String,
    val displayName: String,
    val isGuest: Boolean,
    val email: String? = null,
    val method: SignInMethod = if (isGuest) SignInMethod.Guest else SignInMethod.Email,
)

sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class SignedIn(val user: AppUser) : Session
}

interface AuthRepository {
    val session: Flow<Session>

    suspend fun continueAsGuest(name: String)

    suspend fun signInWithEmail(email: String, password: String)

    suspend fun createAccount(name: String, email: String, password: String)

    /** Signs in with an ID token from the platform's Google account picker. */
    suspend fun signInWithGoogle(idToken: String)

    /** Turns a guest into an account with email and password, keeping their lobbies and chats. */
    suspend fun upgradeWithEmail(email: String, password: String)

    /** Turns a guest into an account with Google, keeping their lobbies and chats. */
    suspend fun upgradeWithGoogle(idToken: String)

    /** Firebase asks accounts to prove who they are again right before deleting them. */
    suspend fun confirmWithPassword(password: String)

    suspend fun confirmWithGoogle(idToken: String)

    /** Deletes the sign-in itself. Delete the user's data first, while it's still allowed. */
    suspend fun deleteAccount()

    /** Changes the name shown in lobbies and chat. */
    suspend fun updateName(name: String)

    suspend fun signOut()
}
