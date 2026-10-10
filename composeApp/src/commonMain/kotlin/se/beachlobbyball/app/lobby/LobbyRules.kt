package se.beachlobbyball.app.lobby

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import se.beachlobbyball.app.model.Location
import se.beachlobbyball.app.model.Lobby
import kotlin.time.Duration
import se.beachlobbyball.app.i18n.tr

const val MIN_PLAYERS = 2
const val MAX_PLAYERS = 40

/**
 * Combines the date picked in a date picker (UTC midnight, as Material's DatePicker reports it)
 * with an hour and minute in the user's time zone.
 */
fun startInstant(pickedDateUtcMillis: Long, hour: Int, minute: Int, timeZone: TimeZone): Instant {
    val date = Instant.fromEpochMilliseconds(pickedDateUtcMillis).toLocalDateTime(TimeZone.UTC).date
    return LocalDateTime(date, LocalTime(hour, minute)).toInstant(timeZone)
}

/** Parses the max players field. Blank means no limit. Returns null for invalid input. */
fun parseMaxPlayers(text: String): Result<Int?> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Result.success(null)
    val value = trimmed.toIntOrNull()
    return if (value != null && value in MIN_PLAYERS..MAX_PLAYERS) Result.success(value)
    else Result.failure(IllegalArgumentException(
        tr("Max players must be between $MIN_PLAYERS and $MAX_PLAYERS", "Max antal spelare måste vara mellan $MIN_PLAYERS och $MAX_PLAYERS"),
    ))
}

/** Returns an error message for a lobby about to be created, or null when it's fine. */
fun validateNewLobby(lobby: Lobby, now: Instant): String? = when {
    lobby.location == Location.Other && lobby.otherLocationName.isNullOrBlank() -> tr("Enter where you're playing", "Skriv var ni spelar")
    lobby.startsAt <= now -> tr("Pick a start time in the future", "Välj en starttid framåt i tiden")
    lobby.duration <= Duration.ZERO -> tr("Pick a duration", "Välj hur länge ni spelar")
    else -> null
}

/** Upcoming and ongoing lobbies, soonest first. */
fun List<Lobby>.upcoming(now: Instant): List<Lobby> =
    filterNot { it.hasEnded(now) }.sortedBy { it.startsAt }
