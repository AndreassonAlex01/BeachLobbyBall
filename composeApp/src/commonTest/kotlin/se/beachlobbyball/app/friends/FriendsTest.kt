package se.beachlobbyball.app.friends

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FriendsTest {
    private val alex = Friend("a", "Alex", listOf("Mixed", "Series practice"))
    private val sam = Friend("s", "sam", listOf("mixed"))
    private val kim = Friend("k", "Kim")

    @Test
    fun tagsAreCleaned() {
        assertEquals("Series practice", cleanTag("  Series   practice "))
        assertEquals(MAX_TAG_LENGTH, cleanTag("x".repeat(50))?.length)
        assertNull(cleanTag("   "))
    }

    @Test
    fun allTagsIgnoresCaseDuplicates() {
        assertEquals(listOf("Mixed", "Series practice"), listOf(alex, sam, kim).allTags())
    }

    @Test
    fun filterByTagIgnoresCaseAndSortsByName() {
        assertEquals(listOf("Alex", "sam"), listOf(sam, kim, alex).withTag("MIXED").map { it.name })
        assertEquals(listOf("Alex", "Kim", "sam"), listOf(sam, kim, alex).withTag(null).map { it.name })
    }

    @Test
    fun toggleTagAddsAndRemoves() {
        assertEquals(listOf("Men"), kim.toggleTag("Men").tags)
        assertEquals(listOf("Series practice"), alex.toggleTag("mixed").tags)
    }

    @Test
    fun relationPicksTheRightState() {
        val incoming = listOf(FriendRequest("r", "R", "me", "Me"))
        val outgoing = listOf(FriendRequest("me", "Me", "o", "O"))
        fun rel(uid: String) = relationTo(uid, "me", listOf(alex), incoming, outgoing)
        assertEquals(Relation.Self, rel("me"))
        assertEquals(Relation.Friend, rel("a"))
        assertEquals(Relation.RequestReceived, rel("r"))
        assertEquals(Relation.RequestSent, rel("o"))
        assertEquals(Relation.None, rel("x"))
    }

    @Test
    fun requestIdIsFromThenTo() {
        assertEquals("me_o", FriendRequest("me", "Me", "o", "O").id)
    }
}
