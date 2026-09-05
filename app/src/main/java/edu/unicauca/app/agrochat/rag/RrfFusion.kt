package edu.unicauca.app.agrochat.rag

object RrfFusion {
    fun fuse(resultLists: List<List<ScoredChunk>>, k: Int = RagConfig.RRF_K): List<ScoredChunk> {
        val scores = linkedMapOf<Int, Float>()
        resultLists.forEach { results ->
            results.forEachIndexed { zeroBasedRank, item ->
                scores[item.index] = (scores[item.index] ?: 0f) + 1f / (k + zeroBasedRank + 1)
            }
        }
        // Python's stable sort preserves first insertion order for exact ties.
        return scores.entries.map { ScoredChunk(it.key, it.value) }.sortedByDescending { it.score }
    }
}
