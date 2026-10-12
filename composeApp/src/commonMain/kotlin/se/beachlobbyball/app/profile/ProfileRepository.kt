package se.beachlobbyball.app.profile

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.beachlobbyball.app.auth.AppUser

private const val SEARCH_LIMIT = 20

/** Sorts after every normal character, so `prefix + SEARCH_END` bounds a prefix search. */
private const val SEARCH_END = "\uf8ff"

class ProfileRepository {
    // Lazy, so screens can be built in screenshot tests without a Firebase app.
    private val firestore by lazy { Firebase.firestore }
    private val profiles by lazy { firestore.collection("profiles") }
    private val emailIndex by lazy { firestore.collection("emailIndex") }

    fun profile(uid: String): Flow<Profile?> =
        profiles.document(uid).snapshots.map {
            if (it.exists) it.data<ProfileDocument>().toProfile(uid) else null
        }

    /** Creates the profile and email lookup for an account the first time it signs in. */
    suspend fun ensureProfile(user: AppUser) {
        if (user.isGuest) return
        val ref = profiles.document(user.uid)
        if (!ref.get().exists) {
            ref.set(Profile(user.uid, user.displayName).toDocument())
        }
        user.email?.let { email ->
            val indexRef = emailIndex.document(emailKey(email))
            if (!indexRef.get().exists) indexRef.set(EmailIndexDocument(user.uid))
        }
    }

    suspend fun save(profile: Profile) {
        profiles.document(profile.uid).set(profile.toDocument())
    }

    /** Finds accounts by exact email, or by the start of their name. Never includes [selfUid]. */
    suspend fun search(query: String, selfUid: String): List<Profile> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val results = if (looksLikeEmail(trimmed)) {
            val index = emailIndex.document(emailKey(trimmed)).get()
            if (!index.exists) return emptyList()
            val uid = index.data<EmailIndexDocument>().uid
            val profile = profiles.document(uid).get()
            if (profile.exists) listOf(profile.data<ProfileDocument>().toProfile(uid)) else emptyList()
        } else {
            val prefix = trimmed.lowercase()
            profiles
                .where { "nameLower" greaterThanOrEqualTo prefix }
                .where { "nameLower" lessThan prefix + SEARCH_END }
                .limit(SEARCH_LIMIT)
                .get()
                .documents
                .map { it.data<ProfileDocument>().toProfile(it.id) }
        }
        return results.filterNot { it.uid == selfUid }
    }
}
