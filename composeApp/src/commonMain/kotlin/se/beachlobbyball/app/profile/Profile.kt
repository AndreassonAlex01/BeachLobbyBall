package se.beachlobbyball.app.profile

import kotlinx.serialization.Serializable
import se.beachlobbyball.app.model.Mode

const val MAX_LEVEL_LENGTH = 30

data class Profile(
    val uid: String,
    val name: String,
    val level: String? = null,
    val preferredModes: List<Mode> = emptyList(),
)

/** Public profile in `profiles/{uid}`. Only accounts have one; guests don't. */
@Serializable
data class ProfileDocument(
    val name: String = "",
    /** Lowercase name, for prefix search. */
    val nameLower: String = "",
    val level: String? = null,
    val preferredModes: List<String> = emptyList(),
)

/**
 * Maps a lowercase email to a uid in `emailIndex/{key}`, so people can be found by exact
 * email without making emails readable. The key is the email itself, which Firestore
 * only lets you read when you already know it.
 */
@Serializable
data class EmailIndexDocument(val uid: String = "")

fun Profile.toDocument() = ProfileDocument(
    name = name,
    nameLower = name.lowercase(),
    level = level,
    preferredModes = preferredModes.map { it.name },
)

fun ProfileDocument.toProfile(uid: String) = Profile(
    uid = uid,
    name = name,
    level = level,
    preferredModes = preferredModes.mapNotNull { name -> Mode.entries.firstOrNull { it.name == name } },
)

/** Firestore document ids can't contain "/". */
fun emailKey(email: String): String = email.trim().lowercase().replace("/", "_")

fun looksLikeEmail(query: String): Boolean = "@" in query.trim()
