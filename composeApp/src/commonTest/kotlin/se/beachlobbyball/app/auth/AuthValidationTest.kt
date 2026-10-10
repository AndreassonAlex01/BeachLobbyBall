package se.beachlobbyball.app.auth

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuthValidationTest {
    @Test
    fun nameMustNotBeBlank() {
        assertNotNull(validateName("   "))
        assertNull(validateName("Alex"))
    }

    @Test
    fun nameHasMaxLength() {
        assertNotNull(validateName("a".repeat(MAX_NAME_LENGTH + 1)))
        assertNull(validateName("a".repeat(MAX_NAME_LENGTH)))
    }

    @Test
    fun emailNeedsAtAndDomain() {
        assertNull(validateEmail("alex@example.se"))
        assertNull(validateEmail(" alex@example.se "))
        assertNotNull(validateEmail("alex"))
        assertNotNull(validateEmail("alex@example"))
        assertNotNull(validateEmail("a lex@example.se"))
    }

    @Test
    fun passwordNeedsMinLength() {
        assertNotNull(validatePassword("12345"))
        assertNull(validatePassword("123456"))
    }
}
