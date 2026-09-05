package edu.unicauca.app.agrochat.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Bm25RetrieverTest {
    @Test
    fun ranksByBm25AndUsesDescendingIndexForTies() {
        val retriever = Bm25Retriever(listOf(listOf("cafe", "roya"), listOf("cafe"), listOf("suelo")))
        val results = retriever.search(listOf("roya"), 3)
        assertEquals(0, results.first().index)
        assertTrue(results.first().score > 0f)
        assertEquals(listOf(0, 2, 1), results.map { it.index })
    }

    @Test
    fun duplicateQueryTermsContributeRepeatedlyLikeRankBm25() {
        val retriever = Bm25Retriever(listOf(listOf("roya"), listOf("suelo")))
        val once = retriever.search(listOf("roya"), 1).first().score
        val twice = retriever.search(listOf("roya", "roya"), 1).first().score
        assertEquals(once * 2f, twice, 0.00001f)
    }
}
