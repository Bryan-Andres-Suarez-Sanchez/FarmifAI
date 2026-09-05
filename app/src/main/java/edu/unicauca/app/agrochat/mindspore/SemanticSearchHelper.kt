package edu.unicauca.app.agrochat.mindspore

import android.content.Context
import android.util.Log
import edu.unicauca.app.agrochat.rag.RagPipeline
import edu.unicauca.app.agrochat.rag.QueryTelemetry
import android.os.SystemClock
import kotlin.math.exp

/** Compatibility facade for UI code; ranking is exclusively performed by [RagPipeline]. */
class SemanticSearchHelper(private val context: Context) {
    data class MatchResult(
        val answer: String,
        val matchedQuestion: String,
        val similarityScore: Float,
        val category: String,
        val entryId: Int,
        val citation: String,
        val date: String
    )

    data class GroundingAssessment(
        val supportScore: Float,
        val lexicalCoverage: Float,
        val entityCoverage: Float,
        val unknownTokenRatio: Float,
        val queryTokens: Set<String>,
        val missingEntityTokens: Set<String>,
        val unknownQueryTokens: Set<String>,
        val hasStrongSupport: Boolean
    )

    data class ContextResult(
        val contexts: List<MatchResult>,
        val combinedContext: String,
        val groundingAssessment: GroundingAssessment? = null
    )

    private var pipeline: RagPipeline? = null
    private var unavailableReason: String = "not_initialized"

    @Suppress("UNUSED_PARAMETER")
    fun setForceTextOnlyMode(enabled: Boolean) = Unit

    fun initialize(): Boolean {
        return try {
            val missing = RagPipeline.missingAssets(context)
            if (missing.isNotEmpty()) {
                unavailableReason = "missing:${missing.joinToString(",")}"
                Log.e("NotebookRAG", "RAG_UNAVAILABLE reason=$unavailableReason")
                false
            } else {
                val started = SystemClock.elapsedRealtimeNanos()
                pipeline = RagPipeline.load(context)
                unavailableReason = ""
                Log.i("NotebookRAG", "RAG_READY chunks=8767 embeddingDim=384 initMs=${QueryTelemetry.elapsedMs(started)}")
                true
            }
        } catch (error: Throwable) {
            unavailableReason = "initialization:${error.javaClass.simpleName}:${error.message}"
            Log.e("SemanticSearchHelper", "Notebook RAG initialization failed: ${error.message}", error)
            Log.e("NotebookRAG", "RAG_UNAVAILABLE reason=$unavailableReason")
            false
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun findTopKContexts(userQuery: String, topK: Int = 3, minScore: Float = 0f, queryId: Long = 0L): ContextResult {
        val activePipeline = pipeline
        if (activePipeline == null) {
            listOf("LEXICAL_BM25", "QUERY_EMBEDDING_E5", "SEMANTIC_SEARCH", "FUSION_RRF", "RERANKER", "RAG_TOTAL")
                .forEach { QueryTelemetry.skipped(queryId, it, unavailableReason) }
            return ContextResult(emptyList(), "")
        }
        val started = SystemClock.elapsedRealtimeNanos()
        val result = try {
            activePipeline.query(userQuery, queryId)
        } catch (error: Throwable) {
            QueryTelemetry.failed(queryId, "RAG_TOTAL", QueryTelemetry.elapsedMs(started), error)
            Log.e("NotebookRAG", "RAG_QUERY_FAIL id=$queryId query=${userQuery.take(100)}", error)
            return ContextResult(emptyList(), "")
        }
        Log.d("NotebookRAG", "BM25=${result.bm25.joinToString { "${it.index}:${it.score}" }}")
        Log.d("NotebookRAG", "SEM=${result.semantic.joinToString { "${it.index}:${it.score}" }}")
        Log.d("NotebookRAG", "RRF=${result.fused.joinToString { "${it.index}:${it.score}" }}")
        Log.d("NotebookRAG", "RERANK=${result.reranked.joinToString { "${it.chunk.index}:${it.rerankScore}" }}")
        val matches = result.reranked.map {
            MatchResult(
                answer = it.chunk.text,
                matchedQuestion = it.chunk.text,
                similarityScore = sigmoid(it.rerankScore),
                category = it.chunk.documentId,
                entryId = it.chunk.index,
                citation = it.chunk.citation,
                date = it.chunk.date
            )
        }
        val queryTokens = lexicalTokens(userQuery)
        val contextTokens = lexicalTokens(result.context)
        val coverage = if (queryTokens.isEmpty()) 0f else queryTokens.count { it in contextTokens }.toFloat() / queryTokens.size
        return ContextResult(
            contexts = matches,
            combinedContext = result.context,
            groundingAssessment = GroundingAssessment(
                supportScore = matches.firstOrNull()?.similarityScore ?: 0f,
                lexicalCoverage = coverage,
                entityCoverage = coverage,
                unknownTokenRatio = 0f,
                queryTokens = queryTokens,
                missingEntityTokens = queryTokens - contextTokens,
                unknownQueryTokens = emptySet(),
                hasStrongSupport = matches.isNotEmpty()
            )
        )
    }

    fun scoreResponseGrounding(responseText: String, userQuery: String, contextText: String): Float {
        val response = lexicalTokens(responseText)
        if (response.isEmpty()) return 0f
        val allowed = lexicalTokens(userQuery) + lexicalTokens(contextText)
        return response.count { it in allowed }.toFloat() / response.size
    }

    fun release() {
        pipeline?.close()
        pipeline = null
    }

    private fun sigmoid(value: Float): Float = (1.0 / (1.0 + exp(-value.toDouble()))).toFloat()
    private fun lexicalTokens(text: String): Set<String> = text.lowercase()
        .split(Regex("[^a-záéíóúüñ0-9]+"))
        .filter { it.length > 1 }
        .toSet()
}
