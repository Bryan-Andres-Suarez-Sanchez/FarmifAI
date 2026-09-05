package edu.unicauca.app.agrochat.llm

import android.content.Context
import android.util.Log
import android.os.SystemClock
import android.llama.cpp.LLamaAndroid
import edu.unicauca.app.agrochat.rag.QueryTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * LlamaService - Servicio de LLM local usando llama.cpp para inferencia offline
 * Permite respuestas inteligentes sin conexión usando modelos GGUF
 */
class LlamaService private constructor() {
    
    companion object {
        private const val TAG = "LlamaService"
        // Modelo offline por defecto FarmifAI (Qwen 3.5 0.8B ajustado)
        private const val DEFAULT_MODEL_FILENAME = "FarmifAI_1.3.Q5_K_M.gguf"
        private const val MAX_TOKENS = 1200  // Salidas más completas por defecto

        // Descarga automática desde FarmifAI/FarmifAI_1.3_GGUF.
        private const val MODEL_DOWNLOAD_URL = "https://huggingface.co/FarmifAI/FarmifAI_1.3_GGUF/resolve/main/FarmifAI_1.3.Q5_K_M.gguf"
        private const val MODEL_SIZE_BYTES = 592_636_640L
        private const val MIN_VALID_GGUF_BYTES = 100_000_000L
        private val MODEL_FILENAME_PREFERENCE = listOf(
            DEFAULT_MODEL_FILENAME,
            "FarmifAI_1.3.Q4_K_M.gguf",
            "FarmifAI_1.3.F16.gguf",
            "Qwen3.5-0.8B-Q4_K_M.gguf",
            "Qwen3.5-0.8B-Q5_K_M.gguf",
            "Qwen3.5-0.8B-Q4_0.gguf",
            "Qwen3.5-0.8B-Q3_K_M.gguf",
            "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"
        )
        
        @Volatile
        private var instance: LlamaService? = null
        
        fun getInstance(): LlamaService {
            return instance ?: synchronized(this) {
                instance ?: LlamaService().also { instance = it }
            }
        }
    }
    
    private val llama: LLamaAndroid = LLamaAndroid.instance()
    private var loadedModelName: String? = null

    private enum class ModelFamily {
        LLAMA3,
        QWEN,
        GENERIC
    }

    // Callback para progreso de descarga
    var onDownloadProgress: ((progress: Int, downloadedMB: Int, totalMB: Int) -> Unit)? = null
    
    /**
     * Verifica si el modelo está disponible en el almacenamiento
     */
    fun isModelAvailable(context: Context): Boolean {
        return getModelFile(context) != null
    }

    /**
     * Verifica específicamente si está disponible el modelo preferido actual.
     */
    fun isPreferredModelAvailable(context: Context): Boolean {
        val dir = context.getExternalFilesDir(null) ?: return false
        val preferred = File(dir, DEFAULT_MODEL_FILENAME)
        return isValidGguf(preferred, MODEL_SIZE_BYTES)
    }

    /**
     * Devuelve el archivo de modelo GGUF a usar.
     */
    private fun getModelFile(context: Context): File? {
        val dir = context.getExternalFilesDir(null) ?: return null

        for (name in MODEL_FILENAME_PREFERENCE) {
            val preferred = File(dir, name)
            val expectedSize = if (name == DEFAULT_MODEL_FILENAME) MODEL_SIZE_BYTES else null
            if (isValidGguf(preferred, expectedSize)) return preferred
        }

        val candidates = dir.listFiles { f ->
            f.isFile && f.name.endsWith(".gguf", ignoreCase = true) && isValidGguf(f)
        } ?: emptyArray()
        // En rama fastmodel priorizamos menor huella para mejorar carga/latencia.
        return candidates.minByOrNull { it.length() }
    }
    
    /**
     * Obtiene la ruta del modelo
     */
    fun getModelPath(context: Context): String {
        val dir = context.getExternalFilesDir(null)
        val selected = getModelFile(context)
        return selected?.absolutePath ?: File(dir, DEFAULT_MODEL_FILENAME).absolutePath
    }

    fun getModelFilename(context: Context): String? = getModelFile(context)?.name
    
    /**
     * Obtiene el tamaño del modelo en MB
     */
    fun getModelSizeMB(context: Context): Long {
        val modelFile = getModelFile(context) ?: return 0L
        return modelFile.length() / (1024 * 1024)
    }

    fun getExpectedDownloadSizeMB(): Int = (MODEL_SIZE_BYTES / (1024 * 1024)).toInt()

