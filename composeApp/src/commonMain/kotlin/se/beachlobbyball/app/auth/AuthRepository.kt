package se.beachlobbyball.app.auth

import kotlinx.coroutines.flow.Flow

data class AppUser(
    val uid: String,
    val displayName: String,
    val isGuest: Boolean,
    val email: String? = null,
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

    /** Changes the name shown in lobbies and chat. */
    suspend fun updateName(name: String)

    suspend fun signOut()
}
