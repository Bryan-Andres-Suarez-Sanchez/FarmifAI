package edu.unicauca.app.agrochat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import edu.unicauca.app.agrochat.llm.GroqService
import edu.unicauca.app.agrochat.llm.LlamaService
import edu.unicauca.app.agrochat.mindspore.SemanticSearchHelper
import edu.unicauca.app.agrochat.models.ModelDownloadService
import edu.unicauca.app.agrochat.routing.KbFallbackComposer
import edu.unicauca.app.agrochat.routing.ResponseRoutingPolicy
import edu.unicauca.app.agrochat.ui.theme.AgroChatTheme
import edu.unicauca.app.agrochat.vision.CameraHelper
import edu.unicauca.app.agrochat.vision.DiseaseResult
import edu.unicauca.app.agrochat.vision.PlantDiseaseClassifier
import edu.unicauca.app.agrochat.voice.VoiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Sistema de logs en memoria para debugging
object AppLogger {
    private val logs = mutableListOf<String>()
    private val maxLogs = 200
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    fun log(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] $tag: $message"
        synchronized(logs) {
            logs.add(entry)
            if (logs.size > maxLogs) logs.removeAt(0)
        }
        Log.d(tag, message)
    }
    
    fun getLogs(): List<String> = synchronized(logs) { logs.toList() }
    fun clear() = synchronized(logs) { logs.clear() }
}

// Colores modernos para la app
object AgroColors {
    val Primary = Color(0xFF2E7D32)
    val PrimaryLight = Color(0xFF4CAF50)
    val Accent = Color(0xFF00C853)
    val Background = Color(0xFF0D1B0F)
    val Surface = Color(0xFF1A2E1C)
    val SurfaceLight = Color(0xFF2D4830)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB8C5B9)
    val MicActive = Color(0xFFFF5252)
    val GradientStart = Color(0xFF1B5E20)
    val GradientEnd = Color(0xFF004D40)
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val imageBitmap: Bitmap? = null,  // Para mensajes con imagen
    val diseaseResult: DiseaseResult? = null,  // Para resultados de diagnóstico
    val canContinue: Boolean = false  // Para mostrar botón "Continuar" en respuestas LLM
)

enum class AppMode { VOICE, CHAT, CAMERA }

// Estado de descarga para pantalla de bienvenida
data class DownloadItem(
    val name: String,
    val description: String,
    val sizeMB: Int,
    val status: DownloadItemStatus = DownloadItemStatus.PENDING,
    val progress: Int = 0
)

enum class DownloadItemStatus {
    PENDING, DOWNLOADING, COMPLETED, FAILED
}

private const val ONLINE_ONLY_VISIT_MODE = true

// Características de la app para mostrar durante la descarga
object AppFeatures {
    val features = listOf(
        "☁️ IA Online - Respuestas en tiempo real",
        "🌱 Asesoría agrícola personalizada",
        "📸 Diagnóstico visual de enfermedades",
        "🎯 Reconocimiento de voz en español",
        "🧠 Modelo cloud de alta capacidad",
        "🔍 Búsqueda semántica avanzada",
        "📊 Base de conocimiento agrícola",
        "🌿 Soporte para múltiples cultivos",
        "🐛 Control de plagas y enfermedades",
        "💧 Recomendaciones de riego"
    )
}

class MainActivity : ComponentActivity() {

