package edu.unicauca.app.agrochat.rag

import org.junit.Assert.assertEquals
import org.junit.Test

class RrfFusionTest {
    @Test
    fun fusesRanksAndDeduplicatesCandidates() {
        val fused = RrfFusion.fuse(
            listOf(
                listOf(ScoredChunk(4, 99f), ScoredChunk(2, 5f)),
                listOf(ScoredChunk(2, 0.9f), ScoredChunk(7, 0.8f))
            )
        )
        assertEquals(listOf(2, 4, 7), fused.map { it.index })
        assertEquals(1f / 62f + 1f / 61f, fused.first().score, 0.000001f)
    }
}
