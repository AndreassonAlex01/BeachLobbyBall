package se.beachlobbyball.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.chat.ChatMessage
import se.beachlobbyball.app.chat.ChatRepository
import se.beachlobbyball.app.chat.MAX_MESSAGE_LENGTH
import se.beachlobbyball.app.chat.cleanMessage
import se.beachlobbyball.app.chat.visibleTo
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.i18n.tr

private sealed interface ChatState {
    data object Loading : ChatState
    data class Loaded(val messages: List<ChatMessage>) : ChatState
    data object Failed : ChatState
}

@Composable
fun ChatPanel(lobby: Lobby, user: AppUser, chat: ChatRepository, blockedIds: Set<String>) {
    if (!lobby.hasPlayer(user.uid)) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(tr("Join the lobby to see and send messages.", "Gå med i lobbyn för att se och skicka meddelanden."), textAlign = TextAlign.Center)
        }
        return
    }

    val flow = remember(lobby.id) {
        chat.messagesIn(lobby.id)
            .map<List<ChatMessage>, ChatState> { ChatState.Loaded(it) }
            .catch { emit(ChatState.Failed) }
    }
    val state by flow.collectAsState(initial = ChatState.Loading)
    val scope = rememberCoroutineScope()
    var draft by remember(lobby.id) { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var toBlock by remember { mutableStateOf<ChatMessage?>(null) }

    fun perform(failure: String, action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                notice = failure
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                ChatState.Loading -> Unit
                ChatState.Failed -> Text(
                    tr("Couldn't load messages. Check your connection.", "Kunde inte ladda meddelanden. Kolla din uppkoppling."),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                is ChatState.Loaded -> {
                    val messages = s.messages.visibleTo(blockedIds)
                    if (messages.isEmpty()) {
                        Text(
                            tr("No messages yet. Say hi!", "Inga meddelanden än. Säg hej!"),
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            reverseLayout = true,
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(messages.asReversed(), key = { it.id }) { message ->
                                MessageBubble(
                                    message = message,
                                    isMine = message.senderId == user.uid,
                                    onDelete = {
                                        perform(tr("Couldn't delete the message", "Kunde inte radera meddelandet")) { chat.delete(lobby.id, message.id) }
                                    },
                                    onReport = {
                                        perform(tr("Couldn't send the report", "Kunde inte skicka anmälan")) {
                                            chat.report(user, lobby.id, message)
                                            notice = tr("Reported. Thanks for letting us know.", "Anmält. Tack för att du säger till.")
                                        }
                                    },
                                    onBlock = { toBlock = message },
                                )
                            }
                        }
                    }
                }
            }
        }

        notice?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(MAX_MESSAGE_LENGTH) },
                placeholder = { Text(tr("Message", "Meddelande")) },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    cleanMessage(draft)?.let { text ->
                        draft = ""
                        notice = null
                        perform(tr("Couldn't send the message", "Kunde inte skicka meddelandet")) { chat.send(lobby.id, user, text) }
                    }
                },
                enabled = cleanMessage(draft) != null,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = tr("Send", "Skicka"))
            }
        }
    }

    toBlock?.let { message ->
        AlertDialog(
            onDismissRequest = { toBlock = null },
            title = { Text(tr("Block ${message.senderName}?", "Blockera ${message.senderName}?")) },
            text = { Text(tr("You won't see their messages anymore. You can unblock them from your profile.", "Du ser inte deras meddelanden längre. Du kan avblockera dem från din profil.")) },
            confirmButton = {
                TextButton(onClick = {
                    toBlock = null
                    perform(tr("Couldn't block the user", "Kunde inte blockera användaren")) { chat.block(user.uid, message.senderId, message.senderName) }
                }) { Text(tr("Block", "Blockera")) }
            },
            dismissButton = { TextButton(onClick = { toBlock = null }) { Text(tr("Cancel", "Avbryt")) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    var menuOpen by remember { mutableStateOf(false) }
    val time = message.sentAt.toLocalDateTime(timeZone).let { formatTime(it.hour, it.minute) }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        if (!isMine) {
            Text(
                message.senderName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isMine) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(onClick = {}, onLongClick = { menuOpen = true }),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(message.text)
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (isMine) {
                    DropdownMenuItem(text = { Text(tr("Delete", "Radera")) }, onClick = { menuOpen = false; onDelete() })
                } else {
                    DropdownMenuItem(text = { Text(tr("Report message", "Anmäl meddelande")) }, onClick = { menuOpen = false; onReport() })
                    DropdownMenuItem(
                        text = { Text(tr("Block ${message.senderName}", "Blockera ${message.senderName}")) },
                        onClick = { menuOpen = false; onBlock() },
                    )
                }
            }
        }
    }
}
