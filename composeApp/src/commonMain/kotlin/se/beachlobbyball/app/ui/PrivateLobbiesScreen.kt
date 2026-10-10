package se.beachlobbyball.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.invites.Invite
import se.beachlobbyball.app.invites.InviteRepository
import se.beachlobbyball.app.invites.JOIN_CODE_LENGTH
import se.beachlobbyball.app.invites.cleanJoinCode
import se.beachlobbyball.app.model.Lobby

/** Invites waiting for an answer, joining with a code, and the private lobbies the user is in. */
@Composable
fun PrivateLobbiesScreen(
    user: AppUser,
    invites: List<Invite>,
    lobbies: List<Lobby>,
    inviteRepository: InviteRepository,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun redeem() {
        val clean = cleanJoinCode(code)
        if (clean == null) {
            error = "A join code is $JOIN_CODE_LENGTH letters and numbers"
            return
        }
        error = null
        busy = true
        scope.launch {
            try {
                val lobbyId = inviteRepository.redeem(user, clean)
                code = ""
                onOpen(lobbyId)
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Private lobbies", style = MaterialTheme.typography.headlineSmall)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(JOIN_CODE_LENGTH + 2) },
                label = { Text("Join code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = ::redeem, enabled = code.isNotBlank() && !busy) { Text("Join") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (invites.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Invites", style = MaterialTheme.typography.titleMedium)
            invites.forEach { invite ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(invite.lobbyId) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${invite.fromName} invited you", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${invite.locationName} · ${formatDateTime(invite.startsAt, timeZone)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                scope.launch { runCatching { inviteRepository.delete(invite) } }
                            }) { Text("Decline") }
                            Button(onClick = { onOpen(invite.lobbyId) }) { Text("View") }
                        }
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Your private lobbies", style = MaterialTheme.typography.titleMedium)
        if (lobbies.isEmpty()) {
            Text(
                "Private lobbies you join or create show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        lobbies.forEach { lobby -> LobbyCard(lobby, onClick = { onOpen(lobby.id) }) }
    }
}