    companion object {
        private const val LANGUAGE_PREFS = "farmifai_prefs"
        private const val LANGUAGE_KEY = "language"
        private val STRICT_TERMINAL_PARITY_MODE = false
        private const val PARITY_SIMILARITY_THRESHOLD = 0.45f
        private const val PARITY_KB_FAST_PATH_THRESHOLD = 0.70f
        private const val PARITY_CONTEXT_RELEVANCE_THRESHOLD = 0.50f
        private const val PARITY_CONTEXT_LENGTH = 1800
        private const val PARITY_CHAT_HISTORY_SIZE = 10
        private const val PARITY_MIN_MAX_TOKENS = 450
        private const val PARITY_SYSTEM_PROMPT =
            "Eres FarmifAI, un asistente agrícola experto. Si se proporciona contexto de KB, úsalo como fuente principal y no inventes datos fuera de esa base. Si falta un dato en la KB, dilo explícitamente."
        private const val SAFE_MIN_SIMILARITY_THRESHOLD = 0.25f
        private const val SAFE_MAX_SIMILARITY_THRESHOLD = 0.80f
        private const val SAFE_MIN_KB_FAST_PATH_THRESHOLD = 0.20f
        private const val SAFE_MAX_KB_FAST_PATH_THRESHOLD = 0.92f
        private const val SAFE_MIN_CONTEXT_RELEVANCE_THRESHOLD = 0.30f
        private const val SAFE_MAX_CONTEXT_RELEVANCE_THRESHOLD = 0.85f
        private const val MIN_SUPPORT_SCORE_FOR_GROUNDED = 0.55f
        private const val MIN_LEXICAL_COVERAGE_FOR_GROUNDED = 0.34f
        private const val MAX_UNKNOWN_RATIO_FOR_GROUNDED = 0.45f
        private const val KB_RETRIEVAL_MIN_SCORE = 0.15f
        private const val KB_RELATED_MIN_SCORE = 0.22f
        private const val KB_RELATED_MIN_SUPPORT = 0.18f
        private const val KB_RELATED_MIN_COVERAGE = 0.16f
        private const val KB_RELATED_MAX_UNKNOWN_RATIO = 0.92f
        
        fun setAppLocale(context: Context, languageCode: String): Context {
            val locale = Locale(languageCode)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences(LANGUAGE_PREFS, Context.MODE_PRIVATE)
        val langCode = prefs.getString(LANGUAGE_KEY, "es") ?: "es"
        super.attachBaseContext(setAppLocale(newBase, langCode))
    }

    private var uiStatus by mutableStateOf("Inicializando...")
    private var isModelReady by mutableStateOf(false)
    private var isProcessing by mutableStateOf(false)
    private var chatMessages = mutableStateListOf<ChatMessage>()
    private var lastResponse by mutableStateOf("")
    private var currentMode by mutableStateOf(AppMode.VOICE)
    private var semanticSearchHelper: SemanticSearchHelper? = null
    private var voiceHelper: VoiceHelper? = null
    private var groqService: GroqService? = null
    private var llamaService: LlamaService? = null
    private var isLlamaLoaded by mutableStateOf(false)
    private var isListening by mutableStateOf(false)
    private var hasAudioPermission by mutableStateOf(false)
    private var isOnlineMode by mutableStateOf(false)
    private var showSettingsDialog by mutableStateOf(false)
    private var isLlamaEnabled by mutableStateOf(false)  // En modo visita se mantiene desactivado
    private var llamaModelStatusText by mutableStateOf("Desactivado en modo visita online")
    private var llamaModelPathText by mutableStateOf("")
    private var isLlamaDownloading by mutableStateOf(false)  // Evitar descargas duplicadas
    private var llamaDownloadFailed by mutableStateOf(false)  // Para mostrar botón de reintentar
    // Pantalla de bienvenida/setup
    private var isFirstLaunch by mutableStateOf(false)
    private var showWelcomeScreen by mutableStateOf(false)
    private var downloadItems = mutableStateListOf<DownloadItem>()
    private var currentTipIndex by mutableStateOf(0)
    
    // Para el botón "Continuar"
    private var lastUserQuery by mutableStateOf("")
    private var lastContext by mutableStateOf<String?>(null)
    
    // Diagnóstico visual
    private var plantDiseaseClassifier: PlantDiseaseClassifier? = null
    private var cameraHelper: CameraHelper? = null
    private var isDiagnosticReady by mutableStateOf(false)
    private var hasCameraPermission by mutableStateOf(false)
    private var capturedBitmap by mutableStateOf<Bitmap?>(null)
    private var lastDiagnosis by mutableStateOf<DiseaseResult?>(null)
    
    // ===== CONFIGURACIÓN AVANZADA =====
    // Valores por defecto orientados a respuestas completas y grounding por KB
    private var advancedMaxTokens by mutableStateOf(450) // Slider max 800
    private var advancedSimilarityThreshold by mutableStateOf(0.45f)
    private var advancedKbFastPathThreshold by mutableStateOf(0.70f)
    private var advancedContextRelevanceThreshold by mutableStateOf(0.50f)
    private var advancedSystemPrompt by mutableStateOf(
        "Eres FarmifAI, un asistente agrícola experto. Si se proporciona contexto de KB, úsalo como fuente principal y no inventes datos fuera de esa base. Si falta un dato en la KB, dilo explícitamente."
    )
    private var advancedUseLlmForAll by mutableStateOf(false)  // Priorizar KB directa cuando haya match claro
    private var advancedContextLength by mutableStateOf(1800) // Slider max 3000
    private var advancedDetectGreetings by mutableStateOf(true)  // Detectar saludos para KB directa (activado)
    private var advancedChatHistoryEnabled by mutableStateOf(true)  // Usar historial del chat como contexto (activado)
    private var advancedChatHistorySize by mutableStateOf(10)  // Ventana 1..20 mensajes anteriores
    private var isDiagnosing by mutableStateOf(false)
    
    // Logs viewer
    private var showLogsDialog by mutableStateOf(false)
    
    // SharedPreferences keys
    private val PREFS_NAME = "agrochat_prefs"
    private val KEY_GROQ_API = "groq_api_key"
    private val KEY_LLAMA_ENABLED = "llama_enabled"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            initializeVoice()
        } else {
            Toast.makeText(this, "Se necesita permiso de micrófono", Toast.LENGTH_LONG).show()
        }
    }
    
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            currentMode = AppMode.CAMERA
        } else {
            Toast.makeText(this, "Se necesita permiso de cámara para diagnóstico visual", Toast.LENGTH_LONG).show()
        }
    }
    
    // Launcher para seleccionar imagen de galería
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            try {
                val inputStream = contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    processCapture(bitmap)
                    currentMode = AppMode.CAMERA
                } else {
                    Toast.makeText(this, "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error cargando imagen: ${e.message}", e)
                Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hasAudioPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        // Cargar preferencias
        loadPreferences()
        
        // Verificar si es primera instalación (no hay modelos descargados)
        checkFirstLaunch()
        
        // Inicializar Groq
        initializeGroq()

        // Si es primera instalación, mostrar pantalla de bienvenida
        // Si no, iniciar normalmente
        if (showWelcomeScreen) {
            lifecycleScope.launch {
                startSequentialDownloads()
            }
        } else {
            // Descargar (si es necesario) modelos MindSpore y luego inicializar
            lifecycleScope.launch {
                ensureMindSporeModelsAvailable()
                initializeDiagnostic()
                initializeSemanticSearch()
                if (hasAudioPermission) {
                    initializeVoice()
                }
            }
        }

        setContent {
            AgroChatTheme {
                // Mostrar pantalla de bienvenida o app principal
                if (showWelcomeScreen) {
                    WelcomeDownloadScreen(
                        downloadItems = downloadItems,
                        currentTipIndex = currentTipIndex
                    )
                } else {
                    AgroChatApp(
                        currentMode = currentMode,
                        messages = chatMessages,
                        lastResponse = lastResponse,
                        statusMessage = uiStatus,
                        isModelReady = isModelReady,
                        isProcessing = isProcessing,
                        isListening = isListening,
                        isOnlineMode = isOnlineMode,
                        isLlamaEnabled = isLlamaEnabled,
                        isLlamaLoaded = isLlamaLoaded,
                        llamaModelStatusText = llamaModelStatusText,
                        llamaModelPathText = llamaModelPathText,
                        isLlamaDownloading = isLlamaDownloading,
                        llamaDownloadFailed = llamaDownloadFailed,
                        showSettingsDialog = showSettingsDialog,
                        isDiagnosticReady = isDiagnosticReady,
                        isDiagnosing = isDiagnosing,
                        capturedBitmap = capturedBitmap,
                        lastDiagnosis = lastDiagnosis,
                        onSendMessage = { sendMessage(it) },
                        onMicClick = { handleMicClick() },
                        onModeChange = { handleModeChange(it) },
                        onSettingsClick = { showSettingsDialog = true },
                        onDismissSettings = { showSettingsDialog = false },
                        onSaveApiKey = { key -> saveGroqApiKey(key) },
                        onToggleLlama = { enabled -> toggleLlama(enabled) },
                        onRetryLlamaDownload = { downloadAndLoadLlama() },
                        onCaptureImage = { bitmap -> processCapture(bitmap) },
                        onClearCapture = { clearCapture() },
                        onDiagnosisToChat = { result -> diagnosisToChat(result) },
                        onOpenGallery = { openGallery() },
                        showLogsDialog = showLogsDialog,
                        onShowLogs = { showLogsDialog = true },
                        onDismissLogs = { showLogsDialog = false },
                        // Configuración avanzada
                        advancedMaxTokens = advancedMaxTokens,
                        advancedSimilarityThreshold = advancedSimilarityThreshold,
                        advancedKbFastPathThreshold = advancedKbFastPathThreshold,
                        advancedContextRelevanceThreshold = advancedContextRelevanceThreshold,
                        advancedSystemPrompt = advancedSystemPrompt,
                        advancedUseLlmForAll = advancedUseLlmForAll,
                        advancedContextLength = advancedContextLength,
                        advancedDetectGreetings = advancedDetectGreetings,
                        advancedChatHistoryEnabled = advancedChatHistoryEnabled,
                        advancedChatHistorySize = advancedChatHistorySize,
                        onSaveAdvancedSettings = { maxTok, simThresh, kbThresh, ctxRelThresh, sysPrompt, llmAll, ctxLen, detectGreet, chatHistEnabled, chatHistSize ->
                            advancedMaxTokens = maxTok.coerceIn(250, 900)
                            advancedSimilarityThreshold = simThresh.coerceIn(SAFE_MIN_SIMILARITY_THRESHOLD, SAFE_MAX_SIMILARITY_THRESHOLD)
                            advancedKbFastPathThreshold = kbThresh.coerceIn(SAFE_MIN_KB_FAST_PATH_THRESHOLD, SAFE_MAX_KB_FAST_PATH_THRESHOLD)
                            advancedContextRelevanceThreshold = ctxRelThresh.coerceIn(SAFE_MIN_CONTEXT_RELEVANCE_THRESHOLD, SAFE_MAX_CONTEXT_RELEVANCE_THRESHOLD)
                            advancedSystemPrompt = sysPrompt
                            advancedUseLlmForAll = llmAll
                            advancedContextLength = ctxLen.coerceIn(300, 3000)
                            advancedDetectGreetings = detectGreet
                            advancedChatHistoryEnabled = chatHistEnabled
                            advancedChatHistorySize = chatHistSize.coerceIn(1, 20)
                            saveAdvancedPreferences()
                            Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
    
    /**
     * Verifica si es la primera instalación (sin modelos descargados)
     */
    private fun checkFirstLaunch() {
        val service = ModelDownloadService.getInstance()
        
        // Es primera instalación si faltan modelos MindSpore
        val needsMindSpore = !service.areAllModelsAvailable(applicationContext)
        
        isFirstLaunch = needsMindSpore
        showWelcomeScreen = isFirstLaunch
        
        if (isFirstLaunch) {
            AppLogger.log("MainActivity", "Primera instalación detectada - mostrando pantalla de bienvenida")
            // Preparar lista de descargas
            prepareDownloadItems()
        }
    }
    
    /**
     * Prepara la lista de items a descargar para la pantalla de bienvenida
     */
    private fun prepareDownloadItems() {
        downloadItems.clear()
        
        val service = ModelDownloadService.getInstance()
        
        // Modelos MindSpore agrupados
        if (!service.areAllModelsAvailable(applicationContext)) {
            val totalMB = service.getTotalDownloadSizeMB(applicationContext)
            downloadItems.add(DownloadItem(
                name = "🧠 Motor de IA",
                description = "Modelo de búsqueda semántica y diagnóstico",
                sizeMB = totalMB
            ))
        }
        
        // Modelo Vosk (speech recognition) - incluido en MindSpore downloads
        // Ya está incluido arriba
    }
    
    /**
     * Inicia las descargas de forma secuencial con actualizaciones visuales
     */
    private suspend fun startSequentialDownloads() {
        AppLogger.log("MainActivity", "Iniciando descargas secuenciales...")
        
        // Iniciar rotación de características
        lifecycleScope.launch {
            while (showWelcomeScreen) {
                kotlinx.coroutines.delay(4000)  // Cambiar cada 4 segundos
                currentTipIndex = (currentTipIndex + 1) % AppFeatures.features.size
            }
        }
        
        var allSuccess = true
        
        // Descargar cada item secuencialmente
        for (index in downloadItems.indices) {
            val item = downloadItems[index]
            
            // Actualizar estado a "descargando"
            downloadItems[index] = item.copy(status = DownloadItemStatus.DOWNLOADING, progress = 0)
            
            val success = when {
                item.name.contains("Motor de IA") -> downloadMindSporeModels(index)
                else -> true
            }
            
            if (success) {
                downloadItems[index] = downloadItems[index].copy(
                    status = DownloadItemStatus.COMPLETED,
                    progress = 100
                )
            } else {
                downloadItems[index] = downloadItems[index].copy(
                    status = DownloadItemStatus.FAILED
                )
                allSuccess = false
            }
        }
        
        // Esperar un momento para mostrar el estado final
        kotlinx.coroutines.delay(1500)
        
        if (allSuccess) {
            // Marcar setup completado y continuar a la app
            showWelcomeScreen = false
            
            // Ahora inicializar todo
            initializeDiagnostic()
            initializeSemanticSearch()
            if (hasAudioPermission) {
                initializeVoice()
            }
        } else {
            // Mostrar error y permitir reintentar
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "Error en algunas descargas. Verifica tu conexión.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Descarga modelos MindSpore con actualización de progreso
     */
    private suspend fun downloadMindSporeModels(itemIndex: Int): Boolean {
        val service = ModelDownloadService.getInstance()
        
        if (service.areAllModelsAvailable(applicationContext)) {
            return true
        }
        
        service.onProgress = { progress ->
            runOnUiThread {
                if (itemIndex < downloadItems.size) {
                    downloadItems[itemIndex] = downloadItems[itemIndex].copy(
                        progress = progress.progress
                    )
                }
            }
        }
        
        return service.downloadAllMissingModels(applicationContext)
    }
    
    /**
     * Descarga modelo LLM con actualización de progreso
     */
    private suspend fun downloadLlamaModel(itemIndex: Int): Boolean {
        val llamaService = LlamaService.getInstance()
        
        // Verificar si ya existe ANTES de descargar
        if (llamaService.isModelAvailable(applicationContext)) {
            AppLogger.log("MainActivity", "Modelo LLM ya existe, saltando descarga")
            return true
        }
        
        llamaService.onDownloadProgress = { progress, _, _ ->
            runOnUiThread {
                if (itemIndex < downloadItems.size) {
                    downloadItems[itemIndex] = downloadItems[itemIndex].copy(
                        progress = progress
                    )
                }
            }
        }
        
        val result = llamaService.downloadModel(applicationContext)
        
        // Verificar DESPUÉS de descargar si el modelo existe
        // (por si result.isSuccess falla pero el archivo sí se descargó)
        if (result.isSuccess) {
            AppLogger.log("MainActivity", "Descarga LLM exitosa")
            return true
        } else {
            // Doble verificación: puede que el archivo se haya descargado correctamente
            val exists = llamaService.isModelAvailable(applicationContext)
            if (exists) {
                AppLogger.log("MainActivity", "Modelo LLM existe después de descarga (verificación secundaria)")
                return true
            }
            AppLogger.log("MainActivity", "Error descargando LLM: ${result.exceptionOrNull()?.message}")
            return false
        }
    }

    /**
     * Asegura que los modelos MindSpore requeridos estén disponibles en almacenamiento interno.
     * Si faltan, los descarga usando ModelDownloadService antes de inicializar SemanticSearch
     * y el clasificador de enfermedades.
     */
    private suspend fun ensureMindSporeModelsAvailable() {
        val service = ModelDownloadService.getInstance()

        if (service.areAllModelsAvailable(applicationContext)) {
            AppLogger.log("MainActivity", "Modelos MindSpore ya disponibles")
            return
        }

        val totalMB = service.getTotalDownloadSizeMB(applicationContext)
        AppLogger.log("MainActivity", "Descargando modelos MindSpore (~${totalMB}MB)...")

        withContext(Dispatchers.Main) {
            uiStatus = "Descargando modelos IA (~${totalMB}MB)..."
        }

        // Solo actualizar UI con progreso, no saturar logs
        service.onProgress = { progress ->
            // Solo loguear al inicio, completar, o error - no cada actualización
            if (progress.progress == 0 || progress.progress == 100 || progress.status == ModelDownloadService.DownloadStatus.FAILED) {
                AppLogger.log("MainActivity", "Descarga ${progress.modelName}: ${progress.status}")
            }
            // Actualizar UI
            runOnUiThread {
                uiStatus = "Descargando: ${progress.modelName} (${progress.progress}%)"
            }
        }

        val success = service.downloadAllMissingModels(applicationContext)

        withContext(Dispatchers.Main) {
            uiStatus = if (success) {
                "Modelos IA listos"
            } else {
                "Error descargando modelos IA"
            }
        }
    }
    
    private fun initializeDiagnostic() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AppLogger.log("MainActivity", "Iniciando diagnóstico visual...")
                plantDiseaseClassifier = PlantDiseaseClassifier(applicationContext)
                val success = plantDiseaseClassifier?.initialize() ?: false
                
                withContext(Dispatchers.Main) {
                    isDiagnosticReady = success
                    if (success) {
                        AppLogger.log("MainActivity", "Diagnóstico visual inicializado")
                    } else {
                        AppLogger.log("MainActivity", "Modelo diagnóstico NO disponible")
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("MainActivity", "Error diagnóstico: ${e.message}")
                withContext(Dispatchers.Main) {
                    isDiagnosticReady = false
                }
            }
        }
    }
    
    private fun handleModeChange(mode: AppMode) {
        if (mode == AppMode.CAMERA && !hasCameraPermission) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            // Limpiar captura al salir del modo cámara
            if (currentMode == AppMode.CAMERA && mode != AppMode.CAMERA) {
                clearCapture()
            }
            currentMode = mode
        }
    }
    
    private fun processCapture(bitmap: Bitmap) {
        capturedBitmap = bitmap
        lastDiagnosis = null
        
        if (!isDiagnosticReady) {
            AppLogger.log("MainActivity", "Diagnóstico no listo, mostrando toast")
            Toast.makeText(this, "Modelo de diagnóstico no disponible", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            isDiagnosing = true
            uiStatus = "Analizando imagen..."
            AppLogger.log("MainActivity", "Clasificando imagen...")
            
            try {
                val result = withContext(Dispatchers.Default) {
                    plantDiseaseClassifier?.classify(bitmap)
                }
                
                lastDiagnosis = result
                uiStatus = if (result != null) {
                    "Diagnóstico completado"
                } else {
                    "No se pudo identificar la planta"
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error en diagnóstico: ${e.message}", e)
                uiStatus = "Error analizando imagen"
                Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isDiagnosing = false
            }
        }
    }
    
    private fun clearCapture() {
        capturedBitmap = null
        lastDiagnosis = null
        isDiagnosing = false
    }
    
    private fun diagnosisToChat(result: DiseaseResult) {
        // Añadir mensaje visual al chat con la imagen y diagnóstico
        chatMessages.add(ChatMessage(
            text = "Diagnóstico visual: ${result.displayName}",
            isUser = true,
            imageBitmap = capturedBitmap,
            diseaseResult = result
        ))
        
        // Cambiar a modo chat y buscar más información
        currentMode = AppMode.CHAT
        
        // Generar consulta RAG basada en el diagnóstico
        lifecycleScope.launch {
            isProcessing = true
            uiStatus = "Buscando tratamiento..."
            
            try {
                val query = result.toRagQuery()
                val responseMeta = findResponseWithMeta(query)
                val response = responseMeta.response
                
                val fullResponse = buildString {
                    append("**${result.displayName}**\n")
                    append("Cultivo: ${result.crop}\n")
                    append("Confianza: ${(result.confidence * 100).toInt()}%\n\n")
                    
                    if (result.isHealthy) {
                        append("La planta se ve saludable.\n\n")
                    } else {
                        append("Enfermedad detectada.\n\n")
                    }
                    
                    append("**Recomendación:**\n")
                    append(response)
                }
                
                chatMessages.add(ChatMessage(fullResponse, isUser = false))
                lastResponse = fullResponse
                voiceHelper?.speak(response)
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error buscando tratamiento", e)
                chatMessages.add(ChatMessage("No pude encontrar información del tratamiento.", isUser = false))
            } finally {
                isProcessing = false
                uiStatus = "Listo"
                clearCapture()
            }
        }
    }

    private fun initializeVoice() {
        voiceHelper = VoiceHelper(this).apply {
            onResult = { text ->
                runOnUiThread {
                    isListening = false
                    if (text.isNotBlank()) sendMessage(text)
                }
            }
            onError = { error ->
                runOnUiThread {
                    isListening = false
                    if (error.contains("No speech") || error.contains("No match") || error.contains("No escuché")) {
                        uiStatus = "No te escuché. Toca para hablar."
                    } else {
                        uiStatus = error
                    }
                }
            }
            onListeningStateChanged = { listening ->
                runOnUiThread {
                    isListening = listening
                    uiStatus = if (listening) "Te escucho..." else if (isModelReady) "Toca para hablar" else "Cargando..."
                }
            }
            onModelStatus = { status ->
                runOnUiThread {
                    uiStatus = status
                }
            }
            initialize()
        }
    }

    private fun handleMicClick() {
        if (!hasAudioPermission) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (isListening) voiceHelper?.stopListening() else voiceHelper?.startListening()
    }
    
    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isLlamaEnabled = if (ONLINE_ONLY_VISIT_MODE) {
            // Forzar modo visita (solo online) aunque existan preferencias previas.
            if (prefs.getBoolean(KEY_LLAMA_ENABLED, true)) {
                prefs.edit().putBoolean(KEY_LLAMA_ENABLED, false).apply()
            }
            false
        } else {
            prefs.getBoolean(KEY_LLAMA_ENABLED, true)
        }
        
        // Configuración avanzada (valores por defecto al máximo de la UI)
        advancedMaxTokens = prefs.getInt("advanced_max_tokens", 450).coerceIn(250, 900)
        advancedSimilarityThreshold = prefs.getFloat("advanced_similarity_threshold", 0.45f).coerceIn(SAFE_MIN_SIMILARITY_THRESHOLD, SAFE_MAX_SIMILARITY_THRESHOLD)
        advancedKbFastPathThreshold = prefs.getFloat("advanced_kb_fast_path_threshold", 0.70f).coerceIn(SAFE_MIN_KB_FAST_PATH_THRESHOLD, SAFE_MAX_KB_FAST_PATH_THRESHOLD)
        advancedContextRelevanceThreshold = prefs.getFloat("advanced_context_relevance_threshold", 0.50f).coerceIn(SAFE_MIN_CONTEXT_RELEVANCE_THRESHOLD, SAFE_MAX_CONTEXT_RELEVANCE_THRESHOLD)
        advancedSystemPrompt = prefs.getString("advanced_system_prompt", 
            "Eres FarmifAI, un asistente agrícola experto. Si se proporciona contexto de KB, úsalo como fuente principal y no inventes datos fuera de esa base. Si falta un dato en la KB, dilo explícitamente.") ?: advancedSystemPrompt
        advancedUseLlmForAll = prefs.getBoolean("advanced_use_llm_for_all", false)
        advancedContextLength = prefs.getInt("advanced_context_length", 1800).coerceIn(300, 3000)
        advancedDetectGreetings = prefs.getBoolean("advanced_detect_greetings", true)
        advancedChatHistoryEnabled = prefs.getBoolean("advanced_chat_history_enabled", true)
        advancedChatHistorySize = prefs.getInt("advanced_chat_history_size", 10).coerceIn(1, 20)
    }
    
    private fun saveAdvancedPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("advanced_max_tokens", advancedMaxTokens)
            putFloat("advanced_similarity_threshold", advancedSimilarityThreshold)
            putFloat("advanced_kb_fast_path_threshold", advancedKbFastPathThreshold)
            putFloat("advanced_context_relevance_threshold", advancedContextRelevanceThreshold)
            putString("advanced_system_prompt", advancedSystemPrompt)
            putBoolean("advanced_use_llm_for_all", advancedUseLlmForAll)
            putInt("advanced_context_length", advancedContextLength)
            putBoolean("advanced_detect_greetings", advancedDetectGreetings)
            putBoolean("advanced_chat_history_enabled", advancedChatHistoryEnabled)
            putInt("advanced_chat_history_size", advancedChatHistorySize)
            apply()
        }
    }
    
    private fun toggleLlama(enabled: Boolean) {
        if (ONLINE_ONLY_VISIT_MODE) {
            isLlamaEnabled = false
            Toast.makeText(this, "Modo visita: LLM local deshabilitado", Toast.LENGTH_SHORT).show()
            return
        }
        isLlamaEnabled = enabled
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LLAMA_ENABLED, enabled).apply()
        
        val status = if (enabled) "LLM Local activado" else "LLM Local desactivado"
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
    }
    
    private fun initializeGroq() {
        groqService = GroqService(applicationContext)
        
        // Cargar API key guardada
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedKey = prefs.getString(KEY_GROQ_API, null)
        if (!savedKey.isNullOrBlank()) {
            groqService?.setApiKey(savedKey)
            updateOnlineStatus()
        }
    }
    
    private fun initializeLlama() {
        if (ONLINE_ONLY_VISIT_MODE) {
            isLlamaEnabled = false
            isLlamaLoaded = false
            llamaModelStatusText = "Desactivado en modo visita online"
            llamaModelPathText = ""
            return
        }
        llamaService = LlamaService.getInstance()

        // Mostrar en UI dónde debe estar el modelo y si hay alguno detectado
        llamaModelPathText = llamaService?.getModelPath(applicationContext) ?: ""
        val detectedName = llamaService?.getModelFilename(applicationContext)
        val detectedSize = llamaService?.getModelSizeMB(applicationContext) ?: 0L
        Log.i("MainActivity", "Llama model path esperado: $llamaModelPathText")
        Log.i("MainActivity", "Llama detectado: ${detectedName ?: "(ninguno)"} (${detectedSize}MB)")
        llamaModelStatusText = if (!detectedName.isNullOrBlank()) {
            "Detectado: $detectedName (${detectedSize}MB)"
        } else {
            "Modelo no disponible"
        }
        
        // Verificar si el modelo está disponible
        if (llamaService?.isModelAvailable(applicationContext) == true) {
            // Modelo existe, cargarlo
            loadLlamaModel()
        } else {
            // Modelo no existe, descargar automáticamente
            Log.i("MainActivity", "Modelo Llama no disponible - iniciando descarga automática...")
            downloadAndLoadLlama()
        }
    }
    
    private fun downloadAndLoadLlama() {
        if (ONLINE_ONLY_VISIT_MODE) {
            isLlamaEnabled = false
            isLlamaLoaded = false
            llamaModelStatusText = "Desactivado en modo visita online"
            return
        }
        // Evitar descargas duplicadas
        if (isLlamaDownloading) {
            Log.i("MainActivity", "Descarga ya en progreso, ignorando...")
            return
        }
        
        lifecycleScope.launch {
            try {
                isLlamaDownloading = true
                llamaDownloadFailed = false
                llamaModelStatusText = "Descargando modelo..."
                uiStatus = "Descargando LLM (0%)..."
                
                llamaService?.onDownloadProgress = { progress, downloadedMB, totalMB ->
                    runOnUiThread {
                        llamaModelStatusText = "Descargando: $downloadedMB/$totalMB MB"
                        uiStatus = "Descargando LLM ($progress%)..."
                    }
                }
                
                val result = llamaService?.downloadModel(applicationContext)
                result?.onSuccess { file ->
                    Log.i("MainActivity", "Model downloaded: ${file.absolutePath}")
                    llamaModelStatusText = "Descargado: ${file.name}"
                    isLlamaDownloading = false
                    loadLlamaModel()
                }?.onFailure { e ->
                    Log.e("MainActivity", "Download error: ${e.message}")
                    llamaModelStatusText = "Error descarga - Toca para reintentar"
                    llamaDownloadFailed = true
                    isLlamaDownloading = false
                    uiStatus = "Sin LLM local"
                    Toast.makeText(applicationContext, "No se pudo descargar LLM", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error en descarga", e)
                llamaModelStatusText = "Error - Toca para reintentar"
                llamaDownloadFailed = true
                isLlamaDownloading = false
                uiStatus = "Sin LLM local"
            }
        }
    }
    
    private fun loadLlamaModel() {
        if (ONLINE_ONLY_VISIT_MODE) {
            isLlamaEnabled = false
            isLlamaLoaded = false
            llamaModelStatusText = "Desactivado en modo visita online"
            return
        }
        lifecycleScope.launch {
            try {
                uiStatus = "Cargando LLM local..."
                llamaModelStatusText = "Cargando..."
                
                val result = llamaService?.load(applicationContext)
                result?.onSuccess {
                    isLlamaLoaded = true
                    llamaModelStatusText = "Cargado: ${llamaService?.getModelFilename(applicationContext)} (${llamaService?.getModelSizeMB(applicationContext)}MB)"
                    Log.i("MainActivity", "Llama model loaded")
                    
                    updateOnlineStatus()
                    if (!isOnlineMode && isLlamaEnabled) {
                        withContext(Dispatchers.Main) {
                            uiStatus = "Llama listo"
                            Toast.makeText(applicationContext, "LLM Local listo", Toast.LENGTH_SHORT).show()
                        }
                    }
                }?.onFailure { e ->
                    Log.w("MainActivity", "Error cargando Llama: ${e.message}")
                    isLlamaLoaded = false
                    llamaModelStatusText = "Error cargando"
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error inicializando Llama", e)
                isLlamaLoaded = false
                llamaModelStatusText = "Error"
            }
        }
    }
    
    private fun saveGroqApiKey(key: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GROQ_API, key).apply()
        groqService?.setApiKey(key)
        updateOnlineStatus()
        showSettingsDialog = false
        
        if (key.isNotBlank()) {
            Toast.makeText(this, "API Key guardada ✓", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateOnlineStatus() {
        if (STRICT_TERMINAL_PARITY_MODE) {
            isOnlineMode = false
            Log.d("MainActivity", "updateOnlineStatus: STRICT_TERMINAL_PARITY_MODE=true, forcing local mode")
            return
        }

        // Verificar conectividad de red
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val hasInternet = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        
        // Solo está en modo online si hay internet Y Groq está configurado
        isOnlineMode = hasInternet && groqService?.isAvailable() == true
        
        Log.d("MainActivity", "updateOnlineStatus: hasInternet=$hasInternet, groqAvailable=${groqService?.isAvailable()}, isOnlineMode=$isOnlineMode")
    }

    private suspend fun initializeSemanticSearch() {
        uiStatus = "Cargando IA..."
        AppLogger.log("MainActivity", "Iniciando SemanticSearch...")
        try {
            semanticSearchHelper = SemanticSearchHelper(applicationContext)
            semanticSearchHelper?.setForceTextOnlyMode(STRICT_TERMINAL_PARITY_MODE)
            val success = withContext(Dispatchers.IO) { semanticSearchHelper?.initialize() ?: false }
            if (success) {
                isModelReady = true
                uiStatus = "Toca para hablar"
                lastResponse = "¡Hola! Soy FarmifAI\nTu asistente agrícola con IA.\n\nPregúntame sobre cultivos, plagas, fertilizantes o cualquier tema agrícola."
                AppLogger.log("MainActivity", "SemanticSearch initialized")
            } else {
                uiStatus = "Error al cargar"
                AppLogger.log("MainActivity", "SemanticSearch init failed")
            }
        } catch (e: Throwable) {
            uiStatus = "Error: ${e.message}"
            AppLogger.log("MainActivity", "SemanticSearch error: ${e.message}")
        }
    }

    /**
     * Detecta si una respuesta del LLM parece estar completa
     * Una respuesta se considera completa si:
     * - Termina con puntuación final (., !, ?, :)
     * - Tiene longitud razonable (>80 chars indica contenido sustancial)
     * - No termina en medio de una palabra o frase
     */
    private fun isResponseComplete(response: String): Boolean {
        val trimmed = response.trim()
        if (trimmed.isEmpty()) return false
        
        // Si es muy larga (>300 chars), probablemente está completa
        if (trimmed.length > 300) return true
        
        // Si termina con puntuación final, está completa
        val lastChar = trimmed.last()
        if (lastChar in listOf('.', '!', '?', ':', ')', '"', '>')) return true
        
        // Si termina con emoji, está completa
        if (trimmed.takeLast(2).any { Character.isSupplementaryCodePoint(it.code) || it.code > 0x1F300 }) return true
        
        // Si tiene múltiples oraciones (varios puntos), está completa
        if (trimmed.count { it == '.' } >= 2) return true
        
        // Si tiene viñetas/listas completadas, está completa
        if (trimmed.contains("\n•") || trimmed.contains("\n-") || trimmed.contains("\n*")) {
            val lines = trimmed.lines()
            val lastLine = lines.lastOrNull()?.trim() ?: ""
            // Si la última línea de lista tiene contenido sustancial
            if (lastLine.length > 10) return true
        }
        
        // Si es muy corta y no termina bien, no está completa
        return false
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validación de calidad de respuestas
    // ═══════════════════════════════════════════════════════════════════════════
    
    data class ResponseQualityReport(
        val isComplete: Boolean,
        val isCoherent: Boolean,
        val isAppropriateLength: Boolean,
        val answersQuestion: Boolean,
        val issues: List<String>,
        val suggestions: List<String>,
        val qualityScore: Float  // 0.0 a 1.0
    )

    data class ResponseMeta(
        val response: String,
        val usedLlm: Boolean,
        val kbSupported: Boolean,
        val kbSupportScore: Float,
        val kbCoverage: Float,
        val kbUnknownRatio: Float,
        val enforcedKbAbstention: Boolean
    )
    
    /**
     * Evalúa la calidad de una respuesta antes de entregarla al usuario.
     */
    private fun evaluateResponseQuality(
        response: String,
        userQuery: String,
        chatHistory: List<ChatMessage>,
        kbSupported: Boolean,
        kbCoverage: Float,
        kbSupportScore: Float,
        enforcedKbAbstention: Boolean
    ): ResponseQualityReport {
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        var score = 1.0f
        
        // 1. VERIFICAR COMPLETITUD
        val incompleteIndicators = listOf(
            response.endsWith("...") && !response.endsWith("etc..."),
            response.endsWith(","),
            response.endsWith(":"),
            response.count { it == '.' } < 1 && response.length > 50,
            response.contains("continuar") && response.contains("si deseas"),
            Regex("\\d+\\.\\s*$").containsMatchIn(response),
            response.trim().endsWith("-")
        )
        val isComplete = incompleteIndicators.none { it }
        if (!isComplete) {
            issues.add("Respuesta incompleta")
            score -= 0.15f
        }
        
        // 2. VERIFICAR LONGITUD APROPIADA
        val isAppropriateLength = when {
            response.length < 20 -> {
                issues.add("Respuesta muy corta")
                false
            }
            response.length > 2000 -> {
                issues.add("Respuesta muy larga")
                score -= 0.1f
                false
            }
            else -> true
        }
        
        // 3. VERIFICAR COHERENCIA CON LA PREGUNTA
        val queryWords = userQuery.lowercase().split(" ").filter { it.length > 3 }
        val responseWords = response.lowercase()
        val relevantWordsInResponse = queryWords.count { responseWords.contains(it) }
        val coherenceRatio = if (queryWords.isNotEmpty()) relevantWordsInResponse.toFloat() / queryWords.size else 1f
        val isCoherent = queryWords.isEmpty() || coherenceRatio >= 0.25f
        if (!isCoherent) {
            issues.add("Respuesta no relacionada con la pregunta")
            score -= 0.25f
        }
        
        // 4. VERIFICAR QUE RESPONDE LA PREGUNTA (no evade)
        val evasivePatterns = listOf(
            "no puedo responder", "no tengo información", "no sé sobre",
            "fuera de mi conocimiento", "no estoy seguro", "deberías consultar"
        )
        val isEvasive = evasivePatterns.any { response.lowercase().contains(it) }
        val isHonestUncertainty = isNoInfoStyleResponse(response) ||
            response.lowercase().contains("no tengo suficiente información en mi base") ||
            response.lowercase().contains("prefiero no inventar")
        val isGenericFailureWithKb = response.lowercase().contains("no pude generar una respuesta confiable") ||
            response.lowercase().contains("llm no está disponible para redactar")
        val likelyAgriculturalQuery = isLikelyAgriculturalQuery(userQuery)
        val answersQuestion = !isEvasive || isHonestUncertainty
        if (isEvasive && !isHonestUncertainty) {
            issues.add("Respuesta evasiva")
            score -= 0.1f
        }
        if (enforcedKbAbstention && !isHonestUncertainty) {
            issues.add("Debió admitir falta de evidencia en KB")
            score -= 0.35f
        }
        if (!kbSupported && !isHonestUncertainty) {
            if (likelyAgriculturalQuery) {
                issues.add("Respuesta general sin respaldo directo de KB")
                score -= 0.12f
            } else {
                issues.add("Respuesta con soporte KB débil")
                score -= 0.35f
            }
        }
        if (kbSupported && isGenericFailureWithKb) {
            issues.add("Fallback genérico con KB disponible")
            score -= 0.4f
        }
        if (kbSupported && kbCoverage < MIN_LEXICAL_COVERAGE_FOR_GROUNDED) {
            issues.add("Cobertura lexical KB insuficiente")
            score -= 0.1f
        }
        if (kbSupported && kbSupportScore < MIN_SUPPORT_SCORE_FOR_GROUNDED) {
            issues.add("Respuesta con soporte parcial de KB")
            score -= 0.1f
        }
        
        // 5. VERIFICAR CONTINUIDAD CON CHAT ANTERIOR
        val lastBotMessage = chatHistory.lastOrNull { !it.isUser }?.text ?: ""
        val isContinuationRequest = userQuery.lowercase().let { q ->
            q in listOf("continúa", "continua", "sigue", "más", "mas", "y qué más", "y que mas", "explica más") ||
            q.startsWith("y ") || q.startsWith("pero ") || q.startsWith("entonces ")
        }
        
        if (isContinuationRequest && lastBotMessage.isNotEmpty()) {
            val lastTopicWords = lastBotMessage.lowercase().split(" ").filter { it.length > 4 }.take(10)
            val newResponseWords = response.lowercase()
            val topicContinuity = lastTopicWords.count { newResponseWords.contains(it) }
            if (topicContinuity < 2 && lastTopicWords.size > 3) {
                issues.add("Pérdida de contexto")
                score -= 0.2f
            }
        }
        
        // 6. DETECTAR RESPUESTAS REPETITIVAS
        if (lastBotMessage.isNotEmpty()) {
            val similarity = calculateTextSimilarity(response, lastBotMessage)
            if (similarity > 0.7f) {
                issues.add("Respuesta repetitiva")
                score -= 0.15f
            }
        }
        
        // 7. VERIFICAR FORMATO
        val hasStructure = response.contains("\n") || response.contains("•") || 
                          response.contains("-") || response.contains("1.")
        if (response.length > 300 && !hasStructure) {
            score -= 0.05f
        }
        
        score = score.coerceIn(0f, 1f)
        
        // Log resumido
        AppLogger.log("MainActivity", "Quality: ${String.format("%.0f", score * 100)}% | complete=$isComplete coherent=$isCoherent issues=${issues.size}")
        
        return ResponseQualityReport(
            isComplete = isComplete,
            isCoherent = isCoherent,
            isAppropriateLength = isAppropriateLength,
            answersQuestion = answersQuestion,
            issues = issues,
            suggestions = suggestions,
            qualityScore = score
        )
    }
    
    /**
     * Calcula similitud simple entre dos textos (Jaccard)
     */
    private fun calculateTextSimilarity(text1: String, text2: String): Float {
        val words1 = text1.lowercase().split(Regex("\\s+")).filter { it.length > 3 }.toSet()
        val words2 = text2.lowercase().split(Regex("\\s+")).filter { it.length > 3 }.toSet()
        if (words1.isEmpty() || words2.isEmpty()) return 0f
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        return intersection.toFloat() / union.toFloat()
    }
    
    /**
     * Mejora una respuesta basándose en el reporte de calidad
     */
    private fun improveResponseIfNeeded(
        response: String,
        qualityReport: ResponseQualityReport,
        userQuery: String
    ): String {
        var improved = response
        
        // Si hay problemas graves, añadir indicadores
        if (!qualityReport.isComplete && response.length > 50) {
            // No modificar, pero el canContinue ya manejará esto
        }
        
        // Limpiar respuestas que terminan mal
        improved = improved.trim()
        if (improved.endsWith(",") || improved.endsWith(":")) {
            improved = improved.dropLast(1).trim()
            if (!improved.endsWith(".") && !improved.endsWith("!") && !improved.endsWith("?")) {
                improved += "."
            }
        }
        
        return improved
    }

    /**
     * Evita que el modelo devuelva conversaciones inventadas con prefijos de rol.
     */
    private fun sanitizeAssistantResponse(response: String): String {
        val original = response
            .replace(Regex("<\\|[^>]+\\|>"), " ")
            .trim()
        if (original.isBlank()) return original

        val cleanedLines = mutableListOf<String>()
        var hasContent = false

        for (rawLine in original.lines()) {
            val trimmed = rawLine.trim()
            if (!hasContent && trimmed.isBlank()) continue

            val lower = trimmed.lowercase()
            val isUserLine = lower.startsWith("usuario:") || lower.startsWith("user:")
            val isAssistantLine = lower.startsWith("asistente:") || lower.startsWith("assistant:")

            if (isUserLine && hasContent) break
            if (isUserLine && !hasContent) continue
            if (lower == "user" || lower == "assistant" || lower == "usuario" || lower == "asistente") continue

            val normalized = when {
                isAssistantLine -> trimmed.substringAfter(":", "").trimStart()
                else -> rawLine
            }

            cleanedLines.add(normalized)
            if (normalized.isNotBlank()) hasContent = true
        }

        val sanitized = cleanedLines.joinToString("\n").trim()
        return if (sanitized.isNotBlank()) sanitized else original
    }

    private fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || !isModelReady || isProcessing) return
        chatMessages.add(ChatMessage(userMessage, isUser = true))
        AppLogger.log("MainActivity", "Mensaje: '$userMessage'")
        
        val isContinuationIntent = isContinuationMessage(userMessage)
        if (!isContinuationIntent) {
            // Guardar última consulta temática real
            lastUserQuery = userMessage
        }
        val effectiveQuery = if (isContinuationIntent && lastUserQuery.isNotBlank()) {
            "Amplía y continúa la explicación sobre: $lastUserQuery"
        } else {
            userMessage
        }
        val forcedContinuationContext = if (isContinuationIntent) {
            buildForcedContinuationContext()
        } else {
            null
        }

        lifecycleScope.launch {
            isProcessing = true
            
            // Actualizar estado de conexión ANTES de procesar
            updateOnlineStatus()
            
            // Mostrar estado según modo disponible
            uiStatus = when {
                isOnlineMode -> "Consultando IA online..."
                else -> "Buscando información..."
            }
            
            try {
                val responseMeta = findResponseWithMeta(
                    userQuery = effectiveQuery,
                    skipKbDirect = isContinuationIntent,
                    forcedContext = forcedContinuationContext
                )
                val sanitizedResponse = sanitizeAssistantResponse(responseMeta.response)
                
                // ═══════════════════════════════════════════════════════════════
                // SISTEMA DE AUTOCONSCIENCIA - Evaluar calidad antes de entregar
                // ═══════════════════════════════════════════════════════════════
                val qualityReport = evaluateResponseQuality(
                    response = sanitizedResponse,
                    userQuery = if (isContinuationIntent) effectiveQuery else userMessage,
                    chatHistory = chatMessages.toList(),
                    kbSupported = responseMeta.kbSupported,
                    kbCoverage = responseMeta.kbCoverage,
                    kbSupportScore = responseMeta.kbSupportScore,
                    enforcedKbAbstention = responseMeta.enforcedKbAbstention
                )
                
                // Mejorar respuesta si es necesario
                val improvedResponse = improveResponseIfNeeded(sanitizedResponse, qualityReport, userMessage)
                
                // Determinar si puede continuar basándose en autoconsciencia
                val canContinue = !ONLINE_ONLY_VISIT_MODE && responseMeta.usedLlm && isLlamaEnabled && isLlamaLoaded && responseMeta.kbSupported &&
                                  (!qualityReport.isComplete || qualityReport.qualityScore < 0.7f)
                
                chatMessages.add(ChatMessage(improvedResponse, isUser = false, canContinue = canContinue))
                lastResponse = improvedResponse
                
                // Log resumen de calidad
                AppLogger.log("MainActivity", "📊 Calidad: ${String.format("%.0f", qualityReport.qualityScore * 100)}% | " +
                    "Completa: ${qualityReport.isComplete} | Coherente: ${qualityReport.isCoherent} | " +
                    "Puede continuar: $canContinue")
                
                AppLogger.log("MainActivity", "Respuesta: ${improvedResponse.take(50)}...")
                
                // Actualizar status final
                uiStatus = if (isOnlineMode) "Online ✓" else "Sin conexión online"
                
                voiceHelper?.speak(improvedResponse)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error en sendMessage", e)
                val err = "Hubo un error. Intenta de nuevo."
                chatMessages.add(ChatMessage(err, isUser = false))
                lastResponse = err
                uiStatus = "Error"
            } finally {
                isProcessing = false
            }
        }
    }

    private fun isContinuationMessage(text: String): Boolean {
        val normalized = text.lowercase().trim()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")

        val exact = setOf(
            "continua", "continua.", "continue", "continue.", "sigue", "sigue.",
            "mas", "mas.", "amplia", "amplia."
        )
        return normalized in exact ||
            normalized.startsWith("continua") ||
            normalized.startsWith("sigue") ||
            normalized.startsWith("mas ")
    }

    private fun buildForcedContinuationContext(): String? {
        val lastBotResponse = chatMessages.lastOrNull { !it.isUser }?.text?.trim().orEmpty()
        val kb = lastContext?.trim().orEmpty()
        if (lastBotResponse.isBlank() && kb.isBlank()) return null

        val parts = mutableListOf<String>()
        if (kb.isNotBlank()) {
            parts.add("=== KB ===\n$kb")
        }
        if (lastBotResponse.isNotBlank()) {
            val topic = if (lastUserQuery.isBlank()) "tema anterior" else lastUserQuery
            parts.add(
                "=== HISTORIAL ===\n" +
                    "Usuario: $topic\n" +
                    "Asistente: ${lastBotResponse.take(1200)}"
            )
        }
        return parts.joinToString("\n\n").ifBlank { null }
    }
    
    /**
     * Continúa la última respuesta del LLM pidiendo más detalles
     */
    private fun continueLastResponse() {
        if (ONLINE_ONLY_VISIT_MODE) return
        if (!isLlamaEnabled || !isLlamaLoaded || isProcessing) return
        if (lastUserQuery.isBlank()) return
        
        AppLogger.log("MainActivity", "Continuando respuesta para: '$lastUserQuery'")
        
        lifecycleScope.launch {
            isProcessing = true
            uiStatus = "Expandiendo respuesta..."
            
            try {
                // Obtener la última respuesta del bot
                val lastBotResponse = chatMessages.lastOrNull { !it.isUser }?.text ?: ""
                
                // Crear prompt para continuar - simple y corto
                val continuePrompt = "Más sobre: $lastUserQuery"
                
                val result = llamaService?.generateAgriResponse(
                    userQuery = continuePrompt,
                    contextFromKB = lastContext,
                    maxTokens = maxOf(advancedMaxTokens, 300),
                    maxContextLength = advancedContextLength,
                    systemPrompt = advancedSystemPrompt
                )
                
                result?.fold(
                    onSuccess = { response ->
                        val cleanResponse = sanitizeAssistantResponse(response).trim()
                        if (cleanResponse.length > 10) {
                            // Verificar si esta continuación está completa
                            val isComplete = isResponseComplete(cleanResponse)
                            // Agregar como continuación
                            chatMessages.add(ChatMessage("$cleanResponse", isUser = false, canContinue = !isComplete))
                            lastResponse = cleanResponse
                            voiceHelper?.speak(cleanResponse)
                        }
                    },
                    onFailure = { error ->
                        AppLogger.log("MainActivity", "Error continuando: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                AppLogger.log("MainActivity", "Error en continuar: ${e.message}")
            } finally {
                isProcessing = false
                uiStatus = "Listo"
            }
        }
    }

    private suspend fun findResponseWithMeta(
        userQuery: String,
        skipKbDirect: Boolean = false,
        forcedContext: String? = null
    ): ResponseMeta = withContext(Dispatchers.IO) {
        Log.d("MainActivity", "findResponse: isOnlineMode=$isOnlineMode, isLlamaEnabled=$isLlamaEnabled, isLlamaLoaded=$isLlamaLoaded")
        val rawSimilarityThreshold = if (STRICT_TERMINAL_PARITY_MODE) PARITY_SIMILARITY_THRESHOLD else advancedSimilarityThreshold
        val rawContextRelevanceThreshold = if (STRICT_TERMINAL_PARITY_MODE) PARITY_CONTEXT_RELEVANCE_THRESHOLD else advancedContextRelevanceThreshold
        val rawKbFastPathThreshold = if (STRICT_TERMINAL_PARITY_MODE) PARITY_KB_FAST_PATH_THRESHOLD else advancedKbFastPathThreshold

        val effectiveSimilarityThreshold = rawSimilarityThreshold.coerceIn(SAFE_MIN_SIMILARITY_THRESHOLD, SAFE_MAX_SIMILARITY_THRESHOLD)
        val effectiveContextRelevanceThreshold = rawContextRelevanceThreshold.coerceIn(SAFE_MIN_CONTEXT_RELEVANCE_THRESHOLD, SAFE_MAX_CONTEXT_RELEVANCE_THRESHOLD)
        val effectiveKbFastPathThreshold = rawKbFastPathThreshold.coerceIn(SAFE_MIN_KB_FAST_PATH_THRESHOLD, SAFE_MAX_KB_FAST_PATH_THRESHOLD)
        val effectiveChatHistoryEnabled = if (STRICT_TERMINAL_PARITY_MODE) true else advancedChatHistoryEnabled
        val effectiveChatHistorySize = if (STRICT_TERMINAL_PARITY_MODE) PARITY_CHAT_HISTORY_SIZE else advancedChatHistorySize
        val effectiveContextLength = if (STRICT_TERMINAL_PARITY_MODE) PARITY_CONTEXT_LENGTH else advancedContextLength
        val effectiveSystemPrompt = if (STRICT_TERMINAL_PARITY_MODE) PARITY_SYSTEM_PROMPT else advancedSystemPrompt
        val effectiveUseLlmForAll = if (STRICT_TERMINAL_PARITY_MODE) false else advancedUseLlmForAll
        val effectiveDetectGreetings = if (STRICT_TERMINAL_PARITY_MODE) true else advancedDetectGreetings
        val retrievalMinScore = KB_RETRIEVAL_MIN_SCORE
        val llmAvailable = (!STRICT_TERMINAL_PARITY_MODE && isOnlineMode && groqService?.isAvailable() == true) ||
            (!ONLINE_ONLY_VISIT_MODE && isLlamaEnabled && isLlamaLoaded && llamaService != null)

        if (rawSimilarityThreshold != effectiveSimilarityThreshold ||
            rawContextRelevanceThreshold != effectiveContextRelevanceThreshold ||
            rawKbFastPathThreshold != effectiveKbFastPathThreshold
        ) {
            AppLogger.log(
                "MainActivity",
                "Thresholds ajustados a rango seguro: sim=$effectiveSimilarityThreshold kbFast=$effectiveKbFastPathThreshold ctxRel=$effectiveContextRelevanceThreshold"
            )
        }

        AppLogger.log(
            "MainActivity",
            "PARITY strict=$STRICT_TERMINAL_PARITY_MODE sim=$effectiveSimilarityThreshold kbFast=$effectiveKbFastPathThreshold ctxRel=$effectiveContextRelevanceThreshold"
        )

        AppLogger.log("MainActivity", "════════════════════════════════════════════════════════════")
        AppLogger.log("MainActivity", "🔍 BÚSQUEDA RAG - Query: '$userQuery'")
        AppLogger.log("MainActivity", "════════════════════════════════════════════════════════════")

        val ragContext = semanticSearchHelper?.findTopKContexts(
            userQuery = userQuery,
            topK = 3,
            minScore = retrievalMinScore
        )

        val combinedKBContext = ragContext?.combinedContext
        val bestMatch = ragContext?.contexts?.firstOrNull()
        val groundingAssessment = ragContext?.groundingAssessment
        val kbSupportScore = groundingAssessment?.supportScore ?: 0f
        val kbCoverage = groundingAssessment?.lexicalCoverage ?: 0f
        val kbUnknownRatio = groundingAssessment?.unknownTokenRatio ?: 1f
        val hasGroundedKbSupport = bestMatch != null &&
            (groundingAssessment?.hasStrongSupport == true) &&
            kbSupportScore >= MIN_SUPPORT_SCORE_FOR_GROUNDED &&
            kbCoverage >= MIN_LEXICAL_COVERAGE_FOR_GROUNDED &&
            kbUnknownRatio <= MAX_UNKNOWN_RATIO_FOR_GROUNDED &&
            bestMatch.similarityScore >= effectiveSimilarityThreshold
        val hasRelatedKbSignal = bestMatch != null && (
            bestMatch.similarityScore >= KB_RELATED_MIN_SCORE ||
                kbSupportScore >= KB_RELATED_MIN_SUPPORT ||
                kbCoverage >= KB_RELATED_MIN_COVERAGE ||
                bestMatch.similarityScore >= effectiveKbFastPathThreshold.coerceAtMost(0.60f)
            ) &&
            kbUnknownRatio <= KB_RELATED_MAX_UNKNOWN_RATIO
        val hasKbContext = hasGroundedKbSupport || hasRelatedKbSignal

        if (forcedContext == null) {
            lastContext = if (!combinedKBContext.isNullOrBlank()) combinedKBContext else bestMatch?.answer
        }

        val kbResults = ragContext?.contexts?.take(3)?.joinToString(" | ") {
            "${String.format("%.2f", it.similarityScore)}:'${it.matchedQuestion.take(30)}'"
        } ?: "none"
        AppLogger.log(
            "MainActivity",
            "KB search: retrievalMin=$retrievalMinScore threshold=$effectiveSimilarityThreshold related=$hasRelatedKbSignal grounded=$hasGroundedKbSupport support=${String.format("%.2f", kbSupportScore)} coverage=${String.format("%.2f", kbCoverage)} unknown=${String.format("%.2f", kbUnknownRatio)} results=$kbResults"
        )

        val isSimpleGreeting = effectiveDetectGreetings && userQuery.lowercase().trim().let { q ->
            q in listOf("hola", "hey", "buenas", "buenos días", "buenas tardes", "buenas noches", "gracias", "adiós", "chao", "hasta luego") ||
                q.length < 15 && (q.startsWith("hola") || q.startsWith("hey") || q.startsWith("gracias"))
        }
        val likelyAgriculturalQuery = isLikelyAgriculturalQuery(userQuery)
        val allowGeneralLlmCandidate = !hasKbContext && llmAvailable && (isSimpleGreeting || !likelyAgriculturalQuery || effectiveUseLlmForAll)

        val routeResult = ResponseRoutingPolicy.decide(
            ResponseRoutingPolicy.Input(
                hasRelatedKbSignal = hasRelatedKbSignal,
                hasGroundedKbSupport = hasGroundedKbSupport,
                allowGeneralLlmMode = allowGeneralLlmCandidate,
                skipKbDirect = skipKbDirect || forcedContext != null,
                useLlmForAll = effectiveUseLlmForAll,
                bestSimilarityScore = bestMatch?.similarityScore ?: 0f,
                kbSupportScore = kbSupportScore,
                kbCoverage = kbCoverage,
                kbUnknownRatio = kbUnknownRatio,
                effectiveKbFastPathThreshold = effectiveKbFastPathThreshold
            )
        )
        val allowGeneralLlmMode = routeResult.decision == ResponseRoutingPolicy.Decision.LLM_GENERAL

        AppLogger.log(
            "MainActivity",
            "Decision route=${routeResult.decision} reason=${routeResult.reason} kbScore=${String.format("%.2f", bestMatch?.similarityScore ?: 0f)} support=${String.format("%.2f", kbSupportScore)} coverage=${String.format("%.2f", kbCoverage)} unknown=${String.format("%.2f", kbUnknownRatio)}"
        )

        if (routeResult.decision == ResponseRoutingPolicy.Decision.KB_DIRECT && forcedContext == null && bestMatch != null) {
            AppLogger.log("MainActivity", "Decision: KB_DIRECT high confidence id=${bestMatch.entryId}")
            return@withContext ResponseMeta(
                response = bestMatch.answer,
                usedLlm = false,
                kbSupported = true,
                kbSupportScore = kbSupportScore,
                kbCoverage = kbCoverage,
                kbUnknownRatio = kbUnknownRatio,
                enforcedKbAbstention = false
            )
        }

        if (routeResult.decision == ResponseRoutingPolicy.Decision.ABSTAIN && !isSimpleGreeting && forcedContext == null) {
            val abstainResponse = buildKbInsufficientEvidenceResponse(userQuery, groundingAssessment)
            AppLogger.log(
                "MainActivity",
                "Decision: KB_ABSTAIN support=${String.format("%.2f", kbSupportScore)} coverage=${String.format("%.2f", kbCoverage)} unknown=${String.format("%.2f", kbUnknownRatio)} related=$hasRelatedKbSignal agri=$likelyAgriculturalQuery llmAvailable=$llmAvailable"
            )
            return@withContext ResponseMeta(
                response = abstainResponse,
                usedLlm = false,
                kbSupported = false,
                kbSupportScore = kbSupportScore,
                kbCoverage = kbCoverage,
                kbUnknownRatio = kbUnknownRatio,
                enforcedKbAbstention = true
            )
        }
        if (allowGeneralLlmMode) {
            AppLogger.log(
                "MainActivity",
                "Decision: LLM_GENERAL support=${String.format("%.2f", kbSupportScore)} coverage=${String.format("%.2f", kbCoverage)} unknown=${String.format("%.2f", kbUnknownRatio)}"
            )
        }

        var rawChatHistoryContext: String? = null
        if (effectiveChatHistoryEnabled && chatMessages.isNotEmpty()) {
            val recentMessages = chatMessages.takeLast(effectiveChatHistorySize * 2)
            if (recentMessages.isNotEmpty()) {
                rawChatHistoryContext = recentMessages.joinToString("\n") { msg ->
                    if (msg.isUser) "Usuario: ${msg.text.take(200)}" else "Asistente: ${msg.text.take(200)}"
                }
            }
        }

        val kbBudget = if (hasKbContext && !combinedKBContext.isNullOrBlank() && !rawChatHistoryContext.isNullOrBlank()) {
            (effectiveContextLength * 0.70f).toInt().coerceIn(500, effectiveContextLength)
        } else {
            effectiveContextLength
        }
        val historyBudget = (effectiveContextLength - kbBudget).coerceAtLeast(200)
        val kbContextForPrompt = combinedKBContext?.take(kbBudget)
        val chatHistoryContext = rawChatHistoryContext?.take(historyBudget)
        val contextParts = mutableListOf<String>()
        if (hasKbContext && !kbContextForPrompt.isNullOrBlank()) {
            contextParts.add("=== KB ===\n$kbContextForPrompt")
        }
        if (!chatHistoryContext.isNullOrBlank()) {
            contextParts.add("=== HISTORIAL ===\n$chatHistoryContext")
        }
        val autoContext = if (contextParts.isNotEmpty()) contextParts.joinToString("\n\n").take(effectiveContextLength) else null
        val forcedContextForPrompt = forcedContext?.take(effectiveContextLength)
        val contextToPass = forcedContextForPrompt ?: autoContext

        val mode = when {
            hasKbContext && chatHistoryContext != null -> "RAG+Chat"
            hasKbContext -> "RAG"
            chatHistoryContext != null -> "Chat"
            else -> "LLM_only"
        }
        AppLogger.log(
            "MainActivity",
            "Decision: LLM mode=$mode kbScore=${String.format("%.2f", bestMatch?.similarityScore ?: 0f)} support=${String.format("%.2f", kbSupportScore)} ctxLen=${contextToPass?.length ?: 0} skipKbDirect=$skipKbDirect forcedCtx=${forcedContext != null}"
        )
        AppLogger.log(
            "MainActivity",
            "Context budget: max=$effectiveContextLength kbLen=${kbContextForPrompt?.length ?: 0} histLen=${chatHistoryContext?.length ?: 0} forcedLen=${forcedContextForPrompt?.length ?: 0} finalLen=${contextToPass?.length ?: 0}"
        )

        withContext(Dispatchers.Main) {
            uiStatus = "Generando respuesta..."
        }

        if (!STRICT_TERMINAL_PARITY_MODE && isOnlineMode && groqService?.isAvailable() == true) {
            Log.d("MainActivity", "→ Usando Groq LLM (online, grounded)")

            val historyWindow = if (effectiveChatHistoryEnabled) effectiveChatHistorySize.coerceIn(0, 20) else 0
            val history = if (historyWindow > 0) {
                chatMessages
                    .filter { it.text.isNotBlank() }
                    .chunked(2)
                    .filter { it.size == 2 && it[0].isUser && !it[1].isUser }
                    .map { it[0].text to it[1].text }
                    .takeLast(historyWindow)
            } else {
                emptyList()
            }

            val groqUserPrompt = if (hasKbContext && !kbContextForPrompt.isNullOrBlank()) {
                """Responde usando únicamente la información del CONTEXTO KB.
Redacta en lenguaje natural y claro para agricultor.
No copies frases textuales del contexto; parafrasea y sintetiza.
Si la respuesta no está en el contexto, responde exactamente: "No tengo suficiente información en la base de conocimiento para responder con seguridad."
No inventes datos externos.

CONTEXTO KB:
$kbContextForPrompt

PREGUNTA:
$userQuery
""".trimIndent()
            } else if (allowGeneralLlmMode) {
                """No hay evidencia suficiente en la KB para responder con precisión.
Puedes responder con conocimiento agrícola general, dejando claro que es una recomendación general.
Evita inventar cifras, normativas o hechos específicos no verificables.
Incluye una breve nota de incertidumbre si aplica.

PREGUNTA:
$userQuery
""".trimIndent()
            } else {
                userQuery
            }

            val groqSystemPrompt = when {
                hasKbContext -> "$effectiveSystemPrompt\nReglas obligatorias: usa SOLO el CONTEXTO KB como fuente factual; parafrasea en lenguaje natural (no copies literal); si falta evidencia, dilo explícitamente; no inventes."
                allowGeneralLlmMode -> "$effectiveSystemPrompt\nNo hay evidencia suficiente en la KB para esta consulta. Puedes responder con conocimiento agrícola general, aclara que es orientación general y qué datos faltan."
                else -> effectiveSystemPrompt
            }
            val groqMaxTokens = maxOf(advancedMaxTokens, 200)
            val result = groqService!!.query(
                userMessage = groqUserPrompt,
                conversationHistory = history,
                config = GroqService.QueryConfig(
                    systemPrompt = groqSystemPrompt,
                    maxTokens = groqMaxTokens,
                    historyWindow = historyWindow
                )
            )
            result.fold(
                onSuccess = { response ->
                        val cleanResponse = response.trim()
                        if (cleanResponse.length > 10) {
                            if (hasKbContext && !kbContextForPrompt.isNullOrBlank() && !isResponseAnchoredToContext(cleanResponse, userQuery, kbContextForPrompt)) {
                                AppLogger.log("MainActivity", "Groq ungrounded guard -> trying local LLM fallback")
                            } else {
                                AppLogger.log("MainActivity", "Groq grounded response: ${cleanResponse.length} chars")
                                return@withContext ResponseMeta(
                                    response = cleanResponse,
                                    usedLlm = true,
                                    kbSupported = hasKbContext,
                                    kbSupportScore = kbSupportScore,
                                    kbCoverage = kbCoverage,
                                    kbUnknownRatio = kbUnknownRatio,
                                    enforcedKbAbstention = false
                                )
                            }
                            AppLogger.log("MainActivity", "Groq response rejected by guard, fallback")
                        } else {
                            AppLogger.log("MainActivity", "Groq response too short (${cleanResponse.length}), fallback")
                        }
                },
                onFailure = { error ->
                    AppLogger.log("MainActivity", "✗ Groq falló: ${error.message}")
                }
            )
        }

        if (!ONLINE_ONLY_VISIT_MODE && isLlamaEnabled && isLlamaLoaded && llamaService != null) {
            val finalSystemPrompt = if (hasKbContext) {
                "$effectiveSystemPrompt\nReglas obligatorias: usa SOLO el CONTEXTO KB como fuente factual; parafrasea en lenguaje natural (no copies literal); si falta evidencia, dilo explícitamente; no inventes; responde de forma completa."
            } else if (allowGeneralLlmMode) {
                "$effectiveSystemPrompt\nNo hay evidencia suficiente en la KB para esta consulta. Puedes responder con conocimiento agrícola general, aclara que es orientación general y qué datos faltan para mayor precisión. No inventes cifras ni hechos específicos no verificables."
            } else {
                effectiveSystemPrompt
            }
            val baseMaxTokens = if (STRICT_TERMINAL_PARITY_MODE) maxOf(advancedMaxTokens, PARITY_MIN_MAX_TOKENS) else maxOf(advancedMaxTokens, 250)
            val effectiveMaxTokens = if (hasKbContext) maxOf(baseMaxTokens, PARITY_MIN_MAX_TOKENS) else baseMaxTokens

            try {
                val result = llamaService!!.generateAgriResponse(
                    userQuery = userQuery,
                    contextFromKB = contextToPass,
                    maxTokens = effectiveMaxTokens,
                    maxContextLength = effectiveContextLength,
                    systemPrompt = finalSystemPrompt
                )

                result.fold(
                    onSuccess = { response ->
                        val cleanResponse = sanitizeAssistantResponse(response).trim()
                        val kbGuardActive = hasKbContext
                        if (cleanResponse.length > 10) {
                            val malformed = kbGuardActive && isMalformedLlmResponse(cleanResponse)
                            val noInfoStyle = kbGuardActive && isNoInfoStyleResponse(cleanResponse)
                            val incomplete = kbGuardActive && !isResponseComplete(cleanResponse)
                            val ungrounded = kbGuardActive && !kbContextForPrompt.isNullOrBlank() &&
                                !isResponseAnchoredToContext(cleanResponse, userQuery, kbContextForPrompt)

                            if (malformed || noInfoStyle || incomplete || ungrounded) {
                                AppLogger.log(
                                    "MainActivity",
                                    "LLM guard reject -> kb=$kbGuardActive malformed=$malformed noInfo=$noInfoStyle incomplete=$incomplete ungrounded=$ungrounded len=${cleanResponse.length} preview='${cleanResponse.take(120)}'"
                                )
                                return@withContext ResponseMeta(
                                    response = buildKbGroundedFallbackResponse(
                                        userQuery = userQuery,
                                        bestMatch = bestMatch,
                                        combinedKBContext = combinedKBContext,
                                        grounding = groundingAssessment,
                                        failureReason = "llm_guard_reject"
                                    ),
                                    usedLlm = false,
                                    kbSupported = kbGuardActive,
                                    kbSupportScore = kbSupportScore,
                                    kbCoverage = kbCoverage,
                                    kbUnknownRatio = kbUnknownRatio,
                                    enforcedKbAbstention = false
                                )
                            }
                            AppLogger.log("MainActivity", "LLM response: ${cleanResponse.length} chars mode=$mode")
                            return@withContext ResponseMeta(
                                response = cleanResponse,
                                usedLlm = true,
                                kbSupported = hasKbContext,
                                kbSupportScore = kbSupportScore,
                                kbCoverage = kbCoverage,
                                kbUnknownRatio = kbUnknownRatio,
                                enforcedKbAbstention = false
                            )
                        } else {
                            AppLogger.log(
                                "MainActivity",
                                "LLM response too short (${cleanResponse.length}) mode=$mode kb=$hasKbContext ctxLen=${contextToPass?.length ?: 0}, fallback"
                            )
                        }
                    },
                    onFailure = { error ->
                        AppLogger.log("MainActivity", "LLM error: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                AppLogger.log("MainActivity", "LLM exception: ${e.message}")
            }
        } else {
            AppLogger.log("MainActivity", "LLM unavailable: enabled=$isLlamaEnabled loaded=$isLlamaLoaded")
            AppLogger.log("MainActivity", "════════════════════════════════════════════════════════════")

            withContext(Dispatchers.Main) {
                uiStatus = "LLM no disponible"
            }

            val kbSignal = hasKbContext || hasRelatedKbSignal
            val unavailableResponse = when {
                kbSignal -> buildKbGroundedFallbackResponse(
                    userQuery = userQuery,
                    bestMatch = bestMatch,
                    combinedKBContext = combinedKBContext,
                    grounding = groundingAssessment,
                    failureReason = "llm_unavailable"
                )
                allowGeneralLlmMode -> buildLlmTemporaryFailureResponse()
                else -> buildKbInsufficientEvidenceResponse(userQuery, groundingAssessment)
            }
            return@withContext ResponseMeta(
                response = unavailableResponse,
                usedLlm = false,
                kbSupported = kbSignal,
                kbSupportScore = kbSupportScore,
                kbCoverage = kbCoverage,
                kbUnknownRatio = kbUnknownRatio,
                enforcedKbAbstention = !kbSignal && !allowGeneralLlmMode
            )
        }

        AppLogger.log(
            "MainActivity",
            "Fallback: no reliable LLM response mode=$mode kb=$hasKbContext related=$hasRelatedKbSignal llmAvailable=$llmAvailable"
        )

        withContext(Dispatchers.Main) {
            uiStatus = "No se pudo generar respuesta confiable"
        }

        val kbSignal = hasKbContext || hasRelatedKbSignal
        val fallbackResponse = when {
            kbSignal -> buildKbGroundedFallbackResponse(
                userQuery = userQuery,
                bestMatch = bestMatch,
                combinedKBContext = combinedKBContext,
                grounding = groundingAssessment,
                failureReason = "llm_no_reliable_response"
            )
            allowGeneralLlmMode -> buildLlmTemporaryFailureResponse()
            else -> buildKbInsufficientEvidenceResponse(userQuery, groundingAssessment)
        }

        return@withContext ResponseMeta(
            response = fallbackResponse,
            usedLlm = false,
            kbSupported = kbSignal,
            kbSupportScore = kbSupportScore,
            kbCoverage = kbCoverage,
            kbUnknownRatio = kbUnknownRatio,
            enforcedKbAbstention = !kbSignal && !allowGeneralLlmMode
        )
    }

    private fun buildKbGroundedFallbackResponse(
        userQuery: String,
        bestMatch: SemanticSearchHelper.MatchResult?,
        combinedKBContext: String?,
        grounding: SemanticSearchHelper.GroundingAssessment?,
        failureReason: String
    ): String {
        val composed = KbFallbackComposer.compose(
            KbFallbackComposer.Input(
                userQuery = userQuery,
                topMatchQuestion = bestMatch?.matchedQuestion,
                topMatchAnswer = bestMatch?.answer,
                combinedContext = combinedKBContext,
                unknownQueryTokens = grounding?.unknownQueryTokens ?: emptySet(),
                maxPoints = 4
            )
        )

        if (composed != null) {
            AppLogger.log(
                "MainActivity",
                "KB fallback generated reason=$failureReason source=${composed.source} keyPoints=${composed.keyPoints} topMatchId=${bestMatch?.entryId ?: -1} score=${String.format("%.2f", bestMatch?.similarityScore ?: 0f)}"
            )
            return composed.response
        }

        AppLogger.log(
            "MainActivity",
            "KB fallback unavailable reason=$failureReason -> abstain response"
        )
        return buildKbInsufficientEvidenceResponse(userQuery, grounding)
    }

    private fun buildLlmTemporaryFailureResponse(): String {
        return "No pude generar una respuesta confiable en este momento. Intenta nuevamente en unos segundos."
    }

    private fun buildKbInsufficientEvidenceResponse(
        userQuery: String,
        grounding: SemanticSearchHelper.GroundingAssessment?
    ): String {
        val missingTopics = grounding?.unknownQueryTokens
            ?.filter { it.length >= 4 }
            ?.take(2)
            ?.joinToString(", ")
            .orEmpty()

        val normalizedQuestion = userQuery.trim().replace(Regex("\\s+"), " ")
        val focus = if (missingTopics.isNotBlank()) {
            " (tema detectado sin respaldo en KB: $missingTopics)"
        } else {
            ""
        }

        return "No tengo suficiente información en la base de conocimiento para responder con seguridad sobre \"$normalizedQuestion\"$focus.\n\n" +
            "Prefiero no inventar datos. Si quieres, puedo ayudarte con temas de la KB como cultivo, plagas, riego, fertilización o diagnóstico."
    }

    private fun isResponseAnchoredToContext(
        response: String,
        userQuery: String,
        kbContext: String
    ): Boolean {
        val responseTokens = extractInformativeTokens(response)
        if (responseTokens.isEmpty()) return true
        val contextTokens = extractInformativeTokens(kbContext)
        val queryTokens = extractInformativeTokens(userQuery)
        val allowedTokens = contextTokens + queryTokens + setOf(
            "recomendado", "recomendacion", "paso", "pasos", "opcion", "opciones",
            "importante", "riesgo", "control", "prevencion", "tratamiento", "siembra",
            "cultivo", "riego", "fertilizacion", "plaga", "enfermedad"
        )
        val unsupportedTokens = responseTokens - allowedTokens
        val unsupportedRatio = unsupportedTokens.size.toFloat() / responseTokens.size.toFloat()
        return unsupportedRatio <= 0.55f
    }

    private fun extractInformativeTokens(text: String): Set<String> {
        val normalized = text.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return emptySet()
        val stopWords = setOf(
            "de", "del", "la", "las", "el", "los", "y", "o", "a", "en", "con", "por",
            "para", "al", "un", "una", "unos", "unas", "que", "me", "te", "se",
            "mi", "tu", "su", "sobre", "acerca", "como", "cuando", "donde", "porque"
        )
        return normalized
            .split(Regex("\\s+"))
            .map {
                when {
                    it.length > 4 && it.endsWith("es") -> it.dropLast(2)
                    it.length > 3 && it.endsWith("s") -> it.dropLast(1)
                    else -> it
                }
            }
            .filter { it.length >= 4 && it !in stopWords }
            .toSet()
    }

    private fun isLikelyAgriculturalQuery(text: String): Boolean {
        val tokens = extractInformativeTokens(text)
        if (tokens.isEmpty()) return false
        val agriKeywords = setOf(
            "cultivo", "siembra", "sembrar", "plantar", "cosecha", "regar", "riego",
            "fertilizar", "fertilizante", "abono", "plaga", "enfermedad", "hongo",
            "pulgon", "mosca", "gusano", "roya", "tizon", "suelo", "huerto", "campo",
            "tomate", "maiz", "papa", "frijol", "cafe", "cebolla", "yuca", "platano",
            "aguacate", "lechuga", "zanahoria", "arroz", "banano"
        )
        return tokens.any { token ->
            token in agriKeywords || token.startsWith("cultiv") || token.startsWith("sembr")
        }
    }

    private fun isNoInfoStyleResponse(text: String): Boolean {
        val lower = text.lowercase()
        return listOf(
            "no tengo información",
            "no tengo suficiente información",
            "no dispongo de información",
            "no tengo datos",
            "no puedo responder",
            "no se",
            "no sé"
        ).any { lower.contains(it) }
    }

    private fun isMalformedLlmResponse(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower.isBlank()) return true
        if (lower.contains("<|") || lower.contains("|>")) return true
        if (lower == "user" || lower == "assistant" || lower == "usuario" || lower == "asistente") return true

        val cleanedRoleTokens = lower
            .replace(Regex("<\\|[^>]+\\|>"), "")
            .replace("assistant", "")
            .replace("asistente", "")
            .replace("user", "")
            .replace("usuario", "")
            .trim()
        return cleanedRoleTokens.isBlank()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceHelper?.release()
        semanticSearchHelper?.release()
        plantDiseaseClassifier?.release()
        cameraHelper?.release()
        if (!ONLINE_ONLY_VISIT_MODE) {
            lifecycleScope.launch {
                llamaService?.unload()
            }
        }
    }
}

@Composable
fun AgroChatApp(
    currentMode: AppMode,
    messages: List<ChatMessage>,
    lastResponse: String,
    statusMessage: String,
    isModelReady: Boolean,
    isProcessing: Boolean,
    isListening: Boolean,
    isOnlineMode: Boolean,
    isLlamaEnabled: Boolean,
    isLlamaLoaded: Boolean,
    llamaModelStatusText: String,
    llamaModelPathText: String,
    isLlamaDownloading: Boolean,
    llamaDownloadFailed: Boolean,
    showSettingsDialog: Boolean,
    isDiagnosticReady: Boolean,
    isDiagnosing: Boolean,
    capturedBitmap: Bitmap?,
    lastDiagnosis: DiseaseResult?,
    onSendMessage: (String) -> Unit,
    onMicClick: () -> Unit,
    onModeChange: (AppMode) -> Unit,
    onSettingsClick: () -> Unit,
    onDismissSettings: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onToggleLlama: (Boolean) -> Unit,
    onRetryLlamaDownload: () -> Unit,
    onCaptureImage: (Bitmap) -> Unit,
    onClearCapture: () -> Unit,
    onDiagnosisToChat: (DiseaseResult) -> Unit,
    onOpenGallery: () -> Unit,
    showLogsDialog: Boolean,
    onShowLogs: () -> Unit,
    onDismissLogs: () -> Unit,
    // Configuración avanzada
    advancedMaxTokens: Int,
    advancedSimilarityThreshold: Float,
    advancedKbFastPathThreshold: Float,
    advancedContextRelevanceThreshold: Float,
    advancedSystemPrompt: String,
    advancedUseLlmForAll: Boolean,
    advancedContextLength: Int,
    advancedDetectGreetings: Boolean,
    advancedChatHistoryEnabled: Boolean,
    advancedChatHistorySize: Int,
    onSaveAdvancedSettings: (Int, Float, Float, Float, String, Boolean, Int, Boolean, Boolean, Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AgroColors.Background, AgroColors.GradientEnd)))
    ) {
        AnimatedContent(
            targetState = currentMode,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "mode"
        ) { mode ->
            when (mode) {
                AppMode.VOICE -> VoiceModeScreen(
                    lastResponse, statusMessage, isModelReady, isProcessing, isListening, isOnlineMode, 
                    isDiagnosticReady, onMicClick, onSettingsClick, onShowLogs,
                    onSwitchToChat = { onModeChange(AppMode.CHAT) },
                    onSwitchToCamera = { onModeChange(AppMode.CAMERA) }
                )
                AppMode.CHAT -> ChatModeScreen(
                    messages = messages,
                    statusMessage = statusMessage,
                    isModelReady = isModelReady,
                    isProcessing = isProcessing,
                    isListening = isListening,
                    isOnlineMode = isOnlineMode,
                    isDiagnosticReady = isDiagnosticReady,
                    onSendMessage = onSendMessage,
                    onMicClick = onMicClick,
                    onSettingsClick = onSettingsClick,
                    onShowLogs = onShowLogs,
                    onSwitchToVoice = { onModeChange(AppMode.VOICE) },
                    onSwitchToCamera = { onModeChange(AppMode.CAMERA) }
                )
                AppMode.CAMERA -> CameraModeScreen(
                    statusMessage = statusMessage,
                    isDiagnosticReady = isDiagnosticReady,
                    isDiagnosing = isDiagnosing,
                    capturedBitmap = capturedBitmap,
                    lastDiagnosis = lastDiagnosis,
                    onCaptureImage = onCaptureImage,
                    onClearCapture = onClearCapture,
                    onDiagnosisToChat = onDiagnosisToChat,
                    onSwitchToChat = { onModeChange(AppMode.CHAT) },
                    onOpenGallery = onOpenGallery
                )
            }
        }
        
        // Diálogo de logs
        if (showLogsDialog) {
            LogsDialog(onDismiss = onDismissLogs)
        }
        
        // Diálogo de configuración
        if (showSettingsDialog) {
            SettingsDialog(
                onDismiss = onDismissSettings,
                onSaveApiKey = onSaveApiKey,
                isLlamaEnabled = isLlamaEnabled,
                isLlamaLoaded = isLlamaLoaded,
                llamaModelStatusText = llamaModelStatusText,
                llamaModelPathText = llamaModelPathText,
                isLlamaDownloading = isLlamaDownloading,
                llamaDownloadFailed = llamaDownloadFailed,
                onToggleLlama = onToggleLlama,
                onRetryLlamaDownload = onRetryLlamaDownload,
                onShowLogs = onShowLogs,
                // Configuración avanzada
                advancedMaxTokens = advancedMaxTokens,
                advancedSimilarityThreshold = advancedSimilarityThreshold,
                advancedKbFastPathThreshold = advancedKbFastPathThreshold,
                advancedContextRelevanceThreshold = advancedContextRelevanceThreshold,
                advancedSystemPrompt = advancedSystemPrompt,
                advancedUseLlmForAll = advancedUseLlmForAll,
                advancedContextLength = advancedContextLength,
                advancedDetectGreetings = advancedDetectGreetings,
                advancedChatHistoryEnabled = advancedChatHistoryEnabled,
                advancedChatHistorySize = advancedChatHistorySize,
                onSaveAdvancedSettings = onSaveAdvancedSettings
            )
        }
    }
}

@Composable
fun VoiceModeScreen(
    lastResponse: String,
    statusMessage: String,
    isModelReady: Boolean,
    isProcessing: Boolean,
    isListening: Boolean,
    isOnlineMode: Boolean,
    isDiagnosticReady: Boolean,
    onMicClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onShowLogs: () -> Unit,
    onSwitchToChat: () -> Unit,
    onSwitchToCamera: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header - Logo y título (compacto)
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_farmifai_logo),
                    contentDescription = "FarmifAI Logo",
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "FarmifAI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AgroColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(stringResource(R.string.assistant_subtitle), style = MaterialTheme.typography.bodySmall, color = AgroColors.TextSecondary)
                }
                // Indicador online/offline + settings
                OnlineIndicator(isOnlineMode, onSettingsClick)
            }
            
            // Área de respuesta (scrolleable si es necesario)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (lastResponse.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        item {
                            ResponseCard(lastResponse, isProcessing)
                        }
                    }
                } else {
                    // Placeholder cuando no hay respuesta
                    Text(
                        "Toca el micrófono y pregunta sobre agricultura",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AgroColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
            
            // Área del micrófono (siempre visible, tamaño fijo)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                BigMicrophoneButton(isListening, isProcessing, isModelReady, onMicClick)
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = statusMessage, 
                    style = MaterialTheme.typography.titleMedium, 
                    color = if (isListening) AgroColors.MicActive else AgroColors.TextSecondary, 
                    fontWeight = if (isListening) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        
        // Botones flotantes
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Botón de cámara (siempre visible, muestra estado si no está listo)
            FloatingActionButton(
                onClick = onSwitchToCamera,
                containerColor = if (isDiagnosticReady) AgroColors.Accent else AgroColors.SurfaceLight,
                contentColor = if (isDiagnosticReady) Color.White else AgroColors.TextSecondary,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.CameraAlt, "Diagnóstico Visual", modifier = Modifier.size(24.dp))
            }
            
            // Botón para cambiar a chat
            FloatingActionButton(
                onClick = onSwitchToChat,
                containerColor = AgroColors.SurfaceLight,
                contentColor = AgroColors.TextPrimary
            ) {
                Icon(Icons.Default.ChatBubble, "Modo Chat")
            }
        }
    }
}

@Composable
fun ResponseCard(text: String, isProcessing: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmer by infiniteTransition.animateFloat(0.3f, 0.6f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "s")
    
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = AgroColors.Surface.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isProcessing) AgroColors.Accent.copy(alpha = shimmer) else AgroColors.SurfaceLight)
    ) {
        Column(Modifier.padding(20.dp)) {
            if (isProcessing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), AgroColors.Accent, strokeWidth = 2.dp)
                    Text("Pensando...", color = AgroColors.Accent, fontWeight = FontWeight.Medium)
                }
            } else {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = AgroColors.TextPrimary, lineHeight = 26.sp)
            }
        }
    }
}

