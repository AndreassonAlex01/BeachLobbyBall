package se.beachlobbyball.app.chat

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatMessageTest {
    @Test
    fun blankMessagesAreNotSent() {
        assertNull(cleanMessage(""))
        assertNull(cleanMessage("   \n "))
    }

    @Test
    fun messagesAreTrimmedAndCapped() {
        assertEquals("hej", cleanMessage("  hej \n"))
        assertEquals(MAX_MESSAGE_LENGTH, cleanMessage("a".repeat(MAX_MESSAGE_LENGTH + 50))?.length)
    }

    @Test
    fun blockedSendersAreHidden() {
        val at = Instant.fromEpochMilliseconds(0)
        val messages = listOf(
            ChatMessage("1", "a", "Alex", "hi", at),
            ChatMessage("2", "b", "Sam", "spam", at),
            ChatMessage("3", "a", "Alex", "see you", at),
        )
        assertEquals(listOf("1", "3"), messages.visibleTo(setOf("b")).map { it.id })
        assertEquals(3, messages.visibleTo(emptySet()).size)
    }

    @Test
    fun documentMapsToMessage() {
        val message = MessageDocument("a", "Alex", "hi", 1_760_000_000_000).toMessage("m1")
        assertEquals(ChatMessage("m1", "a", "Alex", "hi", Instant.fromEpochMilliseconds(1_760_000_000_000)), message)
    }
}
