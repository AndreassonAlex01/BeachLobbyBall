package se.beachlobbyball.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.auth.AuthRepository
import se.beachlobbyball.app.chat.ChatRepository
import se.beachlobbyball.app.friends.Friend
import se.beachlobbyball.app.friends.FriendRequest
import se.beachlobbyball.app.friends.FriendsRepository
import se.beachlobbyball.app.friends.relationTo
import se.beachlobbyball.app.lobby.LobbyRepository
import se.beachlobbyball.app.model.Visibility
import se.beachlobbyball.app.profile.ProfileRepository

enum class MainTab(val label: String, val icon: ImageVector) {
    PublicLobbies("Public", Icons.Filled.Public),
    PrivateLobbies("Private", Icons.Filled.Lock),
    CreateLobby("Create", Icons.Filled.AddCircle),
    Friends("Friends", Icons.Filled.People),
    Profile("Profile", Icons.Filled.Person),
}

/** Listens to a Firestore flow, showing [fallback] until it emits or if it fails. */
@Composable
private fun <T> Flow<T>.collectOr(fallback: T, vararg keys: Any?): T {
    val safe = remember(*keys) { catch { emit(fallback) } }
    val value by safe.collectAsState(initial = fallback)
    return value
}

@Composable
fun MainScreen(user: AppUser, auth: AuthRepository, onSignOut: () -> Unit) {
    val lobbies = remember { LobbyRepository() }
    val chat = remember { ChatRepository() }
    val friendsRepository = remember { FriendsRepository() }
    val profiles = remember { ProfileRepository() }
    val scope = rememberCoroutineScope()

    var selected by rememberSaveable { mutableStateOf(MainTab.PublicLobbies) }
    var openLobbyId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(user.uid, user.email) {
        runCatching { profiles.ensureProfile(user) }
    }

    val publicLobbies = remember { lobbies.publicLobbies() }
    val myPrivateLobbies = remember(user.uid) {
        lobbies.lobbiesWithPlayer(user.uid).map { list -> list.filter { it.visibility == Visibility.Private } }
    }
    val blockedUsers = chat.blockedUsers(user.uid).collectOr(emptyList(), user.uid)
    val blockedIds = remember(blockedUsers) { blockedUsers.map { it.uid }.toSet() }
    val profile = profiles.profile(user.uid).collectOr(null, user.uid)

    // Guests can't have friends, so they skip these listeners.
    val friends = (if (user.isGuest) flowOf(emptyList<Friend>()) else friendsRepository.friends(user.uid))
        .collectOr(emptyList(), user.uid)
    val incoming = (if (user.isGuest) flowOf(emptyList<FriendRequest>()) else friendsRepository.incomingRequests(user.uid))
        .collectOr(emptyList(), user.uid)
    val outgoing = (if (user.isGuest) flowOf(emptyList<FriendRequest>()) else friendsRepository.outgoingRequests(user.uid))
        .collectOr(emptyList(), user.uid)

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected && openLobbyId == null,
                        onClick = {
                            selected = tab
                            openLobbyId = null
                        },
                        icon = {
                            if (tab == MainTab.Friends && incoming.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${incoming.size}") } }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        val lobbyId = openLobbyId
        if (lobbyId != null) {
            LobbyDetailScreen(
                lobbyId = lobbyId,
                user = user,
                repository = lobbies,
                chat = chat,
                blockedIds = blockedIds,
                relationTo = { uid -> relationTo(uid, user.uid, friends, incoming, outgoing) },
                onAddFriend = { player ->
                    scope.launch { runCatching { friendsRepository.sendRequest(user, player.uid, player.name) } }
                },
                onBack = { openLobbyId = null },
                modifier = content,
            )
        } else when (selected) {
            MainTab.PublicLobbies -> LobbyListScreen(
                source = publicLobbies,
                title = "Public lobbies",
                emptyText = "No upcoming public lobbies yet.\nCreate one from the Create tab.",
                onOpen = { openLobbyId = it },
                modifier = content,
            )
            MainTab.PrivateLobbies -> LobbyListScreen(
                source = myPrivateLobbies,
                title = "Your private lobbies",
                emptyText = "You're not in any private lobbies.\nInvites are coming in a later update.",
                onOpen = { openLobbyId = it },
                modifier = content,
            )
            MainTab.CreateLobby -> CreateLobbyScreen(
                user = user,
                repository = lobbies,
                onCreated = { openLobbyId = it },
                modifier = content,
            )
            MainTab.Friends -> FriendsScreen(
                user = user,
                friends = friends,
                incoming = incoming,
                outgoing = outgoing,
                friendsRepository = friendsRepository,
                profiles = profiles,
                modifier = content,
            )
            MainTab.Profile -> ProfileScreen(
                user = user,
                profile = profile,
                blockedUsers = blockedUsers,
                onSave = { edited ->
                    if (edited.name != user.displayName) auth.updateName(edited.name)
                    if (!user.isGuest) profiles.save(edited)
                },
                onUnblock = { blocked -> scope.launch { runCatching { chat.unblock(user.uid, blocked.uid) } } },
                onSignOut = onSignOut,
                modifier = content,
            )
        }
    }
}