@Composable
fun BigMicrophoneButton(isListening: Boolean, isProcessing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic")
    val scale by infiniteTransition.animateFloat(1f, 1.12f, infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse), label = "sc")
    val ringScale by infiniteTransition.animateFloat(1f, 1.8f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "rs")
    val ringAlpha by infiniteTransition.animateFloat(0.5f, 0f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "ra")
    val buttonColor by animateColorAsState(when { isListening -> AgroColors.MicActive; isProcessing -> AgroColors.SurfaceLight; else -> AgroColors.PrimaryLight }, tween(300), label = "bc")

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        if (isListening) {
            Box(Modifier.size(180.dp).scale(ringScale).border(3.dp, AgroColors.MicActive.copy(alpha = ringAlpha), CircleShape))
            Box(Modifier.size(180.dp).scale(ringScale * 0.6f).border(2.dp, AgroColors.MicActive.copy(alpha = ringAlpha * 0.5f), CircleShape))
        }
        
        Surface(
            modifier = Modifier.size(180.dp).scale(if (isListening) scale else 1f).shadow(if (isListening) 32.dp else 16.dp, CircleShape, spotColor = buttonColor).clip(CircleShape).clickable(enabled = enabled && !isProcessing) { onClick() },
            shape = CircleShape,
            color = buttonColor
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(56.dp), Color.White, strokeWidth = 4.dp)
                } else {
                    Icon(if (isListening) Icons.Default.MicOff else Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(72.dp))
                }
            }
        }
    }
}

