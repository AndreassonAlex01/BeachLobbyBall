package se.beachlobbyball.app.i18n

import java.util.Locale

// Android keeps the default locale in step with the phone's language setting.
actual fun systemLanguage(): String = Locale.getDefault().language
