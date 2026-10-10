package se.beachlobbyball.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.chat.ChatRepository
import se.beachlobbyball.app.lobby.LobbyRepository
import se.beachlobbyball.app.model.Visibility

enum class MainTab(val label: String, val icon: ImageVector) {
    PublicLobbies("Public", Icons.Filled.Public),
    PrivateLobbies("Private", Icons.Filled.Lock),
    CreateLobby("Create", Icons.Filled.AddCircle),
    Friends("Friends", Icons.Filled.People),
    Profile("Profile", Icons.Filled.Person),
}

@Composable
fun MainScreen(user: AppUser, onSignOut: () -> Unit) {
    val lobbies = remember { LobbyRepository() }
    val chat = remember { ChatRepository() }
    var selected by rememberSaveable { mutableStateOf(MainTab.PublicLobbies) }
    var openLobbyId by rememberSaveable { mutableStateOf<String?>(null) }

    val publicLobbies = remember { lobbies.publicLobbies() }
    val myPrivateLobbies = remember(user.uid) {
        lobbies.lobbiesWithPlayer(user.uid).map { list -> list.filter { it.visibility == Visibility.Private } }
    }

    val blockedFlow = remember(user.uid) { chat.blockedUsers(user.uid).catch { emit(emptyList()) } }
    val blockedUsers by blockedFlow.collectAsState(initial = emptyList())
    val blockedIds = remember(blockedUsers) { blockedUsers.map { it.uid }.toSet() }
    val scope = rememberCoroutineScope()

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
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
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
            MainTab.Profile -> ProfileScreen(
                user = user,
                blockedUsers = blockedUsers,
                onUnblock = { blocked -> scope.launch { runCatching { chat.unblock(user.uid, blocked.uid) } } },
                onSignOut = onSignOut,
                modifier = content,
            )
            MainTab.Friends -> Box(content.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Friends are coming in a later update.")
            }
        }
    }
}
