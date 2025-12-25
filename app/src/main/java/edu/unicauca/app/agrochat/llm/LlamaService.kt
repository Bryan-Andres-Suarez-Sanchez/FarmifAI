package edu.unicauca.app.agrochat.llm

import android.content.Context
import android.util.Log
import android.llama.cpp.LLamaAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
        // Nombre del modelo a descargar
        private const val DEFAULT_MODEL_FILENAME = "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        private const val MAX_TOKENS = 150  // Tokens para respuestas más extensas
        
        // URL de descarga automática desde Hugging Face
        private const val MODEL_DOWNLOAD_URL = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        private const val MODEL_SIZE_BYTES = 780_000_000L  // ~750MB
        
        @Volatile
        private var instance: LlamaService? = null
        
        fun getInstance(): LlamaService {
            return instance ?: synchronized(this) {
                instance ?: LlamaService().also { instance = it }
            }
        }
    }
    
    private val llama: LLamaAndroid = LLamaAndroid.instance()
    
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

        val preferred = File(dir, DEFAULT_MODEL_FILENAME)
        if (preferred.exists() && preferred.length() > 100_000_000) return preferred

        val candidates = dir.listFiles { f -> 
            f.isFile && f.name.endsWith(".gguf", ignoreCase = true) && f.length() > 100_000_000 
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
            
            if (modelFile.exists() && modelFile.length() > 100_000_000) {
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
            
            if (tempFile.exists() && tempFile.length() > 100_000_000) {
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
        maxContextLength: Int = 200,
        systemPrompt: String = "Eres FarmifAI, un asistente agrícola experto. Responde de forma clara, útil y concisa en español. Si tienes información de contexto, úsala para dar una respuesta precisa."
    ): Result<String> {
        
        // User message: pregunta + contexto opcional
        val userMessage: String = if (!contextFromKB.isNullOrBlank()) {
            // Truncar contexto según configuración
            val shortContext = if (contextFromKB.length > maxContextLength) {
                contextFromKB.take(maxContextLength)
            } else {
                contextFromKB
            }
            "Contexto: $shortContext\n\nPregunta: $userQuery"
        } else {
            userQuery
        }
        
        // Formato Llama 3.2 Chat
        val prompt = """<|begin_of_text|><|start_header_id|>system<|end_header_id|>

$systemPrompt<|eot_id|><|start_header_id|>user<|end_header_id|}

$userMessage<|eot_id|><|start_header_id|>assistant<|end_header_id|>

"""
        
        Log.d(TAG, "Prompt Llama3: ${prompt.length} chars, maxTokens: $maxTokens")
        
        // formatChat = false porque ya formateamos manualmente
        val result = generateCompleteRaw(prompt, maxTokens)
        
        return result.map { response -> cleanResponse(response) }
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
        
        // Tokens especiales de Llama 3
        val specialTokens = listOf(
            "<|begin_of_text|>", "<|end_of_text|>",
            "<|start_header_id|>", "<|end_header_id|>",
            "<|eot_id|>", "<|eom_id|>",
            "system", "user", "assistant"
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
        
        // Eliminar líneas que parecen ser parte del prompt repetido
        val lines = cleaned.lines().filter { line ->
            val lower = line.lowercase().trim()
            !lower.startsWith("info:") &&
            !lower.startsWith("contexto:") &&
            !lower.startsWith("pregunta:") &&
            !lower.startsWith("respuesta:") &&
            !lower.startsWith("respuesta breve:") &&
            !lower.contains("soy un cultivo") &&
            !lower.matches(Regex("^\\d+\\).*"))  // Eliminar "1) ..." "2) ..."
        }
        cleaned = lines.joinToString("\n")
        
        // Limpiar espacios extras
        cleaned = cleaned.trim()
        
        // Si quedó muy corta o vacía, devolver mensaje genérico
        if (cleaned.length < 5) {
            return "Puedo ayudarte con información sobre cultivos, plagas, riego y más. ¿Qué te gustaría saber?"
        }
        
        return cleaned
    }
    
    /**
     * Descarga el modelo y libera recursos
     */
    suspend fun unload() {
        try {
            llama.unload()
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando recursos", e)
        }
    }
    
    /**
     * Obtiene información del sistema
     */
    suspend fun getSystemInfo(): String = llama.getSystemInfo()
}
