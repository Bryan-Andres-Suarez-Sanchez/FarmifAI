package edu.unicauca.app.agrochat.llm

import android.content.Context
import android.util.Log
import android.llama.cpp.LLamaAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File
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
        // Modelo gratuito offline recomendado por defecto (Qwen)
        private const val DEFAULT_MODEL_FILENAME = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
        private const val MAX_TOKENS = 1200  // Salidas más completas por defecto

        // URL de descarga automática desde Hugging Face (Qwen2.5 1.5B Q4_K_M)
        private const val MODEL_DOWNLOAD_URL = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
        private const val MODEL_SIZE_BYTES = 1_050_000_000L  // ~1000MB
        private const val MIN_VALID_GGUF_BYTES = 100_000_000L
        private val MODEL_FILENAME_PREFERENCE = listOf(
            DEFAULT_MODEL_FILENAME,
            "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
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
     * Devuelve el archivo de modelo GGUF a usar.
     */
    private fun getModelFile(context: Context): File? {
        val dir = context.getExternalFilesDir(null) ?: return null

        for (name in MODEL_FILENAME_PREFERENCE) {
            val preferred = File(dir, name)
            if (preferred.exists() && preferred.length() > MIN_VALID_GGUF_BYTES) return preferred
        }

        val candidates = dir.listFiles { f -> 
            f.isFile && f.name.endsWith(".gguf", ignoreCase = true) && f.length() > MIN_VALID_GGUF_BYTES
        } ?: emptyArray()
        return candidates.maxByOrNull { it.length() }
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
    
    /**
     * Descarga el modelo GGUF automáticamente desde Hugging Face
     */
    suspend fun downloadModel(context: Context): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = context.getExternalFilesDir(null) 
                ?: return@withContext Result.failure(Exception("No se puede acceder al almacenamiento"))
            
            val modelFile = File(dir, DEFAULT_MODEL_FILENAME)
            val tempFile = File(dir, "${DEFAULT_MODEL_FILENAME}.tmp")
            
            if (modelFile.exists() && modelFile.length() > MIN_VALID_GGUF_BYTES) {
                Log.i(TAG, "Modelo ya existe: ${modelFile.absolutePath}")
                return@withContext Result.success(modelFile)
            }
            
            Log.i(TAG, "Descargando modelo desde: $MODEL_DOWNLOAD_URL")
            
            val url = URL(MODEL_DOWNLOAD_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "AgroChat/1.0")
            
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
            
            if (tempFile.exists() && tempFile.length() > MIN_VALID_GGUF_BYTES) {
                modelFile.delete()
                tempFile.renameTo(modelFile)
                Log.i(TAG, "✅ Modelo descargado: ${modelFile.absolutePath}")
                Result.success(modelFile)
            } else {
                tempFile.delete()
                Result.failure(Exception("Descarga incompleta"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando modelo: ${e.message}", e)
            Result.failure(e)
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
        systemPrompt: String = "Eres FarmifAI, un asistente agricola experto. Responde en espanol de forma clara, cercana y practica para agricultor. Nunca menciones terminos internos como KB, RAG, LLM, contexto de referencia, modelo o sistema."
    ): Result<String> {
        val prompt = buildAgriPrompt(userQuery, contextFromKB, maxContextLength, systemPrompt)
        
        Log.d(TAG, "Prompt local (${detectModelFamily()}): ${prompt.length} chars, maxTokens: $maxTokens")
        
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
        onPartialResponse: suspend (String) -> Unit
    ): Result<String> {
        return try {
            val prompt = buildAgriPrompt(userQuery, contextFromKB, maxContextLength, systemPrompt)
            Log.d(TAG, "Prompt streaming (${detectModelFamily()}): ${prompt.length} chars, maxTokens: $maxTokens")

            val raw = StringBuilder()
            var emittedChunks = 0
            llama.send(prompt, formatChat = false, maxTokens = maxTokens).collect { chunk ->
                if (chunk.isBlank()) return@collect
                raw.append(chunk)
                emittedChunks++

                // Evita saturar Compose actualizando en cada token.
                if (emittedChunks <= 6 || emittedChunks % 5 == 0 || chunk.contains('\n')) {
                    val partial = cleanResponse(raw.toString())
                    if (partial.length >= 5) {
                        onPartialResponse(partial)
                    }
                }
            }

            val finalResponse = cleanResponse(raw.toString())
            if (finalResponse.length >= 5) {
                onPartialResponse(finalResponse)
            }
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
        
        // Si quedó muy corta, dejar que capas superiores decidan fallback.
        if (cleaned.length < 5) {
            return cleaned
        }
        
        return cleaned
    }

    private fun detectModelFamily(): ModelFamily {
        val name = loadedModelName?.lowercase().orEmpty()
        return when {
            "qwen" in name -> ModelFamily.QWEN
            "llama" in name -> ModelFamily.LLAMA3
            else -> ModelFamily.GENERIC
        }
    }

    private fun buildPromptForCurrentModel(systemPrompt: String, userMessage: String): String {
        return when (detectModelFamily()) {
            ModelFamily.QWEN -> {
                """<|im_start|>system
$systemPrompt
<|im_end|>
<|im_start|>user
$userMessage
<|im_end|>
<|im_start|>assistant
"""
            }
            ModelFamily.LLAMA3 -> {
                """<|begin_of_text|><|start_header_id|>system<|end_header_id|>

$systemPrompt<|eot_id|><|start_header_id|>user<|end_header_id|>

$userMessage<|eot_id|><|start_header_id|>assistant<|end_header_id|>

"""
            }
            ModelFamily.GENERIC -> {
                """Sistema:
$systemPrompt

Usuario:
$userMessage

Asistente:
"""
            }
        }
    }

    private fun buildAgriPrompt(
        userQuery: String,
        contextFromKB: String?,
        maxContextLength: Int,
        systemPrompt: String
    ): String {
        val userMessage: String = if (!contextFromKB.isNullOrBlank()) {
            val shortContext = truncateContextPreservingKb(contextFromKB, maxContextLength)
            """Usa solo estos datos para responder con precision.
Empieza con una recomendacion clara y directa.
Reformula con tus propias palabras y evita copiar frases textuales de DATOS DISPONIBLES.
Explica brevemente por que recomiendas cada paso.
Si faltan datos, pide maximo dos datos concretos en una sola pregunta al final.
No inventes informacion externa ni menciones terminos internos.

DATOS DISPONIBLES:
$shortContext

CONSULTA:
$userQuery
"""
        } else {
            userQuery
        }
        return buildPromptForCurrentModel(systemPrompt, userMessage)
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
     * Trunca contexto priorizando la sección de KB sobre historial para evitar perder
     * información clave al cortar por longitud.
     */
    private fun truncateContextPreservingKb(context: String, maxLen: Int): String {
        if (context.length <= maxLen) return context

        val kbMarker = "=== KB ==="
        val historyMarker = "=== HISTORIAL ==="
        val kbIndex = context.indexOf(kbMarker)
        val historyIndex = context.indexOf(historyMarker)

        if (kbIndex < 0) {
            return context.take(maxLen)
        }

        val kbSection = if (historyIndex >= 0 && kbIndex < historyIndex) {
            context.substring(kbIndex, historyIndex).trim()
        } else {
            context.substring(kbIndex).trim()
        }

        val historySection = if (historyIndex >= 0) {
            if (historyIndex < kbIndex) {
                context.substring(historyIndex, kbIndex).trim()
            } else {
                context.substring(historyIndex).trim()
            }
        } else {
            ""
        }

        val kbBudget = (maxLen * 0.8f).toInt().coerceAtLeast(300)
        val historyBudget = (maxLen - kbBudget).coerceAtLeast(0)

        val kbTruncated = kbSection.take(kbBudget)
        val historyContent = historySection.removePrefix(historyMarker).trim()
        val historyTruncated = if (historyBudget > 0 && historyContent.isNotBlank()) {
            "\n\n$historyMarker\n${historyContent.take(historyBudget)}"
        } else {
            ""
        }

        return (kbTruncated + historyTruncated).take(maxLen)
    }
    
    /**
     * Descarga el modelo y libera recursos
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
