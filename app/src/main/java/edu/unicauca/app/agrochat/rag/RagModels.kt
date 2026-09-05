package edu.unicauca.app.agrochat.rag

data class RagChunk(
    val index: Int,
    val documentId: String,
    val chunkNumber: String,
    val text: String,
    val citation: String = "Fuente no especificada",
    val date: String = ""
)

data class ScoredChunk(val index: Int, val score: Float)

data class RetrievedChunk(
    val chunk: RagChunk,
    val rerankScore: Float
)

data class RagResult(
    val bm25: List<ScoredChunk>,
    val semantic: List<ScoredChunk>,
    val fused: List<ScoredChunk>,
    val reranked: List<RetrievedChunk>,
    val context: String
)

object RagConfig {
    const val LEXICAL_TOP_K = 10
    const val SEMANTIC_TOP_K = 10
    const val FINAL_TOP_K = 3
    const val RRF_K = 60
    const val EMBEDDING_DIMENSION = 384
    const val EMBEDDING_MAX_LENGTH = 512
    const val RERANKER_MAX_LENGTH = 512
    const val BM25_K1 = 1.5f
    const val BM25_B = 0.75f
    const val BM25_EPSILON = 0.25f

    const val CHUNKS_ASSET = "knowledge_base/knowledge_base.json"
    const val EMBEDDINGS_ASSET = "knowledge_base/embeddings_chunks_e5_small.npy"
    const val E5_MODEL = "rag_e5_small.onnx"
    const val E5_TOKENIZER = "rag_e5_tokenizer.json"
    const val RERANKER_MODEL = "rag_mmarco_reranker.onnx"
    const val RERANKER_TOKENIZER = "rag_mmarco_tokenizer.json"
}
