package edu.unicauca.app.agrochat.llm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * GroqService - Servicio para consultas LLM usando Groq API (gratuito)
 * 
 * Groq ofrece:
 * - 30 requests por minuto gratis
 * - Modelos: llama-3.3-70b-versatile, llama-3.1-8b-instant, etc.
 * - Muy rápido (inferencia en chip LPU)
 * 
 * Obtén tu API key gratis en: https://console.groq.com/
 */
class GroqService(private val context: Context) {
    
    companion object {
        private const val TAG = "GroqService"
        private const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
        
        // Modelo recomendado (rápido y bueno en español)
        private const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
        
        // Prompt del sistema para contexto agrícola
        private const val SYSTEM_PROMPT = """Eres AgroChat, un asistente agrícola experto y amigable. 
Tu rol es ayudar a agricultores con información práctica sobre:
- Cultivos y técnicas de siembra
- Control de plagas y enfermedades
- Fertilización y nutrición de plantas
- Riego y manejo del agua
- Cosecha y postcosecha

Directrices:
1. Responde de forma clara, concisa y práctica
2. Usa un lenguaje sencillo que cualquier agricultor pueda entender
3. Si no sabes algo o no hay evidencia suficiente, admítelo honestamente y evita inventar
4. Prioriza soluciones orgánicas cuando sea posible
5. Da respuestas cortas (2-3 párrafos máximo) a menos que se pida más detalle
6. Responde siempre en español"""
    }
    
    // API Key - Se puede configurar dinámicamente
    private var apiKey: String? = null
    
    /**
     * Configura la API key de Groq
     */
    fun setApiKey(key: String) {
        apiKey = key.trim()
        Log.d(TAG, "API key configurada")
    }
    
    /**
     * Verifica si hay una API key configurada
     */
    fun hasApiKey(): Boolean = !apiKey.isNullOrBlank()
    
    /**
     * Verifica si hay conexión a internet
     */
    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Verifica si el servicio está disponible (tiene key y conexión)
     */
    fun isAvailable(): Boolean = hasApiKey() && isOnline()
    
    /**
     * Realiza una consulta al LLM de Groq
     * 
     * @param userMessage Pregunta del usuario
     * @param conversationHistory Historial de conversación opcional (pares user/assistant)
     * @return Respuesta del LLM o null si falla
     */
    suspend fun query(
        userMessage: String,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        
        if (!hasApiKey()) {
            return@withContext Result.failure(Exception("API key no configurada"))
        }
        
        if (!isOnline()) {
            return@withContext Result.failure(Exception("Sin conexión a internet"))
        }
        
        try {
            val requestBody = buildRequestBody(userMessage, conversationHistory)
            val response = makeRequest(requestBody)
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error en query: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Construye el cuerpo JSON de la petición
     */
    private fun buildRequestBody(
        userMessage: String,
        conversationHistory: List<Pair<String, String>>
    ): JSONObject {
        val messages = JSONArray()
        
        // 1. System prompt
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", SYSTEM_PROMPT)
        })
        
        // 2. Historial de conversación (últimos 5 intercambios para no exceder contexto)
        conversationHistory.takeLast(5).forEach { (user, assistant) ->
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", user)
            })
            messages.put(JSONObject().apply {
                put("role", "assistant")
                put("content", assistant)
            })
        }
        
        // 3. Mensaje actual del usuario
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })
        
        return JSONObject().apply {
            put("model", DEFAULT_MODEL)
            put("messages", messages)
            put("temperature", 0.2)
            put("max_tokens", 500)  // Respuestas concisas
            put("top_p", 0.85)
        }
    }
    
    /**
     * Realiza la petición HTTP a Groq
     */
    private fun makeRequest(requestBody: JSONObject): String {
        val url = URL(GROQ_API_URL)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 60000
            }
            
            // Enviar request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }
            
            // Leer respuesta
            val responseCode = connection.responseCode
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Error HTTP $responseCode: $errorStream")
                
                when (responseCode) {
                    401 -> throw Exception("API key inválida")
                    429 -> throw Exception("Límite de requests excedido. Intenta en un momento.")
                    500, 502, 503 -> throw Exception("Servidor Groq no disponible. Intenta luego.")
                    else -> throw Exception("Error de conexión ($responseCode)")
                }
            }
            
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            return parseResponse(responseText)
            
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Parsea la respuesta JSON de Groq
     */
    private fun parseResponse(responseText: String): String {
        val json = JSONObject(responseText)
        val choices = json.getJSONArray("choices")
        
        if (choices.length() == 0) {
            throw Exception("Respuesta vacía del servidor")
        }
        
        val message = choices.getJSONObject(0).getJSONObject("message")
        val content = message.getString("content")
        
        // Log de uso (para monitoreo)
        if (json.has("usage")) {
            val usage = json.getJSONObject("usage")
            Log.d(TAG, "Tokens: prompt=${usage.optInt("prompt_tokens")}, " +
                      "completion=${usage.optInt("completion_tokens")}, " +
                      "total=${usage.optInt("total_tokens")}")
        }
        
        return content.trim()
    }
}
