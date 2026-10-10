package se.beachlobbyball.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import se.beachlobbyball.app.lobby.upcoming
import se.beachlobbyball.app.model.Lobby

private sealed interface ListState {
    data object Loading : ListState
    data class Loaded(val lobbies: List<Lobby>) : ListState
    data object Failed : ListState
}

/** Shows upcoming lobbies from [source], soonest first. */
@Composable
fun LobbyListScreen(
    source: Flow<List<Lobby>>,
    title: String,
    emptyText: String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flow = remember(source) {
        source
            .map<List<Lobby>, ListState> { ListState.Loaded(it.upcoming(Clock.System.now())) }
            .catch { emit(ListState.Failed) }
    }
    val state by flow.collectAsState(initial = ListState.Loading)

    Column(modifier.fillMaxSize()) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        when (val s = state) {
            ListState.Loading -> Centered { CircularProgressIndicator() }
            ListState.Failed -> Centered { Text("Couldn't load lobbies. Check your connection.") }
            is ListState.Loaded -> if (s.lobbies.isEmpty()) {
                Centered { Text(emptyText, textAlign = TextAlign.Center) }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(s.lobbies, key = { it.id }) { lobby ->
                        LobbyCard(lobby, onClick = { onOpen(lobby.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun LobbyCard(lobby: Lobby, onClick: () -> Unit) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(lobby.locationName, style = MaterialTheme.typography.titleMedium)
                Text(formatPlayerCount(lobby), style = MaterialTheme.typography.bodyMedium)
            }
            Text(formatDateTime(lobby.startsAt, timeZone), style = MaterialTheme.typography.bodyMedium)
            val tags = listOfNotNull(
                lobby.type?.label,
                lobby.mode?.label,
                lobby.level,
                if (lobby.isCourtBooked) "Court booked" else null,
                if (!lobby.isOpen) "Closed" else null,
            )
            if (tags.isNotEmpty()) {
                Text(
                    tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
