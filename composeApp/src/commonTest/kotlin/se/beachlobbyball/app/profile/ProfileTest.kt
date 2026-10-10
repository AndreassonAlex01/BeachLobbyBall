package se.beachlobbyball.app.profile

import se.beachlobbyball.app.model.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileTest {
    @Test
    fun roundTripKeepsFieldsAndLowercasesName() {
        val profile = Profile("u1", "Alex Andersson", "Series 3", listOf(Mode.Sparring, Mode.Casual))
        val doc = profile.toDocument()
        assertEquals("alex andersson", doc.nameLower)
        assertEquals(profile, doc.toProfile("u1"))
    }

    @Test
    fun unknownModesAreDropped() {
        assertEquals(listOf(Mode.Practice), ProfileDocument("A", "a", null, listOf("Practice", "Beach")).toProfile("u").preferredModes)
    }

    @Test
    fun emailKeyIsLowercaseAndSafe() {
        assertEquals("alex@example.se", emailKey("  Alex@Example.SE "))
        assertEquals("a_b@example.se", emailKey("a/b@example.se"))
    }

    @Test
    fun emailQueriesAreDetected() {
        assertTrue(looksLikeEmail("alex@example.se"))
        assertFalse(looksLikeEmail("Alex"))
    }
}
