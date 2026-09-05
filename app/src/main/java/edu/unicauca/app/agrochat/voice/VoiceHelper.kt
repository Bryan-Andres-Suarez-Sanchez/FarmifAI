package edu.unicauca.app.agrochat.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.Locale

/**
 * VoiceHelper - Maneja Speech-to-Text (Vosk) y Text-to-Speech para AgroChat
 * 
 * Funciona 100% OFFLINE sin Google:
 * - STT: Vosk (modelo español provisionado localmente)
 * - TTS: Motor del sistema (offline)
 */
class VoiceHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceHelper"
        // Modelo pequeño de español para Vosk (incluido en assets)
        private const val VOSK_MODEL = "model-es-small"
    }
    
    // Text-to-Speech (sistema)
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    
    // Speech-to-Text (Vosk - offline)
    private var voskModel: Model? = null
    private var speechService: SpeechService? = null
    private var isVoskReady = false
    private var isListening = false
    private var recognitionSession = 0L
    private var suppressRecognitionResults = false
    
    // Modo conversación (auto-escuchar después de hablar)
    private var conversationMode = false
    
    // Callbacks públicos para el Activity
    var onResult: ((String) -> Unit)? = null
    var onPartialResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onListeningStateChanged: ((Boolean) -> Unit)? = null
    var onSpeakingStateChanged: ((Boolean) -> Unit)? = null
    var onModelStatus: ((String) -> Unit)? = null
    
    /**
     * Inicializa el sistema de voz (Vosk + TTS)
     */
    fun initialize() {
        Log.d(TAG, "Inicializando VoiceHelper con Vosk...")
        
        // 1. Inicializar Text-to-Speech (sistema)
        initializeTts()
        
        // 2. Inicializar Vosk (offline STT)
        initializeVosk()
    }
    
    /**
     * Inicializa Text-to-Speech del sistema
     */
    private fun initializeTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Configurar idioma español
                val result = tts?.setLanguage(Locale("es", "ES"))
                
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val resultLatam = tts?.setLanguage(Locale("es", "MX"))
                    if (resultLatam == TextToSpeech.LANG_MISSING_DATA || resultLatam == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale("es"))
                    }
                }
                
                // Configurar para voz más natural
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(0.95f)
                
                // Seleccionar mejor voz offline
                selectBestVoice()
                
                // Listener para estado de habla
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        suppressRecognitionResults = true
                        onSpeakingStateChanged?.invoke(true)
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        suppressRecognitionResults = false
                        onSpeakingStateChanged?.invoke(false)
                    }
                    
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        suppressRecognitionResults = false
                        onSpeakingStateChanged?.invoke(false)
                    }
                    
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        suppressRecognitionResults = false
                        onSpeakingStateChanged?.invoke(false)
                    }
                })
                
                isTtsReady = true
                Log.i(TAG, "TTS listo")
            } else {
                Log.e(TAG, "Error al inicializar TTS")
            }
        }
    }
    
    /**
     * Inicializa Vosk para reconocimiento offline
     */
    private fun initializeVosk() {
        onModelStatus?.invoke("Preparando modelo de voz...")
        Log.d(TAG, "Cargando modelo Vosk: $VOSK_MODEL")
        
        // Copiar modelo desde assets o desde files/models a files en un hilo separado.
        Thread {
            try {
                val modelDir = java.io.File(context.filesDir, "vosk-model")
                
                // Si ya existe el modelo, cargarlo directamente
                if (modelDir.exists() && java.io.File(modelDir, "am/final.mdl").exists()) {
                    Log.i(TAG, "Modelo ya existe, cargando...")
                    loadVoskModel(modelDir)
                    return@Thread
                }
                
                val localModelDir = java.io.File(context.filesDir, "models/$VOSK_MODEL")
                if (localModelDir.exists() && java.io.File(localModelDir, "am/final.mdl").exists()) {
                    Log.i(TAG, "Usando modelo local desde: ${localModelDir.absolutePath}")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onModelStatus?.invoke("Preparando modelo de voz...")
                    }
                    copyDirectory(localModelDir, modelDir)
                    loadVoskModel(modelDir)
                    return@Thread
                }
                
                // Fallback: copy from assets (if bundled)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onModelStatus?.invoke("Extrayendo modelo de voz...")
                }
                
                try {
                    copyAssetFolder(VOSK_MODEL, modelDir)
                    loadVoskModel(modelDir)
                } catch (e: Exception) {
                    Log.e(TAG, "No se encontró modelo en assets ni almacenamiento local: ${e.message}")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onModelStatus?.invoke("Modelo de voz no disponible")
                        onError?.invoke("Modelo de voz no disponible. Provisiona el modelo localmente.")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando Vosk: ${e.message}")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onModelStatus?.invoke("Error: ${e.message}")
                    onError?.invoke("Error al cargar modelo de voz: ${e.message}")
                }
            }
        }.start()
    }
    
    /**
     * Copy a directory recursively
     */
    private fun copyDirectory(src: java.io.File, dest: java.io.File) {
        dest.mkdirs()
        src.listFiles()?.forEach { file ->
            val destFile = java.io.File(dest, file.name)
            if (file.isDirectory) {
                copyDirectory(file, destFile)
            } else {
                file.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
    
    /**
     * Carga el modelo Vosk desde un directorio
     */
    private fun loadVoskModel(modelDir: java.io.File) {
        try {
            voskModel = Model(modelDir.absolutePath)
            isVoskReady = true
            Log.i(TAG, "Vosk listo desde: ${modelDir.absolutePath}")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onModelStatus?.invoke("Listo para escuchar")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creando modelo Vosk: ${e.message}")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onError?.invoke("Error inicializando reconocimiento de voz")
            }
        }
    }
    
    /**
     * Copia una carpeta desde assets al sistema de archivos
     */
    private fun copyAssetFolder(assetPath: String, destDir: java.io.File) {
        val assetManager = context.assets
        
        destDir.mkdirs()
        
        val files = assetManager.list(assetPath) ?: return
        
        for (file in files) {
            val srcPath = "$assetPath/$file"
            val destFile = java.io.File(destDir, file)
            
            try {
                // Intentar abrir como archivo
                val inputStream = assetManager.open(srcPath)
                destFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
            } catch (e: Exception) {
                // Es un directorio, copiar recursivamente
                copyAssetFolder(srcPath, destFile)
            }
        }
    }
    
    /**
     * Selecciona la mejor voz offline disponible
     */
    private fun selectBestVoice() {
        try {
            val voices = tts?.voices ?: return
            
            val offlineSpanishVoices = voices.filter { voice ->
                voice.locale.language == "es" && !voice.isNetworkConnectionRequired
            }
            
            Log.d(TAG, "Voces offline en español: ${offlineSpanishVoices.size}")
            
            val bestVoice = offlineSpanishVoices
                .sortedByDescending { it.quality }
                .firstOrNull()
            
            bestVoice?.let {
                tts?.voice = it
                Log.i(TAG, "Voz seleccionada: ${it.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error seleccionando voz: ${e.message}")
        }
    }
    
    /**
     * Verifica si Vosk está listo
     */
    fun isReady(): Boolean = isVoskReady && voskModel != null
    
    /**
     * Activa/desactiva modo conversación
     */
    fun setConversationMode(enabled: Boolean) {
        conversationMode = false
        Log.d(TAG, "Escucha automática deshabilitada (solicitado=$enabled)")
    }
    
    fun isConversationModeEnabled(): Boolean = conversationMode
    
    /**
     * Inicia reconocimiento de voz con Vosk (offline)
     */
    fun startListening() {
        if (!isVoskReady || voskModel == null) {
            onError?.invoke("Modelo de voz cargando. Espera un momento...")
            return
        }
        
        if (isListening) {
            Log.w(TAG, "Ya está escuchando")
            return
        }
        
        // Detener TTS si está hablando
        stopSpeaking()
        suppressRecognitionResults = false
        
        try {
            val session = ++recognitionSession
            val recognizer = Recognizer(voskModel, 16000.0f)
            
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(object : RecognitionListener {
                
                override fun onPartialResult(hypothesis: String?) {
                    if (!isActiveRecognition(session)) return
                    hypothesis?.let {
                        try {
                            val json = JSONObject(it)
                            val partial = json.optString("partial", "")
                            if (partial.isNotEmpty()) {
                                Log.d(TAG, "Parcial: $partial")
                                onPartialResult?.invoke(partial)
                            }
                        } catch (e: Exception) {
                            // Ignorar errores de parsing
                        }
                    }
                }
                
                override fun onResult(hypothesis: String?) {
                    processResultOnce(hypothesis, session)
                }
                
                override fun onFinalResult(hypothesis: String?) {
                    if (!processResultOnce(hypothesis, session) && isActiveRecognition(session)) {
                        finishRecognition(session)
                    }
                }
                
                override fun onError(exception: Exception?) {
                    if (session != recognitionSession) return
                    Log.e(TAG, "Error Vosk: ${exception?.message}")
                    finishRecognition(session)
                    onError?.invoke("Error de reconocimiento: ${exception?.message ?: "desconocido"}")
                }
                
                override fun onTimeout() {
                    if (session != recognitionSession) return
                    Log.d(TAG, "Timeout")
                    finishRecognition(session)
                    onError?.invoke("No escuché nada. Intenta de nuevo.")
                }
            })
            
            isListening = true
            onListeningStateChanged?.invoke(true)
            Log.d(TAG, "Escuchando con Vosk (offline)...")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar Vosk: ${e.message}")
            onError?.invoke("Error al iniciar el micrófono")
            isListening = false
            onListeningStateChanged?.invoke(false)
        }
    }
    
    /**
     * Procesa el resultado de Vosk
     */
    private fun isActiveRecognition(session: Long): Boolean =
        session == recognitionSession && isListening && !suppressRecognitionResults && !isSpeaking()

    private fun processResultOnce(hypothesis: String?, session: Long): Boolean {
        if (!isActiveRecognition(session) || hypothesis == null) return false
        return try {
            val text = JSONObject(hypothesis).optString("text", "").trim()
            if (text.isEmpty()) {
                false
            } else {
                finishRecognition(session)
                Log.i(TAG, "Resultado único: $text")
                onResult?.invoke(text)
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing: ${e.message}")
            false
        }
    }

    private fun finishRecognition(session: Long) {
        if (session != recognitionSession) return
        recognitionSession += 1
        isListening = false
        val service = speechService
        speechService = null
        onListeningStateChanged?.invoke(false)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                service?.stop()
                service?.shutdown()
            } catch (e: Exception) {
                Log.w(TAG, "Error cerrando sesión Vosk: ${e.message}")
            }
        }
    }
    
    /**
     * Detiene el reconocimiento
     */
    fun stopListening() {
        recognitionSession += 1
        isListening = false
        val service = speechService
        speechService = null
        try {
            service?.stop()
            service?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener: ${e.message}")
        }
        onListeningStateChanged?.invoke(false)
    }
    
    /**
     * Verifica si está escuchando
     */
    fun isCurrentlyListening(): Boolean = isListening
    
    /**
     * Habla el texto dado
     */
    fun speak(text: String) {
        if (!isTtsReady) {
            Log.w(TAG, "TTS no está listo")
            return
        }
        
        // IMPORTANTE: Detener reconocimiento antes de hablar para evitar que se escuche a sí mismo
        stopSpeaking()
        suppressRecognitionResults = true
        stopListening()
        
        val cleanText = cleanTextForSpeech(text)
        if (cleanText.isBlank()) return
        
        val utteranceId = "agrochat_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        
        Log.d(TAG, "Hablando (Vosk pausado): ${cleanText.take(50)}...")
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }
    
    /**
     * Detiene el habla
     */
    fun stopSpeaking() {
        tts?.stop()
        suppressRecognitionResults = false
        onSpeakingStateChanged?.invoke(false)
    }
    
    /**
     * Verifica si está hablando
     */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true
    
    /**
     * Limpia texto para pronunciación
     */
    private fun cleanTextForSpeech(text: String): String {
        return text
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            .replace("**", "")
            .replace("*", "")
            .replace("•", ", ")
            .replace("→", ", ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    /**
     * Libera recursos
     */
    fun release() {
        Log.d(TAG, "Liberando recursos")
        stopListening()
        stopSpeaking()
        
        try {
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando speechService: ${e.message}")
        }
        speechService = null
        
        tts?.shutdown()
        tts = null
        
        isVoskReady = false
        isTtsReady = false
    }
}
