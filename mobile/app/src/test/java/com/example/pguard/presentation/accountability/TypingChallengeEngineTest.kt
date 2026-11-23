package com.example.pguard.presentation.accountability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TypingChallengeEngineTest {

    @Test
    fun `generateChallenge appends unique 4-character verification suffix`() {
        val c1 = TypingChallengeEngine.generateChallenge(Random(1))
        val c2 = TypingChallengeEngine.generateChallenge(Random(2))

        assertNotEquals(c1, c2)
        assertTrue(Regex(""".+\[[A-Z2-9]{4}\]$""").matches(c1))
        assertTrue(Regex(""".+\[[A-Z2-9]{4}\]$""").matches(c2))
    }

    @Test
    fun `sanitizeTypedInput allows single character typing and deletions while rejecting paste bursts`() {
        val typed = TypingChallengeEngine.sanitizeTypedInput("I choos", "I choose")
        assertFalse(typed.pasteBlocked)
        assertEquals("I choose", typed.acceptedText)

        val deleted = TypingChallengeEngine.sanitizeTypedInput("I choose", "I choo")
        assertFalse(deleted.pasteBlocked)
        assertEquals("I choo", deleted.acceptedText)

        val pasted = TypingChallengeEngine.sanitizeTypedInput(
            "I ",
            "I choose my long term goals over temporary impulse"
        )
        assertTrue(pasted.pasteBlocked)
        assertEquals("I ", pasted.acceptedText)

        // Selection-replacement paste burst (same length, different content > 2 chars) is also rejected
        val replaced = TypingChallengeEngine.sanitizeTypedInput(
            "abcdefghij",
            "xyzzzzzzzz"
        )
        assertTrue(replaced.pasteBlocked)
        assertEquals("abcdefghij", replaced.acceptedText)
    }

    @Test
    fun `evaluatePrefix tracks matched prefix first mismatch index and completion`() {
        val partial = TypingChallengeEngine.evaluatePrefix("Stay strong [AB23]", "Stay s")
        assertEquals(6, partial.matchedCount)
        assertNull(partial.firstMismatchIndex)
        assertFalse(partial.isComplete)

        val typo = TypingChallengeEngine.evaluatePrefix("Stay strong [AB23]", "Stay Xtrong")
        assertEquals(5, typo.matchedCount)
        assertEquals(5, typo.firstMismatchIndex)
        assertFalse(typo.isComplete)

        val complete = TypingChallengeEngine.evaluatePrefix("Stay strong [AB23]", "Stay strong [AB23]")
        assertEquals(18, complete.matchedCount)
        assertNull(complete.firstMismatchIndex)
        assertTrue(complete.isComplete)

        val overtyped = TypingChallengeEngine.evaluatePrefix("abc", "abcd")
        assertEquals(3, overtyped.matchedCount)
        assertEquals(3, overtyped.firstMismatchIndex)
        assertFalse(overtyped.isComplete)
    }
}
