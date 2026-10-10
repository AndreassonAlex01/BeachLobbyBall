package se.beachlobbyball.app.chat

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import se.beachlobbyball.app.auth.AppUser

/** The newest messages shown in a lobby chat. Older ones are kept but not loaded. */
private const val MESSAGE_LIMIT = 200

class ChatRepository {
    private val firestore = Firebase.firestore

    private fun messages(lobbyId: String) =
        firestore.collection("lobbies").document(lobbyId).collection("messages")

    private fun blocked(uid: String) =
        firestore.collection("users").document(uid).collection("blocked")

    /** Messages oldest first. */
    fun messagesIn(lobbyId: String): Flow<List<ChatMessage>> =
        messages(lobbyId)
            .orderBy("sentAtMillis", Direction.DESCENDING)
            .limit(MESSAGE_LIMIT)
            .snapshots
            .map { snapshot ->
                snapshot.documents
                    .map { it.data<MessageDocument>().toMessage(it.id) }
                    .reversed()
            }

    suspend fun send(lobbyId: String, sender: AppUser, text: String) {
        val clean = cleanMessage(text) ?: return
        messages(lobbyId).add(
            MessageDocument(
                senderId = sender.uid,
                senderName = sender.displayName,
                text = clean,
                sentAtMillis = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    suspend fun delete(lobbyId: String, messageId: String) {
        messages(lobbyId).document(messageId).delete()
    }

    suspend fun report(reporter: AppUser, lobbyId: String, message: ChatMessage) {
        firestore.collection("reports").add(
            ReportDocument(
                reporterId = reporter.uid,
                reportedUserId = message.senderId,
                reportedUserName = message.senderName,
                lobbyId = lobbyId,
                messageId = message.id,
                messageText = message.text,
                createdAtMillis = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    fun blockedUsers(uid: String): Flow<List<BlockedUser>> =
        blocked(uid).snapshots.map { snapshot ->
            snapshot.documents.map { BlockedUser(it.id, it.data<BlockedDocument>().name) }
        }

    suspend fun block(uid: String, other: String, otherName: String) {
        blocked(uid).document(other).set(
            BlockedDocument(name = otherName, blockedAtMillis = Clock.System.now().toEpochMilliseconds())
        )
    }

    suspend fun unblock(uid: String, other: String) {
        blocked(uid).document(other).delete()
    }
}
