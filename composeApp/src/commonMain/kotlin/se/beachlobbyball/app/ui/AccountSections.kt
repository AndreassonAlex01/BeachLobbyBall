package se.beachlobbyball.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.beachlobbyball.app.auth.AppUser
import se.beachlobbyball.app.auth.SignInMethod
import se.beachlobbyball.app.auth.validateEmail
import se.beachlobbyball.app.auth.validatePassword

/** Lets a guest become an account without losing their lobbies and chats. */
@Composable
fun GuestUpgradeSection(
    onUpgradeWithEmail: suspend (email: String, password: String) -> Unit,
    onUpgradeWithGoogle: (suspend () -> Unit)?,
) {
    var useEmail by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run(action: suspend () -> Unit) {
        error = null
        busy = true
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Keep your games", style = MaterialTheme.typography.titleMedium)
        Text(
            "Create an account to add friends, get invites and sign in on another phone. " +
                "Your lobbies and chats come with you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onUpgradeWithGoogle != null) {
            Button(
                onClick = { run(onUpgradeWithGoogle) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Continue with Google") }
        }
        if (!useEmail) {
            OutlinedButton(onClick = { useEmail = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Use email and password")
            }
        } else {
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
            Button(
                onClick = {
                    error = validateEmail(email) ?: validatePassword(password)
                    if (error == null) run { onUpgradeWithEmail(email, password) }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create account") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** The delete button and its confirmation, which Play requires apps to offer. */
@Composable
fun DeleteAccountButton(user: AppUser, onDelete: suspend (password: String?) -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    TextButton(onClick = { confirming = true }) {
        Text("Delete account", color = MaterialTheme.colorScheme.error)
    }
    if (!confirming) return

    AlertDialog(
        onDismissRequest = { if (!busy) confirming = false },
        title = { Text("Delete your account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This deletes your profile, friends, invites and chat messages, and the lobbies you host. " +
                        "You leave every other lobby. This can't be undone."
                )
                when (user.method) {
                    SignInMethod.Email -> OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Your password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SignInMethod.Google -> Text(
                        "You'll confirm with your Google account first.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SignInMethod.Guest -> Unit
                }
                if (busy) Text("Deleting…", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && (user.method != SignInMethod.Email || password.isNotEmpty()),
                onClick = {
                    error = null
                    busy = true
                    scope.launch {
                        try {
                            onDelete(password.ifEmpty { null })
                        } catch (e: Exception) {
                            error = e.message ?: "Something went wrong"
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = { confirming = false }, enabled = !busy) { Text("Cancel") }
        },
    )
}