@Composable
fun ChatModeScreen(
    messages: List<ChatMessage>,
    statusMessage: String,
    isModelReady: Boolean,
    isProcessing: Boolean,
    isListening: Boolean,
    isOnlineMode: Boolean,
    isDiagnosticReady: Boolean,
    onSendMessage: (String) -> Unit,
    onMicClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onShowLogs: () -> Unit,
    onSwitchToVoice: () -> Unit,
    onSwitchToCamera: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Column(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxWidth(), color = AgroColors.Surface, tonalElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_farmifai_logo),
                        contentDescription = "FarmifAI Logo",
                        modifier = Modifier.size(36.dp)
                    )
                    Column {
                        Text(
                            "FarmifAI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AgroColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = AgroColors.TextSecondary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OnlineIndicator(isOnlineMode, onSettingsClick)
                    // Botón de cámara siempre visible
                    IconButton(
                        onClick = onSwitchToCamera, 
                        modifier = Modifier.size(48.dp).background(
                            if (isDiagnosticReady) AgroColors.Accent else AgroColors.SurfaceLight, 
                            CircleShape
                        )
                    ) {
                        Icon(Icons.Default.CameraAlt, "Diagnóstico Visual", tint = if (isDiagnosticReady) Color.White else AgroColors.TextSecondary)
                    }
                    IconButton(onClick = onSwitchToVoice, Modifier.size(48.dp).background(AgroColors.SurfaceLight, CircleShape)) {
                        Icon(Icons.Default.RecordVoiceOver, "Modo Voz", tint = AgroColors.Accent)
                    }
                }
            }
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            if (messages.isEmpty()) item { EmptyStateChat() }
            items(messages) { message ->
                ModernMessageBubble(message = message)
            }
            if (isProcessing) item { ModernTypingIndicator() }
            if (isListening) item { ModernListeningIndicator() }
        }

        Surface(Modifier.fillMaxWidth(), color = AgroColors.Surface, tonalElevation = 8.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(), 
                verticalAlignment = Alignment.Bottom, 
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallMicButton(isListening, isModelReady && !isProcessing, onMicClick)
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe tu pregunta...", color = AgroColors.TextSecondary) },
                    enabled = isModelReady && !isProcessing && !isListening,
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgroColors.Accent,
                        unfocusedBorderColor = AgroColors.SurfaceLight,
                        focusedContainerColor = AgroColors.SurfaceLight,
                        unfocusedContainerColor = AgroColors.SurfaceLight,
                        cursorColor = AgroColors.Accent,
                        focusedTextColor = AgroColors.TextPrimary,
                        unfocusedTextColor = AgroColors.TextPrimary
                    )
                )
                IconButton(
                    onClick = { if (inputText.isNotBlank()) { onSendMessage(inputText); inputText = "" } },
                    enabled = isModelReady && !isProcessing && inputText.isNotBlank() && !isListening,
                    modifier = Modifier.size(48.dp).background(if (inputText.isNotBlank()) AgroColors.Accent else AgroColors.SurfaceLight, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = if (inputText.isNotBlank()) Color.White else AgroColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
fun EmptyStateChat() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🌾", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AgroColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_chat_hint), style = MaterialTheme.typography.bodyMedium, color = AgroColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun SmallMicButton(isListening: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "sm")
    val scale by infiniteTransition.animateFloat(1f, 1.1f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "s")
    val buttonColor by animateColorAsState(if (isListening) AgroColors.MicActive else AgroColors.PrimaryLight, tween(200), label = "c")

    IconButton(
        onClick = onClick, 
        enabled = enabled, 
        modifier = Modifier.size(48.dp).scale(if (isListening) scale else 1f).background(buttonColor, CircleShape)
    ) {
        Icon(if (isListening) Icons.Default.MicOff else Icons.Default.Mic, null, tint = Color.White)
    }
}

@Composable
fun ModernMessageBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (message.isUser) AgroColors.PrimaryLight else AgroColors.Surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (message.isUser) 20.dp else 4.dp, bottomEnd = if (message.isUser) 4.dp else 20.dp),
            modifier = Modifier.widthIn(max = 300.dp).padding(4.dp),
            border = if (!message.isUser) androidx.compose.foundation.BorderStroke(1.dp, AgroColors.SurfaceLight) else null
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(message.text, style = MaterialTheme.typography.bodyLarge, color = AgroColors.TextPrimary, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
fun ModernTypingIndicator() {
    Row(Modifier.fillMaxWidth(), Arrangement.Start) {
        Surface(color = AgroColors.Surface, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AgroColors.SurfaceLight)) {
            Row(Modifier.padding(16.dp), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
                val t = rememberInfiniteTransition(label = "t")
                repeat(3) { i ->
                    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 200), RepeatMode.Reverse), label = "d$i")
                    Box(Modifier.size(10.dp).background(AgroColors.Accent.copy(alpha = a), CircleShape))
                }
            }
        }
    }
}

