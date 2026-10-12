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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.chat.ChatMessage
import se.beachlobbyball.app.chat.ChatRepository
import se.beachlobbyball.app.friends.Friend
import se.beachlobbyball.app.friends.Relation
import se.beachlobbyball.app.invites.InviteRepository
import se.beachlobbyball.app.lobby.LobbyRepository
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.Player
import se.beachlobbyball.app.model.Visibility
import se.beachlobbyball.app.i18n.tr

private sealed interface DetailState {
    data object Loading : DetailState
    data class Loaded(val lobby: Lobby) : DetailState
    data object Gone : DetailState
    data object Failed : DetailState
}

/**
 * [lobbySource] and [chatMessages] default to live Firestore data. Screenshot tests pass their own,
 * and [startOnChat] to open on the chat tab.
 */
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
    lobbySource: Flow<Lobby?> = remember(lobbyId) { repository.lobby(lobbyId) },
    chatMessages: Flow<List<ChatMessage>> = remember(lobbyId) { chat.messagesIn(lobbyId) },
    startOnChat: Boolean = false,
) {
    PlatformBackHandler(onBack = onBack)
    var showChat by rememberSaveable(lobbyId) { mutableStateOf(startOnChat) }

    val flow = remember(lobbySource) {
        lobbySource
            .map<Lobby?, DetailState> { if (it == null) DetailState.Gone else DetailState.Loaded(it) }
            .catch { emit(DetailState.Failed) }
    }
    val state by flow.collectAsState(initial = DetailState.Loading)

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "Tillbaka"))
            }
            Text("Lobby", style = MaterialTheme.typography.titleLarge)
        }
        when (val s = state) {
            DetailState.Loading -> Message { CircularProgressIndicator() }
            DetailState.Gone -> Message { Text(tr("This lobby was deleted.", "Lobbyn har raderats.")) }
            DetailState.Failed -> Message { Text(tr("Couldn't load the lobby. Check your connection.", "Kunde inte ladda lobbyn. Kolla din uppkoppling.")) }
            is DetailState.Loaded -> {
                TabRow(selectedTabIndex = if (showChat) 1 else 0) {
                    Tab(selected = !showChat, onClick = { showChat = false }, text = { Text(tr("Details", "Detaljer")) })
                    Tab(selected = showChat, onClick = { showChat = true }, text = { Text(tr("Chat", "Chatt")) })
                }
                if (showChat) {
                    ChatPanel(s.lobby, user, chat, blockedIds, chatMessages)
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
    var removing by remember { mutableStateOf<Player?>(null) }
    val isHost = lobby.isHost(user.uid)
    val isPlayer = lobby.hasPlayer(user.uid)
    val isWaiting = lobby.isWaiting(user.uid)
    val now = Clock.System.now()
    val isPrivate = lobby.visibility == Visibility.Private
    // Private lobbies take an invite, either from a friend or made with the join code.
    val mayEnter = !isPrivate || hasInvite
    val joinsWaitlist = lobby.canJoinWaitlist(now)

    fun perform(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                error = e.message ?: tr("Something went wrong", "Något gick fel")
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

        InfoRow(tr("Host", "Värd"), lobby.hostName)
        lobby.type?.let { InfoRow(tr("Type", "Typ"), it.label) }
        lobby.mode?.let { InfoRow(tr("Mode", "Spelform"), it.label) }
        lobby.format?.let { InfoRow(tr("Format", "Format"), it.label) }
        lobby.level?.let { InfoRow(tr("Level", "Nivå"), it) }
        InfoRow(tr("Players", "Spelare"), formatPlayerCount(lobby))
        if (lobby.waitlist.isNotEmpty()) InfoRow(tr("Waiting", "Väntar"), "${lobby.waitlist.size}")
        InfoRow(
            tr("Court", "Bana"),
            if (lobby.isCourtBooked) listOfNotNull(tr("Booked", "Bokad"), lobby.courtNumber).joinToString(", ")
            else tr("Not booked", "Inte bokad"),
        )
        InfoRow(tr("Lobby", "Lobby"), listOf(
            if (lobby.visibility == Visibility.Private) tr("Private", "Privat") else tr("Public", "Öppen"),
            if (lobby.isOpen) tr("Open", "Tar emot spelare") else tr("Closed", "Stängd"),
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
                    Text(tr("Join code", "Kod"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(code, style = MaterialTheme.typography.titleLarge)
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text(tr("Copy", "Kopiera")) }
            }
            Text(
                tr("Anyone with this code can join. Share it in a group chat, or invite friends below.", "Alla med koden kan gå med. Dela den i en gruppchatt, eller bjud in vänner nedan."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(tr("Players", "Spelare"), style = MaterialTheme.typography.titleMedium)
        lobby.players.forEach { player ->
            PlayerRow(
                player = player,
                lobby = lobby,
                user = user,
                relationTo = relationTo,
                onAddFriend = onAddFriend,
                onMakeHost = { perform { repository.makeHost(lobby.id, player.uid) } },
                onRemove = { removing = player },
            )
        }
        if (lobby.waitlist.isNotEmpty()) {
            Text(tr("Waitlist", "Väntelista"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            lobby.waitlist.forEachIndexed { index, player ->
                PlayerRow(
                    player = player,
                    lobby = lobby,
                    user = user,
                    relationTo = relationTo,
                    onAddFriend = onAddFriend,
                    onMakeHost = null,
                    onRemove = { removing = player },
                    position = index + 1,
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        // Guests can't have friends, so only accounts invite.
        if (isPlayer && !user.isGuest && !lobby.hasEnded(now)) {
            Button(onClick = { inviting = true }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Invite friends", "Bjud in vänner"))
            }
        }

        when {
            isHost -> {
                OutlinedButton(
                    onClick = { perform { repository.setOpen(lobby.id, !lobby.isOpen) } },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (lobby.isOpen) tr("Close lobby to new players", "Stäng lobbyn för nya spelare") else tr("Reopen lobby", "Öppna lobbyn igen"))
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(tr("Delete lobby", "Radera lobby"), color = MaterialTheme.colorScheme.error)
                }
            }
            isPlayer || isWaiting -> OutlinedButton(
                onClick = { perform { repository.leave(lobby.id, user.uid) } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isWaiting) tr("Leave waitlist", "Lämna väntelistan") else tr("Leave lobby", "Lämna lobbyn"))
            }
            else -> Button(
                onClick = { perform { repository.join(lobby.id, user, hasInvite) } },
                enabled = !busy && mayEnter && (lobby.canJoin(now) || joinsWaitlist),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        !lobby.isOpen || lobby.hasEnded(now) -> tr("Lobby is closed", "Lobbyn är stängd")
                        !mayEnter -> tr("Invite only", "Endast inbjudna")
                        joinsWaitlist -> tr("Join waitlist", "Ställ dig i kö")
                        else -> tr("Join lobby", "Gå med")
                    }
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete this lobby?", "Radera lobbyn?")) },
            text = { Text(tr("Everyone in it will lose access. This can't be undone.", "Alla i den förlorar åtkomst. Det går inte att ångra.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    perform {
                        repository.delete(lobby)
                        onDeleted()
                    }
                }) { Text(tr("Delete", "Radera")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel", "Avbryt")) } },
        )
    }

    if (inviting) {
        InviteFriendsDialog(lobby, user, friends, invites, onDismiss = { inviting = false })
    }

    removing?.let { player ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(tr("Remove ${player.name}?", "Ta bort ${player.name}?")) },
            text = { Text(tr("They can join again unless you close the lobby.", "De kan gå med igen om du inte stänger lobbyn.")) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    perform { repository.remove(lobby.id, player.uid) }
                }) { Text(tr("Remove", "Ta bort")) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(tr("Cancel", "Avbryt")) } },
        )
    }
}

/** A player or someone waiting. The host gets a menu to hand over the host role or remove them. */
@Composable
private fun PlayerRow(
    player: Player,
    lobby: Lobby,
    user: AppUser,
    relationTo: (String) -> Relation,
    onAddFriend: (Player) -> Unit,
    onMakeHost: (() -> Unit)?,
    onRemove: () -> Unit,
    position: Int? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildString {
                position?.let { append("$it. ") }
                append(player.name)
                if (lobby.isHost(player.uid)) append(tr(" (host)", " (värd)"))
                if (player.uid == user.uid) append(tr(" (you)", " (du)"))
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        // Guests can't have friends, so they don't get the button.
        if (!user.isGuest) {
            when (relationTo(player.uid)) {
                Relation.None -> TextButton(onClick = { onAddFriend(player) }) { Text(tr("Add friend", "Lägg till vän")) }
                Relation.RequestSent -> Text(tr("Requested", "Förfrågan skickad"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Relation.RequestReceived -> Text(tr("Wants to be friends", "Vill bli vän"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Relation.Friend -> Text(tr("Friend", "Vän"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Relation.Self -> Unit
            }
        }
        if (lobby.isHost(user.uid) && player.uid != user.uid) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = tr("More for ${player.name}", "Mer för ${player.name}"))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onMakeHost != null) {
                        DropdownMenuItem(
                            text = { Text(tr("Make host", "Gör till värd")) },
                            onClick = {
                                menuOpen = false
                                onMakeHost()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(tr("Remove from lobby", "Ta bort från lobbyn")) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
