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
        private const val MODEL_FILENAME = "llama-3.2-1b-q4.gguf"
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
        val modelPath = getModelPath(context)
        return File(modelPath).exists()
    }
    
    /**
     * Obtiene la ruta del modelo
     */
    fun getModelPath(context: Context): String {
        return File(context.getExternalFilesDir(null), MODEL_FILENAME).absolutePath
    }
    
    /**
     * Obtiene el tamaño del modelo en MB
     */
    fun getModelSizeMB(context: Context): Long {
        val modelFile = File(getModelPath(context))
        return if (modelFile.exists()) {
            modelFile.length() / (1024 * 1024)
        } else {
            0L
        }
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
            val modelPath = getModelPath(context)
            Log.i(TAG, "Cargando modelo desde: $modelPath")
            
            if (!File(modelPath).exists()) {
                return Result.failure(Exception("Modelo no encontrado en: $modelPath"))
            }
            
            llama.load(modelPath)
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
