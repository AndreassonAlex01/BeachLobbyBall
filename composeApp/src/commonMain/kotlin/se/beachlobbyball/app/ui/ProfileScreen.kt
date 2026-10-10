package se.beachlobbyball.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.auth.validateName
import se.beachlobbyball.app.chat.BlockedUser
import se.beachlobbyball.app.friends.Relation
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.Mode
import se.beachlobbyball.app.model.Player
import se.beachlobbyball.app.profile.MAX_LEVEL_LENGTH
import se.beachlobbyball.app.profile.Profile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    user: AppUser,
    profile: Profile?,
    recentLobbies: List<Lobby>,
    recentPlayers: List<Player>,
    relationTo: (String) -> Relation,
    onOpenLobby: (String) -> Unit,
    onAddFriend: (Player) -> Unit,
    blockedUsers: List<BlockedUser>,
    onSave: suspend (Profile) -> Unit,
    onUnblock: (BlockedUser) -> Unit,
    onUpgradeWithEmail: suspend (email: String, password: String) -> Unit,
    onUpgradeWithGoogle: (suspend () -> Unit)?,
    onDeleteAccount: suspend (password: String?) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val saved = profile ?: Profile(user.uid, user.displayName)
    // Start from the saved values, and reset if they change elsewhere.
    var name by remember(saved) { mutableStateOf(saved.name) }
    var level by remember(saved) { mutableStateOf(saved.level.orEmpty()) }
    var modes by remember(saved) { mutableStateOf(saved.preferredModes) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val edited = Profile(user.uid, name.trim(), level.trim().ifEmpty { null }, modes)
    val changed = edited != saved

    fun save() {
        message = validateName(name)
        if (message != null) return
        busy = true
        scope.launch {
            message = try {
                onSave(edited)
                "Saved"
            } catch (e: Exception) {
                "Couldn't save. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (user.isGuest) "Guest" else user.email ?: "Account",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (user.isGuest) {
            Text(
                "Level and preferred modes are saved with an account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OutlinedTextField(
                value = level,
                onValueChange = { level = it.take(MAX_LEVEL_LENGTH) },
                label = { Text("Level (optional)") },
                placeholder = { Text("e.g. Series 3") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Preferred modes (optional)", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode in modes,
                        onClick = { modes = if (mode in modes) modes - mode else modes + mode },
                        label = { Text(mode.label) },
                    )
                }
            }
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = ::save, enabled = changed && !busy) { Text("Save") }

        if (user.isGuest) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            GuestUpgradeSection(onUpgradeWithEmail, onUpgradeWithGoogle)
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Recently joined lobbies", style = MaterialTheme.typography.titleMedium)
        if (recentLobbies.isEmpty()) {
            Text(
                "Lobbies you've played in show up here after they end.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        recentLobbies.forEach { lobby ->
            LobbyCard(lobby, onClick = { onOpenLobby(lobby.id) })
        }

        if (recentPlayers.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Recently played with", style = MaterialTheme.typography.titleMedium)
            recentPlayers.forEach { player ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(player.name, modifier = Modifier.weight(1f))
                    if (!user.isGuest) {
                        when (relationTo(player.uid)) {
                            Relation.None -> TextButton(onClick = { onAddFriend(player) }) { Text("Add friend") }
                            Relation.RequestSent -> Text("Requested", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Relation.RequestReceived ->
                                Text("Wants to be friends", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Relation.Friend -> Text("Friend", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Relation.Self -> Unit
                        }
                    }
                }
            }
        }

        if (blockedUsers.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Blocked users", style = MaterialTheme.typography.titleMedium)
            blockedUsers.forEach { blocked ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(blocked.name)
                    TextButton(onClick = { onUnblock(blocked) }) { Text("Unblock") }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        OutlinedButton(onClick = onSignOut) {
            Text("Sign out")
        }
        DeleteAccountButton(user, onDeleteAccount)
    }
}
