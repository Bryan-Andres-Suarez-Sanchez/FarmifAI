package edu.unicauca.app.agrochat.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class ResponseRoutingPolicyTest {

    @Test
    fun `direct KB only for very high confidence`() {
        val result = ResponseRoutingPolicy.decide(
            ResponseRoutingPolicy.Input(
                hasRelatedKbSignal = true,
                hasGroundedKbSupport = true,
                allowGeneralLlmMode = false,
                skipKbDirect = false,
                useLlmForAll = false,
                bestSimilarityScore = 0.93f,
                kbSupportScore = 0.81f,
                kbCoverage = 0.70f,
                kbUnknownRatio = 0.18f,
                effectiveKbFastPathThreshold = 0.20f
            )
        )

        assertEquals(ResponseRoutingPolicy.Decision.KB_DIRECT, result.decision)
    }

    @Test
    fun `related KB with medium confidence goes to LLM with KB`() {
        val result = ResponseRoutingPolicy.decide(
            ResponseRoutingPolicy.Input(
                hasRelatedKbSignal = true,
                hasGroundedKbSupport = true,
                allowGeneralLlmMode = false,
                skipKbDirect = false,
                useLlmForAll = false,
                bestSimilarityScore = 0.62f,
                kbSupportScore = 0.58f,
                kbCoverage = 0.44f,
                kbUnknownRatio = 0.30f,
                effectiveKbFastPathThreshold = 0.20f
            )
        )

        assertEquals(ResponseRoutingPolicy.Decision.LLM_WITH_KB, result.decision)
    }

    @Test
    fun `skip direct forces LLM with KB even at high confidence`() {
        val result = ResponseRoutingPolicy.decide(
            ResponseRoutingPolicy.Input(
                hasRelatedKbSignal = true,
                hasGroundedKbSupport = true,
                allowGeneralLlmMode = false,
                skipKbDirect = true,
                useLlmForAll = false,
                bestSimilarityScore = 0.95f,
                kbSupportScore = 0.88f,
                kbCoverage = 0.72f,
                kbUnknownRatio = 0.14f,
                effectiveKbFastPathThreshold = 0.20f
            )
        )

        assertEquals(ResponseRoutingPolicy.Decision.LLM_WITH_KB, result.decision)
    }

    @Test
    fun `use llm for all disables direct KB`() {
        val result = ResponseRoutingPolicy.decide(
            ResponseRoutingPolicy.Input(
                hasRelatedKbSignal = true,
                hasGroundedKbSupport = true,
                allowGeneralLlmMode = false,
                skipKbDirect = false,
                useLlmForAll = true,
                bestSimilarityScore = 0.96f,
                kbSupportScore = 0.90f,
                kbCoverage = 0.80f,
                kbUnknownRatio = 0.12f,
                effectiveKbFastPathThreshold = 0.20f
            )
        )

        assertEquals(ResponseRoutingPolicy.Decision.LLM_WITH_KB, result.decision)
    }

    @Test
    fun `no related KB but llm general allowed uses LLM general`() {
        val result = ResponseRoutingPolicy.decide(
            ResponseRoutingPolicy.Input(
                hasRelatedKbSignal = false,
                hasGroundedKbSupport = false,
                allowGeneralLlmMode = true,
                skipKbDirect = false,
                useLlmForAll = false,
                bestSimilarityScore = 0.11f,
                kbSupportScore = 0.10f,
                kbCoverage = 0.08f,
                kbUnknownRatio = 0.95f,
                effectiveKbFastPathThreshold = 0.20f
            )
        )

        assertEquals(ResponseRoutingPolicy.Decision.LLM_GENERAL, result.decision)
    }

    @Test
    fun `no related KB and no general llm abstains`() {
        val result = ResponseRoutingPolicy.decide(
            ResponseRoutingPolicy.Input(
                hasRelatedKbSignal = false,
                hasGroundedKbSupport = false,
                allowGeneralLlmMode = false,
                skipKbDirect = false,
                useLlmForAll = false,
                bestSimilarityScore = 0.10f,
                kbSupportScore = 0.06f,
                kbCoverage = 0.07f,
                kbUnknownRatio = 0.97f,
                effectiveKbFastPathThreshold = 0.20f
            )
        )

        assertEquals(ResponseRoutingPolicy.Decision.ABSTAIN, result.decision)
    }
}
