package se.beachlobbyball.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.chat.ChatRepository
import se.beachlobbyball.app.friends.Friend
import se.beachlobbyball.app.friends.Relation
import se.beachlobbyball.app.invites.InviteRepository
import se.beachlobbyball.app.lobby.LobbyRepository
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.Player
import se.beachlobbyball.app.model.Visibility

private sealed interface DetailState {
    data object Loading : DetailState
    data class Loaded(val lobby: Lobby) : DetailState
    data object Gone : DetailState
    data object Failed : DetailState
}

@Composable
fun LobbyDetailScreen(
    lobbyId: String,
    user: AppUser,
    repository: LobbyRepository,
    chat: ChatRepository,
    blockedIds: Set<String>,
    friends: List<Friend>,
    invites: InviteRepository,
    hasInvite: Boolean,
    relationTo: (String) -> Relation,
    onAddFriend: (Player) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformBackHandler(onBack = onBack)
    var showChat by rememberSaveable(lobbyId) { mutableStateOf(false) }

    val flow = remember(lobbyId) {
        repository.lobby(lobbyId)
            .map<Lobby?, DetailState> { if (it == null) DetailState.Gone else DetailState.Loaded(it) }
            .catch { emit(DetailState.Failed) }
    }
    val state by flow.collectAsState(initial = DetailState.Loading)

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Lobby", style = MaterialTheme.typography.titleLarge)
        }
        when (val s = state) {
            DetailState.Loading -> Message { CircularProgressIndicator() }
            DetailState.Gone -> Message { Text("This lobby was deleted.") }
            DetailState.Failed -> Message { Text("Couldn't load the lobby. Check your connection.") }
            is DetailState.Loaded -> {
                TabRow(selectedTabIndex = if (showChat) 1 else 0) {
                    Tab(selected = !showChat, onClick = { showChat = false }, text = { Text("Details") })
                    Tab(selected = showChat, onClick = { showChat = true }, text = { Text("Chat") })
                }
                if (showChat) {
                    ChatPanel(s.lobby, user, chat, blockedIds)
                } else {
                    LobbyDetails(
                        lobby = s.lobby,
                        user = user,
                        repository = repository,
                        friends = friends,
                        invites = invites,
                        hasInvite = hasInvite,
                        relationTo = relationTo,
                        onAddFriend = onAddFriend,
                        onDeleted = onBack,
                    )
                }
            }
        }
    }
}

@Composable
private fun Message(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun LobbyDetails(
    lobby: Lobby,
    user: AppUser,
    repository: LobbyRepository,
    friends: List<Friend>,
    invites: InviteRepository,
    hasInvite: Boolean,
    relationTo: (String) -> Relation,
    onAddFriend: (Player) -> Unit,
    onDeleted: () -> Unit,
) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf(false) }
    val isHost = lobby.isHost(user.uid)
    val isPlayer = lobby.hasPlayer(user.uid)
    val now = Clock.System.now()
    val isPrivate = lobby.visibility == Visibility.Private
    // Private lobbies take an invite, either from a friend or made with the join code.
    val mayJoin = lobby.canJoin(now) && (!isPrivate || hasInvite)

    fun perform(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(lobby.locationName, style = MaterialTheme.typography.headlineSmall)
        Text(
            "${formatDateTime(lobby.startsAt, timeZone)} · ${formatDuration(lobby.duration)}",
            style = MaterialTheme.typography.titleMedium,
        )

        InfoRow("Host", lobby.hostName)
        lobby.type?.let { InfoRow("Type", it.label) }
        lobby.mode?.let { InfoRow("Mode", it.label) }
        lobby.level?.let { InfoRow("Level", it) }
        InfoRow("Players", formatPlayerCount(lobby))
        InfoRow(
            "Court",
            if (lobby.isCourtBooked) listOfNotNull("Booked", lobby.courtNumber).joinToString(", ") else "Not booked",
        )
        InfoRow("Lobby", listOf(
            if (lobby.visibility == Visibility.Private) "Private" else "Public",
            if (lobby.isOpen) "Open" else "Closed",
        ).joinToString(", "))
        lobby.note?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        val code = lobby.joinCode
        if (isPlayer && isPrivate && code != null) {
            val clipboard = LocalClipboardManager.current
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Join code", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(code, style = MaterialTheme.typography.titleLarge)
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Copy") }
            }
            Text(
                "Anyone with this code can join. Share it in a group chat, or invite friends below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Players", style = MaterialTheme.typography.titleMedium)
        lobby.players.forEach { player ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildString {
                        append(player.name)
                        if (lobby.isHost(player.uid)) append(" (host)")
                        if (player.uid == user.uid) append(" (you)")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                // Guests can't have friends, so they don't get the button.
                if (!user.isGuest) {
                    when (relationTo(player.uid)) {
                        Relation.None -> TextButton(onClick = { onAddFriend(player) }) { Text("Add friend") }
                        Relation.RequestSent -> Text("Requested", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Relation.RequestReceived -> Text("Wants to be friends", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Relation.Friend -> Text("Friend", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Relation.Self -> Unit
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        // Guests can't have friends, so only accounts invite.
        if (isPlayer && !user.isGuest && !lobby.hasEnded(now)) {
            Button(onClick = { inviting = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Invite friends")
            }
        }

        when {
            isHost -> {
                OutlinedButton(
                    onClick = { perform { repository.setOpen(lobby.id, !lobby.isOpen) } },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (lobby.isOpen) "Close lobby to new players" else "Reopen lobby")
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete lobby", color = MaterialTheme.colorScheme.error)
                }
            }
            isPlayer -> OutlinedButton(
                onClick = { perform { repository.leave(lobby.id, user.uid) } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Leave lobby")
            }
            else -> Button(
                onClick = { perform { repository.join(lobby.id, user, hasInvite) } },
                enabled = !busy && mayJoin,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        !lobby.canJoin(now) -> "Lobby is full or closed"
                        !mayJoin -> "Invite only"
                        else -> "Join lobby"
                    }
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this lobby?") },
            text = { Text("Everyone in it will lose access. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    perform {
                        repository.delete(lobby)
                        onDeleted()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (inviting) {
        InviteFriendsDialog(lobby, user, friends, invites, onDismiss = { inviting = false })
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
