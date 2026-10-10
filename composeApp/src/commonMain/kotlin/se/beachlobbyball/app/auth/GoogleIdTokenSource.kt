package se.beachlobbyball.app.auth

/** Opens the platform's Google account picker. */
fun interface GoogleIdTokenSource {
    /** Returns an ID token for Firebase, or null when the user closes the picker. */
    suspend fun requestIdToken(): String?
}
