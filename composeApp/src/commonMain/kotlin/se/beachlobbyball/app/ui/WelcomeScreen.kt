package se.beachlobbyball.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.beachlobbyball.app.auth.AuthRepository
import se.beachlobbyball.app.auth.validateEmail
import se.beachlobbyball.app.auth.validateName
import se.beachlobbyball.app.auth.validatePassword

private enum class WelcomeMode { Choose, Guest, SignIn, CreateAccount }

@Composable
fun WelcomeScreen(auth: AuthRepository) {
    var mode by rememberSaveable { mutableStateOf(WelcomeMode.Choose) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        error = when (mode) {
            WelcomeMode.Guest -> validateName(name)
            WelcomeMode.SignIn -> validateEmail(email) ?: validatePassword(password)
            WelcomeMode.CreateAccount ->
                validateName(name) ?: validateEmail(email) ?: validatePassword(password)
            WelcomeMode.Choose -> null
        }
        if (error != null) return
        busy = true
        scope.launch {
            try {
                when (mode) {
                    WelcomeMode.Guest -> auth.continueAsGuest(name)
                    WelcomeMode.SignIn -> auth.signInWithEmail(email, password)
                    WelcomeMode.CreateAccount -> auth.createAccount(name, email, password)
                    WelcomeMode.Choose -> Unit
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BeachLobbyBall", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Find and organize beach volleyball games",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))

        if (mode == WelcomeMode.Choose) {
            Button(onClick = { mode = WelcomeMode.SignIn }, modifier = Modifier.fillMaxWidth()) {
                Text("Sign in with email")
            }
            OutlinedButton(
                onClick = { mode = WelcomeMode.CreateAccount },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Create account")
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            TextButton(onClick = { mode = WelcomeMode.Guest }) {
                Text("Continue as guest")
            }
            return@Column
        }

        if (mode == WelcomeMode.Guest || mode == WelcomeMode.CreateAccount) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (mode == WelcomeMode.SignIn || mode == WelcomeMode.CreateAccount) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(
                when (mode) {
                    WelcomeMode.Guest -> "Continue as guest"
                    WelcomeMode.SignIn -> "Sign in"
                    else -> "Create account"
                }
            )
        }
        TextButton(onClick = {
            mode = WelcomeMode.Choose
            error = null
        }) {
            Text("Back")
        }
    }
}