    /**
     * Verifica si el modelo está cargado
     */
    fun isLoaded(): Boolean = llama.isLoaded()

    fun cancelGeneration() {
        llama.cancelGeneration()
    }

    /**
     * Descarga el modelo GGUF automáticamente desde Hugging Face
     */
    suspend fun downloadModel(context: Context): Result<File> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val dir = context.getExternalFilesDir(null)
                ?: return@withContext Result.failure(Exception("No se puede acceder al almacenamiento"))

            val modelFile = File(dir, DEFAULT_MODEL_FILENAME)
            val tempFile = File(dir, "${DEFAULT_MODEL_FILENAME}.tmp")

            if (isValidGguf(modelFile, MODEL_SIZE_BYTES)) {
                Log.i(TAG, "Modelo ya existe: ${modelFile.absolutePath}")
                return@withContext Result.success(modelFile)
            }
            if (modelFile.exists()) {
                Log.w(TAG, "Eliminando GGUF incompleto o inválido: ${modelFile.length()} bytes")
                modelFile.delete()
            }

            Log.i(TAG, "Descargando modelo desde: $MODEL_DOWNLOAD_URL")

            val url = URL(MODEL_DOWNLOAD_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "AgroChat/1.0")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("Error HTTP descargando GGUF: $responseCode"))
            }

            val totalSize = connection.contentLengthLong.takeIf { it > 0 } ?: MODEL_SIZE_BYTES
            val totalMB = (totalSize / (1024 * 1024)).toInt()

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var lastProgress = 0

                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break

                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val progress = ((downloaded * 100) / totalSize).toInt()
                        if (progress > lastProgress) {
                            lastProgress = progress
                            val downloadedMB = (downloaded / (1024 * 1024)).toInt()
                            onDownloadProgress?.invoke(progress, downloadedMB, totalMB)
                        }
                    }
                }
            }

            if (isValidGguf(tempFile, MODEL_SIZE_BYTES)) {
                if (modelFile.exists()) modelFile.delete()
                if (!tempFile.renameTo(modelFile)) {
                    tempFile.copyTo(modelFile, overwrite = true)
                    tempFile.delete()
                }
                if (!isValidGguf(modelFile, MODEL_SIZE_BYTES)) {
                    modelFile.delete()
                    return@withContext Result.failure(Exception("No se pudo guardar el GGUF validado"))
                }
                Log.i(TAG, "Modelo descargado: ${modelFile.absolutePath}")
                Result.success(modelFile)
            } else {
                val actualSize = tempFile.length()
                tempFile.delete()
                Result.failure(Exception("Descarga GGUF incompleta o inválida: $actualSize/$MODEL_SIZE_BYTES bytes"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error descargando modelo: ${e.message}", e)
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun isValidGguf(file: File, expectedSize: Long? = null): Boolean {
        if (!file.isFile || file.length() <= MIN_VALID_GGUF_BYTES) return false
        if (expectedSize != null && file.length() != expectedSize) return false
        return try {
            val magic = ByteArray(4)
            FileInputStream(file).use { it.read(magic) == magic.size } &&
                magic.contentEquals(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
        } catch (_: Exception) {
            false
        }
    }
    
    /**
     * Carga el modelo GGUF
     */
    suspend fun load(context: Context): Result<Unit> {
        return try {
            val modelFile = getModelFile(context)
                ?: return Result.failure(
                    Exception(
                        "Modelo GGUF no encontrado. Copia un .gguf a: ${context.getExternalFilesDir(null)?.absolutePath}"
                    )
                )

            Log.i(TAG, "Cargando modelo: ${modelFile.name} (${modelFile.length() / (1024 * 1024)}MB) desde: ${modelFile.absolutePath}")

            llama.load(modelFile.absolutePath)
            loadedModelName = modelFile.name
            Log.i(TAG, "Modelo cargado exitosamente")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando modelo", e)
            Result.failure(e)
        }
    }
    
    /**
     * Genera respuesta usando el LLM local (streaming)
     */
    fun generate(prompt: String): Flow<String> = llama.send(prompt, formatChat = true)
    
    /**
     * Genera respuesta completa (no streaming)
     */
    suspend fun generateComplete(prompt: String, maxTokens: Int = MAX_TOKENS): Result<String> {
        return try {
            val response = llama.sendComplete(prompt, formatChat = true, maxTokens = maxTokens)
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error generando respuesta", e)
            Result.failure(e)
        }
    }
    
    /**
     * Genera respuesta para chat agrícola con contexto RAG
     * Usa formato Llama 3.2 con system prompt para respuestas coherentes
     * @param userQuery La pregunta del usuario
     * @param contextFromKB Contexto de la base de conocimiento (opcional)
     * @param maxTokens Máximo de tokens a generar
     * @param maxContextLength Longitud máxima del contexto a incluir
     * @param systemPrompt Prompt del sistema personalizable
     */
    suspend fun generateAgriResponse(
        userQuery: String,
        contextFromKB: String? = null,
        maxTokens: Int = MAX_TOKENS,
        maxContextLength: Int = 1200,
        systemPrompt: String = "Eres FarmifAI, un asistente agricola experto. Responde en espanol de forma clara, cercana y practica para agricultor. Nunca menciones terminos internos como KB, RAG, LLM, contexto de referencia, modelo o sistema.",
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<String> {
        val prompt = buildAgriPrompt(userQuery, contextFromKB, maxContextLength, systemPrompt, conversationHistory)
        
        Log.d(TAG, "Prompt local (${detectModelFamily()}): ${prompt.length} chars, maxTokens: $maxTokens, history: ${conversationHistory.size} turns")
        
        // formatChat = false porque ya formateamos manualmente
        val result = generateCompleteRaw(prompt, maxTokens)
        
        return result.map { response -> cleanResponse(response) }
    }

    /**
     * Genera respuesta agrícola en streaming. Va entregando texto parcial para mejorar
     * el tiempo percibido por el usuario.
     */
    suspend fun generateAgriResponseStreaming(
        userQuery: String,
        contextFromKB: String? = null,
        maxTokens: Int = MAX_TOKENS,
        maxContextLength: Int = 1200,
        systemPrompt: String = "Eres FarmifAI, un asistente agricola experto. Responde en espanol de forma clara, cercana y practica para agricultor. Nunca menciones terminos internos como KB, RAG, LLM, contexto de referencia, modelo o sistema.",
        conversationHistory: List<Pair<String, String>> = emptyList(),
        queryId: Long = 0L,
        onFormatRetry: suspend () -> Unit = {},
        onPartialResponse: suspend (String) -> Unit
    ): Result<String> {
        return try {
            val requiresXml = systemPrompt.contains("<answer>", ignoreCase = true)

            suspend fun runAttempt(attemptSystemPrompt: String, attempt: Int): String {
                var stageStart = SystemClock.elapsedRealtimeNanos()
                val prompt = buildAgriPrompt(userQuery, contextFromKB, maxContextLength, attemptSystemPrompt, conversationHistory)
                QueryTelemetry.record(queryId, "SLM_PROMPT_BUILD_A$attempt", QueryTelemetry.elapsedMs(stageStart))
                Log.d(TAG, "Prompt streaming (${detectModelFamily()}): ${prompt.length} chars, maxTokens: $maxTokens")
                val raw = StringBuilder()
                var emittedLength = 0
                var firstTokenRecorded = false
                stageStart = SystemClock.elapsedRealtimeNanos()
                llama.send(prompt, formatChat = false, maxTokens = maxTokens).collect { chunk ->
                    if (chunk.isBlank()) return@collect
                    if (!firstTokenRecorded) {
                        firstTokenRecorded = true
                        QueryTelemetry.record(queryId, "SLM_TTFT_A$attempt", QueryTelemetry.elapsedMs(stageStart))
                    }
                    raw.append(chunk)

                    // Con XML estricto no mostramos parciales: antes de </answer>
                    // podrían contener el razonamiento interno o etiquetas incompletas.
                    if (!requiresXml) {
                        val partial = cleanResponse(raw.toString())
                        val shouldEmit = partial.length >= 48 &&
                            (partial.length - emittedLength >= 32 || partial.endsWith("\n") ||
                                partial.endsWith(".") || partial.endsWith(":"))
                        if (shouldEmit) {
                            emittedLength = partial.length
                            onPartialResponse(partial)
                        }
                    }
                }
                QueryTelemetry.record(queryId, "SLM_INFERENCE_A$attempt", QueryTelemetry.elapsedMs(stageStart))
                return raw.toString()
            }

            var rawResponse = runAttempt(systemPrompt, 1)
            var parseStart = SystemClock.elapsedRealtimeNanos()
            var extraction = if (requiresXml) AnswerXmlValidator.extract(rawResponse) else null
            QueryTelemetry.record(queryId, "SLM_FORMAT_PARSE_A1", QueryTelemetry.elapsedMs(parseStart))
            if (requiresXml && extraction == null) {
                Log.w(TAG, "FORMAT_UNRECOVERABLE attempt=1: no se pudo aislar la respuesta; regenerando")
                onFormatRetry()
                val retryPrompt = "$systemPrompt\nREINTENTO: entrega la respuesta final dentro de <answer>...</answer>. Si generas razonamiento, mantenlo antes en <reasoning>...</reasoning>."
                rawResponse = runAttempt(retryPrompt, 2)
                parseStart = SystemClock.elapsedRealtimeNanos()
                extraction = AnswerXmlValidator.extract(rawResponse)
                QueryTelemetry.record(queryId, "SLM_FORMAT_PARSE_A2", QueryTelemetry.elapsedMs(parseStart))
                if (extraction == null) {
                    Log.e(TAG, "FORMAT_UNRECOVERABLE attempt=2: no fue posible separar el razonamiento")
                    return Result.failure(IllegalStateException("No fue posible aislar la respuesta final después de 2 intentos"))
                }
                Log.i(TAG, "FORMAT_RECOVERED attempt=2 strategy=${extraction.strategy}")
            } else if (requiresXml) {
                Log.i(TAG, "FORMAT_OK attempt=1 strategy=${extraction?.strategy}")
            }

            val cleanStart = SystemClock.elapsedRealtimeNanos()
            val finalResponse = cleanResponse(extraction?.answer ?: rawResponse)
            QueryTelemetry.record(queryId, "SLM_RESPONSE_CLEAN", QueryTelemetry.elapsedMs(cleanStart))
            onPartialResponse(finalResponse)
            Result.success(finalResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error generando respuesta streaming", e)
            Result.failure(e)
        }
    }
    
    /**
     * Genera respuesta sin formateo automático (para prompts pre-formateados)
     */
    private suspend fun generateCompleteRaw(prompt: String, maxTokens: Int = MAX_TOKENS): Result<String> {
        return try {
            val response = llama.sendComplete(prompt, formatChat = false, maxTokens = maxTokens)
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error generando respuesta raw", e)
            Result.failure(e)
        }
    }
    
    /**
     * Limpia la respuesta de tokens especiales y texto incoherente
     */
    private fun cleanResponse(response: String): String {
        var cleaned = response
        
        // Tokens especiales comunes (Llama / Qwen)
        val specialTokens = listOf(
            "<|begin_of_text|>", "<|end_of_text|>",
            "<|start_header_id|>", "<|end_header_id|>",
            "<|eot_id|>", "<|eom_id|>",
            "<|im_start|>", "<|im_end|>"
        )
        
        // Eliminar tokens al inicio
        for (token in specialTokens) {
            while (cleaned.startsWith(token)) {
                cleaned = cleaned.removePrefix(token).trimStart()
            }
        }
        
        // Cortar en el primer token especial encontrado
        for (token in specialTokens) {
            val idx = cleaned.indexOf(token)
            if (idx > 0) {
                cleaned = cleaned.substring(0, idx)
                break
            }
        }
        
        // Eliminar artefactos obvios del prompt repetido, preservando listas numeradas.
        val lines = cleaned.lines().filter { line ->
            val lower = line.lowercase().trim()
            lower != "system" &&
            lower != "user" &&
            lower != "assistant" &&
            !lower.startsWith("info:")
        }
        cleaned = lines.joinToString("\n")
        
        // Limpiar artefactos de conversación inventada (Usuario:/Asistente:)
        cleaned = removeRoleDialogueArtifacts(cleaned).trim()

        // The notebook keeps reasoning internal and returns only <answer>.
        cleaned = AnswerXmlValidator.extract(cleaned)?.answer ?: cleaned.trim()
        
        // Si quedó muy corta, dejar que capas superiores decidan fallback.
        if (cleaned.length < 5) {
            return cleaned
        }
        
        return cleaned
    }

    private fun detectModelFamily(): ModelFamily {
        val name = loadedModelName?.lowercase().orEmpty()
        return when {
            "qwen" in name || "farmifai_1.3" in name -> ModelFamily.QWEN
            "llama" in name -> ModelFamily.LLAMA3
            else -> ModelFamily.GENERIC
        }
    }

    private fun buildPromptForCurrentModel(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): String {
        return when (detectModelFamily()) {
            ModelFamily.QWEN -> {
                buildString {
                    append("<|im_start|>system\n")
                    append(systemPrompt)
                    append("\n<|im_end|>\n")
                    for ((userMsg, assistantMsg) in conversationHistory) {
                        append("<|im_start|>user\n")
                        append(userMsg)
                        append("\n<|im_end|>\n")
                        append("<|im_start|>assistant\n")
                        append(assistantMsg)
                        append("\n<|im_end|>\n")
                    }
                    append("<|im_start|>user\n")
                    append(userMessage)
                    append("\n<|im_end|>\n")
                    append("<|im_start|>assistant\n")
                }
            }
            ModelFamily.LLAMA3 -> {
                buildString {
                    append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n")
                    append(systemPrompt)
                    append("<|eot_id|>")
                    for ((userMsg, assistantMsg) in conversationHistory) {
                        append("<|start_header_id|>user<|end_header_id|>\n\n")
                        append(userMsg)
                        append("<|eot_id|>")
                        append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                        append(assistantMsg)
                        append("<|eot_id|>")
                    }
                    append("<|start_header_id|>user<|end_header_id|>\n\n")
                    append(userMessage)
                    append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
                }
            }
            ModelFamily.GENERIC -> {
                buildString {
                    append("Sistema:\n")
                    append(systemPrompt)
                    append("\n\n")
                    for ((userMsg, assistantMsg) in conversationHistory) {
                        append("Usuario:\n")
                        append(userMsg)
                        append("\n\nAsistente:\n")
                        append(assistantMsg)
                        append("\n\n")
                    }
                    append("Usuario:\n")
                    append(userMessage)
                    append("\n\nAsistente:\n")
                }
            }
        }
    }

    private fun buildAgriPrompt(
        userQuery: String,
        contextFromKB: String?,
        maxContextLength: Int,
        systemPrompt: String,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): String {
        // Keep both inputs explicit and independent: prior dialogue tells the model
        // what the conversation is about; knowledge contains the current query's
        // three freshly retrieved chunks.
        val knowledgeContext = if (!contextFromKB.isNullOrBlank()) {
            "<knowledge>\n$contextFromKB\n</knowledge>"
        } else {
            "<knowledge>Sin fragmentos recuperados.</knowledge>"
        }
        val userMessage = "$knowledgeContext\n\n<pregunta_actual>\n$userQuery\n</pregunta_actual>"
        val independentSystemPrompt = "$systemPrompt\nEsta es una consulta independiente. Usa solo <pregunta_actual> y <knowledge>; no supongas información de consultas anteriores."
        return buildPromptForCurrentModel(independentSystemPrompt, userMessage)
    }

    /**
     * Corta diálogos multi-turn generados por el modelo y conserva solo la primera
     * respuesta útil del asistente.
     */
    private fun removeRoleDialogueArtifacts(text: String): String {
        val lines = text.lines()
        val result = mutableListOf<String>()
        var hasContent = false

        for (line in lines) {
            val trimmed = line.trim()
            if (!hasContent && trimmed.isBlank()) continue

            val lower = trimmed.lowercase()
            val isUser = lower.startsWith("usuario:") || lower.startsWith("user:")
            val isAssistant = lower.startsWith("asistente:") || lower.startsWith("assistant:")

            if (isUser && hasContent) break
            if (isUser && !hasContent) continue

            val normalized = if (isAssistant) {
                trimmed.substringAfter(":", "").trimStart()
            } else {
                line
            }
            result.add(normalized)
            if (normalized.isNotBlank()) hasContent = true
        }

        val cleaned = result.joinToString("\n").trim()
        return if (cleaned.isNotBlank()) cleaned else text
    }

    /**
     * Trunca contexto de KB para que no exceda el limite de longitud.
     */
    private fun truncateContextPreservingKb(context: String, maxLen: Int): String {
        if (context.length <= maxLen) return context

        val chunks = context
            .split("\n---\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val out = StringBuilder()
        for (chunk in chunks) {
            val next = if (out.isEmpty()) chunk else "\n---\n$chunk"
            if (out.length + next.length > maxLen) break
            out.append(next)
        }
        if (out.isNotEmpty()) return out.toString()

        val outLines = StringBuilder()
        for (line in context.lines()) {
            val candidate = if (outLines.isEmpty()) line else "\n$line"
            if (outLines.length + candidate.length > maxLen) break
            outLines.append(candidate)
        }
        return outLines.toString().trim()
    }

    /**
     * Libera de memoria el modelo y sus recursos.
     */
    suspend fun unload() {
        try {
            llama.unload()
            loadedModelName = null
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando recursos", e)
        }
    }
    
    /**
     * Obtiene información del sistema
     */
    suspend fun getSystemInfo(): String = llama.getSystemInfo()
}
