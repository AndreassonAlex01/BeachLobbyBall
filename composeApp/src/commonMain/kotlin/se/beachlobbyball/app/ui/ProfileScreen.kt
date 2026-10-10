package se.beachlobbyball.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.chat.BlockedUser

@Composable
fun ProfileScreen(
    user: AppUser,
    blockedUsers: List<BlockedUser>,
    onUnblock: (BlockedUser) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(user.displayName, style = MaterialTheme.typography.headlineMedium)
        Text(if (user.isGuest) "Guest" else "Account", style = MaterialTheme.typography.bodyMedium)

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
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }

        OutlinedButton(onClick = onSignOut) {
            Text("Sign out")
        }
    }
}
