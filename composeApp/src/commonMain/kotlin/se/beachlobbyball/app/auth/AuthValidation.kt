package se.beachlobbyball.app.auth

import se.beachlobbyball.app.i18n.tr

const val MIN_PASSWORD_LENGTH = 6
const val MAX_NAME_LENGTH = 30

private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Returns an error message, or null when the name is fine. */
fun validateName(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> tr("Enter a name", "Skriv ett namn")
        trimmed.length > MAX_NAME_LENGTH -> tr("Name can be at most $MAX_NAME_LENGTH characters", "Namnet får vara högst $MAX_NAME_LENGTH tecken")
        else -> null
    }
}

fun validateEmail(email: String): String? =
    if (emailPattern.matches(email.trim())) null else tr("Enter a valid email address", "Skriv en giltig e-postadress")

fun validatePassword(password: String): String? =
    if (password.length >= MIN_PASSWORD_LENGTH) null
    else tr("Password must be at least $MIN_PASSWORD_LENGTH characters", "Lösenordet måste ha minst $MIN_PASSWORD_LENGTH tecken")
