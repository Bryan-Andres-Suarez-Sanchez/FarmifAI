package edu.unicauca.app.agrochat.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class ResponseRoutingPolicyTest {

    @Test
    fun `high confidence related KB uses strict grounded LLM`() {
        val result = ResponseRoutingPolicy.decide(
            ResponseRoutingPolicy.Input(
                hasRelatedKbSignal = true,
                bestSimilarityScore = 0.93f,
                kbSupportScore = 0.81f,
                kbCoverage = 0.70f,
                kbUnknownRatio = 0.18f
            )
        )

        assertEquals(ResponseRoutingPolicy.Decision.LLM_GROUNDED_HIGH_CONFIDENCE, result.decision)
    }

    @Test
    fun `related KB with medium confidence uses low confidence grounded LLM`() {
        val result = ResponseRoutingPolicy.decide(
            ResponseRoutingPolicy.Input(
                hasRelatedKbSignal = true,
                bestSimilarityScore = 0.62f,
                kbSupportScore = 0.58f,
                kbCoverage = 0.44f,
                kbUnknownRatio = 0.30f
            )
        )

        assertEquals(ResponseRoutingPolicy.Decision.LLM_GROUNDED_LOW_CONFIDENCE, result.decision)
    }

    @Test
    fun `no related KB still uses low confidence grounded LLM policy`() {
        val result = ResponseRoutingPolicy.decide(
            ResponseRoutingPolicy.Input(
                hasRelatedKbSignal = false,
                bestSimilarityScore = 0.11f,
                kbSupportScore = 0.10f,
                kbCoverage = 0.08f,
                kbUnknownRatio = 0.95f
            )
        )

        assertEquals(ResponseRoutingPolicy.Decision.LLM_GROUNDED_LOW_CONFIDENCE, result.decision)
    }

    @Test
    fun `high unknown ratio prevents high confidence`() {
        val result = ResponseRoutingPolicy.decide(
            ResponseRoutingPolicy.Input(
                hasRelatedKbSignal = true,
                bestSimilarityScore = 0.96f,
                kbSupportScore = 0.90f,
                kbCoverage = 0.80f,
                kbUnknownRatio = 0.70f
            )
        )

        assertEquals(ResponseRoutingPolicy.Decision.LLM_GROUNDED_LOW_CONFIDENCE, result.decision)
    }
}