@Composable
fun ModernListeningIndicator() {
    Row(Modifier.fillMaxWidth(), Arrangement.Center) {
        Surface(color = AgroColors.MicActive.copy(alpha = 0.15f), shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AgroColors.MicActive.copy(alpha = 0.3f))) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), Arrangement.spacedBy(5.dp), Alignment.CenterVertically) {
                val t = rememberInfiniteTransition(label = "l")
                repeat(5) { i ->
                    val h by t.animateFloat(8f, 28f, infiniteRepeatable(tween(350, delayMillis = i * 80), RepeatMode.Reverse), label = "b$i")
                    Box(Modifier.width(4.dp).height(h.dp).background(AgroColors.MicActive, RoundedCornerShape(2.dp)))
                }
                Spacer(Modifier.width(10.dp))
                Text("Escuchando...", style = MaterialTheme.typography.bodyMedium, color = AgroColors.MicActive, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun OnlineIndicator(isOnline: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isOnline) AgroColors.Accent.copy(alpha = 0.2f) else AgroColors.SurfaceLight)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (isOnline) Icons.Default.Cloud else Icons.Default.CloudOff,
            contentDescription = if (isOnline) "Online" else "Offline",
            tint = if (isOnline) AgroColors.Accent else AgroColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isOnline) "Online" else "Sin red",
            style = MaterialTheme.typography.labelSmall,
            color = if (isOnline) AgroColors.Accent else AgroColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    isLlamaEnabled: Boolean,
    isLlamaLoaded: Boolean,
    llamaModelStatusText: String,
    llamaModelPathText: String,
    isLlamaDownloading: Boolean,
    llamaDownloadFailed: Boolean,
    onToggleLlama: (Boolean) -> Unit,
    onRetryLlamaDownload: () -> Unit,
    onShowLogs: () -> Unit,
    // Configuración avanzada
    advancedMaxTokens: Int,
    advancedSimilarityThreshold: Float,
    advancedKbFastPathThreshold: Float,
    advancedContextRelevanceThreshold: Float,
    advancedSystemPrompt: String,
    advancedUseLlmForAll: Boolean,
    advancedContextLength: Int,
    advancedDetectGreetings: Boolean,
    advancedChatHistoryEnabled: Boolean,
    advancedChatHistorySize: Int,
    onSaveAdvancedSettings: (Int, Float, Float, Float, String, Boolean, Int, Boolean, Boolean, Int) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    val context = LocalContext.current
    val appPrefs = remember { context.getSharedPreferences("agrochat_prefs", Context.MODE_PRIVATE) }
    var selectedLanguage by remember { 
        mutableStateOf(
            context.getSharedPreferences("farmifai_prefs", Context.MODE_PRIVATE)
                .getString("language", "es") ?: "es"
        )
    }
    LaunchedEffect(Unit) {
        apiKey = appPrefs.getString("groq_api_key", "") ?: ""
    }
    
    // Estados locales para configuración avanzada (se sincronizan al guardar)
    var showAdvanced by remember { mutableStateOf(false) }
    var localMaxTokens by remember { mutableStateOf(advancedMaxTokens) }
    // Mostrar los umbrales en porcentaje (0..100) en la UI, convertir a 0..1 al guardar
    var localSimThreshold by remember { mutableStateOf(advancedSimilarityThreshold * 100f) }
    var localKbThreshold by remember { mutableStateOf(advancedKbFastPathThreshold * 100f) }
    var localCtxRelThreshold by remember { mutableStateOf(advancedContextRelevanceThreshold * 100f) }
    var localSystemPrompt by remember { mutableStateOf(advancedSystemPrompt) }
    var localUseLlmForAll by remember { mutableStateOf(advancedUseLlmForAll) }
    var localContextLength by remember { mutableStateOf(advancedContextLength) }
    var localDetectGreetings by remember { mutableStateOf(advancedDetectGreetings) }
    var localChatHistoryEnabled by remember { mutableStateOf(advancedChatHistoryEnabled) }
    var localChatHistorySize by remember { mutableStateOf(advancedChatHistorySize) }
    
    LaunchedEffect(
        advancedMaxTokens,
        advancedSimilarityThreshold,
        advancedKbFastPathThreshold,
        advancedContextRelevanceThreshold,
        advancedSystemPrompt,
        advancedUseLlmForAll,
        advancedContextLength,
        advancedDetectGreetings,
        advancedChatHistoryEnabled,
        advancedChatHistorySize
    ) {
        localMaxTokens = advancedMaxTokens
        localSimThreshold = advancedSimilarityThreshold * 100f
        localKbThreshold = advancedKbFastPathThreshold * 100f
        localCtxRelThreshold = advancedContextRelevanceThreshold * 100f
        localSystemPrompt = advancedSystemPrompt
        localUseLlmForAll = advancedUseLlmForAll
        localContextLength = advancedContextLength
        localDetectGreetings = advancedDetectGreetings
        localChatHistoryEnabled = advancedChatHistoryEnabled
        localChatHistorySize = advancedChatHistorySize
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AgroColors.Surface,
        titleContentColor = AgroColors.TextPrimary,
        textContentColor = AgroColors.TextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = AgroColors.Accent)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.settings))
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Sección Idioma
                Surface(
                    color = AgroColors.SurfaceLight,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🌐", fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.language_label),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AgroColors.TextPrimary
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedLanguage == "es",
                                onClick = { 
                                    if (selectedLanguage != "es") {
                                        selectedLanguage = "es"
                                        context.getSharedPreferences("farmifai_prefs", Context.MODE_PRIVATE)
                                            .edit().putString("language", "es").apply()
                                        // Recreate activity to apply new locale
                                        (context as? ComponentActivity)?.recreate()
                                    }
                                },
                                label = { Text("🇪🇸 Español") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AgroColors.Accent,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = selectedLanguage == "en",
                                onClick = { 
                                    if (selectedLanguage != "en") {
                                        selectedLanguage = "en"
                                        context.getSharedPreferences("farmifai_prefs", Context.MODE_PRIVATE)
                                            .edit().putString("language", "en").apply()
                                        // Recreate activity to apply new locale
                                        (context as? ComponentActivity)?.recreate()
                                    }
                                },
                                label = { Text("🇺🇸 English") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AgroColors.Accent,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        Text(
                            stringResource(R.string.language_change_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = AgroColors.TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                
                HorizontalDivider(color = AgroColors.SurfaceLight)
                
                if (!ONLINE_ONLY_VISIT_MODE) {
                    // Sección LLM Local (Llama)
                    Surface(
                        color = AgroColors.SurfaceLight,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "🦙",
                                            fontSize = 20.sp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "LLM Local (Llama)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AgroColors.TextPrimary
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        llamaModelStatusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            isLlamaLoaded -> AgroColors.Accent
                                            llamaDownloadFailed -> Color(0xFFE57373)
                                            isLlamaDownloading -> AgroColors.TextSecondary
                                            else -> AgroColors.TextSecondary
                                        }
                                    )
                                }
                                Switch(
                                    checked = isLlamaEnabled,
                                    onCheckedChange = { onToggleLlama(it) },
                                    enabled = isLlamaLoaded,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AgroColors.Accent,
                                        checkedTrackColor = AgroColors.Accent.copy(alpha = 0.5f),
                                        uncheckedThumbColor = AgroColors.TextSecondary,
                                        uncheckedTrackColor = AgroColors.SurfaceLight
                                    )
                                )
                            }

                            // Botón de reintentar si falló la descarga
                            if (llamaDownloadFailed && !isLlamaDownloading) {
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = onRetryLlamaDownload,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AgroColors.Accent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🔄 Reintentar descarga")
                                }
                            }

                            // Indicador de descarga en progreso
                            if (isLlamaDownloading) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = AgroColors.Accent
                                )
                            }
                        }
                    }

                    if (isLlamaEnabled && isLlamaLoaded) {
                        Text(
                            "✓ Respuestas inteligentes offline con IA generativa",
                            style = MaterialTheme.typography.bodySmall,
                            color = AgroColors.Accent
                        )
                    } else if (!isLlamaLoaded) {
                        Text(
                            if (llamaModelPathText.isNotBlank()) {
                                "Copia un .gguf a: $llamaModelPathText"
                            } else {
                                "Copia un modelo .gguf a la carpeta de la app para habilitar"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AgroColors.TextSecondary
                        )
                    }
                } else {
                    Surface(
                        color = AgroColors.SurfaceLight,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Modo visita activo: respuestas solo con modelo online.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AgroColors.Accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        )
                    }
                }
                
                HorizontalDivider(color = AgroColors.SurfaceLight)
                
                // Sección Groq Online
                Text(
                    "☁️ LLM Online (Groq)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AgroColors.TextPrimary
                )
                
                Text(
                    "Para respuestas más fluidas cuando hay internet.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    "Obtén tu key gratis en: console.groq.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = AgroColors.Accent
                )
                
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key de Groq") },
                    placeholder = { Text("gsk_...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgroColors.Accent,
                        unfocusedBorderColor = AgroColors.SurfaceLight,
                        focusedLabelColor = AgroColors.Accent,
                        unfocusedLabelColor = AgroColors.TextSecondary,
                        cursorColor = AgroColors.Accent,
                        focusedTextColor = AgroColors.TextPrimary,
                        unfocusedTextColor = AgroColors.TextPrimary
                    )
                )
                
                HorizontalDivider(color = AgroColors.SurfaceLight)
                
                // ===== SECCIÓN CONFIGURACIÓN AVANZADA (colapsable) =====
                Surface(
                    color = AgroColors.SurfaceLight,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { showAdvanced = !showAdvanced }
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚙️", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Configuración Avanzada",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AgroColors.TextPrimary
                                )
                            }
                            Icon(
                                imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showAdvanced) "Colapsar" else "Expandir",
                                tint = AgroColors.TextSecondary
                            )
                        }
                        
                        // Contenido expandible
                        AnimatedVisibility(visible = showAdvanced) {
                            Column(
                                modifier = Modifier.padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Max Tokens
                                Text("Longitud de respuestas", style = MaterialTheme.typography.labelMedium, color = AgroColors.TextSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Slider(
                                        value = localMaxTokens.toFloat(),
                                        onValueChange = { localMaxTokens = it.toInt().coerceIn(250, 900) },
                                        valueRange = 250f..900f,
                                        steps = 25,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = AgroColors.Accent,
                                            activeTrackColor = AgroColors.Accent
                                        )
                                    )
                                    Text("$localMaxTokens", style = MaterialTheme.typography.bodySmall, color = AgroColors.TextPrimary, modifier = Modifier.width(55.dp))
                                }
                                
                                // Context Length
                                Text("Longitud de contexto KB", style = MaterialTheme.typography.labelMedium, color = AgroColors.TextSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Slider(
                                        value = localContextLength.toFloat(),
                                        onValueChange = { localContextLength = it.toInt() },
                                        valueRange = 300f..3000f,
                                        steps = 17,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = AgroColors.Accent,
                                            activeTrackColor = AgroColors.Accent
                                        )
                                    )
                                    Text("$localContextLength", style = MaterialTheme.typography.bodySmall, color = AgroColors.TextPrimary, modifier = Modifier.width(55.dp))
                                }
                                
                                // KB Fast Path Threshold (mostrar como porcentaje 0..100)
                                Text("Umbral KB directa (sin LLM): ${localKbThreshold.toInt()}%", style = MaterialTheme.typography.labelMedium, color = AgroColors.TextSecondary)
                                Slider(
                                    value = localKbThreshold,
                                    onValueChange = { localKbThreshold = it },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AgroColors.Accent,
                                        activeTrackColor = AgroColors.Accent
                                    )
                                )
                                Text("Mayor = responde directo desde KB solo con evidencia muy alta", style = MaterialTheme.typography.bodySmall, color = AgroColors.TextSecondary)
                                
                                // Similarity Threshold (mostrar como porcentaje 0..100)
                                Text("Umbral mínimo similitud: ${localSimThreshold.toInt()}%", style = MaterialTheme.typography.labelMedium, color = AgroColors.TextSecondary)
                                Slider(
                                    value = localSimThreshold,
                                    onValueChange = { localSimThreshold = it },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AgroColors.Accent,
                                        activeTrackColor = AgroColors.Accent
                                    )
                                )
                                
                                Text("Se aplica rango seguro interno para evitar configuraciones extremas", style = MaterialTheme.typography.bodySmall, color = AgroColors.TextSecondary)
                                
                                // Context Relevance Threshold (mostrar como porcentaje 0..100)
                                Text("Umbral contexto relevante: ${localCtxRelThreshold.toInt()}%", style = MaterialTheme.typography.labelMedium, color = AgroColors.TextSecondary)
                                Slider(
                                    value = localCtxRelThreshold,
                                    onValueChange = { localCtxRelThreshold = it },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AgroColors.Accent,
                                        activeTrackColor = AgroColors.Accent
                                    )
                                )
                                Text("Si KB score < este valor, el asistente admite falta de evidencia en lugar de inventar", style = MaterialTheme.typography.bodySmall, color = AgroColors.TextSecondary)
                                
                                HorizontalDivider(color = AgroColors.Surface)
                                
                                // Toggles
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Detectar saludos (KB directa)", style = MaterialTheme.typography.bodySmall, color = AgroColors.TextPrimary)
                                    Switch(
                                        checked = localDetectGreetings,
                                        onCheckedChange = { localDetectGreetings = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = AgroColors.Accent,
                                            checkedTrackColor = AgroColors.Accent.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Usar LLM para todo", style = MaterialTheme.typography.bodySmall, color = AgroColors.TextPrimary)
                                    Switch(
                                        checked = localUseLlmForAll,
                                        onCheckedChange = { localUseLlmForAll = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = AgroColors.Accent,
                                            checkedTrackColor = AgroColors.Accent.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                
                                HorizontalDivider(color = AgroColors.Surface)
                                
                                // Configuración del historial del chat
                                Text("💬 Contexto del Chat", style = MaterialTheme.typography.labelMedium, color = AgroColors.Accent)
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Usar historial del chat", style = MaterialTheme.typography.bodySmall, color = AgroColors.TextPrimary)
                                    Switch(
                                        checked = localChatHistoryEnabled,
                                        onCheckedChange = { localChatHistoryEnabled = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = AgroColors.Accent,
                                            checkedTrackColor = AgroColors.Accent.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                Text("Permite continuar conversaciones y dar contexto", style = MaterialTheme.typography.bodySmall, color = AgroColors.TextSecondary)
                                
                                if (localChatHistoryEnabled) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Mensajes de historial: ${localChatHistorySize}", style = MaterialTheme.typography.bodySmall, color = AgroColors.TextPrimary)
                                    Slider(
                                        value = localChatHistorySize.toFloat(),
                                        onValueChange = { localChatHistorySize = it.toInt().coerceIn(1, 20) },
                                        valueRange = 1f..20f,
                                        steps = 18,
                                        colors = SliderDefaults.colors(
                                            thumbColor = AgroColors.Accent,
                                            activeTrackColor = AgroColors.Accent
                                        )
                                    )
                                    Text("Cuántos mensajes anteriores incluir como contexto", style = MaterialTheme.typography.bodySmall, color = AgroColors.TextSecondary)
                                }
                                
                                HorizontalDivider(color = AgroColors.Surface)
                                
                                // System Prompt
                                Text("System Prompt del LLM", style = MaterialTheme.typography.labelMedium, color = AgroColors.TextSecondary)
                                OutlinedTextField(
                                    value = localSystemPrompt,
                                    onValueChange = { localSystemPrompt = it },
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AgroColors.Accent,
                                        unfocusedBorderColor = AgroColors.SurfaceLight,
                                        cursorColor = AgroColors.Accent,
                                        focusedTextColor = AgroColors.TextPrimary,
                                        unfocusedTextColor = AgroColors.TextPrimary
                                    )
                                )
                                
                                // Botón guardar avanzado
                                Button(
                                    onClick = {
                                        onSaveAdvancedSettings(
                                            localMaxTokens,
                                            // Convertir porcentajes 0..100 a fracciones 0..1 para el almacenamiento interno
                                            localSimThreshold / 100f,
                                            localKbThreshold / 100f,
                                            localCtxRelThreshold / 100f,
                                            localSystemPrompt,
                                            localUseLlmForAll,
                                            localContextLength,
                                            localDetectGreetings,
                                            localChatHistoryEnabled,
                                            localChatHistorySize
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = AgroColors.Accent)
                                ) {
                                    Text("💾 Guardar configuración avanzada")
                                }
                            }
                        }
                    }
                }
                
                HorizontalDivider(color = AgroColors.SurfaceLight)
                
                // Sección Depuración
                Surface(
                    color = AgroColors.SurfaceLight,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { onShowLogs() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport, null, tint = AgroColors.TextSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Ver Logs de Depuración",
                                style = MaterialTheme.typography.titleSmall,
                                color = AgroColors.TextPrimary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = AgroColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveApiKey(apiKey)
                    onSaveAdvancedSettings(
                        localMaxTokens,
                        localSimThreshold / 100f,
                        localKbThreshold / 100f,
                        localCtxRelThreshold / 100f,
                        localSystemPrompt,
                        localUseLlmForAll,
                        localContextLength,
                        localDetectGreetings,
                        localChatHistoryEnabled,
                        localChatHistorySize
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = AgroColors.Accent)
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = AgroColors.TextSecondary)
            ) {
                Text("Cerrar")
            }
        }
    )
}

