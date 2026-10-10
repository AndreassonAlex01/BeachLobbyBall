package se.beachlobbyball.app.auth

const val MIN_PASSWORD_LENGTH = 6
const val MAX_NAME_LENGTH = 30

private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Returns an error message, or null when the name is fine. */
fun validateName(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Enter a name"
        trimmed.length > MAX_NAME_LENGTH -> "Name can be at most $MAX_NAME_LENGTH characters"
        else -> null
    }
}

fun validateEmail(email: String): String? =
    if (emailPattern.matches(email.trim())) null else "Enter a valid email address"

fun validatePassword(password: String): String? =
    if (password.length >= MIN_PASSWORD_LENGTH) null
    else "Password must be at least $MIN_PASSWORD_LENGTH characters"
