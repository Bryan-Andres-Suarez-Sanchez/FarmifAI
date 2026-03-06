package edu.unicauca.app.agrochat.kb

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.min
import kotlin.math.sqrt

class PdfKnowledgeManager(context: Context) {

    data class PdfDocumentMeta(
        val id: String,
        val displayName: String,
        val sourceUri: String,
        val pageCount: Int,
        val chunkCount: Int,
        val textLength: Int,
        val addedAtEpochMs: Long
    )

    data class ImportResult(
        val document: PdfDocumentMeta
    )

    data class QueryContext(
        val context: String,
        val topScore: Float,
        val chunkCount: Int,
        val documentNames: List<String>
    )

    private data class PdfChunk(
        val text: String,
        val tokens: Set<String>
    )

    private data class PdfDocumentData(
        val meta: PdfDocumentMeta,
        val chunks: List<PdfChunk>
    )

    private data class RankedChunk(
        val document: PdfDocumentData,
        val chunk: PdfChunk,
        val score: Float
    )

    private data class ExtractedPdf(
        val pageCount: Int,
        val text: String
    )

    private val appContext = context.applicationContext
    private val lock = Any()
    private val storageDir = File(appContext.filesDir, STORAGE_DIR_NAME)
    private val indexFile = File(storageDir, INDEX_FILE_NAME)

    private var loaded = false
    private var documents = mutableListOf<PdfDocumentData>()

