package edu.unicauca.app.agrochat.rag

class CrossEncoderReranker(private val scorer: (String, String) -> Float) {
    fun rerank(query: String, candidates: List<RagChunk>): List<RetrievedChunk> = candidates
        .map { RetrievedChunk(it, scorer(query, it.text)) }
        .sortedByDescending { it.rerankScore }
}