// ==================== PANTALLA DE CÁMARA ====================

@Composable
fun CameraModeScreen(
    statusMessage: String,
    isDiagnosticReady: Boolean,
    isDiagnosing: Boolean,
    capturedBitmap: Bitmap?,
    lastDiagnosis: DiseaseResult?,
    onCaptureImage: (Bitmap) -> Unit,
    onClearCapture: () -> Unit,
    onDiagnosisToChat: (DiseaseResult) -> Unit,
    onSwitchToChat: () -> Unit,
    onOpenGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var cameraHelper by remember { mutableStateOf<CameraHelper?>(null) }
    var isCameraReady by remember { mutableStateOf(false) }
    
    // Inicializar CameraHelper
    DisposableEffect(Unit) {
        cameraHelper = CameraHelper(context)
        onDispose {
            cameraHelper?.release()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AgroColors.Surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📸", fontSize = 28.sp)
                        Column {
                            Text(
                                "Diagnóstico Visual",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AgroColors.TextPrimary
                            )
                            Text(
                                statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = AgroColors.TextSecondary
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = onSwitchToChat,
                        modifier = Modifier
                            .size(40.dp)
                            .background(AgroColors.SurfaceLight, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            "Cerrar",
                            tint = AgroColors.TextPrimary
                        )
                    }
                }
            }
            
            // Área principal
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (capturedBitmap != null) {
                    // Mostrar imagen capturada y resultado
                    CapturedImageView(
                        bitmap = capturedBitmap,
                        diagnosis = lastDiagnosis,
                        isDiagnosing = isDiagnosing,
                        onRetake = onClearCapture,
                        onGetTreatment = { 
                            lastDiagnosis?.let { onDiagnosisToChat(it) }
                        }
                    )
                } else {
                    val currentCameraHelper = cameraHelper
                    if (currentCameraHelper != null) {
                        // Preview de cámara
                        CameraPreview(
                            cameraHelper = currentCameraHelper,
                            lifecycleOwner = lifecycleOwner,
                            onCameraReady = { isCameraReady = true },
                            onCapture = { bitmap -> onCaptureImage(bitmap) }
                        )
                    } else {
                        // Cargando cámara
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AgroColors.Primary)
                        }
                    }
                }
            }
        }
        
        // Instrucciones
        if (capturedBitmap == null && isCameraReady) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
                    .padding(horizontal = 32.dp),
                color = AgroColors.Surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Enfoca una hoja de la planta y toca para capturar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AgroColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Cultivos: Café • Maíz • Papa • Pimiento • Tomate",
                        style = MaterialTheme.typography.bodySmall,
                        color = AgroColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // Botón de galería (esquina inferior izquierda)
        if (capturedBitmap == null) {
            FloatingActionButton(
                onClick = onOpenGallery,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
                    .navigationBarsPadding(),
                containerColor = AgroColors.Surface,
                contentColor = AgroColors.TextPrimary
            ) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = "Seleccionar de galería",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun CameraPreview(
    cameraHelper: CameraHelper,
    lifecycleOwner: LifecycleOwner,
    onCameraReady: () -> Unit,
    onCapture: (Bitmap) -> Unit
) {
    var isCapturing by remember { mutableStateOf(false) }
    
    // Track si la cámara ya fue iniciada con este helper
    var cameraStarted by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Preview de cámara - usar factory para iniciar solo una vez
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    
                    // Iniciar cámara inmediatamente en factory
                    post {
                        if (!cameraStarted) {
                            cameraStarted = true
                            Log.d("CameraPreview", "Iniciando cámara en factory...")
                            cameraHelper.startCamera(
                                lifecycleOwner = lifecycleOwner,
                                previewView = this,
                                callback = object : CameraHelper.CameraCallback {
                                    override fun onCameraReady() {
                                        Log.d("CameraPreview", "Camera ready")
                                        onCameraReady()
                                    }
                                    override fun onCameraError(message: String) {
                                        Log.e("CameraPreview", "Error: $message")
                                    }
                                }
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Botón de captura
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isCapturing) {
                        isCapturing = true
                        Log.d("CameraPreview", "Capturando imagen...")
                        cameraHelper.captureImage(object : CameraHelper.CaptureCallback {
                            override fun onImageCaptured(bitmap: Bitmap) {
                                isCapturing = false
                                Log.d("CameraPreview", "Image captured: ${bitmap.width}x${bitmap.height}")
                                onCapture(bitmap)
                            }
                            override fun onCaptureError(message: String) {
                                isCapturing = false
                                Log.e("CameraPreview", "Capture error: $message")
                            }
                        })
                    },
                color = Color.White,
                shape = CircleShape,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = AgroColors.Primary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Capturar",
                            tint = AgroColors.Primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
        
        // Marco de enfoque
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(280.dp)
                .border(3.dp, AgroColors.Accent.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
        )
    }
}

@Composable
fun CapturedImageView(
    bitmap: Bitmap,
    diagnosis: DiseaseResult?,
    isDiagnosing: Boolean,
    onRetake: () -> Unit,
    onGetTreatment: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Imagen capturada
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 8.dp
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Imagen capturada",
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Resultado del diagnóstico
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AgroColors.Surface,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isDiagnosing) {
                    // Estado: Analizando
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AgroColors.Accent,
                            strokeWidth = 3.dp
                        )
                        Text(
                            "Analizando imagen...",
                            style = MaterialTheme.typography.titleMedium,
                            color = AgroColors.TextPrimary
                        )
                    }
                } else if (diagnosis != null) {
                    // Resultado obtenido
                    val emoji = if (diagnosis.isHealthy) "✅" else "⚠️"
                    val statusColor = if (diagnosis.isHealthy) AgroColors.Accent else Color(0xFFFF9800)
                    
                    Text(
                        "$emoji ${diagnosis.displayName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AgroColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Cultivo
                        Surface(
                            color = AgroColors.SurfaceLight,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "🌿 ${diagnosis.crop}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AgroColors.TextPrimary
                            )
                        }
                        
                        // Confianza
                        Surface(
                            color = statusColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "📊 ${(diagnosis.confidence * 100).toInt()}%",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = statusColor
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(20.dp))
                    
                    // Botones de acción
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Botón Retomar
                        OutlinedButton(
                            onClick = onRetake,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AgroColors.TextSecondary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = Brush.horizontalGradient(
                                    listOf(AgroColors.SurfaceLight, AgroColors.SurfaceLight)
                                )
                            )
                        ) {
                            Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Retomar")
                        }
                        
                        // Botón Ver Tratamiento
                        Button(
                            onClick = onGetTreatment,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AgroColors.Accent
                            )
                        ) {
                            Icon(Icons.Default.ChatBubble, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Tratamiento")
                        }
                    }
                } else {
                    // No se pudo identificar
                    Text(
                        "❓ No se pudo identificar",
                        style = MaterialTheme.typography.titleMedium,
                        color = AgroColors.TextSecondary
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        "Intenta con mejor iluminación o enfocando solo la hoja",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AgroColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Button(
                        onClick = onRetake,
                        colors = ButtonDefaults.buttonColors(containerColor = AgroColors.Accent)
                    ) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Intentar de nuevo")
                    }
                }
            }
        }
    }
}