    init {
        PDFBoxResourceLoader.init(appContext)
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    fun listDocuments(): List<PdfDocumentMeta> = synchronized(lock) {
        ensureLoadedLocked()
        documents.map { it.meta }.sortedByDescending { it.addedAtEpochMs }
    }

    fun importPdf(uri: Uri): ImportResult {
        val displayName = resolveDisplayName(uri)
        val extracted = extractPdfText(uri)
        val chunks = chunkText(extracted.text)
        require(chunks.isNotEmpty()) { "No fue posible extraer texto util del PDF." }

        val createdAt = System.currentTimeMillis()
        val docId = UUID.randomUUID().toString().replace("-", "")
        val meta = PdfDocumentMeta(
            id = docId,
            displayName = displayName,
            sourceUri = uri.toString(),
            pageCount = extracted.pageCount,
            chunkCount = chunks.size,
            textLength = extracted.text.length,
            addedAtEpochMs = createdAt
        )
        val data = PdfDocumentData(
            meta = meta,
            chunks = chunks.map { PdfChunk(text = it, tokens = informativeTokens(it)) }
        )

        synchronized(lock) {
            ensureLoadedLocked()
            saveDocumentLocked(data)
            documents.removeAll { it.meta.id == meta.id }
            documents.add(data)
            persistIndexLocked()
        }

        return ImportResult(document = meta)
    }

    fun deleteDocument(documentId: String): Boolean = synchronized(lock) {
        ensureLoadedLocked()
        val removed = documents.removeAll { it.meta.id == documentId }
        if (!removed) return false
        documentFile(documentId).delete()
        persistIndexLocked()
        true
    }

    fun findContext(
        userQuery: String,
        maxChunks: Int = 4,
        minScore: Float = DEFAULT_MIN_SCORE,
        maxChars: Int = DEFAULT_MAX_CONTEXT_CHARS
    ): QueryContext? = synchronized(lock) {
        ensureLoadedLocked()
        if (documents.isEmpty()) return null

        val queryTokens = informativeTokens(userQuery)
        if (queryTokens.isEmpty()) return null
        val queryLower = normalize(userQuery)

        val ranked = mutableListOf<RankedChunk>()
        for (document in documents) {
            for (chunk in document.chunks) {
                val score = scoreChunk(queryTokens, queryLower, chunk)
                if (score >= minScore) {
                    ranked.add(RankedChunk(document = document, chunk = chunk, score = score))
                }
            }
        }

        if (ranked.isEmpty()) return null

        val selected = mutableListOf<RankedChunk>()
        val perDocumentCounter = mutableMapOf<String, Int>()
        for (candidate in ranked.sortedByDescending { it.score }) {
            if (selected.size >= maxChunks) break
            val currentCount = perDocumentCounter[candidate.document.meta.id] ?: 0
            if (currentCount >= 2 && documents.size > 1) continue
            selected.add(candidate)
            perDocumentCounter[candidate.document.meta.id] = currentCount + 1
        }

        if (selected.isEmpty()) return null

        val contextBuilder = StringBuilder()
        for (candidate in selected) {
            val section = buildString {
                append("[Documento: ")
                append(candidate.document.meta.displayName)
                append("]\n")
                append(candidate.chunk.text.trim())
            }

            if (contextBuilder.length + section.length + 2 > maxChars) break
            if (contextBuilder.isNotEmpty()) contextBuilder.append("\n\n")
            contextBuilder.append(section)
        }

        val finalContext = contextBuilder.toString().trim()
        if (finalContext.isBlank()) return null

        QueryContext(
            context = finalContext,
            topScore = selected.first().score,
            chunkCount = selected.size,
            documentNames = selected.map { it.document.meta.displayName }.distinct()
        )
    }

    private fun ensureLoadedLocked() {
        if (loaded) return
        documents.clear()

        if (!indexFile.exists()) {
            loaded = true
            return
        }

        try {
            val indexJson = JSONObject(indexFile.readText())
            val docs = indexJson.optJSONArray("documents") ?: JSONArray()
            val recovered = mutableListOf<PdfDocumentData>()
            for (i in 0 until docs.length()) {
                val metaJson = docs.optJSONObject(i) ?: continue
                val meta = metaFromJson(metaJson) ?: continue
                val data = loadDocumentLocked(meta) ?: continue
                recovered.add(data)
            }
            documents = recovered.toMutableList()
            persistIndexLocked()
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando indice PDF: ${e.message}", e)
            documents.clear()
            indexFile.delete()
        } finally {
            loaded = true
        }
    }

    private fun loadDocumentLocked(meta: PdfDocumentMeta): PdfDocumentData? {
        val file = documentFile(meta.id)
        if (!file.exists()) return null

        return try {
            val json = JSONObject(file.readText())
            val chunksArray = json.optJSONArray("chunks") ?: JSONArray()
            val chunks = mutableListOf<PdfChunk>()
            for (i in 0 until chunksArray.length()) {
                val text = chunksArray.optString(i).trim()
                if (text.isBlank()) continue
                chunks.add(PdfChunk(text = text, tokens = informativeTokens(text)))
            }
            if (chunks.isEmpty()) null else PdfDocumentData(meta = meta, chunks = chunks)
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando documento PDF ${meta.id}: ${e.message}", e)
            null
        }
    }

    private fun saveDocumentLocked(document: PdfDocumentData) {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val json = JSONObject().apply {
            put("meta", metaToJson(document.meta))
            put("chunks", JSONArray().apply { document.chunks.forEach { put(it.text) } })
        }
        documentFile(document.meta.id).writeText(json.toString())
    }

    private fun persistIndexLocked() {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val json = JSONObject().apply {
            put("version", 1)
            put(
                "documents",
                JSONArray().apply {
                    documents
                        .map { it.meta }
                        .sortedByDescending { it.addedAtEpochMs }
                        .forEach { put(metaToJson(it)) }
                }
            )
        }
        indexFile.writeText(json.toString())
    }

    private fun extractPdfText(uri: Uri): ExtractedPdf {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("No fue posible abrir el PDF.")

        input.use { stream ->
            PDDocument.load(stream).use { document ->
                val pageCount = document.numberOfPages
                val text = PDFTextStripper().getText(document)
                val normalized = normalizePdfText(text).take(MAX_TEXT_CHARS)
                require(normalized.isNotBlank()) { "El PDF no tiene texto legible." }
                return ExtractedPdf(pageCount = pageCount, text = normalized)
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        var name: String? = null
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(column)
            }
        }

        return name?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "documento_${System.currentTimeMillis()}.pdf"
    }

    private fun scoreChunk(queryTokens: Set<String>, queryLower: String, chunk: PdfChunk): Float {
        if (queryTokens.isEmpty() || chunk.tokens.isEmpty()) return 0f
        val overlapCount = queryTokens.intersect(chunk.tokens).size
        if (overlapCount == 0) return 0f

        val coverage = overlapCount.toFloat() / queryTokens.size.toFloat()
        val specificity = overlapCount.toFloat() / sqrt(chunk.tokens.size.toFloat())
        val directPhraseBoost = if (queryLower.length >= 12 && chunk.text.lowercase(Locale.ROOT).contains(queryLower.take(80))) {
            0.18f
        } else {
            0f
        }

        return (coverage * 0.78f + specificity * 0.22f + directPhraseBoost).coerceAtMost(1f)
    }

    private fun chunkText(text: String): List<String> {
        val sections = splitSections(text)
        if (sections.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        var current = StringBuilder()

        fun flushCurrent() {
            val candidate = current.toString().trim()
            if (candidate.length >= MIN_CHUNK_CHARS) {
                chunks.add(candidate)
            }
            current = StringBuilder()
        }

        fun appendSection(section: String) {
            val cleaned = section.trim()
            if (cleaned.isBlank()) return

            if (current.isNotEmpty() && current.length + cleaned.length + 2 > CHUNK_TARGET_CHARS) {
                val previous = current.toString().trim()
                flushCurrent()
                if (previous.isNotBlank()) {
                    val overlap = previous.takeLast(min(CHUNK_OVERLAP_CHARS, previous.length))
                    if (overlap.isNotBlank()) {
                        current.append(overlap).append("\n\n")
                    }
                }
            }

            if (current.isNotEmpty() && !current.endsWith("\n\n")) {
                current.append("\n\n")
            }
            current.append(cleaned)
        }

        for (section in sections) {
            if (section.length <= CHUNK_TARGET_CHARS) {
                appendSection(section)
            } else {
                val windows = splitLargeSection(section, CHUNK_TARGET_CHARS)
                for (window in windows) {
                    appendSection(window)
                }
            }
        }

        if (current.isNotEmpty()) {
            flushCurrent()
        }

        return chunks.distinct()
    }

    private fun splitSections(text: String): List<String> {
        val paragraphSplit = text
            .split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.length >= 40 }

        if (paragraphSplit.isNotEmpty()) return paragraphSplit

        return text
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length >= 40 }
    }

