package com.example.pguard.presentation.accountability

import kotlin.random.Random

/**
 * Pure-Kotlin generator and anti-paste evaluator for the "Myself" typing commitment challenge.
 *
 * Appends a unique 4-character confirmation code to each randomized phrase so pre-saved
 * clipboard snippets cannot be reused, and blocks bulk paste insertions.
 */
object TypingChallengeEngine {

    private val PLEDGES = listOf(
        "I choose my long term goals over temporary impulse and take full ownership of this action.",
        "I understand that turning off protection puts my progress at risk and I proceed with awareness.",
        "I am calm, deliberate, and taking full responsibility for changing my digital boundaries today.",
        "Short term urges fade within minutes while discipline builds lasting freedom and focus.",
        "I refuse to trade future clarity and self respect for a moment of mindless distraction.",
        "Every time I pause and reflect before disabling a guard I strengthen my self control.",
        "My attention is valuable and I intentionally protect my mind from impulsive scrolling.",
        "I commit to staying honest with myself and keeping my recovery standards intact today.",
        "Discomfort is temporary growth; breaking a habit requires patience and clear boundaries.",
        "I am stepping back to think clearly instead of reacting automatically to a craving.",
        "True freedom comes from mastering my impulses rather than giving in to convenience.",
        "I take full accountability for this decision and the impact it has on my daily habits."
    )

    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val CODE_LENGTH = 4
    private const val MAX_SINGLE_EDIT_INSERTION = 2

    data class TypingInputResult(
        val acceptedText: String,
        val pasteBlocked: Boolean
    )

    data class PrefixMatchState(
        val matchedCount: Int,
        val firstMismatchIndex: Int?,
        val isComplete: Boolean
    )

    fun generateChallenge(random: Random = Random.Default): String {
        val base = PLEDGES[random.nextInt(PLEDGES.size)]
        val code = buildString(CODE_LENGTH) {
            repeat(CODE_LENGTH) {
                append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)])
            }
        }
        return "$base [$code]"
    }

    /**
     * Allows normal character-by-character typing and deletions while rejecting clipboard paste
     * or autofill bursts (> 2 inserted characters in a single change event).
     */
    fun sanitizeTypedInput(previous: String, candidate: String): TypingInputResult {
        if (candidate == previous) {
            return TypingInputResult(acceptedText = previous, pasteBlocked = false)
        }

        val minLen = minOf(previous.length, candidate.length)
        var prefix = 0
        while (prefix < minLen && previous[prefix] == candidate[prefix]) {
            prefix++
        }

        var suffix = 0
        while (
            suffix < (previous.length - prefix) &&
            suffix < (candidate.length - prefix) &&
            previous[previous.length - 1 - suffix] == candidate[candidate.length - 1 - suffix]
        ) {
            suffix++
        }

        val insertedLen = candidate.length - prefix - suffix
        if (insertedLen > MAX_SINGLE_EDIT_INSERTION) {
            return TypingInputResult(acceptedText = previous, pasteBlocked = true)
        }
        return TypingInputResult(acceptedText = candidate, pasteBlocked = false)
    }

    fun evaluatePrefix(target: String, input: String): PrefixMatchState {
        val overlap = minOf(target.length, input.length)
        for (i in 0 until overlap) {
            if (target[i] != input[i]) {
                return PrefixMatchState(
                    matchedCount = i,
                    firstMismatchIndex = i,
                    isComplete = false
                )
            }
        }
        if (input.length > target.length) {
            return PrefixMatchState(
                matchedCount = target.length,
                firstMismatchIndex = target.length,
                isComplete = false
            )
        }
        return PrefixMatchState(
            matchedCount = overlap,
            firstMismatchIndex = null,
            isComplete = input == target
        )
    }
}
