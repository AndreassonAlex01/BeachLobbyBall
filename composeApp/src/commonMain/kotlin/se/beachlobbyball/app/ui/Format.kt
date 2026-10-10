package se.beachlobbyball.app.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.beachlobbyball.app.i18n.isSwedish
import se.beachlobbyball.app.i18n.tr
import se.beachlobbyball.app.model.Lobby
import kotlin.time.Duration

private val swedishDays = listOf("mån", "tis", "ons", "tor", "fre", "lör", "sön")
private val swedishMonths = listOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "aug", "sep", "okt", "nov", "dec")

/** For example "Sat 11 Oct, 18:00", or "lör 11 okt, 18:00" in Swedish. */
fun formatDateTime(instant: Instant, timeZone: TimeZone): String {
    val dt = instant.toLocalDateTime(timeZone)
    val day = if (isSwedish) swedishDays[dt.dayOfWeek.ordinal]
    else dt.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val month = if (isSwedish) swedishMonths[dt.month.ordinal]
    else dt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$day ${dt.dayOfMonth} $month, ${formatTime(dt.hour, dt.minute)}"
}

/** For example "18:05". */
fun formatTime(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** For example "2 h", "1 h 30 min" or "45 min". */
fun formatDuration(duration: Duration): String {
    val hours = duration.inWholeHours
    val minutes = duration.inWholeMinutes % 60
    val h = tr("h", "tim")
    return when {
        hours == 0L -> "$minutes min"
        minutes == 0L -> "$hours $h"
        else -> "$hours $h $minutes min"
    }
}

/** For example "3/4 players" or "3 players". */
fun formatPlayerCount(lobby: Lobby): String {
    val count = lobby.players.size
    return if (lobby.maxPlayers != null) tr("$count/${lobby.maxPlayers} players", "$count/${lobby.maxPlayers} spelare")
    else tr("$count ${if (count == 1) "player" else "players"}", "$count spelare")
}
