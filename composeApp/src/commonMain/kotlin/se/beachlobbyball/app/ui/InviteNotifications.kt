package se.beachlobbyball.app.ui

import androidx.compose.runtime.Composable
import se.beachlobbyball.app.invites.Invite

/** Shows a system notification for each new invite while the app is running. */
@Composable
expect fun InviteNotifications(invites: List<Invite>)
