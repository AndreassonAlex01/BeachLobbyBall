package se.beachlobbyball.app.friends

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import se.beachlobbyball.app.auth.AppUser

class FriendsRepository {
    // Lazy, so screens can be built in screenshot tests without a Firebase app.
    private val firestore by lazy { Firebase.firestore }
    private val requests by lazy { firestore.collection("friendRequests") }

    private fun friendsOf(uid: String) = firestore.collection("users").document(uid).collection("friends")

    fun friends(uid: String): Flow<List<Friend>> =
        friendsOf(uid).snapshots.map { snapshot ->
            snapshot.documents.map { it.data<FriendDocument>().toFriend(it.id) }
        }

    fun incomingRequests(uid: String): Flow<List<FriendRequest>> =
        requests.where { "toId" equalTo uid }.snapshots.map { snapshot ->
            snapshot.documents.map { it.data<FriendRequestDocument>().toRequest() }
        }

    fun outgoingRequests(uid: String): Flow<List<FriendRequest>> =
        requests.where { "fromId" equalTo uid }.snapshots.map { snapshot ->
            snapshot.documents.map { it.data<FriendRequestDocument>().toRequest() }
        }

    suspend fun sendRequest(from: AppUser, toId: String, toName: String) {
        requests.document(requestId(from.uid, toId)).set(
            FriendRequestDocument(
                fromId = from.uid,
                fromName = from.displayName,
                toId = toId,
                toName = toName,
                createdAtMillis = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    /** Makes both people friends and removes the request, in one write. */
    suspend fun accept(me: AppUser, request: FriendRequest) {
        val now = Clock.System.now().toEpochMilliseconds()
        val batch = firestore.batch()
        batch.set(friendsOf(me.uid).document(request.fromId), FriendDocument(request.fromName, sinceMillis = now))
        batch.set(friendsOf(request.fromId).document(me.uid), FriendDocument(me.displayName, sinceMillis = now))
        batch.delete(requests.document(request.id))
        batch.commit()
    }

    /** Declines an incoming request or cancels an outgoing one. */
    suspend fun deleteRequest(request: FriendRequest) {
        requests.document(request.id).delete()
    }

    suspend fun remove(meUid: String, friendUid: String) {
        val batch = firestore.batch()
        batch.delete(friendsOf(meUid).document(friendUid))
        batch.delete(friendsOf(friendUid).document(meUid))
        batch.commit()
    }

    suspend fun save(meUid: String, friend: Friend) {
        friendsOf(meUid).document(friend.uid).set(friend.toDocument())
    }
}