// ==================== LOGS DIALOG ====================

@Composable
fun LogsDialog(onDismiss: () -> Unit) {
    val logs = remember { mutableStateOf(AppLogger.getLogs()) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var showCopiedToast by remember { mutableStateOf(false) }
    
    // Auto-refresh logs
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            logs.value = AppLogger.getLogs()
        }
    }
    
    // Show toast when copied
    LaunchedEffect(showCopiedToast) {
        if (showCopiedToast) {
            kotlinx.coroutines.delay(2000)
            showCopiedToast = false
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.8f),
        containerColor = AgroColors.Surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔍 Logs de Depuración", color = AgroColors.TextPrimary)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val logsText = logs.value.joinToString("\n")
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("FarmifAI Logs", logsText)
                            clipboard.setPrimaryClip(clip)
                            showCopiedToast = true
                            android.widget.Toast.makeText(context, "✓ Logs copiados al portapapeles", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AgroColors.Accent)
                    ) {
                        Text("📋 Copiar")
                    }
                    OutlinedButton(
                        onClick = { AppLogger.clear(); logs.value = emptyList() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AgroColors.TextSecondary)
                    ) {
                        Text("🗑️ Limpiar")
                    }
                    OutlinedButton(
                        onClick = { logs.value = AppLogger.getLogs() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AgroColors.Accent)
                    ) {
                        Text("🔄 Refrescar")
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                // Logs content
                Surface(
                    color = AgroColors.Background,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .verticalScroll(scrollState)
                    ) {
                        if (logs.value.isEmpty()) {
                            Text(
                                "No hay logs aún",
                                color = AgroColors.TextSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            logs.value.forEach { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        log.contains("Error", ignoreCase = true) || log.contains("✗") -> Color.Red
                                        log.contains("✓") || log.contains("✅") -> Color.Green
                                        log.contains("Warning", ignoreCase = true) || log.contains("⚠") -> Color.Yellow
                                        else -> AgroColors.TextSecondary
                                    },
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                
                // Log count
                Text(
                    "${logs.value.size} entradas",
                    style = MaterialTheme.typography.bodySmall,
                    color = AgroColors.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = AgroColors.Accent)
            }
        }
    )
}

