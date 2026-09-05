package edu.unicauca.app.agrochat.rag

import android.content.Context
import edu.unicauca.app.agrochat.UniversalNativeTokenizer
import edu.unicauca.app.agrochat.models.LocalModelRegistry
import android.os.SystemClock
import android.util.Log

class RagPipeline private constructor(
    private val store: ChunkStore,
    private val preprocessor: SpanishTextPreprocessor,
    private val bm25: Bm25Retriever,
    private val semantic: SemanticRetriever,
    private val reranker: CrossEncoderReranker,
    private val sessions: List<OnnxTextSession>
) : AutoCloseable {
    @Synchronized
    fun query(userQuery: String, queryId: Long = 0L): RagResult {
        val totalStart = SystemClock.elapsedRealtimeNanos()
        var start = SystemClock.elapsedRealtimeNanos()
        val queryTokens = preprocessor.tokenize(userQuery)
        val lexical = bm25.search(queryTokens, RagConfig.LEXICAL_TOP_K)
        QueryTelemetry.record(queryId, "LEXICAL_BM25", QueryTelemetry.elapsedMs(start))
        Log.i("NotebookRAG", "LEXICAL_OK id=$queryId tokens=${queryTokens.joinToString(",")} corpus=${store.all().size} topK=${lexical.size} top=${describe(lexical)}")

        start = SystemClock.elapsedRealtimeNanos()
        val queryEmbedding = semantic.encodeQuery(userQuery)
        QueryTelemetry.record(queryId, "QUERY_EMBEDDING_E5", QueryTelemetry.elapsedMs(start))
        Log.i("NotebookRAG", "EMBEDDING_OK id=$queryId model=multilingual-e5-small prefix=query dimension=${queryEmbedding.size} norm=${vectorNorm(queryEmbedding)}")

        start = SystemClock.elapsedRealtimeNanos()
        val semanticResults = semantic.search(queryEmbedding, RagConfig.SEMANTIC_TOP_K)
        QueryTelemetry.record(queryId, "SEMANTIC_SEARCH", QueryTelemetry.elapsedMs(start))
        Log.i("NotebookRAG", "SEMANTIC_OK id=$queryId matrix=${store.all().size}x${RagConfig.EMBEDDING_DIMENSION} topK=${semanticResults.size} top=${describe(semanticResults)}")

        start = SystemClock.elapsedRealtimeNanos()
        val fused = RrfFusion.fuse(listOf(lexical, semanticResults), RagConfig.RRF_K)
        QueryTelemetry.record(queryId, "FUSION_RRF", QueryTelemetry.elapsedMs(start))
        val overlap = lexical.map { it.index }.toSet().intersect(semanticResults.map { it.index }.toSet()).size
        Log.i("NotebookRAG", "FUSION_OK id=$queryId algorithm=RRF k=${RagConfig.RRF_K} lexical=${lexical.size} semantic=${semanticResults.size} overlap=$overlap candidates=${fused.size} top=${describe(fused)}")
        val candidates = fused.map { store[it.index] }

        start = SystemClock.elapsedRealtimeNanos()
        val final = reranker.rerank(userQuery, candidates).take(RagConfig.FINAL_TOP_K)
        QueryTelemetry.record(queryId, "RERANKER", QueryTelemetry.elapsedMs(start))
        Log.i("NotebookRAG", "RERANKER_OK id=$queryId model=mmarco-mMiniLMv2-L12-H384-v1 candidates=${candidates.size} selected=${final.size} top=${final.joinToString { "idx=${it.chunk.index},doc=${it.chunk.documentId},score=${it.rerankScore}" }}")

        start = SystemClock.elapsedRealtimeNanos()
        val context = ContextBuilder.build(final)
        QueryTelemetry.record(queryId, "CONTEXT_BUILD", QueryTelemetry.elapsedMs(start))
        QueryTelemetry.record(queryId, "RAG_TOTAL", QueryTelemetry.elapsedMs(totalStart))
        Log.i("NotebookRAG", "RAG_OK id=$queryId contextChars=${context.length} chunks=${final.joinToString { it.chunk.index.toString() }}")
        return RagResult(lexical, semanticResults, fused, final, context)
    }

    private fun describe(results: List<ScoredChunk>): String = results.take(3).joinToString(";") {
        "idx=${it.index},doc=${store[it.index].documentId},score=${it.score}"
    }

    private fun vectorNorm(vector: FloatArray): String = String.format(
        java.util.Locale.US,
        "%.4f",
        kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() })
    )

    override fun close() = sessions.forEach { it.close() }

    companion object {
        fun missingAssets(context: Context): List<String> {
            val registry = LocalModelRegistry.getInstance()
            val regularAssets = listOf(
                RagConfig.CHUNKS_ASSET,
                "rag_spanish_stopwords.txt",
                RagConfig.EMBEDDINGS_ASSET
            )
            val downloadedArtifacts = listOf(
                RagConfig.E5_MODEL,
                RagConfig.E5_TOKENIZER,
                RagConfig.RERANKER_MODEL,
                RagConfig.RERANKER_TOKENIZER
            )
            return regularAssets.filterNot { assetExists(context, it) } +
                downloadedArtifacts.filterNot { registry.isModelAvailable(context, it) }
        }

        fun load(context: Context): RagPipeline {
            val missing = missingAssets(context)
            require(missing.isEmpty()) { "Faltan assets RAG: ${missing.joinToString()}" }
            val store = ChunkStore.fromAsset(context)
            val preprocessor = context.assets.open("rag_spanish_stopwords.txt").bufferedReader().useLines {
                SpanishTextPreprocessor.fromLines(it)
            }
            val tokenizedCorpus = store.all().map { preprocessor.tokenize(it.text) }
            val matrix = context.assets.open(RagConfig.EMBEDDINGS_ASSET).use(NpyFloatMatrix::read)
            require(matrix.rows == store.all().size) { "Chunk/embedding row mismatch: ${store.all().size}/${matrix.rows}" }
            require(matrix.columns == RagConfig.EMBEDDING_DIMENSION) { "Expected 384-dimensional E5 embeddings" }

            val e5Tokenizer = UniversalNativeTokenizer(context, RagConfig.E5_TOKENIZER)
            val rerankerTokenizer = UniversalNativeTokenizer(context, RagConfig.RERANKER_TOKENIZER)
            require(e5Tokenizer.isReady()) { "Missing E5 tokenizer ${RagConfig.E5_TOKENIZER}" }
            require(rerankerTokenizer.isReady()) { "Missing reranker tokenizer ${RagConfig.RERANKER_TOKENIZER}" }
            val e5 = OnnxTextSession(resolveModel(context, RagConfig.E5_MODEL), e5Tokenizer, RagConfig.EMBEDDING_MAX_LENGTH)
            val crossEncoder = OnnxTextSession(resolveModel(context, RagConfig.RERANKER_MODEL), rerankerTokenizer, RagConfig.RERANKER_MAX_LENGTH)
            return RagPipeline(
                store,
                preprocessor,
                Bm25Retriever(tokenizedCorpus),
                SemanticRetriever(matrix, e5::encodeSingle),
                CrossEncoderReranker(crossEncoder::scorePair),
                listOf(e5, crossEncoder)
            )
        }

        private fun resolveModel(context: Context, name: String): String {
            return requireNotNull(LocalModelRegistry.getInstance().getModelPath(context, name)) {
                "Modelo RAG no descargado: $name"
            }
        }

        private fun assetExists(context: Context, name: String): Boolean = try {
            context.assets.open(name).close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
