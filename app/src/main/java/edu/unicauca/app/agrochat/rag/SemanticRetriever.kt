package edu.unicauca.app.agrochat.rag

class SemanticRetriever(
    private val embeddings: NpyFloatMatrix,
    private val encoder: (String) -> FloatArray
) {
    fun encodeQuery(query: String): FloatArray {
        val vector = encoder("query: $query")
        require(vector.size == RagConfig.EMBEDDING_DIMENSION)
        normalize(vector)
        return vector
    }

    fun search(vector: FloatArray, topK: Int): List<ScoredChunk> {
        require(vector.size == RagConfig.EMBEDDING_DIMENSION)
        val scores = FloatArray(embeddings.rows) { embeddings.dot(it, vector) }
        return Bm25Retriever.stableTopK(scores, topK)
    }

    private fun normalize(vector: FloatArray) {
        val norm = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) vector.indices.forEach { vector[it] /= norm }
    }
}
