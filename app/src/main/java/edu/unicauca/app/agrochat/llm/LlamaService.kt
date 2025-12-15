package edu.unicauca.app.agrochat.llm

import android.content.Context
import android.util.Log
import android.llama.cpp.LLamaAndroid
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * LlamaService - Servicio de LLM local usando llama.cpp para inferencia offline
 * Permite respuestas inteligentes sin conexión usando modelos GGUF
 */
class LlamaService private constructor() {
    
    companion object {
        private const val TAG = "LlamaService"
        // Nombre recomendado (pero la app también autodetecta cualquier .gguf en el directorio)
        private const val DEFAULT_MODEL_FILENAME = "llama-3.2-1b-q4.gguf"
        private const val MAX_TOKENS = 350  // Tokens máximos de generación (aumentado para respuestas completas)
        
        @Volatile
        private var instance: LlamaService? = null
        
        fun getInstance(): LlamaService {
            return instance ?: synchronized(this) {
                instance ?: LlamaService().also { instance = it }
            }
        }
    }
    
    private val llama: LLamaAndroid = LLamaAndroid.instance()
    
    /**
     * Verifica si el modelo está disponible en el almacenamiento
     */
    fun isModelAvailable(context: Context): Boolean {
        return getModelFile(context) != null
    }

    /**
     * Devuelve el archivo de modelo GGUF a usar.
     * - Primero intenta el nombre por defecto.
     * - Si no existe, busca cualquier archivo `.gguf` en el directorio de la app y elige el más grande.
     */
    private fun getModelFile(context: Context): File? {
        val dir = context.getExternalFilesDir(null) ?: return null

        val preferred = File(dir, DEFAULT_MODEL_FILENAME)
        if (preferred.exists()) return preferred

        val candidates = dir.listFiles { f -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) } ?: emptyArray()
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
     * 
     * @param userQuery La pregunta del usuario
     * @param contextFromKB Contexto combinado de la base de conocimiento (múltiples entradas)
     * @param maxTokens Máximo de tokens a generar
     */
    suspend fun generateAgriResponse(
        userQuery: String,
        contextFromKB: String? = null,
        maxTokens: Int = MAX_TOKENS
    ): Result<String> {
        
        // Prompt mejorado para que Llama USE la información del JSON
        val systemPrompt: String
        val contextPart: String
        
        if (!contextFromKB.isNullOrBlank()) {
            // CON contexto de la base de conocimiento - debe usarlo
            systemPrompt = """<|start_header_id|>system<|end_header_id|}

Eres AgroChat, un asistente agrícola experto colombiano. 

INSTRUCCIONES IMPORTANTES:
1. USA la información de la BASE DE CONOCIMIENTO para responder
2. Expande y mejora esa información con explicaciones adicionales
3. Responde siempre en español de forma clara y práctica
4. Si la base de conocimiento tiene datos específicos (cantidades, tiempos, pasos), INCLÚYELOS en tu respuesta
<|eot_id|>"""
            
            contextPart = """<|start_header_id|>system<|end_header_id|>

BASE DE CONOCIMIENTO (usa esta información para responder):
$contextFromKB
<|eot_id|>"""
        } else {
            // SIN contexto - responder con conocimiento general
            systemPrompt = """<|start_header_id|>system<|end_header_id|>

Eres AgroChat, un asistente agrícola experto colombiano.
Responde en español de forma clara, práctica y concisa.
<|eot_id|>"""
            
            contextPart = ""
        }
        
        val userPart = """<|start_header_id|>user<|end_header_id|>

$userQuery<|eot_id|><|start_header_id|>assistant<|end_header_id|>

"""
        
        val fullPrompt = systemPrompt + contextPart + userPart
        
        Log.d(TAG, "RAG Prompt length: ${fullPrompt.length} chars")
        Log.d(TAG, "Context from KB: ${contextFromKB?.take(200) ?: "null"}")
        
        val result = generateComplete(fullPrompt, maxTokens)
        
        // Log respuesta raw para debug
        result.onSuccess { raw ->
            Log.d(TAG, "Raw response (${raw.length} chars): ${raw.take(200)}")
        }
        
        // Limpiar respuesta de tokens especiales que podrían aparecer
        return result.map { response ->
            cleanResponse(response)
        }
    }
    
    /**
     * Limpia la respuesta de tokens especiales de Llama
     */
    private fun cleanResponse(response: String): String {
        var cleaned = response
        
        // Solo remover tokens especiales reales de Llama 3 (con delimitadores)
        val specialTokens = listOf(
            "<|begin_of_text|>",
            "<|end_of_text|>",
            "<|start_header_id|>",
            "<|end_header_id|>",
            "<|eot_id|>",
            "<|eom_id|>"
        )
        
        // Primero, remover cualquier token especial al inicio
        for (token in specialTokens) {
            while (cleaned.startsWith(token)) {
                cleaned = cleaned.removePrefix(token).trimStart()
            }
        }
        
        // Luego, cortar en el primer token especial que aparezca (indica fin de respuesta)
        for (token in specialTokens) {
            val idx = cleaned.indexOf(token)
            if (idx > 0) {
                cleaned = cleaned.substring(0, idx)
                break
            }
        }
        
        // Limpiar espacios y saltos de línea extra al inicio y final
        cleaned = cleaned.trim()
        
        Log.d(TAG, "Cleaned response (${cleaned.length} chars): ${cleaned.take(150)}...")
        
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
