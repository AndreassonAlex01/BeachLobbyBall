package se.beachlobbyball.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.beachlobbyball.app.auth.AppUser

@Composable
fun ProfileScreen(user: AppUser, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(user.displayName, style = MaterialTheme.typography.headlineMedium)
        Text(if (user.isGuest) "Guest" else "Account", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onSignOut) {
            Text("Sign out")
        }
    }
}
