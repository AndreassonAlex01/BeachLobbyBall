package se.beachlobbyball.app.ui

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import se.beachlobbyball.app.R
import se.beachlobbyball.app.invites.Invite

private const val CHANNEL_ID = "invites"

@Composable
actual fun InviteNotifications(invites: List<Invite>) {
    val context = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !canNotify(context)) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Invites sent before the app opened only show as a badge. Newer ones notify once each.
    val openedAt = remember { Clock.System.now() }
    val notified = remember { mutableSetOf<String>() }
    LaunchedEffect(invites) {
        for (invite in invites) {
            if (invite.sentAt > openedAt && notified.add(invite.id)) notify(context, invite)
        }
    }
}

private fun canNotify(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun notify(context: Context, invite: Invite) {
    if (!canNotify(context)) return
    val manager = NotificationManagerCompat.from(context)
    manager.createNotificationChannel(
        NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Lobby invites")
            .build()
    )
    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val tap = PendingIntent.getActivity(
        context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("${invite.fromName} invited you")
        .setContentText("${invite.locationName}, ${formatDateTime(invite.startsAt, TimeZone.currentSystemDefault())}")
        .setContentIntent(tap)
        .setAutoCancel(true)
        .build()
    try {
        manager.notify(invite.id.hashCode(), notification)
    } catch (e: SecurityException) {
        // Permission was revoked in the meantime. The badge still shows the invite.
    }
}
