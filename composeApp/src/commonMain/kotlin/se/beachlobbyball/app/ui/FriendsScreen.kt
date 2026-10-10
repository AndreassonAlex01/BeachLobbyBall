package se.beachlobbyball.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.friends.Friend
import se.beachlobbyball.app.friends.FriendRequest
import se.beachlobbyball.app.friends.FriendsRepository
import se.beachlobbyball.app.friends.Relation
import se.beachlobbyball.app.friends.allTags
import se.beachlobbyball.app.friends.cleanTag
import se.beachlobbyball.app.friends.relationTo
import se.beachlobbyball.app.friends.toggleTag
import se.beachlobbyball.app.friends.withTag
import se.beachlobbyball.app.profile.Profile
import se.beachlobbyball.app.profile.ProfileRepository

/** Friends, requests and search. The lists come from MainScreen so other screens can share them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FriendsScreen(
    user: AppUser,
    friends: List<Friend>,
    incoming: List<FriendRequest>,
    outgoing: List<FriendRequest>,
    friendsRepository: FriendsRepository,
    profiles: ProfileRepository,
    modifier: Modifier = Modifier,
) {
    if (user.isGuest) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Friends need an account.\nSign out and choose Create account to add friends.",
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Profile>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Friend?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun perform(failure: String, action: suspend () -> Unit) {
        error = null
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                error = failure
            }
        }
    }

    fun search() {
        if (query.isBlank()) return
        searching = true
        scope.launch {
            results = try {
                profiles.search(query, user.uid)
            } catch (e: Exception) {
                error = "Search failed. Check your connection."
                null
            }
            searching = false
        }
    }

    val tags = friends.allTags()
    val shown = friends.withTag(tagFilter)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Friends", style = MaterialTheme.typography.headlineSmall) }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Find people by name or email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                trailingIcon = {
                    TextButton(onClick = ::search, enabled = !searching && query.isNotBlank()) { Text("Search") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        results?.let { found ->
            if (found.isEmpty()) {
                item { Text("No one found. Names match from the start; emails must match exactly.") }
            }
            items(found, key = { "result-${it.uid}" }) { profile ->
                PersonRow(
                    name = profile.name,
                    detail = profile.level,
                    relation = relationTo(profile.uid, user.uid, friends, incoming, outgoing),
                    onAdd = {
                        perform("Couldn't send the request") {
                            friendsRepository.sendRequest(user, profile.uid, profile.name)
                        }
                    },
                    onAccept = {
                        incoming.firstOrNull { it.fromId == profile.uid }?.let { request ->
                            perform("Couldn't accept the request") { friendsRepository.accept(user, request) }
                        }
                    },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        }

        if (incoming.isNotEmpty()) {
            item { Text("Friend requests", style = MaterialTheme.typography.titleMedium) }
            items(incoming, key = { "in-${it.id}" }) { request ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(request.fromName, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        perform("Couldn't decline the request") { friendsRepository.deleteRequest(request) }
                    }) { Text("Decline") }
                    OutlinedButton(onClick = {
                        perform("Couldn't accept the request") { friendsRepository.accept(user, request) }
                    }) { Text("Accept") }
                }
            }
        }

        if (outgoing.isNotEmpty()) {
            item { Text("Sent requests", style = MaterialTheme.typography.titleMedium) }
            items(outgoing, key = { "out-${it.id}" }) { request ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(request.toName, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        perform("Couldn't cancel the request") { friendsRepository.deleteRequest(request) }
                    }) { Text("Cancel") }
                }
            }
        }

        item {
            Text(
                "Your friends (${friends.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (tags.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tagFilter == null, onClick = { tagFilter = null }, label = { Text("All") })
                    tags.forEach { tag ->
                        FilterChip(
                            selected = tagFilter.equals(tag, ignoreCase = true),
                            onClick = { tagFilter = if (tagFilter.equals(tag, ignoreCase = true)) null else tag },
                            label = { Text(tag) },
                        )
                    }
                }
            }
        }
        if (friends.isEmpty()) {
            item {
                Text(
                    "No friends yet. Search above, or add people from a lobby's player list.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(shown, key = { "friend-${it.uid}" }) { friend ->
            Column(
                Modifier.fillMaxWidth().clickable { editing = friend }.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(friend.name, style = MaterialTheme.typography.bodyLarge)
                if (friend.tags.isNotEmpty()) {
                    Text(
                        friend.tags.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    editing?.let { friend ->
        FriendDialog(
            friend = friend,
            allTags = tags,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                perform("Couldn't save the tags") { friendsRepository.save(user.uid, updated) }
            },
            onRemove = {
                editing = null
                perform("Couldn't remove the friend") { friendsRepository.remove(user.uid, friend.uid) }
            },
        )
    }
}

@Composable
private fun PersonRow(
    name: String,
    detail: String?,
    relation: Relation,
    onAdd: () -> Unit,
    onAccept: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name)
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (relation) {
            Relation.None -> OutlinedButton(onClick = onAdd) { Text("Add") }
            Relation.RequestReceived -> OutlinedButton(onClick = onAccept) { Text("Accept") }
            Relation.RequestSent -> Text("Requested", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Relation.Friend -> Text("Friend", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Relation.Self -> Unit
        }
    }
}

/** Edits a friend's private tags, or removes the friend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FriendDialog(
    friend: Friend,
    allTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (Friend) -> Unit,
    onRemove: () -> Unit,
) {
    var current by remember(friend.uid) { mutableStateOf(friend) }
    var newTag by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    val options = (allTags + current.tags).distinctBy { it.lowercase() }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${friend.name}?") },
            text = { Text("You'll both be removed from each other's friend lists.") },
            confirmButton = { TextButton(onClick = onRemove) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(friend.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tags are private. ${friend.name} can't see them.", style = MaterialTheme.typography.bodySmall)
                if (options.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { tag ->
                            FilterChip(
                                selected = current.tags.any { it.equals(tag, ignoreCase = true) },
                                onClick = { current = current.toggleTag(tag) },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text("New tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    AssistChip(
                        onClick = {
                            cleanTag(newTag)?.let { tag ->
                                if (current.tags.none { it.equals(tag, ignoreCase = true) }) {
                                    current = current.copy(tags = current.tags + tag)
                                }
                            }
                            newTag = ""
                        },
                        label = { Text("Add") },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                TextButton(onClick = { confirmRemove = true }) {
                    Text("Remove friend", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(current) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
