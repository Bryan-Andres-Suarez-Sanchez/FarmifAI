package edu.unicauca.app.agrochat.routing

import kotlin.math.max

/**
 * Pure routing policy for response decisions.
 * Keeps decision rules testable outside Android runtime.
 */
object ResponseRoutingPolicy {
    private const val HIGH_DIRECT_MIN_SIMILARITY = 0.88f
    private const val HIGH_DIRECT_MIN_SUPPORT = 0.72f
    private const val HIGH_DIRECT_MIN_COVERAGE = 0.58f
    private const val HIGH_DIRECT_MAX_UNKNOWN_RATIO = 0.28f

    data class Input(
        val hasRelatedKbSignal: Boolean,
        val hasGroundedKbSupport: Boolean,
        val allowGeneralLlmMode: Boolean,
        val skipKbDirect: Boolean,
        val useLlmForAll: Boolean,
        val bestSimilarityScore: Float,
        val kbSupportScore: Float,
        val kbCoverage: Float,
        val kbUnknownRatio: Float,
        val effectiveKbFastPathThreshold: Float
    )

    enum class Decision {
        KB_DIRECT,
        LLM_WITH_KB,
        LLM_GENERAL,
        ABSTAIN
    }

    data class Result(
        val decision: Decision,
        val reason: String
    )

    fun decide(input: Input): Result {
        val directSimilarityThreshold = max(input.effectiveKbFastPathThreshold, HIGH_DIRECT_MIN_SIMILARITY)
        val isHighConfidenceKbDirect =
            input.hasRelatedKbSignal &&
                input.hasGroundedKbSupport &&
                !input.skipKbDirect &&
                !input.useLlmForAll &&
                input.bestSimilarityScore >= directSimilarityThreshold &&
                input.kbSupportScore >= HIGH_DIRECT_MIN_SUPPORT &&
                input.kbCoverage >= HIGH_DIRECT_MIN_COVERAGE &&
                input.kbUnknownRatio <= HIGH_DIRECT_MAX_UNKNOWN_RATIO

        if (isHighConfidenceKbDirect) {
            return Result(
                decision = Decision.KB_DIRECT,
                reason = "high_confidence_kb_match"
            )
        }

        if (input.hasRelatedKbSignal) {
            return Result(
                decision = Decision.LLM_WITH_KB,
                reason = "kb_related_signal"
            )
        }

        if (input.allowGeneralLlmMode) {
            return Result(
                decision = Decision.LLM_GENERAL,
                reason = "general_llm_mode"
            )
        }

        return Result(
            decision = Decision.ABSTAIN,
            reason = "no_relevant_kb_signal"
        )
    }
}
