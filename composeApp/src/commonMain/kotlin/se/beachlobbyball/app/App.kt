package se.beachlobbyball.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import se.beachlobbyball.app.auth.FirebaseAuthRepository
import se.beachlobbyball.app.auth.Session
import se.beachlobbyball.app.ui.MainScreen
import se.beachlobbyball.app.ui.WelcomeScreen

@Composable
fun App() {
    val auth = remember { FirebaseAuthRepository() }
    val session by auth.session.collectAsState(initial = Session.Loading)
    val scope = rememberCoroutineScope()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val s = session) {
                Session.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                Session.SignedOut -> WelcomeScreen(auth)
                is Session.SignedIn -> MainScreen(
                    user = s.user,
                    onSignOut = { scope.launch { auth.signOut() } },
                )
            }
        }
    }
}
