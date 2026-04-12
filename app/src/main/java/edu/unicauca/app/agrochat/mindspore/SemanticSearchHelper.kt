package edu.unicauca.app.agrochat.mindspore

import android.content.Context
import android.util.Log
import edu.unicauca.app.agrochat.AppLogger
import edu.unicauca.app.agrochat.MindSporeHelper
import edu.unicauca.app.agrochat.UniversalNativeTokenizer
import edu.unicauca.app.agrochat.models.ModelDownloadService
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
        private const val EMBEDDINGS_MAPPING_FILE = "kb_embeddings_mapping.json"
        private const val KNOWLEDGE_BASE_FILE = "agrochat_knowledge_base.json"
        private const val KNOWLEDGE_RECORDS_DIR = "kb_nueva/extract"
        private const val MODEL_FILE = "sentence_encoder.ms"
        private const val TOKENIZER_FILE = "sentence_tokenizer.json"
        
        // Configuración del modelo
        private const val EMBEDDING_DIM = 384  // MiniLM produce embeddings de 384 dims
        private const val MAX_SEQ_LENGTH = 128
        private const val NUM_THREADS = 2
        private const val ANN_MIN_CLUSTERS = 12
        private const val ANN_MAX_CLUSTERS = 64
        private const val ANN_KMEANS_ITERS = 6
        private const val ANN_PROBE_CLUSTERS = 5
        private const val ANN_MAX_CANDIDATES = 900
    }
    
    // Datos cargados
    private var kbEmbeddings: Array<FloatArray>? = null
    private var kbQuestions: List<String>? = null
    private var kbEntryIds: List<Int>? = null
    private var kbEntries: Map<Int, KnowledgeEntry>? = null
    private var kbInformativeVocabulary: Set<String> = emptySet()
    private var embeddingIndexAligned: Boolean = false
    private var entrySemanticEmbeddings: Map<Int, FloatArray> = emptyMap()
    private var annCentroids: Array<FloatArray> = emptyArray()
    private var annPostingLists: List<IntArray> = emptyList()
    
    // Modelo y tokenizador para búsqueda semántica real
    private var modelHandle: Long = 0L
    private var tokenizer: UniversalNativeTokenizer? = null
    private var useMindSporeEncoder = false
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
        "cafe" to "cafe",
        "cafeto" to "cafe",
        "cafetal" to "cafe",
        "cafetera" to "cafe",
        "cafetero" to "cafe",
        "caficultura" to "cafe",
        "siembra" to "siembra",
        "sembrar" to "siembra",
        "plantar" to "siembra",
        "vivero" to "siembra",
        "almacigo" to "siembra",
        "chapola" to "siembra",
        "colino" to "siembra",
        "plateo" to "siembra",
        "prendimiento" to "siembra",
        "densidad" to "siembra",
        "cosechar" to "cosecha",
        "cosechas" to "cosecha",
        "maduro" to "cosecha",
        "maduracion" to "cosecha",
        "cereza" to "cosecha",
        "beneficio" to "poscosecha",
        "despulpado" to "poscosecha",
        "fermentado" to "poscosecha",
        "fermentacion" to "poscosecha",
        "anaerobia" to "poscosecha",
        "anaerobica" to "poscosecha",
        "honey" to "poscosecha",
        "natural" to "poscosecha",
        "lavado" to "poscosecha",
        "secado" to "poscosecha",
        "catacion" to "calidad",
        "taza" to "calidad",
        "sensorial" to "calidad",
        "defecto" to "calidad",
        "quaker" to "calidad",
        "pasilla" to "calidad",
        "pergamino" to "poscosecha",
        "humedad" to "poscosecha",
        "brix" to "calidad",
        "trazabilidad" to "calidad",
        "poda" to "poda",
        "podas" to "poda",
        "podar" to "poda",
        "recepa" to "renovacion",
        "zoca" to "renovacion",
        "zoqueo" to "renovacion",
        "renovar" to "renovacion",
        "renovacion" to "renovacion",
        "variedades" to "variedad",
        "geisha" to "variedad",
        "caturra" to "variedad",
        "castillo" to "variedad",
        "catimor" to "variedad",
        "bourbon" to "variedad",
        "tabi" to "variedad",
        "fotosintesis" to "fisiologia",
        "transpiracion" to "fisiologia",
        "respiracion" to "fisiologia",
        "estomas" to "fisiologia",
        "estoma" to "fisiologia",
        "evapotranspiracion" to "clima",
        "vpd" to "clima",
        "broca" to "broca",
        "roya" to "roya",
        "fragancia" to "calidad",
        "retrogusto" to "calidad",
        "inocuidad" to "calidad",
        "bpa" to "gestion",
        "bpm" to "gestion",
        "auditoria" to "gestion",
        "costos" to "gestion",
        "margen" to "gestion",
        "rentabilidad" to "gestion",
        "flujo" to "gestion",
        "roi" to "gestion",
        "inoculo" to "enfermedad",
        "umbral" to "plaga"
    )
    private val genericIntentTokens = setOf(
        "como", "cuando", "donde", "porque", "cual", "cuales", "informacion",
        "saber", "tema", "sobre", "acerca", "explicacion", "consulta", "ayuda",
        "orientacion", "recomendacion", "recomendaciones", "metodo", "forma"
    )
    // Estado
    private var isInitialized = false
    private var forceTextOnlyMode = false
    
    data class KnowledgeEntry(
        val id: Int,
        val category: String,
        val questions: List<String>,
        val answer: String,
        val entityTokens: Set<String> = emptySet()
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

    private data class RankedSemanticCandidate(
        val index: Int,
        val score: Float,
        val baseScore: Float,
        val expandedScore: Float,
        val entryScore: Float
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
            Log.i(TAG, "  - MindSpore encoder: ${if (useMindSporeEncoder) "activo" else "desactivado"}")

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
            
            if (!embeddingIndexAligned) {
                useMindSporeEncoder = false
                AppLogger.log(
                    TAG,
                    "MindSpore cargado (handle=$modelHandle) pero embeddings desalineados; se desactiva retrieval semantico."
                )
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
     * Carga la base de conocimiento.
     *
     * Prioridad:
     * 1) Registros crudos en assets/kb_nueva/extract (archivos .jsonl)
     * 2) Formato legacy agrochat_knowledge_base.json
     */
    private fun loadKnowledgeBase() {
        Log.d(TAG, "Cargando base de conocimiento...")

        val loadedFromRecords = loadKnowledgeBaseFromRecordsJsonl()
        if (loadedFromRecords) return

        val loadedLegacy = loadKnowledgeBaseFromLegacyJson()
        if (loadedLegacy) return

        throw IllegalStateException("No se pudo cargar KB desde records jsonl ni desde formato legacy")
    }

    private fun loadKnowledgeBaseFromLegacyJson(): Boolean {
        return try {
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
            Log.d(TAG, "Base de conocimiento legacy cargada: ${entriesMap.size} entradas")
            true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo cargar KB legacy: ${e.message}")
            false
        }
    }

    private fun loadKnowledgeBaseFromRecordsJsonl(): Boolean {
        val fileNames = context.assets.list(KNOWLEDGE_RECORDS_DIR)
            ?.filter { it.endsWith(".jsonl") }
            ?.sorted()
            ?: emptyList()

        if (fileNames.isEmpty()) {
            Log.i(TAG, "No se encontraron records jsonl en $KNOWLEDGE_RECORDS_DIR")
            return false
        }

        val entriesMap = mutableMapOf<Int, KnowledgeEntry>()
        var nextId = 1

        for (fileName in fileNames) {
            val path = "$KNOWLEDGE_RECORDS_DIR/$fileName"
            context.assets.open(path).bufferedReader().useLines { lines ->
                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isBlank()) return@forEach

                    try {
                        val record = JSONObject(line)
                        val questions = buildQuestionsFromRecord(record)
                        if (questions.isEmpty()) return@forEach

                        val category = buildRecordCategory(record)
                        val answer = buildAnswerFromRecord(record)
                        val entityTokens = extractEntityTokens(record)
                        entriesMap[nextId] = KnowledgeEntry(
                            id = nextId,
                            category = category,
                            questions = questions,
                            answer = answer,
                            entityTokens = entityTokens
                        )
                        nextId++
                    } catch (parseError: Exception) {
                        Log.w(TAG, "Linea invalida en $path: ${parseError.message}")
                    }
                }
            }
        }

        if (entriesMap.isEmpty()) {
            Log.w(TAG, "No se pudieron construir entradas desde records jsonl")
            return false
        }

        kbEntries = entriesMap
        kbInformativeVocabulary = buildKbVocabulary(entriesMap)
        Log.d(TAG, "KB records cargada: ${entriesMap.size} entradas desde ${fileNames.size} archivos")
        return true
    }

    private fun buildRecordCategory(record: JSONObject): String {
        val chapterId = record.optString("chapter_id", "").trim().ifBlank { "00" }
        val topic = record.optString("topic", "").trim().ifBlank { "general" }
        val chapterToken = chapterId.padStart(2, '0')
        val topicToken = slugifyToken(topic).ifBlank { "general" }
        return "cap${chapterToken}_$topicToken"
    }

    private fun extractEntityTokens(record: JSONObject): Set<String> {
        val tokens = mutableSetOf<String>()
        val entitiesArr = record.optJSONArray("entities")
        if (entitiesArr != null) {
            for (i in 0 until entitiesArr.length()) {
                val entity = entitiesArr.optString(i, "").trim()
                if (entity.isNotBlank()) {
                    val normalized = normalizeText(entity)
                    normalized.split(Regex("\\s+")).filter { it.length >= 3 && it !in stopWords }.forEach { word ->
                        tokens.add(tokenCanonicalMap[word] ?: word)
                    }
                }
            }
        }
        // Also include topic and subtopic as entity tokens
        val topic = record.optString("topic", "").trim()
        if (topic.isNotBlank()) {
            val normalizedTopic = normalizeText(topic)
            normalizedTopic.split(Regex("[\\s_]+")).filter { it.length >= 3 && it !in stopWords }.forEach { word ->
                tokens.add(tokenCanonicalMap[word] ?: word)
            }
        }
        return tokens
    }

    private fun slugifyToken(value: String): String {
        return normalizeText(value)
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .take(48)
    }

    private fun buildQuestionsFromRecord(record: JSONObject): List<String> {
        val raw = mutableListOf<String>()
        raw.addAll(jsonArrayToStrings(record.optJSONArray("retrieval_hints")))
        raw.addAll(jsonArrayToStrings(record.optJSONArray("aliases")))
        val title = record.optString("title", "").trim()
        if (title.isNotBlank()) raw.add(title)

        val deduped = dedupeQuestions(raw)
        if (deduped.isNotEmpty()) return deduped

        val fallback = mutableListOf<String>()
        if (title.isNotBlank()) fallback.add(title)
        val statement = record.optString("statement", "").trim()
        if (statement.isNotBlank()) fallback.add(statement.take(180))
        return dedupeQuestions(fallback)
    }

    private fun jsonArrayToStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val values = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, "").trim()
            if (value.isNotBlank()) values.add(value)
        }
        return values
    }

    private fun dedupeQuestions(raw: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<String>()
        raw.forEach { value ->
            val cleaned = value.trim().replace(Regex("\\s+"), " ")
            if (cleaned.isBlank()) return@forEach
            val key = normalizeText(cleaned)
            if (key.isBlank() || key in seen) return@forEach
            seen.add(key)
            out.add(cleaned)
        }
        return out
    }

    private fun buildAnswerFromRecord(record: JSONObject): String {
        val lines = mutableListOf<String>()

        val title = record.optString("title", "").trim()
        val statement = record.optString("statement", "").trim()
        if (title.isNotBlank()) lines.add(title)
        if (statement.isNotBlank()) lines.add(statement)

        val condition = record.optString("condition", "").trim()
        if (condition.isNotBlank()) lines.add("Condicion: $condition")

        val action = record.optString("action", "").trim()
        if (action.isNotBlank()) lines.add("Accion: $action")

        val expectedEffect = record.optString("expected_effect", "").trim()
        if (expectedEffect.isNotBlank()) lines.add("Efecto esperado: $expectedEffect")

        val riskIfIgnored = record.optString("risk_if_ignored", "").trim()
        if (riskIfIgnored.isNotBlank()) lines.add("Riesgo si se ignora: $riskIfIgnored")

        val applicability = record.optString("applicability", "").trim()
        if (applicability.isNotBlank()) lines.add("Aplicabilidad: $applicability")

        val quantData = record.optJSONArray("quant_data")
        if (quantData != null && quantData.length() > 0) {
            lines.add("Datos cuantitativos:")
            for (i in 0 until quantData.length()) {
                val item = quantData.optJSONObject(i) ?: continue
                val metric = item.optString("metric", "").trim().ifBlank { "dato" }
                val valueExact = item.opt("value_exact")
                val valueMin = item.opt("value_min")
                val valueMax = item.opt("value_max")
                val unit = item.optString("unit", "").trim()
                val qualifier = item.optString("qualifier", "").trim()
                val valueText = when {
                    valueExact != null && valueExact != JSONObject.NULL -> valueExact.toString()
                    valueMin != null && valueMin != JSONObject.NULL && valueMax != null && valueMax != JSONObject.NULL ->
                        "${valueMin}-${valueMax}"
                    valueMin != null && valueMin != JSONObject.NULL -> ">= $valueMin"
                    valueMax != null && valueMax != JSONObject.NULL -> "<= $valueMax"
                    else -> "sin valor"
                }
                val unitPart = if (unit.isNotBlank()) " $unit" else ""
                val qualifierPart = if (qualifier.isNotBlank()) " ($qualifier)" else ""
                lines.add("- $metric: $valueText$unitPart$qualifierPart")
            }
        }

        val classification = record.optJSONObject("classification")
        if (classification != null) {
            val criterion = classification.optString("criterion", "").trim()
            val classes = jsonArrayToStrings(classification.optJSONArray("classes"))
            if (classes.isNotEmpty()) {
                if (criterion.isNotBlank()) {
                    lines.add("Clasificacion ($criterion): ${classes.joinToString(", ")}")
                } else {
                    lines.add("Clasificacion: ${classes.joinToString(", ")}")
                }
            }
        }

        if (record.optBoolean("uncertain_text", false)) {
            val note = record.optString("uncertainty_note", "").trim()
            lines.add("Nota de incertidumbre: ${if (note.isNotBlank()) note else "la fuente marca este registro como incierto"}")
        }

        return lines.joinToString("\n").trim()
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
        
        val mappingFromFile = loadEmbeddingMapping(embeddings.size)
        val (questions, entryIds) = if (mappingFromFile != null) {
            mappingFromFile
        } else {
            val q = mutableListOf<String>()
            val ids = mutableListOf<Int>()
            kbEntries?.entries?.sortedBy { it.key }?.forEach { (id, entry) ->
                entry.questions.forEach { question ->
                    q.add(question)
                    ids.add(id)
                }
            }
            q to ids
        }

        embeddingIndexAligned = questions.size == embeddings.size && entryIds.size == embeddings.size
        if (!embeddingIndexAligned) {
            Log.w(
                TAG,
                "Desalineacion KB/embeddings: preguntas=${questions.size}, embeddings=${embeddings.size}. Se desactiva retrieval semantico."
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
        buildEntrySemanticEmbeddings(embeddings, entryIds)
        buildSemanticAnnIndex(embeddings)
    }

    private fun loadEmbeddingMapping(expectedRows: Int): Pair<MutableList<String>, MutableList<Int>>? {
        return try {
            val jsonString = context.assets.open(EMBEDDINGS_MAPPING_FILE).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val questionsJson = json.optJSONArray("questions") ?: return null
            val entryIdsJson = json.optJSONArray("entry_ids") ?: return null

            if (questionsJson.length() != entryIdsJson.length()) {
                Log.w(TAG, "Mapping invalido: questions y entry_ids tienen distinta longitud")
                return null
            }
            if (questionsJson.length() != expectedRows) {
                Log.w(TAG, "Mapping invalido: filas mapping=${questionsJson.length()} filas embeddings=$expectedRows")
                return null
            }

            val entries = kbEntries ?: return null
            val questions = mutableListOf<String>()
            val entryIds = mutableListOf<Int>()

            for (i in 0 until questionsJson.length()) {
                val question = questionsJson.optString(i, "").trim()
                val entryId = entryIdsJson.optInt(i, -1)
                if (question.isBlank() || entryId < 0 || !entries.containsKey(entryId)) {
                    Log.w(TAG, "Mapping invalido en posicion $i (q='$question', entryId=$entryId)")
                    return null
                }
                questions.add(question)
                entryIds.add(entryId)
            }
            Log.d(TAG, "Mapping de embeddings cargado: ${questions.size} preguntas")
            questions to entryIds
        } catch (e: Exception) {
            Log.i(TAG, "No se pudo cargar mapping de embeddings: ${e.message}")
            null
        }
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
        val shouldUseEncoder = useMindSporeEncoder && embeddingIndexAligned && !forceTextOnlyMode
        AppLogger.log(TAG, "findBestMatch: '$userQuery' useMindSpore=$shouldUseEncoder")
        
        if (!shouldUseEncoder) {
            AppLogger.log(TAG, "findBestMatch: semantic encoder unavailable, no lexical fallback")
            return null
        }
        val normalizedQuery = normalizeQueryForSearch(userQuery)
        val queryEmbedding = computeEmbedding(normalizedQuery) ?: run {
            AppLogger.log(TAG, "findBestMatch: no se pudo calcular embedding de query")
            return null
        }

        val ranked = rankSemanticCandidates(
            queryEmbedding = queryEmbedding,
            embeddings = embeddings,
            minScore = -1f
        )
        val best = ranked.firstOrNull() ?: return null

        val elapsedTime = System.currentTimeMillis() - startTime
        AppLogger.log(
            TAG,
            "findBestMatch: bestScore=${best.score} bestIdx=${best.index} base=${String.format("%.2f", best.baseScore)} expanded=${String.format("%.2f", best.expandedScore)} entry=${String.format("%.2f", best.entryScore)} (${elapsedTime}ms)"
        )

        val matchedQuestion = questions[best.index]
        val entryId = entryIds[best.index]
        val entry = entries[entryId]!!

        return MatchResult(
            answer = entry.answer,
            matchedQuestion = matchedQuestion,
            similarityScore = best.score,
            category = entry.category,
            entryId = entryId
        )
    }

    private fun buildEntrySemanticEmbeddings(
        embeddings: Array<FloatArray>,
        entryIds: List<Int>
    ) {
        if (embeddings.isEmpty() || entryIds.size != embeddings.size) {
            entrySemanticEmbeddings = emptyMap()
            return
        }

        val sums = mutableMapOf<Int, FloatArray>()
        val counts = mutableMapOf<Int, Int>()

        for (idx in embeddings.indices) {
            val entryId = entryIds[idx]
            val source = embeddings[idx]
            val sum = sums.getOrPut(entryId) { FloatArray(source.size) }
            for (dim in source.indices) {
                sum[dim] += source[dim]
            }
            counts[entryId] = (counts[entryId] ?: 0) + 1
        }

        val averaged = mutableMapOf<Int, FloatArray>()
        sums.forEach { (entryId, sum) ->
            val count = (counts[entryId] ?: 1).toFloat()
            for (dim in sum.indices) {
                sum[dim] /= count
            }
            normalizeInPlace(sum)
            averaged[entryId] = sum
        }

        entrySemanticEmbeddings = averaged
    }

    private fun buildSemanticAnnIndex(embeddings: Array<FloatArray>) {
        if (embeddings.isEmpty()) {
            annCentroids = emptyArray()
            annPostingLists = emptyList()
            return
        }

        val clusterCount = sqrt(embeddings.size.toDouble()).toInt()
            .coerceIn(ANN_MIN_CLUSTERS, ANN_MAX_CLUSTERS)
            .coerceAtMost(embeddings.size)

        if (clusterCount <= 1) {
            annCentroids = arrayOf(embeddings.first().copyOf())
            annPostingLists = listOf(IntArray(embeddings.size) { it })
            AppLogger.log(TAG, "ANN index trivial: clusters=1 docs=${embeddings.size}")
            return
        }

        val dimension = embeddings[0].size
        val centroids = Array(clusterCount) { clusterIdx ->
            val seedIdx = ((clusterIdx.toLong() * embeddings.size) / clusterCount).toInt()
                .coerceIn(0, embeddings.lastIndex)
            embeddings[seedIdx].copyOf()
        }

        repeat(ANN_KMEANS_ITERS) { iter ->
            val sums = Array(clusterCount) { FloatArray(dimension) }
            val counts = IntArray(clusterCount)

            for (vector in embeddings) {
                val nearest = nearestCentroidIndex(vector, centroids)
                counts[nearest] += 1
                val target = sums[nearest]
                for (dim in 0 until dimension) {
                    target[dim] += vector[dim]
                }
            }

            for (clusterIdx in 0 until clusterCount) {
                if (counts[clusterIdx] <= 0) {
                    val reassignIdx = ((clusterIdx + iter + 1).toLong() * embeddings.size / clusterCount).toInt()
                        .coerceIn(0, embeddings.lastIndex)
                    centroids[clusterIdx] = embeddings[reassignIdx].copyOf()
                } else {
                    val centroid = sums[clusterIdx]
                    val countInv = 1f / counts[clusterIdx].toFloat()
                    for (dim in centroid.indices) {
                        centroid[dim] *= countInv
                    }
                    normalizeInPlace(centroid)
                    centroids[clusterIdx] = centroid
                }
            }
        }

        val postings = MutableList(clusterCount) { mutableListOf<Int>() }
        for (idx in embeddings.indices) {
            val nearest = nearestCentroidIndex(embeddings[idx], centroids)
            postings[nearest].add(idx)
        }

        annCentroids = centroids
        annPostingLists = postings.map { bucket -> bucket.toIntArray() }
        AppLogger.log(
            TAG,
            "ANN index listo: clusters=${annCentroids.size}, docs=${embeddings.size}, probe=$ANN_PROBE_CLUSTERS"
        )
    }

    private fun nearestCentroidIndex(vector: FloatArray, centroids: Array<FloatArray>): Int {
        var bestIdx = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (idx in centroids.indices) {
            val score = cosineSimilarity(vector, centroids[idx])
            if (score > bestScore) {
                bestScore = score
                bestIdx = idx
            }
        }
        return bestIdx
    }

    private fun rankSemanticCandidates(
        queryEmbedding: FloatArray,
        embeddings: Array<FloatArray>,
        minScore: Float,
        queryTokens: Set<String> = emptySet()
    ): List<RankedSemanticCandidate> {
        if (embeddings.isEmpty()) return emptyList()

        val candidateIndices = findAnnCandidateIndices(queryEmbedding, embeddings.size)
        if (candidateIndices.isEmpty()) return emptyList()

        val entryIds = kbEntryIds ?: return emptyList()
        val entries = kbEntries ?: return emptyList()

        val ranked = ArrayList<RankedSemanticCandidate>(candidateIndices.size)
        for (idx in candidateIndices) {
            val baseScore = cosineSimilarity(queryEmbedding, embeddings[idx])
            if (baseScore < minScore) continue

            // Hybrid scoring: combine embedding similarity with entity overlap.
            // Problem: embedding model weights common words ("café") too heavily,
            // so "roya en café" matches "raíz del café" (0.84) better than
            // "reducir pérdidas por roya" (0.52). Fix: when query tokens
            // match entry entities, guarantee a competitive score floor.
            val entryId = entryIds[idx]
            val entry = entries[entryId]
            val finalScore = if (queryTokens.isNotEmpty() && entry != null && entry.entityTokens.isNotEmpty()) {
                val genericTokens = setOf("cafe", "cafeto", "cafetal")
                val specificQueryTokens = queryTokens.filterNot { it in genericTokens }
                val specificEntityTokens = entry.entityTokens.filterNot { it in genericTokens }
                if (specificQueryTokens.isNotEmpty() && specificEntityTokens.isNotEmpty()) {
                    val overlap = specificQueryTokens.count { it in specificEntityTokens }
                    if (overlap > 0) {
                        // Any entity match = topic-relevant. Guarantee minimum 0.85.
                        // Additional matches push higher.
                        val entityFloor = (0.85f + (overlap - 1) * 0.05f).coerceAtMost(0.95f)
                        maxOf(baseScore, entityFloor)
                    } else {
                        // Query has specific terms but none match this entry's entities.
                        // Slight penalty: this entry is probably off-topic.
                        baseScore * 0.88f
                    }
                } else baseScore
            } else baseScore

            if (finalScore < minScore) continue
            val boostedScore = finalScore.coerceIn(0f, 1f)
            ranked.add(
                RankedSemanticCandidate(
                    index = idx,
                    score = boostedScore,
                    baseScore = baseScore.coerceIn(0f, 1f),
                    expandedScore = boostedScore,
                    entryScore = baseScore.coerceIn(0f, 1f)
                )
            )
        }

        return ranked.sortedByDescending { it.score }
    }

    private fun findAnnCandidateIndices(queryEmbedding: FloatArray, totalEmbeddings: Int): IntArray {
        if (totalEmbeddings <= 0) return IntArray(0)

        // En KB pequeñas/medianas priorizar recall: full-scan semántico.
        // 5k x 384 sigue siendo manejable en móvil y evita perder el match correcto.
        if (totalEmbeddings <= 5000) {
            return IntArray(totalEmbeddings) { it }
        }
        if (annCentroids.isEmpty() || annPostingLists.isEmpty()) {
            return IntArray(totalEmbeddings) { it }
        }

        val centroidScores = annCentroids.indices
            .map { idx -> idx to cosineSimilarity(queryEmbedding, annCentroids[idx]) }
            .sortedByDescending { it.second }
            .take(minOf(ANN_PROBE_CLUSTERS, annCentroids.size))

        val candidates = LinkedHashSet<Int>(ANN_MAX_CANDIDATES)
        for ((centroidIdx, _) in centroidScores) {
            val posting = annPostingLists.getOrNull(centroidIdx) ?: IntArray(0)
            for (docIdx in posting) {
                candidates.add(docIdx)
                if (candidates.size >= ANN_MAX_CANDIDATES) break
            }
            if (candidates.size >= ANN_MAX_CANDIDATES) break
        }

        return if (candidates.isNotEmpty()) {
            candidates.toIntArray()
        } else {
            IntArray(totalEmbeddings) { it }
        }
    }

    private fun normalizeInPlace(vector: FloatArray) {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = sqrt(norm)
        if (norm <= 0f) return
        for (i in vector.indices) {
            vector[i] /= norm
        }
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
        if (!useMindSporeEncoder || !embeddingIndexAligned) return
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
        if (sim < 0.50f) {
            useMindSporeEncoder = false
            AppLogger.log(TAG, "Tokenizer/KB desalineados criticamente (sim=$sim). Se desactiva retrieval semantico.")
        } else if (sim < 0.70f) {
            AppLogger.log(TAG, "Tokenizer/KB con desalineacion moderada (sim=$sim). Se mantiene retrieval semantico.")
        }
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
        
        // Recuperacion semantica pura: sin fallback lexical.
        val shouldUseEncoder = useMindSporeEncoder && embeddingIndexAligned && !forceTextOnlyMode
        if (!shouldUseEncoder) {
            AppLogger.log(TAG, "findTopKContexts: semantic encoder unavailable, returning empty context")
            return ContextResult(emptyList(), "", null)
        }
        val normalizedQuery = normalizeQueryForSearch(userQuery)
        val queryEmbedding = computeEmbedding(normalizedQuery) ?: run {
            AppLogger.log(TAG, "findTopKContexts: no se pudo calcular embedding de query")
            return ContextResult(emptyList(), "", null)
        }

        val queryTokens = informativeTokensFromText(normalizedQuery)
        val ranked = rankSemanticCandidates(
            queryEmbedding = queryEmbedding,
            embeddings = embeddings,
            minScore = minScore,
            queryTokens = queryTokens
        )

        if (ranked.isEmpty()) {
            Log.d(TAG, "Sin resultados semanticos >= umbral (minScore=$minScore)")
            return ContextResult(
                contexts = emptyList(),
                combinedContext = "",
                groundingAssessment = buildGroundingAssessment(userQuery, null, queryEmbedding)
            )
        }

        val bestCandidate = ranked.first()
        Log.d(
            TAG,
            "Busqueda semantica: best=${String.format("%.2f", bestCandidate.score)} base=${String.format("%.2f", bestCandidate.baseScore)} expanded=${String.format("%.2f", bestCandidate.expandedScore)} entry=${String.format("%.2f", bestCandidate.entryScore)} q='${questions[bestCandidate.index]}'"
        )

        // Evitar duplicados de la misma entrada (múltiples preguntas pueden mapear a la misma respuesta)
        val seenEntryIds = mutableSetOf<Int>()
        val uniqueResults = mutableListOf<MatchResult>()

        for (candidate in ranked) {
            val entryId = entryIds[candidate.index]
            if (entryId !in seenEntryIds) {
                seenEntryIds.add(entryId)
                val entry = entries[entryId]!!
                uniqueResults.add(
                    MatchResult(
                        answer = entry.answer,
                        matchedQuestion = questions[candidate.index],
                        similarityScore = candidate.score,
                        category = entry.category,
                        entryId = entryId
                    )
                )
                if (uniqueResults.size >= topK) break
            }
        }

        val elapsedTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "findTopKContexts: ${uniqueResults.size} contextos en ${elapsedTime}ms")

        // Construir contexto combinado para el LLM
        val combinedContext = buildCombinedContext(uniqueResults)
        val groundingAssessment = buildGroundingAssessment(
            userQuery = userQuery,
            topMatch = uniqueResults.firstOrNull(),
            queryEmbedding = queryEmbedding
        )

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
        topMatch: MatchResult?,
        queryEmbedding: FloatArray? = null
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

        val effectiveQueryEmbedding = queryEmbedding ?: computeEmbedding(normalizeQueryForSearch(userQuery))
        val entryScore = if (effectiveQueryEmbedding != null) {
            entrySemanticEmbeddings[topMatch.entryId]?.let { cosineSimilarity(effectiveQueryEmbedding, it) }
        } else {
            null
        } ?: topMatch.similarityScore

        val supportScore = (
            topMatch.similarityScore * 0.66f +
                entryScore * 0.34f
            ).coerceIn(0f, 1f)
        val semanticCoverage = ((topMatch.similarityScore + entryScore) * 0.5f).coerceIn(0f, 1f)
        val entityCoverage = maxOf(topMatch.similarityScore, entryScore).coerceIn(0f, 1f)
        val unknownTokenRatio = (1f - supportScore).coerceIn(0f, 1f)
        val hasStrongSupport =
            supportScore >= 0.50f &&
                semanticCoverage >= 0.42f &&
                unknownTokenRatio <= 0.58f

        return GroundingAssessment(
            supportScore = supportScore,
            lexicalCoverage = semanticCoverage,
            entityCoverage = entityCoverage,
            unknownTokenRatio = unknownTokenRatio,
            queryTokens = queryTokens,
            missingEntityTokens = emptySet(),
            unknownQueryTokens = emptySet(),
            hasStrongSupport = hasStrongSupport
        )
    }

    fun scoreResponseGrounding(
        responseText: String,
        userQuery: String,
        contextText: String
    ): Float? {
        val shouldUseEncoder = useMindSporeEncoder && embeddingIndexAligned && !forceTextOnlyMode
        if (!shouldUseEncoder) return null

        val responseEmbedding = computeEmbedding(responseText.take(900)) ?: return null
        val queryEmbedding = computeEmbedding(normalizeQueryForSearch(userQuery)) ?: return null
        val contextChunks = splitContextIntoSemanticChunks(contextText)
        if (contextChunks.isEmpty()) return null

        var bestContextScore = Float.NEGATIVE_INFINITY
        var contextScoreSum = 0f
        var count = 0

        for (chunk in contextChunks) {
            val chunkEmbedding = computeEmbedding(chunk) ?: continue
            val similarity = cosineSimilarity(responseEmbedding, chunkEmbedding).coerceIn(0f, 1f)
            if (similarity > bestContextScore) bestContextScore = similarity
            contextScoreSum += similarity
            count += 1
        }

        if (count <= 0) return null

        val avgContextScore = contextScoreSum / count.toFloat()
        val queryScore = cosineSimilarity(responseEmbedding, queryEmbedding).coerceIn(0f, 1f)
        return (
            bestContextScore.coerceIn(0f, 1f) * 0.55f +
                avgContextScore * 0.20f +
                queryScore * 0.25f
            ).coerceIn(0f, 1f)
    }

    private fun splitContextIntoSemanticChunks(contextText: String): List<String> {
        val clean = contextText.replace("\r", "").trim()
        if (clean.isBlank()) return emptyList()

        val bySeparator = clean
            .split("\n---\n")
            .map { it.trim() }
            .filter { it.length >= 30 }
            .take(4)
        if (bySeparator.isNotEmpty()) return bySeparator

        return clean
            .chunked(700)
            .map { it.trim() }
            .filter { it.length >= 30 }
            .take(4)
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
        entrySemanticEmbeddings = emptyMap()
        annCentroids = emptyArray()
        annPostingLists = emptyList()
        embeddingIndexAligned = false
        isInitialized = false
        useMindSporeEncoder = false
        
        Log.d(TAG, "Recursos liberados")
    }
}
