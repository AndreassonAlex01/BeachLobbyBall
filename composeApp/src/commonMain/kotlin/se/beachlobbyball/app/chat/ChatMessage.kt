package se.beachlobbyball.app.chat

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

const val MAX_MESSAGE_LENGTH = 1000

data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val sentAt: Instant,
)

/** How a message is stored in Firestore, in `lobbies/{lobbyId}/messages`. */
@Serializable
data class MessageDocument(
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val sentAtMillis: Long = 0,
)

fun MessageDocument.toMessage(id: String) = ChatMessage(
    id = id,
    senderId = senderId,
    senderName = senderName,
    text = text,
    sentAt = Instant.fromEpochMilliseconds(sentAtMillis),
)

/** Stored in the `reports` collection. Only readable from the Firebase console. */
@Serializable
data class ReportDocument(
    val reporterId: String = "",
    val reportedUserId: String = "",
    val reportedUserName: String = "",
    val lobbyId: String = "",
    val messageId: String = "",
    val messageText: String = "",
    val createdAtMillis: Long = 0,
)

/** Stored in `users/{uid}/blocked/{blockedUid}`. Private to the user who blocked. */
@Serializable
data class BlockedDocument(
    val name: String = "",
    val blockedAtMillis: Long = 0,
)

data class BlockedUser(val uid: String, val name: String)

/** Returns the cleaned text to send, or null if there's nothing to send. */
fun cleanMessage(text: String): String? =
    text.trim().take(MAX_MESSAGE_LENGTH).ifEmpty { null }

/** Hides messages from users the viewer has blocked. */
fun List<ChatMessage>.visibleTo(blockedIds: Set<String>): List<ChatMessage> =
    filterNot { it.senderId in blockedIds }
