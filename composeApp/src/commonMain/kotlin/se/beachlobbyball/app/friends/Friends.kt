package se.beachlobbyball.app.friends

import kotlinx.serialization.Serializable

const val MAX_TAG_LENGTH = 20

/** A friend as the current user sees them. Tags are private to the current user. */
data class Friend(
    val uid: String,
    val name: String,
    val tags: List<String> = emptyList(),
    val sinceMillis: Long = 0,
)

data class FriendRequest(
    val fromId: String,
    val fromName: String,
    val toId: String,
    val toName: String,
) {
    val id: String get() = requestId(fromId, toId)
}

/** Stored in `users/{uid}/friends/{friendUid}`. */
@Serializable
data class FriendDocument(
    val name: String = "",
    val tags: List<String> = emptyList(),
    val sinceMillis: Long = 0,
)

/** Stored in `friendRequests/{fromId}_{toId}`. */
@Serializable
data class FriendRequestDocument(
    val fromId: String = "",
    val fromName: String = "",
    val toId: String = "",
    val toName: String = "",
    val createdAtMillis: Long = 0,
)

fun requestId(fromId: String, toId: String) = "${fromId}_$toId"

fun FriendDocument.toFriend(uid: String) = Friend(uid, name, tags, sinceMillis)

fun Friend.toDocument() = FriendDocument(name, tags, sinceMillis)

fun FriendRequestDocument.toRequest() = FriendRequest(fromId, fromName, toId, toName)

/** Cleans a tag typed by the user. Returns null when nothing is left. */
fun cleanTag(tag: String): String? =
    tag.trim().split(Regex("\\s+")).joinToString(" ").take(MAX_TAG_LENGTH).trim().ifEmpty { null }

/** Every tag used on any friend, alphabetically, without duplicates (ignoring case). */
fun List<Friend>.allTags(): List<String> =
    flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

/** Friends with [tag], or everyone when [tag] is null. Sorted by name. */
fun List<Friend>.withTag(tag: String?): List<Friend> =
    (if (tag == null) this else filter { friend -> friend.tags.any { it.equals(tag, ignoreCase = true) } })
        .sortedBy { it.name.lowercase() }

/** Adds [tag] if the friend doesn't have it, removes it if they do. */
fun Friend.toggleTag(tag: String): Friend =
    if (tags.any { it.equals(tag, ignoreCase = true) }) copy(tags = tags.filterNot { it.equals(tag, ignoreCase = true) })
    else copy(tags = tags + tag)

/** How the current user relates to someone, for showing the right button. */
enum class Relation { Self, Friend, RequestSent, RequestReceived, None }

fun relationTo(
    uid: String,
    selfUid: String,
    friends: List<Friend>,
    incoming: List<FriendRequest>,
    outgoing: List<FriendRequest>,
): Relation = when {
    uid == selfUid -> Relation.Self
    friends.any { it.uid == uid } -> Relation.Friend
    incoming.any { it.fromId == uid } -> Relation.RequestReceived
    outgoing.any { it.toId == uid } -> Relation.RequestSent
    else -> Relation.None
}
