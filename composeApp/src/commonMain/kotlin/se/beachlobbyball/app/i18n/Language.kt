package se.beachlobbyball.app.i18n

/** The phone's language as a code like "sv" or "en". */
expect fun systemLanguage(): String

/** Lets tests pick a language. */
internal var languageOverride: String? = null

/** Swedish phones get Swedish, everyone else English. */
val isSwedish: Boolean get() = (languageOverride ?: systemLanguage()) == "sv"

/** Picks the text for the phone's language. Both versions sit side by side where the text is used. */
fun tr(en: String, sv: String): String = if (isSwedish) sv else en
