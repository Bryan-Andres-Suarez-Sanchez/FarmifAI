package edu.unicauca.app.agrochat.mindspore

import android.content.Context
import android.util.Log
import edu.unicauca.app.agrochat.AppLogger
import edu.unicauca.app.agrochat.MindSporeHelper
import edu.unicauca.app.agrochat.UniversalNativeTokenizer
import edu.unicauca.app.agrochat.models.ModelDownloadService
import org.apache.commons.text.similarity.FuzzyScore
import org.apache.commons.text.similarity.JaccardSimilarity
import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt

/**
 * SemanticSearchHelper - Búsqueda semántica para AgroChat
 * 
 * Este helper implementa un sistema de búsqueda semántica que:
 * 1. Carga embeddings pre-calculados de la base de conocimiento
 * 2. Usa MindSpore Lite para generar embeddings de preguntas del usuario
 * 3. Encuentra la respuesta más similar usando similitud coseno
 */
class SemanticSearchHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "SemanticSearchHelper"
        
        // Archivos de assets
        private const val EMBEDDINGS_FILE = "kb_embeddings.npy"
        private const val KNOWLEDGE_BASE_FILE = "agrochat_knowledge_base.json"
        private const val MODEL_FILE = "sentence_encoder.ms"
        private const val TOKENIZER_FILE = "sentence_tokenizer.json"
        
        // Configuración del modelo
        private const val EMBEDDING_DIM = 384  // MiniLM produce embeddings de 384 dims
        private const val MAX_SEQ_LENGTH = 128
        private const val NUM_THREADS = 2
    }
    
    // Datos cargados
    private var kbEmbeddings: Array<FloatArray>? = null
    private var kbQuestions: List<String>? = null
    private var kbEntryIds: List<Int>? = null
    private var kbEntries: Map<Int, KnowledgeEntry>? = null
    private var kbInformativeVocabulary: Set<String> = emptySet()
    
    // Modelo y tokenizador para búsqueda semántica real
    private var modelHandle: Long = 0L
    private var tokenizer: UniversalNativeTokenizer? = null
    private var useMindSporeEncoder = false  // Flag para usar MindSpore o fallback
    
    // Librería externa para similitud textual (fallback cuando embeddings no están disponibles)
    private val jaccardSimilarity = JaccardSimilarity()
    private val jaroWinklerSimilarity = JaroWinklerSimilarity()
    private val fuzzyScore = FuzzyScore(Locale("es", "ES"))
    private val discoursePrefixes = listOf(
        Regex("^y\\s+que\\s+me\\s+dices\\s+de\\s+"),
        Regex("^que\\s+me\\s+dices\\s+de\\s+"),
        Regex("^me\\s+puedes\\s+hablar\\s+de\\s+"),
        Regex("^hablame\\s+de\\s+"),
        Regex("^dime\\s+sobre\\s+"),
        Regex("^acerca\\s+de\\s+"),
        Regex("^sobre\\s+"),
        Regex("^amplia\\s+y\\s+continua\\s+la\\s+explicacion\\s+sobre\\s+"),
        Regex("^mas\\s+sobre\\s+")
    )
    private val stopWords = setOf(
        "de", "del", "la", "las", "el", "los", "y", "o", "a", "en", "con",
        "por", "para", "al", "un", "una", "unos", "unas", "que", "me", "te",
        "se", "mi", "tu", "su", "sobre", "acerca", "dices"
    )
    private val tokenCanonicalMap = mapOf(
        "fertilizante" to "fertilizacion",
        "fertilizantes" to "fertilizacion",
        "fertilizar" to "fertilizacion",
        "abono" to "fertilizacion",
        "abonos" to "fertilizacion",
        "abonar" to "fertilizacion",
        "nutriente" to "fertilizacion",
        "nutrientes" to "fertilizacion",
        "riego" to "riego",
        "regar" to "riego",
        "irrigacion" to "riego",
        "plagas" to "plaga",
        "insectos" to "plaga",
        "enfermedades" to "enfermedad",
        "hongos" to "hongo",
        "siembra" to "siembra",
        "sembrar" to "siembra",
        "plantar" to "siembra",
        "cosechar" to "cosecha",
        "cosechas" to "cosecha"
    )
    private val intentKeywords = mapOf(
        "fertilizacion" to setOf("fertilizacion", "npk", "urea", "nitrogeno", "fosforo", "potasio"),
        "riego" to setOf("riego", "goteo", "agua", "irrigacion"),
        "plaga" to setOf("plaga", "gusano", "mosca", "pulgon", "trips", "minador"),
        "enfermedad" to setOf("enfermedad", "hongo", "virus", "bacteria", "tizon", "roya", "pudricion", "mancha"),
        "siembra" to setOf("siembra", "cultivo", "cosecha", "semilla")
    )
    private val genericIntentTokens: Set<String> = run {
        val tokens = mutableSetOf<String>()
        intentKeywords.values.forEach { tokens.addAll(it) }
        tokens.addAll(
            setOf(
                "como", "cuando", "donde", "porque", "cual", "cuales", "informacion",
                "saber", "tema", "sobre", "acerca", "explicacion"
            )
        )
        tokens
    }
    
    // Estado
    private var isInitialized = false
    private var forceTextOnlyMode = false
    
    data class KnowledgeEntry(
        val id: Int,
        val category: String,
        val questions: List<String>,
        val answer: String
    )
    
    data class MatchResult(
        val answer: String,
        val matchedQuestion: String,
        val similarityScore: Float,
        val category: String,
        val entryId: Int
    )
    
    data class ContextResult(
        val contexts: List<MatchResult>,
        val combinedContext: String,
        val groundingAssessment: GroundingAssessment? = null
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

    fun setForceTextOnlyMode(enabled: Boolean) {
        forceTextOnlyMode = enabled
        AppLogger.log(TAG, "forceTextOnlyMode=$forceTextOnlyMode")
    }
    
    /**
     * Inicializa el sistema cargando embeddings y base de conocimiento
     */
    fun initialize(): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Ya inicializado")
            return true
        }
        
        try {
            Log.i(TAG, "Inicializando SemanticSearchHelper...")
            
            // 1. Cargar base de conocimiento
            loadKnowledgeBase()
            
            // 2. Cargar embeddings pre-calculados
            loadEmbeddings()
            
            // 3. Intentar cargar modelo MindSpore para encoding
            tryLoadMindSporeEncoder()
            
            isInitialized = true
            AppLogger.log(TAG, "SemanticSearch OK: KB=${kbEntries?.size}, Q=${kbQuestions?.size}, MindSpore=${useMindSporeEncoder}")
            Log.i(TAG, "SemanticSearchHelper inicializado correctamente")
            Log.i(TAG, "  - Entradas en KB: ${kbEntries?.size}")
            Log.i(TAG, "  - Preguntas indexadas: ${kbQuestions?.size}")
            Log.i(TAG, "  - Dimensión embeddings: $EMBEDDING_DIM")
            Log.i(TAG, "  - MindSpore encoder: ${if (useMindSporeEncoder) "activo" else "fallback a texto"}")

            verifyTokenizerAndEmbeddingsAlignment()
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando SemanticSearchHelper: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Intenta cargar el encoder MindSpore
     */
    private fun tryLoadMindSporeEncoder() {
        try {
            AppLogger.log(TAG, "Cargando encoder MindSpore...")
            
            // Verificar si existe el modelo en almacenamiento interno (descargado)
            val modelService = ModelDownloadService.getInstance()
            val modelPath = modelService.getModelPath(context, MODEL_FILE)
            if (modelPath == null) {
                AppLogger.log(TAG, "$MODEL_FILE not available in internal storage")
                return
            }
            AppLogger.log(TAG, "Model found: $modelPath")
            
            // Intentar cargar tokenizador (permanece en assets)
            AppLogger.log(TAG, "Cargando tokenizador...")
            tokenizer = UniversalNativeTokenizer(context, TOKENIZER_FILE)
            if (tokenizer?.isReady() != true) {
                AppLogger.log(TAG, "Tokenizer not ready")
                tokenizer = null
                return
            }
            AppLogger.log(TAG, "Tokenizer ready")
            
            // Cargar modelo MindSpore
            AppLogger.log(TAG, "Cargando modelo $MODEL_FILE...")
            modelHandle = MindSporeHelper.loadModelFromFilePath(modelPath, NUM_THREADS)
            if (modelHandle == 0L) {
                AppLogger.log(TAG, "MindSpore returned handle=0")
                return
            }
            
            useMindSporeEncoder = true
            AppLogger.log(TAG, "MindSpore encoder loaded (handle=$modelHandle)")
            
        } catch (e: Exception) {
            AppLogger.log(TAG, "MindSpore error: ${e.message}")
            useMindSporeEncoder = false
        }
    }
    
    /**
     * Carga la base de conocimiento desde JSON
     */
    private fun loadKnowledgeBase() {
        Log.d(TAG, "Cargando base de conocimiento...")
        
        val jsonString = context.assets.open(KNOWLEDGE_BASE_FILE).bufferedReader().use { it.readText() }
        val json = JSONObject(jsonString)
        val entries = json.getJSONArray("entries")
        
        val entriesMap = mutableMapOf<Int, KnowledgeEntry>()
        
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val id = entry.getInt("id")
            val category = entry.getString("category")
            val answer = entry.getString("answer")
            
            val questionsArray = entry.getJSONArray("questions")
            val questions = mutableListOf<String>()
            for (j in 0 until questionsArray.length()) {
                questions.add(questionsArray.getString(j))
            }
            
            entriesMap[id] = KnowledgeEntry(id, category, questions, answer)
        }
        
        kbEntries = entriesMap
        kbInformativeVocabulary = buildKbVocabulary(entriesMap)
        Log.d(TAG, "Base de conocimiento cargada: ${entriesMap.size} entradas")
    }
    
    /**
     * Carga los embeddings pre-calculados desde archivo .npy
     */
    private fun loadEmbeddings() {
        Log.d(TAG, "Cargando embeddings pre-calculados...")
        
        val inputStream = context.assets.open(EMBEDDINGS_FILE)
        val dataInputStream = DataInputStream(BufferedInputStream(inputStream))
        
        // Leer header de NumPy v1.0
        // Formato: \x93NUMPY (6 bytes) + version (2 bytes) + header_len (2 bytes) + header
        val magic = ByteArray(6)
        dataInputStream.readFully(magic)
        Log.d(TAG, "NPY magic: ${magic.map { it.toInt() and 0xFF }}")
        
        // Leer versión (2 bytes: major, minor)
        val versionMajor = dataInputStream.readByte().toInt() and 0xFF
        val versionMinor = dataInputStream.readByte().toInt() and 0xFF
        Log.d(TAG, "NPY version: $versionMajor.$versionMinor")
        
        // Leer tamaño del header (little-endian)
        // v1: 2 bytes, v2+: 4 bytes
        val headerLen: Int
        if (versionMajor == 1) {
            val byte1 = dataInputStream.readByte().toInt() and 0xFF
            val byte2 = dataInputStream.readByte().toInt() and 0xFF
            headerLen = byte1 or (byte2 shl 8)
        } else {
            // Version 2+: 4 bytes little-endian
            val bytes = ByteArray(4)
            dataInputStream.readFully(bytes)
            headerLen = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
        }
        Log.d(TAG, "NPY header length: $headerLen")
        
        // Leer y parsear el header
        val headerBytes = ByteArray(headerLen)
        dataInputStream.readFully(headerBytes)
        val headerStr = String(headerBytes)
        
        Log.d(TAG, "Header NPY: $headerStr")
        
        // Extraer shape del header
        val shapeMatch = Regex("""'shape':\s*\((\d+),\s*(\d+)\)""").find(headerStr)
        
        val numQuestions: Int
        val embeddingDim: Int
        
        if (shapeMatch != null) {
            numQuestions = shapeMatch.groupValues[1].toInt()
            embeddingDim = shapeMatch.groupValues[2].toInt()
        } else {
            Log.w(TAG, "No se pudo parsear shape del header, usando fallback")
            embeddingDim = EMBEDDING_DIM
            numQuestions = 119  // Fallback basado en nuestra KB
        }
        
        Log.d(TAG, "Embeddings shape: ($numQuestions, $embeddingDim)")
        
        // Leer los datos de embeddings
        val embeddings = Array(numQuestions) { FloatArray(embeddingDim) }
        val buffer = ByteArray(4)
        
        for (i in 0 until numQuestions) {
            for (j in 0 until embeddingDim) {
                dataInputStream.readFully(buffer)
                embeddings[i][j] = ByteBuffer.wrap(buffer)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .float
            }
            
            // Normalizar cada embedding (L2 norm = 1)
            var norm = 0f
            for (j in 0 until embeddingDim) {
                norm += embeddings[i][j] * embeddings[i][j]
            }
            norm = sqrt(norm)
            if (norm > 0) {
                for (j in 0 until embeddingDim) {
                    embeddings[i][j] /= norm
                }
            }
        }
        
        dataInputStream.close()
        
        Log.d(TAG, "Embeddings cargados y normalizados: ${embeddings.size} vectores de $embeddingDim dims")
        
        // Generar mapeo de preguntas desde la KB
        val questions = mutableListOf<String>()
        val entryIds = mutableListOf<Int>()
        
        kbEntries?.entries?.sortedBy { it.key }?.forEach { (id, entry) ->
            entry.questions.forEach { q ->
                questions.add(q)
                entryIds.add(id)
            }
        }

        if (questions.size != embeddings.size) {
            Log.w(
                TAG,
                "Desalineacion KB/embeddings: preguntas=${questions.size}, embeddings=${embeddings.size}. Se activa fallback textual para cobertura completa."
            )
            AppLogger.log(
                TAG,
                "KB/embeddings desalineados (q=${questions.size}, emb=${embeddings.size}). MindSpore encoder desactivado."
            )
            useMindSporeEncoder = false
        }
        
        kbEmbeddings = embeddings
        kbQuestions = questions
        kbEntryIds = entryIds
    }
    
    /**
     * Obtiene el tamaño de la base de conocimiento
     */
    fun getKnowledgeBaseSize(): Int {
        return kbEntries?.size ?: 0
    }
    
    /**
     * Busca la mejor coincidencia para la pregunta del usuario
     */
    fun findBestMatch(userQuery: String): MatchResult? {
        if (!isInitialized) {
            Log.e(TAG, "SemanticSearchHelper no inicializado")
            return null
        }
        
        val embeddings = kbEmbeddings ?: return null
        val questions = kbQuestions ?: return null
        val entryIds = kbEntryIds ?: return null
        val entries = kbEntries ?: return null
        
        val startTime = System.currentTimeMillis()
        val shouldUseEncoder = useMindSporeEncoder && !forceTextOnlyMode
        AppLogger.log(TAG, "findBestMatch: '$userQuery' useMindSpore=$shouldUseEncoder")
        
        // Calcular embedding de la query o usar fallback
        val queryEmbedding = if (shouldUseEncoder) {
            computeEmbedding(userQuery)
        } else {
            null
        }
        
        var bestScore = -1f
        var bestIdx = -1
        val queryLower = normalizeQueryForSearch(userQuery)
        
        if (queryEmbedding != null) {
            AppLogger.log(TAG, "Buscando con scoring híbrido (embedding + texto)")
            for (i in embeddings.indices) {
                val semanticScore = cosineSimilarity(queryEmbedding, embeddings[i])
                val textScore = calculateTextSimilarity(queryLower, questions[i].lowercase())
                val score = combineScores(semanticScore, textScore)
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }
        } else {
            AppLogger.log(TAG, "Fallback: búsqueda por texto")
            for (i in questions.indices) {
                val questionLower = questions[i].lowercase()
                val score = calculateTextSimilarity(queryLower, questionLower)
                
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }
        }
        
        val elapsedTime = System.currentTimeMillis() - startTime
        AppLogger.log(TAG, "findBestMatch: bestScore=$bestScore bestIdx=$bestIdx (${elapsedTime}ms)")
        
        if (bestIdx < 0) {
            return null
        }
        
        val matchedQuestion = questions[bestIdx]
        val entryId = entryIds[bestIdx]
        val entry = entries[entryId]!!
        
        return MatchResult(
            answer = entry.answer,
            matchedQuestion = matchedQuestion,
            similarityScore = bestScore,
            category = entry.category,
            entryId = entryId
        )
    }
    
    /**
     * Computa el embedding de un texto usando MindSpore
     */
    private fun computeEmbedding(text: String): FloatArray? {
        if (modelHandle == 0L || tokenizer == null) return null
        
        try {
            // Tokenizar
            val rawTokenIds = tokenizer!!.encode(text, addSpecialTokens = true)
            if (rawTokenIds.isEmpty()) {
                AppLogger.log(TAG, "computeEmbedding: tokenIds vacío")
                return null
            }
            
            // Filtrar tokens de padding del resultado del tokenizer
            val padTokenId = tokenizer!!.padTokenId
            val tokenIds = rawTokenIds.filter { it != padTokenId }.toIntArray()
            
            if (tokenIds.isEmpty()) {
                AppLogger.log(TAG, "computeEmbedding: Solo padding")
                return null
            }
            
            AppLogger.log(TAG, "computeEmbedding: ${tokenIds.size} tokens")
            
            // Preparar entrada con padding
            val inputIds = IntArray(MAX_SEQ_LENGTH) { padTokenId }
            val attentionMask = IntArray(MAX_SEQ_LENGTH) { 0 }
            
            val numToCopy = minOf(tokenIds.size, MAX_SEQ_LENGTH)
            for (i in 0 until numToCopy) {
                inputIds[i] = tokenIds[i]
                attentionMask[i] = 1
            }
            
            // Ejecutar modelo con ambas entradas (input_ids y attention_mask)
            val output = MindSporeHelper.predictSentenceEncoder(modelHandle, inputIds, attentionMask)
            
            if (output == null) {
                AppLogger.log(TAG, "computeEmbedding: MindSpore null")
                return null
            }
            
            AppLogger.log(TAG, "computeEmbedding: output size=${output.size}")
            
            // El output esperado es de 384 elementos (embedding pooled directo)
            val embedding: FloatArray
            
            when {
                output.size == EMBEDDING_DIM -> {
                    // Caso ideal: el output ya es el embedding de 384 dims
                    embedding = output.copyOf()
                    AppLogger.log(TAG, "computeEmbedding: direct 384d embedding")
                }
                output.size == MAX_SEQ_LENGTH * EMBEDDING_DIM -> {
                    // Recibimos last_hidden_state [128, 384] - hacer mean pooling
                    AppLogger.log(TAG, "computeEmbedding: mean pooling")
                    embedding = FloatArray(EMBEDDING_DIM)
                    var validTokens = 0
                    for (i in 0 until numToCopy) {
                        if (attentionMask[i] == 1) {
                            val offset = i * EMBEDDING_DIM
                            for (j in 0 until EMBEDDING_DIM) {
                                embedding[j] += output[offset + j]
                            }
                            validTokens++
                        }
                    }
                    if (validTokens > 0) {
                        for (j in 0 until EMBEDDING_DIM) {
                            embedding[j] /= validTokens
                        }
                    }
                }
                output.size > EMBEDDING_DIM -> {
                    // Tamaño inesperado - tomar los primeros EMBEDDING_DIM valores
                    Log.w(TAG, "computeEmbedding: Tamaño inesperado ${output.size}")
                    embedding = output.copyOfRange(0, EMBEDDING_DIM)
                }
                else -> {
                    Log.e(TAG, "computeEmbedding: Output demasiado pequeño: ${output.size}")
                    return null
                }
            }
            
            // Normalizar el embedding (L2 norm)
            var norm = 0f
            for (v in embedding) norm += v * v
            norm = sqrt(norm)
            if (norm > 0) {
                for (i in embedding.indices) embedding[i] /= norm
            }
            
            return embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "computeEmbedding: Error: ${e.message}", e)
            return null
        }
    }

    /**
     * Verificación rápida (debug) para detectar desalineación entre:
     * - `sentence_tokenizer.json` + `sentence_encoder.ms`
     * - `kb_embeddings.npy` (precalculado)
     *
     * Si el tokenizer no corresponde (o el orden de preguntas no coincide), la similitud
     * entre el embedding calculado de una pregunta de KB y su embedding precomputado
     * caerá notablemente.
     */
    private fun verifyTokenizerAndEmbeddingsAlignment() {
        if (!useMindSporeEncoder) return
        val embeddings = kbEmbeddings ?: return
        val questions = kbQuestions ?: return
        if (embeddings.isEmpty() || questions.isEmpty()) return

        val idx = 0
        val q = questions.getOrNull(idx) ?: return
        val precomputed = embeddings.getOrNull(idx) ?: return
        val computed = computeEmbedding(q) ?: run {
            Log.w(TAG, "⚠ Verificación tokenizer: no se pudo computar embedding")
            return
        }

        val sim = cosineSimilarity(computed, precomputed)
        Log.i(TAG, "Verificación tokenizer/KB: cosine(q0, emb0)=${String.format("%.4f", sim)}")
        if (sim < 0.90f) {
            Log.w(TAG, "⚠ Posible tokenizer incorrecto o KB desalineada (sim < 0.90).")
        }
        if (sim < 0.70f) {
            useMindSporeEncoder = false
            AppLogger.log(TAG, "Tokenizer/KB desalineados (sim=$sim). Se desactiva encoder y se usa fallback textual.")
        }
    }
    
    /**
     * Calcula similitud de texto mejorada usando múltiples métricas
     */
    private fun calculateTextSimilarity(text1: String, text2: String): Float {
        // Normalizar textos
        val norm1 = normalizeText(text1)
        val norm2 = normalizeText(text2)
        
        if (norm1.isBlank() || norm2.isBlank()) return 0f
        
        val words1 = norm1.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        val words2 = norm2.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        
        if (words1.isEmpty() || words2.isEmpty()) return 0f

        val informative1 = extractInformativeTokens(words1)
        val informative2 = extractInformativeTokens(words2)
        val queryEntities = informative1.filterNot { it in genericIntentTokens }.toSet()
        val candidateEntities = informative2.filterNot { it in genericIntentTokens }.toSet()
        
        // 1. Similitud Jaccard usando librería Apache Commons Text
        val jaccard = (jaccardSimilarity.apply(norm1, norm2)).toFloat().coerceIn(0f, 1f)
        
        // 2. Bonus por palabras clave coincidentes
        val keywordScore = calculateKeywordBonus(words1, words2)
        
        // 3. Bonus por sinónimos
        val synonymScore = calculateSynonymBonus(words1, words2)
        
        // 4. Similitud de forma de cadena usando librería (Jaro-Winkler + FuzzyScore)
        val jaroScore = (jaroWinklerSimilarity.apply(norm1, norm2)).toFloat().coerceIn(0f, 1f)
        val fuzzyRaw = fuzzyScore.fuzzyScore(norm1, norm2).toFloat()
        val maxLen = maxOf(norm1.length, norm2.length).coerceAtLeast(1)
        val fuzzyNormalized = (fuzzyRaw / maxLen).coerceIn(0f, 1f)
        
        // Mantener un bonus explícito cuando una frase contiene a la otra
        val substringBonus = if (norm1.contains(norm2) || norm2.contains(norm1)) 0.20f else 0f
        
        // 5. Bonus por bigramas compartidos
        val bigram1 = generateBigrams(norm1)
        val bigram2 = generateBigrams(norm2)
        val bigramScore = if (bigram1.isNotEmpty() && bigram2.isNotEmpty()) {
            bigram1.intersect(bigram2).size.toFloat() / bigram1.union(bigram2).size.toFloat()
        } else 0f
        
        val tokenCoverage = if (informative1.isNotEmpty()) {
            informative1.intersect(informative2).size.toFloat() / informative1.size.toFloat()
        } else 0f
        val entityCoverage = if (queryEntities.isNotEmpty()) {
            queryEntities.intersect(candidateEntities).size.toFloat() / queryEntities.size.toFloat()
        } else 1f
        val queryIntents = detectIntents(informative1)
        val candidateIntents = detectIntents(informative2)
        val missesIntent = queryIntents.isNotEmpty() && queryIntents.intersect(candidateIntents).isEmpty()
        val sharesIntent = queryIntents.isNotEmpty() && queryIntents.intersect(candidateIntents).isNotEmpty()
        
        // Combinar métricas con pesos
        var finalScore = (jaccard * 0.20f + 
                         keywordScore * 0.25f + 
                         synonymScore * 0.20f + 
                         bigramScore * 0.10f +
                         jaroScore * 0.15f +
                         fuzzyNormalized * 0.10f +
                         substringBonus +
                         tokenCoverage * 0.20f).coerceIn(0f, 1f)

        if (sharesIntent) {
            finalScore = (finalScore + 0.08f).coerceIn(0f, 1f)
        }
        if (missesIntent) {
            finalScore *= 0.70f
        }
        if (queryEntities.isNotEmpty()) {
            finalScore *= if (entityCoverage == 0f) 0.45f else (0.70f + entityCoverage * 0.30f)
        }
        if (informative1.size >= 3 && tokenCoverage < 0.34f) {
            finalScore *= 0.72f
        }
        
        return finalScore.coerceIn(0f, 1f)
    }
    
    /**
     * Normaliza el texto removiendo acentos y caracteres especiales
     */
    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeQueryForSearch(userQuery: String): String {
        val normalized = normalizeText(userQuery)
        var refined = normalized
        for (pattern in discoursePrefixes) {
            refined = refined.replace(pattern, "")
        }
        return if (refined.isNotBlank()) refined.trim() else normalized
    }

    private fun informativeTokensFromText(text: String): Set<String> {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return emptySet()
        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        return extractInformativeTokens(words)
    }

    private fun buildKbVocabulary(entries: Map<Int, KnowledgeEntry>): Set<String> {
        val vocab = mutableSetOf<String>()
        entries.values.forEach { entry ->
            entry.questions.forEach { question ->
                vocab.addAll(informativeTokensFromText(question))
            }
            vocab.addAll(informativeTokensFromText(entry.answer))
        }
        return vocab
    }
    
    /**
     * Genera bigramas de un texto
     */
    private fun generateBigrams(text: String): Set<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 2) return emptySet()
        return words.zipWithNext { a, b -> "$a $b" }.toSet()
    }

    private fun extractInformativeTokens(words: Set<String>): Set<String> {
        return words
            .asSequence()
            .map { tokenCanonicalMap[it] ?: it }
            .map {
                when {
                    it.length > 4 && it.endsWith("es") -> it.dropLast(2)
                    it.length > 3 && it.endsWith("s") -> it.dropLast(1)
                    else -> it
                }
            }
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
    }

    private fun detectIntents(tokens: Set<String>): Set<String> {
        return intentKeywords
            .filter { (_, keywords) -> tokens.any { it in keywords } }
            .keys
    }
    
    /**
     * Calcula bonus por palabras clave importantes
     */
    private fun calculateKeywordBonus(words1: Set<String>, words2: Set<String>): Float {
        val keywords = setOf(
            // Acciones
            "cultivar", "sembrar", "plantar", "regar", "fertilizar", "abonar", "cosechar",
            "podar", "fumigar", "controlar", "prevenir", "tratar", "diagnosticar",
            // Cultivos
            "tomate", "maiz", "papa", "frijol", "cafe", "cebolla", "lechuga", "zanahoria",
            "pepino", "pimenton", "aji", "yuca", "platano", "aguacate", "mango", "naranja",
            // Problemas
            "plaga", "enfermedad", "hongo", "virus", "bacteria", "gusano", "mosca", "arana",
            "pulgon", "trips", "minador", "marchitez", "pudricion", "mancha", "amarillo",
            "amarillamiento", "secas", "marchitas", "caidas", "manchadas",
            "tizon", "norteno",
            // Recursos
            "fertilizante", "abono", "npk", "organico", "riego", "goteo", "agua", "suelo",
            "tierra", "sustrato", "semilla", "nutriente", "nitrogeno", "fosforo", "potasio",
            // Partes de planta
            "hoja", "hojas", "raiz", "tallo", "flor", "fruto", "frutos",
            // Otros
            "mejor", "cuando", "como", "porque", "cantidad", "frecuencia"
        )
        
        val important1 = words1.filter { it in keywords }.toSet()
        val important2 = words2.filter { it in keywords }.toSet()
        
        if (important1.isEmpty() && important2.isEmpty()) return 0.5f
        if (important1.isEmpty() || important2.isEmpty()) return 0f
        
        return important1.intersect(important2).size.toFloat() / 
               important1.union(important2).size.toFloat()
    }
    
    /**
     * Calcula bonus por sinónimos agrícolas
     */
    private fun calculateSynonymBonus(words1: Set<String>, words2: Set<String>): Float {
        // Diccionario de sinónimos agrícolas
        val synonymGroups = listOf(
            setOf("amarillo", "amarillamiento", "amarillas", "clorosis"),
            setOf("marchito", "marchitas", "marchitez", "secas", "mustias"),
            setOf("plaga", "plagas", "insecto", "insectos", "bicho", "bichos"),
            setOf("enfermedad", "enfermedades", "padecimiento", "mal"),
            setOf("fertilizante", "abono", "fertilizacion", "abonar", "nutriente"),
            setOf("riego", "regar", "agua", "irrigacion", "mojado"),
            setOf("sembrar", "siembra", "plantar", "plantacion", "cultivar"),
            setOf("cosecha", "cosechar", "recolectar", "recoleccion"),
            setOf("hoja", "hojas", "follaje", "foliar"),
            setOf("raiz", "raices", "radicular"),
            setOf("fruto", "frutos", "frutas", "produccion"),
            setOf("hongo", "hongos", "fungico", "fungica"),
            setOf("tizon", "blight", "norteno", "northern"),
            setOf("control", "controlar", "combatir", "eliminar", "tratamiento", "tratar"),
            setOf("tomate", "tomates", "jitomate"),
            setOf("maiz", "elote", "choclo"),
            setOf("papa", "patata", "papas", "patatas"),
            setOf("cafe", "cafeto", "cafetal"),
            setOf("mejor", "optimo", "recomendado", "ideal"),
            setOf("cuando", "epoca", "momento", "tiempo"),
            setOf("como", "manera", "forma", "metodo")
        )
        
        var matchCount = 0
        for (group in synonymGroups) {
            val has1 = words1.any { it in group }
            val has2 = words2.any { it in group }
            if (has1 && has2) matchCount++
        }
        
        return if (matchCount > 0) (matchCount.toFloat() / 5f).coerceIn(0f, 1f) else 0f
    }
    
    /**
     * Calcula similitud coseno entre dos vectores
     */
    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        
        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0) dotProduct / denominator else 0f
    }

    /**
     * Mezcla score semántico y textual para estabilizar retrieval cuando embeddings
     * no están perfectamente alineados.
     */
    private fun combineScores(semanticScore: Float, textScore: Float): Float {
        return (semanticScore * 0.65f + textScore * 0.35f).coerceIn(0f, 1f)
    }
    
    /**
     * Busca los K mejores contextos para RAG (Retrieval Augmented Generation)
     * Esto permite que el LLM tenga múltiples fuentes de información
     * 
     * @param userQuery La pregunta del usuario
     * @param topK Número de contextos a recuperar (default 3)
     * @param minScore Score mínimo para incluir un resultado (default 0.4)
     * @return ContextResult con los contextos encontrados
     */
    fun findTopKContexts(userQuery: String, topK: Int = 3, minScore: Float = 0.4f): ContextResult {
        if (!isInitialized) {
            Log.e(TAG, "SemanticSearchHelper no inicializado")
            return ContextResult(emptyList(), "", null)
        }
        
        val embeddings = kbEmbeddings ?: return ContextResult(emptyList(), "", null)
        val questions = kbQuestions ?: return ContextResult(emptyList(), "", null)
        val entryIds = kbEntryIds ?: return ContextResult(emptyList(), "", null)
        val entries = kbEntries ?: return ContextResult(emptyList(), "", null)
        
        val startTime = System.currentTimeMillis()
        
        // Calcular embedding de la query o usar fallback
        val shouldUseEncoder = useMindSporeEncoder && !forceTextOnlyMode
        val queryEmbedding = if (shouldUseEncoder) {
            computeEmbedding(userQuery)
        } else {
            null
        }
        
        // Lista de (índice, score) para ordenar
        val scores = mutableListOf<Pair<Int, Float>>()
        
        // Variable para tracking del mejor score semántico
        var bestSemanticScore = -1f
        var bestSemanticIdx = -1
        val queryLower = normalizeQueryForSearch(userQuery)
        
        if (queryEmbedding != null) {
            // Búsqueda semántica + textual (híbrida)
            for (i in embeddings.indices) {
                val semanticScore = cosineSimilarity(queryEmbedding, embeddings[i])
                val textScore = calculateTextSimilarity(queryLower, questions[i].lowercase())
                val score = combineScores(semanticScore, textScore)
                if (semanticScore > bestSemanticScore) {
                    bestSemanticScore = semanticScore
                    bestSemanticIdx = i
                }
                if (score >= minScore) {
                    scores.add(i to score)
                }
            }
            Log.d(TAG, "Búsqueda semántica: mejor score=$bestSemanticScore, pregunta='${if (bestSemanticIdx >= 0) questions[bestSemanticIdx] else "N/A"}'")
            
            // Si no hay matches por umbral, fallback textual con umbral suave
            if (scores.isEmpty()) {
                Log.d(TAG, "Sin matches híbridos (minScore=$minScore), usando fallback de texto")
                var bestTextScore = 0f
                var bestTextQuestion = ""
                for (i in questions.indices) {
                    val questionLower = questions[i].lowercase()
                    val textScore = calculateTextSimilarity(queryLower, questionLower)
                    if (textScore > bestTextScore) {
                        bestTextScore = textScore
                        bestTextQuestion = questions[i]
                    }
                    if (textScore >= 0.2f) {
                        scores.add(i to textScore)
                    }
                }
                Log.d(TAG, "Fallback texto: mejor score=$bestTextScore, pregunta='$bestTextQuestion', total matches=${scores.size}")
            }
        } else {
            // Fallback: similitud de texto
            for (i in questions.indices) {
                val questionLower = questions[i].lowercase()
                val score = calculateTextSimilarity(queryLower, questionLower)
                if (score >= minScore) {
                    scores.add(i to score)
                }
            }
        }

        // Garantizar al menos top-k por similitud textual cuando no hay ninguno por umbral.
        if (scores.isEmpty()) {
            Log.d(TAG, "Sin resultados >= umbral, recuperando top-$topK textual forzado")
            val fallbackAll = questions.indices
                .map { idx -> idx to calculateTextSimilarity(queryLower, questions[idx].lowercase()) }
                .sortedByDescending { it.second }
                .take(topK.coerceAtLeast(1))
            scores.addAll(fallbackAll)
        }
        
        // Ordenar por score descendente y tomar top K
        val topResults = scores.sortedByDescending { it.second }.take(topK)
        
        // Evitar duplicados de la misma entrada (diferentes preguntas pueden apuntar a la misma respuesta)
        val seenEntryIds = mutableSetOf<Int>()
        val uniqueResults = mutableListOf<MatchResult>()
        
        for ((idx, score) in topResults) {
            val entryId = entryIds[idx]
            if (entryId !in seenEntryIds) {
                seenEntryIds.add(entryId)
                val entry = entries[entryId]!!
                uniqueResults.add(
                    MatchResult(
                        answer = entry.answer,
                        matchedQuestion = questions[idx],
                        similarityScore = score,
                        category = entry.category,
                        entryId = entryId
                    )
                )
            }
        }
        
        val elapsedTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "findTopKContexts: ${uniqueResults.size} contextos en ${elapsedTime}ms")
        
        // Construir contexto combinado para el LLM
        val combinedContext = buildCombinedContext(uniqueResults)
        val groundingAssessment = buildGroundingAssessment(userQuery, uniqueResults.firstOrNull())

        if (groundingAssessment != null) {
            AppLogger.log(
                TAG,
                "Grounding: support=${String.format("%.2f", groundingAssessment.supportScore)} " +
                    "coverage=${String.format("%.2f", groundingAssessment.lexicalCoverage)} " +
                    "entity=${String.format("%.2f", groundingAssessment.entityCoverage)} " +
                    "unknown=${String.format("%.2f", groundingAssessment.unknownTokenRatio)}"
            )
        }
        
        return ContextResult(uniqueResults, combinedContext, groundingAssessment)
    }
    
    /**
     * Construye un contexto combinado formateado para el LLM
     * NOTA: Se evita incluir las preguntas del KB para que el LLM no las repita
     */
    private fun buildCombinedContext(results: List<MatchResult>): String {
        if (results.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("Informacion agricola relevante:\n")
        results.forEachIndexed { index, result ->
            sb.append("\n[${index + 1}] ${result.category.uppercase()}\n")
            sb.append(result.answer.trim())
            if (index < results.size - 1) {
                sb.append("\n---\n")
            }
        }
        return sb.toString().trim()
    }

    private fun buildGroundingAssessment(
        userQuery: String,
        topMatch: MatchResult?
    ): GroundingAssessment? {
        val queryTokens = informativeTokensFromText(normalizeQueryForSearch(userQuery))
        if (queryTokens.isEmpty()) return null

        if (topMatch == null) {
            return GroundingAssessment(
                supportScore = 0f,
                lexicalCoverage = 0f,
                entityCoverage = 0f,
                unknownTokenRatio = 1f,
                queryTokens = queryTokens,
                missingEntityTokens = queryTokens,
                unknownQueryTokens = queryTokens,
                hasStrongSupport = false
            )
        }

        val evidenceTokens = informativeTokensFromText("${topMatch.matchedQuestion} ${topMatch.answer}")
        val overlap = queryTokens.intersect(evidenceTokens)
        val lexicalCoverage = overlap.size.toFloat() / queryTokens.size.toFloat()
        val queryEntities = queryTokens.filterNot { it in genericIntentTokens }.toSet()
        val evidenceEntities = evidenceTokens.filterNot { it in genericIntentTokens }.toSet()
        val matchedEntities = queryEntities.intersect(evidenceEntities)
        val entityCoverage = if (queryEntities.isNotEmpty()) {
            matchedEntities.size.toFloat() / queryEntities.size.toFloat()
        } else {
            lexicalCoverage
        }

        val unknownQueryTokens = if (kbInformativeVocabulary.isNotEmpty()) {
            queryTokens.filter { it !in kbInformativeVocabulary }.toSet()
        } else {
            emptySet()
        }
        val unknownTokenRatio = if (queryTokens.isNotEmpty()) {
            unknownQueryTokens.size.toFloat() / queryTokens.size.toFloat()
        } else {
            0f
        }

        val supportScore = (
            topMatch.similarityScore * 0.55f +
                lexicalCoverage * 0.30f +
                entityCoverage * 0.20f -
                unknownTokenRatio * 0.35f
            ).coerceIn(0f, 1f)

        val hasStrongSupport =
            supportScore >= 0.55f &&
                lexicalCoverage >= 0.34f &&
                (queryEntities.isEmpty() || entityCoverage >= 0.45f) &&
                unknownTokenRatio <= 0.45f

        return GroundingAssessment(
            supportScore = supportScore,
            lexicalCoverage = lexicalCoverage,
            entityCoverage = entityCoverage,
            unknownTokenRatio = unknownTokenRatio,
            queryTokens = queryTokens,
            missingEntityTokens = queryEntities - matchedEntities,
            unknownQueryTokens = unknownQueryTokens,
            hasStrongSupport = hasStrongSupport
        )
    }
    
    /**
     * Libera recursos
     */
    fun release() {
        if (modelHandle != 0L) {
            MindSporeHelper.unloadModel(modelHandle)
            modelHandle = 0L
        }
        tokenizer?.release()
        tokenizer = null
        
        kbEmbeddings = null
        kbQuestions = null
        kbEntryIds = null
        kbEntries = null
        kbInformativeVocabulary = emptySet()
        isInitialized = false
        useMindSporeEncoder = false
        
        Log.d(TAG, "Recursos liberados")
    }
}
