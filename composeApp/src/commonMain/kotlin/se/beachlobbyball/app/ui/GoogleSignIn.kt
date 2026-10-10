package se.beachlobbyball.app.ui

import androidx.compose.runtime.Composable
import se.beachlobbyball.app.auth.GoogleIdTokenSource

/** The Google account picker, or null on platforms that don't have one yet. */
@Composable
expect fun rememberGoogleIdTokenSource(): GoogleIdTokenSource?
