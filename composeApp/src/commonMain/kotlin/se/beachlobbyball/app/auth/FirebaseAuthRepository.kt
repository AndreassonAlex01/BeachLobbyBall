package se.beachlobbyball.app.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseAuthWeakPasswordException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** Thrown with a message that can be shown to the user as is. */
class AuthException(message: String) : Exception(message)

class FirebaseAuthRepository : AuthRepository {
    private val auth = Firebase.auth

    // Profile updates don't trigger authStateChanged, so bump this to re-read the user.
    private val profileVersion = MutableStateFlow(0)

    override val session: Flow<Session> =
        combine(auth.authStateChanged, profileVersion) { _, _ ->
            auth.currentUser?.let { Session.SignedIn(it.toAppUser()) } ?: Session.SignedOut
        }

    override suspend fun continueAsGuest(name: String) = mapErrors {
        val user = auth.signInAnonymously().user ?: throw AuthException(GENERIC_ERROR)
        user.updateProfile(displayName = name.trim())
        profileVersion.value++
    }

    override suspend fun signInWithEmail(email: String, password: String) = mapErrors {
        auth.signInWithEmailAndPassword(email.trim(), password)
        Unit
    }

    override suspend fun createAccount(name: String, email: String, password: String) = mapErrors {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).user
            ?: throw AuthException(GENERIC_ERROR)
        user.updateProfile(displayName = name.trim())
        profileVersion.value++
    }

    override suspend fun signInWithGoogle(idToken: String) = mapErrors {
        auth.signInWithCredential(GoogleAuthProvider.credential(idToken, null))
        Unit
    }

    override suspend fun updateName(name: String) = mapErrors {
        val user = auth.currentUser ?: throw AuthException(GENERIC_ERROR)
        user.updateProfile(displayName = name.trim())
        profileVersion.value++
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    private fun FirebaseUser.toAppUser() = AppUser(
        uid = uid,
        displayName = displayName?.takeIf { it.isNotBlank() } ?: email ?: "Player",
        isGuest = isAnonymous,
        email = email,
    )

    private inline fun mapErrors(block: () -> Unit) {
        try {
            block()
        } catch (e: AuthException) {
            throw e
        } catch (e: FirebaseAuthUserCollisionException) {
            throw AuthException("An account with this email already exists")
        } catch (e: FirebaseAuthWeakPasswordException) {
            throw AuthException("Choose a stronger password")
        } catch (e: FirebaseAuthInvalidUserException) {
            throw AuthException("Wrong email or password")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw AuthException("Wrong email or password")
        } catch (e: Exception) {
            throw AuthException(GENERIC_ERROR)
        }
    }

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Check your connection and try again."
    }
}
