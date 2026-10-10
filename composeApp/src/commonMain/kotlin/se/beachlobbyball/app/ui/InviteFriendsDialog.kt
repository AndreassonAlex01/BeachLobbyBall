package se.beachlobbyball.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.friends.Friend
import se.beachlobbyball.app.friends.allTags
import se.beachlobbyball.app.friends.withTag
import se.beachlobbyball.app.invites.InviteRepository
import se.beachlobbyball.app.invites.invitableTo
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.i18n.tr

/** Pick friends to invite, one by one or everyone with a tag. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InviteFriendsDialog(
    lobby: Lobby,
    user: AppUser,
    friends: List<Friend>,
    invites: InviteRepository,
    onDismiss: () -> Unit,
) {
    val candidates = remember(friends, lobby) { friends.invitableTo(lobby) }
    val tags = remember(candidates) { candidates.allTags() }
    val sentFlow = remember(user.uid, lobby.id) {
        invites.sent(user.uid, lobby.id)
            .map { sent -> sent.map { it.toId }.toSet() }
            .catch { emit(emptySet()) }
    }
    val alreadyInvited by sentFlow.collectAsState(initial = emptySet())
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun toggle(uid: String) {
        selected = if (uid in selected) selected - uid else selected + uid
    }

    fun send() {
        busy = true
        error = null
        scope.launch {
            val failed = invites.send(user, lobby, candidates.filter { it.uid in selected })
            busy = false
            if (failed == 0) {
                onDismiss()
            } else {
                error = tr("$failed couldn't be sent. Check your connection and try again.", "$failed kunde inte skickas. Kolla din uppkoppling och försök igen.")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Invite friends", "Bjud in vänner")) },
        text = {
            if (candidates.isEmpty()) {
                Text(
                    if (friends.isEmpty()) tr("Add friends on the Friends tab to invite them.", "Lägg till vänner under Vänner för att bjuda in dem.")
                    else tr("All your friends are already in this lobby.", "Alla dina vänner är redan med i lobbyn.")
                )
            } else Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (tags.isNotEmpty()) {
                    Text(tr("Select everyone tagged", "Välj alla med taggen"), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag ->
                            val tagged = candidates.withTag(tag).map { it.uid }.filterNot { it in alreadyInvited }.toSet()
                            val allSelected = tagged.isNotEmpty() && selected.containsAll(tagged)
                            FilterChip(
                                selected = allSelected,
                                enabled = tagged.isNotEmpty(),
                                onClick = { selected = if (allSelected) selected - tagged else selected + tagged },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
                candidates.forEach { friend ->
                    val invited = friend.uid in alreadyInvited
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !invited) { toggle(friend.uid) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = invited || friend.uid in selected,
                            onCheckedChange = { toggle(friend.uid) },
                            enabled = !invited,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(friend.name)
                            if (friend.tags.isNotEmpty()) {
                                Text(
                                    friend.tags.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (invited) {
                            Text(tr("Invited", "Inbjuden"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = ::send, enabled = selected.isNotEmpty() && !busy) {
                Text(if (selected.isEmpty()) tr("Invite", "Bjud in") else tr("Invite ${selected.size}", "Bjud in ${selected.size}"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Close", "Stäng")) } },
    )
}
