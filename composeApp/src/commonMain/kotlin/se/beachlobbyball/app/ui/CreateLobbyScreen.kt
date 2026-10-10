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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.lobby.LobbyRepository
import se.beachlobbyball.app.lobby.parseMaxPlayers
import se.beachlobbyball.app.lobby.startInstant
import se.beachlobbyball.app.lobby.validateNewLobby
import se.beachlobbyball.app.model.Format
import se.beachlobbyball.app.model.Location
import se.beachlobbyball.app.model.Lobby
import se.beachlobbyball.app.model.LobbyType
import se.beachlobbyball.app.model.Mode
import se.beachlobbyball.app.model.Visibility
import kotlin.time.Duration.Companion.minutes
import se.beachlobbyball.app.i18n.tr

private val durationOptions = listOf(60, 90, 120, 180)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateLobbyScreen(
    user: AppUser,
    repository: LobbyRepository,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val todayUtcMillis = remember {
        Clock.System.todayIn(timeZone).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    }

    var location by rememberSaveable { mutableStateOf(Location.GBC) }
    var otherLocationName by rememberSaveable { mutableStateOf("") }
    var dateUtcMillis by rememberSaveable { mutableLongStateOf(todayUtcMillis) }
    var hour by rememberSaveable { mutableIntStateOf(18) }
    var minute by rememberSaveable { mutableIntStateOf(0) }
    var durationMinutes by rememberSaveable { mutableIntStateOf(120) }
    var type by rememberSaveable { mutableStateOf<LobbyType?>(null) }
    var mode by rememberSaveable { mutableStateOf<Mode?>(null) }
    var format by rememberSaveable { mutableStateOf<Format?>(null) }
    var maxPlayersText by rememberSaveable { mutableStateOf("4") }
    var level by rememberSaveable { mutableStateOf("") }
    var isPrivate by rememberSaveable { mutableStateOf(false) }
    var isCourtBooked by rememberSaveable { mutableStateOf(false) }
    var courtNumber by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val startsAt = startInstant(dateUtcMillis, hour, minute, timeZone)

    fun submit() {
        val maxPlayers = parseMaxPlayers(maxPlayersText).getOrElse {
            error = it.message
            return
        }
        val lobby = Lobby(
            id = "",
            hostId = user.uid,
            hostName = user.displayName,
            location = location,
            otherLocationName = otherLocationName.trim().takeIf { location == Location.Other },
            createdAt = Clock.System.now(),
            startsAt = startsAt,
            duration = durationMinutes.minutes,
            type = type,
            mode = mode,
            format = format,
            maxPlayers = maxPlayers,
            level = level.trim().ifEmpty { null },
            visibility = if (isPrivate) Visibility.Private else Visibility.Public,
            isCourtBooked = isCourtBooked,
            courtNumber = courtNumber.trim().ifEmpty { null },
            note = note.trim().ifEmpty { null },
        )
        error = validateNewLobby(lobby, Clock.System.now())
        if (error != null) return
        busy = true
        scope.launch {
            try {
                onCreated(repository.create(lobby, user))
            } catch (e: Exception) {
                error = tr("Couldn't create the lobby. Check your connection and try again.", "Kunde inte skapa lobbyn. Kolla din uppkoppling och försök igen.")
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(tr("Create lobby", "Skapa lobby"), style = MaterialTheme.typography.headlineSmall)

        Section(tr("Where", "Var")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Location.entries.forEach {
                    FilterChip(selected = location == it, onClick = { location = it }, label = { Text(it.label) })
                }
            }
            if (location == Location.Other) {
                OutlinedTextField(
                    value = otherLocationName,
                    onValueChange = { otherLocationName = it },
                    label = { Text(tr("Place", "Plats")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Section(tr("When", "När")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text(formatDateTime(startsAt, timeZone).substringBefore(","))
                }
                OutlinedButton(onClick = { showTimePicker = true }) {
                    Text(formatTime(hour, minute))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                durationOptions.forEach {
                    FilterChip(
                        selected = durationMinutes == it,
                        onClick = { durationMinutes = it },
                        label = { Text(formatDuration(it.minutes)) },
                    )
                }
            }
        }

        Section(tr("Type", "Typ")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LobbyType.entries.forEach {
                    FilterChip(
                        selected = type == it,
                        onClick = { type = if (type == it) null else it },
                        label = { Text(it.label) },
                    )
                }
            }
        }

        Section(tr("Mode", "Spelform")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach {
                    FilterChip(
                        selected = mode == it,
                        onClick = { mode = if (mode == it) null else it },
                        label = { Text(it.label) },
                    )
                }
            }
        }

        Section(tr("Format", "Format")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Format.entries.forEach {
                    FilterChip(
                        selected = format == it,
                        onClick = {
                            format = if (format == it) null else it
                            // A format sets the matching player count, which can still be changed.
                            format?.let { picked -> maxPlayersText = "${picked.players}" }
                        },
                        label = { Text(it.label) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = maxPlayersText,
                onValueChange = { maxPlayersText = it.filter(Char::isDigit).take(2) },
                label = { Text(tr("Max players", "Max antal spelare")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = level,
                onValueChange = { level = it.take(30) },
                label = { Text(tr("Level", "Nivå")) },
                placeholder = { Text(tr("e.g. Series 3", "t.ex. Serie 3")) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        SwitchRow(tr("Private (invite only)", "Privat (endast inbjudna)"), isPrivate) { isPrivate = it }
        if (isPrivate) {
            Text(
                tr("Only friends you invite, or people you give the join code to, can join.", "Bara vänner du bjuder in, eller som får koden av dig, kan gå med."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SwitchRow(tr("Court booked", "Bana bokad"), isCourtBooked) { isCourtBooked = it }
        if (isCourtBooked) {
            OutlinedTextField(
                value = courtNumber,
                onValueChange = { courtNumber = it.take(10) },
                label = { Text(tr("Court", "Bana")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(300) },
            label = { Text(tr("Note (optional)", "Meddelande (valfritt)")) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Create lobby", "Skapa lobby"))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dateUtcMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(tr("Cancel", "Avbryt")) } },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour
                    minute = state.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(tr("Cancel", "Avbryt")) } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