    private fun splitLargeSection(section: String, windowChars: Int): List<String> {
        val words = section.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < words.size) {
            val builder = StringBuilder()
            var end = start
            while (end < words.size) {
                val nextWord = words[end]
                val nextLength = if (builder.isEmpty()) nextWord.length else builder.length + 1 + nextWord.length
                if (nextLength > windowChars) break
                if (builder.isNotEmpty()) builder.append(' ')
                builder.append(nextWord)
                end++
            }

            val candidate = builder.toString().trim()
            if (candidate.length >= MIN_CHUNK_CHARS) {
                chunks.add(candidate)
            }
            if (end <= start) break
            start = (end - LARGE_SECTION_OVERLAP_WORDS).coerceAtLeast(start + 1)
        }
        return chunks
    }

    private fun normalizePdfText(raw: String): String {
        return raw
            .replace("\u0000", " ")
            .replace(Regex("[\\t\\x0B\\f\\r]+"), " ")
            .replace(Regex(" +"), " ")
            .replace(Regex("\\n\\s*\\n+"), "\n\n")
            .replace(Regex("\\s+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun informativeTokens(text: String): Set<String> {
        val normalized = normalize(text)
        if (normalized.isBlank()) return emptySet()

        return normalized
            .split(Regex("\\s+"))
            .map { normalizeToken(it) }
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .toSet()
    }

    private fun normalize(text: String): String {
        return text
            .lowercase(Locale.ROOT)
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ü", "u")
            .replace("ñ", "n")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeToken(token: String): String {
        return when {
            token.length > 5 && token.endsWith("es") -> token.dropLast(2)
            token.length > 4 && token.endsWith("s") -> token.dropLast(1)
            else -> token
        }
    }

    private fun metaToJson(meta: PdfDocumentMeta): JSONObject {
        return JSONObject().apply {
            put("id", meta.id)
            put("displayName", meta.displayName)
            put("sourceUri", meta.sourceUri)
            put("pageCount", meta.pageCount)
            put("chunkCount", meta.chunkCount)
            put("textLength", meta.textLength)
            put("addedAtEpochMs", meta.addedAtEpochMs)
        }
    }

    private fun metaFromJson(json: JSONObject): PdfDocumentMeta? {
        val id = json.optString("id").trim()
        if (id.isBlank()) return null
        return PdfDocumentMeta(
            id = id,
            displayName = json.optString("displayName", "Documento PDF"),
            sourceUri = json.optString("sourceUri", ""),
            pageCount = json.optInt("pageCount", 0),
            chunkCount = json.optInt("chunkCount", 0),
            textLength = json.optInt("textLength", 0),
            addedAtEpochMs = json.optLong("addedAtEpochMs", 0L)
        )
    }

    private fun documentFile(documentId: String): File {
        return File(storageDir, "doc_${documentId}.json")
    }

    companion object {
        private const val TAG = "PdfKnowledgeManager"
        private const val STORAGE_DIR_NAME = "pdf_kb"
        private const val INDEX_FILE_NAME = "index.json"
        private const val MAX_TEXT_CHARS = 250_000
        private const val CHUNK_TARGET_CHARS = 820
        private const val CHUNK_OVERLAP_CHARS = 140
        private const val LARGE_SECTION_OVERLAP_WORDS = 24
        private const val MIN_CHUNK_CHARS = 120
        private const val DEFAULT_MIN_SCORE = 0.18f
        private const val DEFAULT_MAX_CONTEXT_CHARS = 1_900

        private val STOP_WORDS = setOf(
            "de", "del", "la", "las", "el", "los", "y", "o", "u", "e",
            "a", "en", "con", "por", "para", "al", "un", "una", "unos", "unas",
            "que", "como", "cuando", "donde", "porque", "sobre", "entre", "hasta",
            "desde", "sin", "segun", "segun", "este", "esta", "estos", "estas",
            "ese", "esa", "esos", "esas", "ser", "estar", "fue", "fueron", "son",
            "se", "lo", "le", "les", "su", "sus", "mi", "mis", "tu", "tus"
        )
    }
}
