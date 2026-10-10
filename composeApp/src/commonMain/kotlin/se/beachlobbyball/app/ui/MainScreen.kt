package se.beachlobbyball.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import se.beachlobbyball.app.auth.AppUser

enum class Tab(val label: String, val icon: ImageVector) {
    PublicLobbies("Public", Icons.Filled.Public),
    PrivateLobbies("Private", Icons.Filled.Lock),
    CreateLobby("Create", Icons.Filled.AddCircle),
    Friends("Friends", Icons.Filled.People),
    Profile("Profile", Icons.Filled.Person),
}

@Composable
fun MainScreen(user: AppUser, onSignOut: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(Tab.PublicLobbies) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        if (selected == Tab.Profile) {
            ProfileScreen(user, onSignOut, Modifier.padding(padding))
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(selected.label)
            }
        }
    }
}
