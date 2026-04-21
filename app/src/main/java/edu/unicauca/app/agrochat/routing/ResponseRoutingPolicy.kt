package edu.unicauca.app.agrochat.routing

/**
 * Pure routing policy for response decisions.
 * The policy never chooses a visible non-LLM answer. It only decides how strict
 * the grounded LLM generation should be.
 */
object ResponseRoutingPolicy {
    private const val HIGH_CONFIDENCE_MIN_SIMILARITY = 0.74f
    private const val HIGH_CONFIDENCE_MIN_SUPPORT = 0.62f
    private const val HIGH_CONFIDENCE_MIN_COVERAGE = 0.46f
    private const val HIGH_CONFIDENCE_MAX_UNKNOWN_RATIO = 0.42f

    data class Input(
        val hasRelatedKbSignal: Boolean,
        val bestSimilarityScore: Float,
        val kbSupportScore: Float,
        val kbCoverage: Float,
        val kbUnknownRatio: Float
    )

    enum class Decision {
        LLM_GROUNDED_HIGH_CONFIDENCE,
        LLM_GROUNDED_LOW_CONFIDENCE
    }

    data class Result(
        val decision: Decision,
        val reason: String
    )

    fun decide(input: Input): Result {
        val highConfidence =
            input.hasRelatedKbSignal &&
                input.bestSimilarityScore >= HIGH_CONFIDENCE_MIN_SIMILARITY &&
                input.kbSupportScore >= HIGH_CONFIDENCE_MIN_SUPPORT &&
                input.kbCoverage >= HIGH_CONFIDENCE_MIN_COVERAGE &&
                input.kbUnknownRatio <= HIGH_CONFIDENCE_MAX_UNKNOWN_RATIO

        return if (highConfidence) {
            Result(
                decision = Decision.LLM_GROUNDED_HIGH_CONFIDENCE,
                reason = "high_confidence_grounded_generation"
            )
        } else {
            Result(
                decision = Decision.LLM_GROUNDED_LOW_CONFIDENCE,
                reason = "low_confidence_grounded_generation"
            )
        }
    }
}
