package edu.unicauca.app.agrochat.rag

import kotlin.math.ln

/** Exact rank_bm25.BM25Okapi scoring, including its negative-IDF epsilon floor. */
class Bm25Retriever(
    documents: List<List<String>>,
    private val k1: Float = RagConfig.BM25_K1,
    private val b: Float = RagConfig.BM25_B,
    epsilon: Float = RagConfig.BM25_EPSILON
) {
    private val frequencies = documents.map { tokens -> tokens.groupingBy { it }.eachCount() }
    private val lengths = documents.map { it.size }
    private val averageLength = if (lengths.isEmpty()) 0f else lengths.average().toFloat()
    private val idf: Map<String, Float>

    init {
        val documentFrequency = mutableMapOf<String, Int>()
        frequencies.forEach { doc -> doc.keys.forEach { term -> documentFrequency[term] = (documentFrequency[term] ?: 0) + 1 } }
        val raw = documentFrequency.mapValues { (_, freq) ->
            (ln((documents.size - freq + 0.5).toDouble()) - ln((freq + 0.5).toDouble())).toFloat()
        }.toMutableMap()
        val averageIdf = if (raw.isEmpty()) 0f else raw.values.average().toFloat()
        val floor = epsilon * averageIdf
        raw.entries.filter { it.value < 0f }.forEach { it.setValue(floor) }
        idf = raw
    }

    fun search(queryTokens: List<String>, topK: Int): List<ScoredChunk> {
        if (frequencies.isEmpty()) return emptyList()
        val scores = FloatArray(frequencies.size)
        queryTokens.forEach { term ->
            val termIdf = idf[term] ?: 0f
            frequencies.indices.forEach { index ->
                val frequency = frequencies[index][term]?.toFloat() ?: 0f
                val normalization = frequency + k1 * (1f - b + b * lengths[index] / averageLength)
                if (normalization != 0f) scores[index] += termIdf * frequency * (k1 + 1f) / normalization
            }
        }
        return stableTopK(scores, topK)
    }

    companion object {
        /** NumPy argsort(scores)[::-1] equivalent: descending score, descending index on ties. */
        fun stableTopK(scores: FloatArray, topK: Int): List<ScoredChunk> = scores.indices
            .map { ScoredChunk(it, scores[it]) }
            .sortedWith(compareByDescending<ScoredChunk> { it.score }.thenByDescending { it.index })
            .take(topK.coerceAtLeast(0))
    }
}