// ==================== PANTALLA DE BIENVENIDA ====================

@Composable
fun WelcomeDownloadScreen(
    downloadItems: List<DownloadItem>,
    currentTipIndex: Int
) {
    val feature = remember(currentTipIndex) {
        AppFeatures.features.getOrElse(currentTipIndex % AppFeatures.features.size) { AppFeatures.features[0] }
    }
    
    // Animación para el tip
    val tipAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(500),
        label = "tipAlpha"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AgroColors.Background, AgroColors.GradientEnd)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            
            // Logo de la app
            Image(
                painter = painterResource(id = R.drawable.ic_farmifai_logo),
                contentDescription = "FarmifAI Logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "FarmifAI",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = AgroColors.TextPrimary
            )
            Text(
                "Tu asistente agrícola con IA",
                style = MaterialTheme.typography.titleMedium,
                color = AgroColors.TextSecondary
            )
            
            Spacer(Modifier.height(48.dp))
            
            // Área de descargas
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AgroColors.Surface,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Preparando tu asistente...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AgroColors.TextPrimary
                    )
                    
                    downloadItems.forEach { item ->
                        DownloadItemRow(item)
                    }
                }
            }
            
            Spacer(Modifier.weight(1f))
            
            // Característica de la app rotativa
            AnimatedContent(
                targetState = feature,
                transitionSpec = {
                    fadeIn(tween(500)) togetherWith fadeOut(tween(500))
                },
                label = "feature"
            ) { currentFeature ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AgroColors.Accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "FarmifAI",
                            style = MaterialTheme.typography.labelMedium,
                            color = AgroColors.Accent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            currentFeature,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AgroColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Indicador de progreso total (solo mostrar barra, sin texto de componentes)
            val totalItems = downloadItems.size
            val overallProgress = if (totalItems > 0) {
                downloadItems.sumOf { item ->
                    when (item.status) {
                        DownloadItemStatus.COMPLETED -> 100
                        DownloadItemStatus.DOWNLOADING -> item.progress
                        else -> 0
                    }
                } / (totalItems * 100f)
            } else 0f
            
            LinearProgressIndicator(
                progress = { overallProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = AgroColors.Accent,
                trackColor = AgroColors.SurfaceLight
            )
        }
    }
}

@Composable
fun DownloadItemRow(item: DownloadItem) {
    val infiniteTransition = rememberInfiniteTransition(label = "download")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "shimmer"
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icono de estado
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    when (item.status) {
                        DownloadItemStatus.COMPLETED -> AgroColors.Accent.copy(alpha = 0.2f)
                        DownloadItemStatus.DOWNLOADING -> AgroColors.Accent.copy(alpha = shimmer)
                        DownloadItemStatus.FAILED -> Color.Red.copy(alpha = 0.2f)
                        else -> AgroColors.SurfaceLight
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when (item.status) {
                DownloadItemStatus.COMPLETED -> Text("✓", color = AgroColors.Accent, fontWeight = FontWeight.Bold)
                DownloadItemStatus.DOWNLOADING -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AgroColors.Accent,
                    strokeWidth = 2.dp
                )
                DownloadItemStatus.FAILED -> Text("✗", color = Color.Red, fontWeight = FontWeight.Bold)
                else -> Text("○", color = AgroColors.TextSecondary)
            }
        }
        
        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = AgroColors.TextPrimary
            )
            Text(
                when (item.status) {
                    DownloadItemStatus.DOWNLOADING -> "${item.description} (${item.progress}%)"
                    DownloadItemStatus.COMPLETED -> "Listo ✓"
                    DownloadItemStatus.FAILED -> "Error - Verifica tu conexión"
                    else -> "${item.sizeMB} MB"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (item.status) {
                    DownloadItemStatus.COMPLETED -> AgroColors.Accent
                    DownloadItemStatus.FAILED -> Color.Red
                    else -> AgroColors.TextSecondary
                }
            )
        }
        
        // Progress bar para item en descarga
        if (item.status == DownloadItemStatus.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { item.progress / 100f },
                modifier = Modifier
                    .width(60.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AgroColors.Accent,
                trackColor = AgroColors.SurfaceLight
            )
        }
    }
}
