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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import edu.unicauca.app.agrochat.feedback.FeedbackEventStore
import edu.unicauca.app.agrochat.llm.LlamaService
import edu.unicauca.app.agrochat.mindspore.SemanticSearchHelper
import edu.unicauca.app.agrochat.models.ModelDownloadService
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
import java.util.UUID

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
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val imageBitmap: Bitmap? = null,  // Para mensajes con imagen
    val diseaseResult: DiseaseResult? = null,  // Para resultados de diagnóstico
    val canContinue: Boolean = false,  // Para mostrar botón "Continuar" en respuestas LLM
    val feedbackEligible: Boolean = false,
    val responseGuidanceType: ResponseGuidanceType? = null,
    val isThinking: Boolean = false
)

enum class ResponseGuidanceType(val label: String) {
    CASE_BASED("Respuesta basada en tu caso"),
    GENERAL_GUIDANCE("Orientacion general")
}

data class MessageFeedbackState(
    val helpful: Boolean? = null,
    val clear: Boolean? = null,
    val wouldApplyToday: Boolean? = null,
    val updatedAt: Long = 0L
)

data class FeedbackMessageContext(
    val userQuery: String,
    val assistantResponse: String,
    val responseLabel: String,
    val usedLlm: Boolean,
    val kbSupported: Boolean,
    val kbSupportScore: Float,
    val kbCoverage: Float,
    val kbUnknownRatio: Float,
    val turnIndex: Int
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

// Características de la app para mostrar durante la descarga
object AppFeatures {
    val features = listOf(
        "🧠 IA local qwen3.5_FarmifAI2.0 con razonamiento",
        "🌱 Asesoría agrícola personalizada",
        "📸 Diagnóstico visual de enfermedades",
        "🎯 Reconocimiento de voz en español",
        "📱 Operación local sin dependencia cloud",
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
        private const val PARITY_MIN_MAX_TOKENS = 1200
        private const val ADAPTIVE_MIN_MAX_TOKENS = 120
        private const val ADAPTIVE_DEFAULT_MAX_TOKENS = 1000
        private const val ADAPTIVE_MAX_MAX_TOKENS = 1400
        private const val GREETING_MAX_TOKENS = 60
        private const val PARITY_SYSTEM_PROMPT =
            "Eres FarmifAI, un asistente agrícola experto. Si se proporciona informacion de referencia, usala como fuente principal y no inventes datos fuera de esa informacion. Si falta un dato, dilo de forma clara y natural."
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
    private val messageFeedbackStates = mutableStateMapOf<String, MessageFeedbackState>()
    private val feedbackContextByMessage = mutableMapOf<String, FeedbackMessageContext>()
    private var feedbackTurnCounter = 0
    private val feedbackSessionId: String = UUID.randomUUID().toString()
    private lateinit var feedbackStore: FeedbackEventStore
    private var lastResponse by mutableStateOf("")
    private var currentMode by mutableStateOf(AppMode.VOICE)
    private var semanticSearchHelper: SemanticSearchHelper? = null
    private var voiceHelper: VoiceHelper? = null
    private var llamaService: LlamaService? = null
    private var isLlamaLoaded by mutableStateOf(false)
    private var isListening by mutableStateOf(false)
    private var hasAudioPermission by mutableStateOf(false)
    private var showSettingsDialog by mutableStateOf(false)
    private var isLlamaEnabled by mutableStateOf(false)
    private var llamaModelStatusText by mutableStateOf("LLM local no inicializado")
    private var llamaModelPathText by mutableStateOf("")
    private var isLlamaChecking by mutableStateOf(false)
    private var llamaModelMissing by mutableStateOf(false)
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
    private var advancedMaxTokens by mutableStateOf(ADAPTIVE_DEFAULT_MAX_TOKENS) // Slider max 1200
    private var advancedSimilarityThreshold by mutableStateOf(0.45f)
    private var advancedKbFastPathThreshold by mutableStateOf(0.70f)
    private var advancedContextRelevanceThreshold by mutableStateOf(0.50f)
    private var advancedSystemPrompt by mutableStateOf(
        "Eres FarmifAI, un asistente agricola que habla de forma cercana y practica, como un asesor en campo. Basa tus respuestas en los datos que se te proporcionan. Responde breve por defecto, pero si el usuario pide indicaciones o pasos puedes extenderte un poco con 3 a 6 pasos cortos y claros. Si no tienes datos sobre algo, dilo de forma clara. No inventes cifras ni recomendaciones."
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

        feedbackStore = FeedbackEventStore(applicationContext)
        AppLogger.log("Feedback", "Storage path: ${feedbackStore.storagePath()}")
        AppLogger.log("Feedback", "Local manifest: ${feedbackStore.syncManifestPath()}")

        // Cargar preferencias
        loadPreferences()
        
        logLocalResourceStatus()

        // Verificar si es primera instalación (sin modelos descargados)
        checkFirstLaunch()

        // Si es primera instalación, mostrar pantalla de bienvenida
        // Si no, iniciar normalmente
        if (showWelcomeScreen) {
            lifecycleScope.launch {
                startSequentialDownloads()
            }
        } else {
            lifecycleScope.launch {
                initializeLlama()
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
                            isLlamaEnabled = isLlamaEnabled,
                            isLlamaLoaded = isLlamaLoaded,
                            llamaModelStatusText = llamaModelStatusText,
                            llamaModelPathText = llamaModelPathText,
                            isLlamaChecking = isLlamaChecking,
                            llamaModelMissing = llamaModelMissing,
                            showSettingsDialog = showSettingsDialog,
                            isDiagnosticReady = isDiagnosticReady,
                            isDiagnosing = isDiagnosing,
                            capturedBitmap = capturedBitmap,
                            lastDiagnosis = lastDiagnosis,
                            feedbackStates = messageFeedbackStates,
                            onSendMessage = { sendMessage(it) },
                            onMicClick = { handleMicClick() },
                            onModeChange = { handleModeChange(it) },
                            onHelpfulFeedback = { messageId, helpful -> onHelpfulFeedback(messageId, helpful) },
                            onClarityFeedback = { messageId, clear -> onClarityFeedback(messageId, clear) },
                            onApplyTodayFeedback = { messageId, wouldApply -> onApplyTodayFeedback(messageId, wouldApply) },
                            onSettingsClick = { showSettingsDialog = true },
                            onDismissSettings = { showSettingsDialog = false },
                            onToggleLlama = { enabled -> toggleLlama(enabled) },
                            onCheckLlamaModel = { checkAndLoadLlamaModel() },
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
                                advancedMaxTokens = maxTok.coerceIn(ADAPTIVE_MIN_MAX_TOKENS, ADAPTIVE_MAX_MAX_TOKENS)
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
    
    private fun logLocalResourceStatus() {
        AppLogger.log("MainActivity", "Modo local activo: sin servicios remotos; descarga automatica de Qwen habilitada")
    }

    /**
     * Verifica si es la primera instalación (sin modelos descargados).
     */
    private fun checkFirstLaunch() {
        val service = ModelDownloadService.getInstance()
        val localLlmService = LlamaService.getInstance()

        val needsMindSpore = !service.areAllModelsAvailable(applicationContext)
        val needsLocalLlm = !localLlmService.isPreferredModelAvailable(applicationContext)

        isFirstLaunch = needsMindSpore || needsLocalLlm
        showWelcomeScreen = isFirstLaunch

        if (isFirstLaunch) {
            AppLogger.log("MainActivity", "Primera instalación detectada - mostrando pantalla de bienvenida")
            prepareDownloadItems()
        }
    }

    /**
     * Prepara la lista de items a descargar para la pantalla de bienvenida.
     */
    private fun prepareDownloadItems() {
        downloadItems.clear()

        val service = ModelDownloadService.getInstance()
        val localLlmService = LlamaService.getInstance()

        if (!service.areAllModelsAvailable(applicationContext)) {
            val totalMB = service.getTotalDownloadSizeMB(applicationContext)
            downloadItems.add(
                DownloadItem(
                    name = "🧠 Motor de IA",
                    description = "Modelo de búsqueda semántica y diagnóstico",
                    sizeMB = totalMB
                )
            )
        }

        if (!localLlmService.isPreferredModelAvailable(applicationContext)) {
            downloadItems.add(
                DownloadItem(
                    name = "🦙 LLM Offline (qwen3.5_FarmifAI2.0)",
                    description = "Modelo generativo local para respuestas sin internet",
                    sizeMB = localLlmService.getExpectedDownloadSizeMB()
                )
            )
        }
    }

    /**
     * Inicia las descargas de forma secuencial con actualizaciones visuales.
     */
    private suspend fun startSequentialDownloads() {
        AppLogger.log("MainActivity", "Iniciando descargas secuenciales...")

        lifecycleScope.launch {
            while (showWelcomeScreen) {
                kotlinx.coroutines.delay(4000)
                currentTipIndex = (currentTipIndex + 1) % AppFeatures.features.size
            }
        }

        var allSuccess = true

        for (index in downloadItems.indices) {
            val item = downloadItems[index]
            downloadItems[index] = item.copy(status = DownloadItemStatus.DOWNLOADING, progress = 0)

            val success = when {
                item.name.contains("Motor de IA") -> downloadMindSporeModels(index)
                item.name.contains("LLM Offline") -> downloadLlamaModel(index)
                else -> true
            }

            if (success) {
                downloadItems[index] = downloadItems[index].copy(
                    status = DownloadItemStatus.COMPLETED,
                    progress = 100
                )
            } else {
                downloadItems[index] = downloadItems[index].copy(status = DownloadItemStatus.FAILED)
                allSuccess = false
            }
        }

        kotlinx.coroutines.delay(1500)

        if (allSuccess) {
            showWelcomeScreen = false

            initializeLlama()
            initializeDiagnostic()
            initializeSemanticSearch()
            if (hasAudioPermission) {
                initializeVoice()
            }
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    "Error en algunas descargas. Verifica tu conexión.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Descarga modelos MindSpore con actualización de progreso.
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
     * Descarga modelo LLM con actualización de progreso.
     */
    private suspend fun downloadLlamaModel(itemIndex: Int): Boolean {
        val localLlamaService = LlamaService.getInstance()

        if (localLlamaService.isPreferredModelAvailable(applicationContext)) {
            AppLogger.log("MainActivity", "Modelo LLM ya existe, saltando descarga")
            return true
        }

        localLlamaService.onDownloadProgress = { progress, _, _ ->
            runOnUiThread {
                if (itemIndex < downloadItems.size) {
                    downloadItems[itemIndex] = downloadItems[itemIndex].copy(progress = progress)
                }
            }
        }

        val result = localLlamaService.downloadModel(applicationContext)
        if (result.isSuccess) {
            AppLogger.log("MainActivity", "Descarga LLM exitosa")
            return true
        }

        val exists = localLlamaService.isPreferredModelAvailable(applicationContext)
        if (exists) {
            AppLogger.log("MainActivity", "Modelo LLM existe después de descarga (verificación secundaria)")
            return true
        }

        AppLogger.log("MainActivity", "Error descargando LLM: ${result.exceptionOrNull()?.message}")
        return false
    }

    /**
     * Asegura que los modelos MindSpore requeridos estén disponibles en almacenamiento interno.
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

        service.onProgress = { progress ->
            if (progress.progress == 0 || progress.progress == 100 || progress.status == ModelDownloadService.DownloadStatus.FAILED) {
                AppLogger.log("MainActivity", "Descarga ${progress.modelName}: ${progress.status}")
            }
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
                val displayPayload = buildAssistantDisplayPayload(responseMeta.response)
                val response = displayPayload.answer

                if (displayPayload.thinking.isNotBlank()) {
                    chatMessages.add(
                        ChatMessage(
                            text = displayPayload.thinking,
                            isUser = false,
                            isThinking = true,
                            feedbackEligible = false
                        )
                    )
                }
                
                val fullResponse = buildString {
                    append("${result.displayName}\n")
                    append("Cultivo: ${result.crop}\n")
                    append("Confianza: ${(result.confidence * 100).toInt()}%\n\n")
                    
                    if (result.isHealthy) {
                        append("La planta se ve saludable.\n\n")
                    } else {
                        append("Enfermedad detectada.\n\n")
                    }
                    
                    append("Recomendación:\n")
                    append(response)
                }

                val guidanceType = if (responseMeta.kbSupported) {
                    ResponseGuidanceType.CASE_BASED
                } else {
                    ResponseGuidanceType.GENERAL_GUIDANCE
                }
                val assistantMessage = ChatMessage(
                    text = fullResponse,
                    isUser = false,
                    feedbackEligible = true,
                    responseGuidanceType = guidanceType
                )
                chatMessages.add(assistantMessage)
                lastResponse = fullResponse

                registerAssistantResponseForFeedback(
                    message = assistantMessage,
                    userQuery = query,
                    responseMeta = responseMeta
                )
                voiceHelper?.speak(response)
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error buscando tratamiento", e)
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
        isLlamaEnabled = prefs.getBoolean(KEY_LLAMA_ENABLED, true)
        
        // Configuración avanzada (valores por defecto al máximo de la UI)
        advancedMaxTokens = prefs.getInt("advanced_max_tokens", ADAPTIVE_DEFAULT_MAX_TOKENS)
            .coerceIn(ADAPTIVE_MIN_MAX_TOKENS, ADAPTIVE_MAX_MAX_TOKENS)
        advancedSimilarityThreshold = prefs.getFloat("advanced_similarity_threshold", 0.45f).coerceIn(SAFE_MIN_SIMILARITY_THRESHOLD, SAFE_MAX_SIMILARITY_THRESHOLD)
        advancedKbFastPathThreshold = prefs.getFloat("advanced_kb_fast_path_threshold", 0.70f).coerceIn(SAFE_MIN_KB_FAST_PATH_THRESHOLD, SAFE_MAX_KB_FAST_PATH_THRESHOLD)
        advancedContextRelevanceThreshold = prefs.getFloat("advanced_context_relevance_threshold", 0.50f).coerceIn(SAFE_MIN_CONTEXT_RELEVANCE_THRESHOLD, SAFE_MAX_CONTEXT_RELEVANCE_THRESHOLD)
        advancedSystemPrompt = "Eres FarmifAI, un asistente agricola que habla de forma cercana y practica, como un asesor en campo. Basa tus respuestas en los datos que se te proporcionan. Responde breve por defecto, pero si el usuario pide indicaciones o pasos puedes extenderte un poco con 3 a 6 pasos cortos y claros. Si no tienes datos sobre algo, dilo de forma clara. No inventes cifras ni recomendaciones."
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
        isLlamaEnabled = enabled
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LLAMA_ENABLED, enabled).apply()
        
        val status = if (enabled) "LLM Local activado" else "LLM Local desactivado"
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
    }
    
    private fun initializeLlama() {
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
        if (llamaService?.isPreferredModelAvailable(applicationContext) == true) {
            // Modelo existe, cargarlo
            loadLlamaModel()
        } else {
            Log.i("MainActivity", "Modelo local no disponible - iniciando descarga automatica de Qwen")
            isLlamaLoaded = false
            llamaModelMissing = true
            llamaModelStatusText = "Modelo local no encontrado"
            downloadAndLoadLlama()
        }
    }
    
    private fun checkAndLoadLlamaModel() {
        if (isLlamaChecking) {
            Log.i("MainActivity", "Verificacion/descarga de modelo ya en progreso")
            return
        }
        
        lifecycleScope.launch {
            try {
                isLlamaChecking = true
                llamaModelMissing = false
                llamaModelStatusText = "Verificando modelo local..."
                uiStatus = "Verificando LLM local..."

                if (llamaService?.isPreferredModelAvailable(applicationContext) == true) {
                    isLlamaChecking = false
                    loadLlamaModel()
                } else {
                    isLlamaChecking = false
                    downloadAndLoadLlama()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error verificando Llama", e)
                llamaModelStatusText = "Error verificando modelo"
                llamaModelMissing = true
                isLlamaChecking = false
                uiStatus = "Sin LLM local"
            }
        }
    }

    private fun downloadAndLoadLlama() {
        if (isLlamaChecking) {
            Log.i("MainActivity", "Descarga de modelo ya en progreso")
            return
        }

        lifecycleScope.launch {
            try {
                isLlamaChecking = true
                llamaModelMissing = false
                llamaModelStatusText = "Descargando modelo..."
                uiStatus = "Descargando LLM (0%)..."

                val localLlamaService = llamaService ?: LlamaService.getInstance().also {
                    llamaService = it
                }

                localLlamaService.onDownloadProgress = { progress, downloadedMB, totalMB ->
                    runOnUiThread {
                        llamaModelStatusText = "Descargando: $downloadedMB/$totalMB MB"
                        uiStatus = "Descargando LLM ($progress%)..."
                    }
                }

                val result = localLlamaService.downloadModel(applicationContext)
                result.onSuccess { file ->
                    Log.i("MainActivity", "Modelo descargado: ${file.absolutePath}")
                    llamaModelStatusText = "Descargado: ${file.name}"
                    llamaModelPathText = localLlamaService.getModelPath(applicationContext)
                    isLlamaChecking = false
                    loadLlamaModel()
                }.onFailure { e ->
                    Log.e("MainActivity", "Error descargando modelo: ${e.message}")
                    llamaModelStatusText = "Error descarga - Toca para reintentar"
                    llamaModelMissing = true
                    isLlamaChecking = false
                    uiStatus = "Sin LLM local"
                    Toast.makeText(applicationContext, "No se pudo descargar qwen3.5_FarmifAI2.0", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error en descarga de modelo", e)
                llamaModelStatusText = "Error descarga - Toca para reintentar"
                llamaModelMissing = true
                isLlamaChecking = false
                uiStatus = "Sin LLM local"
            }
        }
    }
    
    private fun loadLlamaModel() {
        lifecycleScope.launch {
            try {
                uiStatus = "Cargando LLM local..."
                llamaModelStatusText = "Cargando..."
                
                val result = llamaService?.load(applicationContext)
                result?.onSuccess {
                    isLlamaLoaded = true
                    llamaModelStatusText = "Cargado: ${llamaService?.getModelFilename(applicationContext)} (${llamaService?.getModelSizeMB(applicationContext)}MB)"
                    Log.i("MainActivity", "Local model loaded")

                    withContext(Dispatchers.Main) {
                        uiStatus = "LLM local listo"
                        Toast.makeText(applicationContext, "LLM Local listo", Toast.LENGTH_SHORT).show()
                    }
                }?.onFailure { e ->
                    Log.w("MainActivity", "Error cargando Llama: ${e.message}")
                    isLlamaLoaded = false
                    llamaModelStatusText = "Error cargando"
                    llamaModelMissing = true
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error inicializando Llama", e)
                isLlamaLoaded = false
                llamaModelStatusText = "Error"
                llamaModelMissing = true
            }
        }
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
        
        // Si es muy larga (>800 chars), probablemente está completa
        if (trimmed.length > 800) return true
        
        val lastChar = trimmed.last()
        
        // Si termina con indicadores de continuación, NO está completa
        val lastLine = trimmed.lines().lastOrNull()?.trim() ?: ""
        val incompleteEndings = listOf(":", "como", "por ejemplo", "tales como", "entre ellos", "es decir", "ademas", "también", "tambien")
        if (incompleteEndings.any { lastLine.lowercase().endsWith(it) }) return false
        
        // Si termina con puntuación final, está completa
        if (lastChar in listOf('.', '!', '?', ')', '"', '>')) return true
        
        // Si termina con emoji, está completa
        if (trimmed.takeLast(2).any { Character.isSupplementaryCodePoint(it.code) || it.code > 0x1F300 }) return true
        
        // Si tiene múltiples oraciones (3+ puntos), está completa
        if (trimmed.count { it == '.' } >= 3) return true
        
        // Si tiene viñetas/listas completadas, está completa
        if (trimmed.contains("\n•") || trimmed.contains("\n-") || trimmed.contains("\n*")) {
            val lastListLine = lastLine
            if (lastListLine.length > 15 && lastListLine.last() in listOf('.', '!', '?')) return true
        }
        
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

    private data class TokenBudgetPlan(
        val maxTokens: Int,
        val reason: String
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
            response.lowercase().contains("no tengo datos suficientes") ||
            response.lowercase().contains("no tengo suficiente informacion") ||
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
            issues.add("Salida genérica con KB disponible")
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
        val lastBotMessage = chatHistory.lastOrNull { !it.isUser && !it.isThinking }?.text ?: ""
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

    private data class ThinkAnswerParts(
        val thinking: String,
        val answer: String
    )

    private data class AssistantDisplayPayload(
        val thinking: String,
        val answer: String
    )

    private fun splitThinkAndAnswer(raw: String): ThinkAnswerParts {
        if (raw.isBlank()) return ThinkAnswerParts(thinking = "", answer = "")

        val source = raw
            .replace("&lt;think&gt;", "<think>", ignoreCase = true)
            .replace("&lt;/think&gt;", "</think>", ignoreCase = true)
            .replace("<thinking>", "<think>", ignoreCase = true)
            .replace("</thinking>", "</think>", ignoreCase = true)

        val openTag = "<think>"
        val closeTag = "</think>"
        var index = 0
        var inThink = false
        val thinkingBuilder = StringBuilder()
        val answerBuilder = StringBuilder()

        while (index < source.length) {
            if (!inThink && source.regionMatches(index, openTag, 0, openTag.length, ignoreCase = true)) {
                inThink = true
                index += openTag.length
                continue
            }

            if (inThink && source.regionMatches(index, closeTag, 0, closeTag.length, ignoreCase = true)) {
                inThink = false
                index += closeTag.length
                continue
            }

            if (inThink) {
                thinkingBuilder.append(source[index])
            } else {
                answerBuilder.append(source[index])
            }
            index += 1
        }

        val thinking = stripUserFacingMarkdown(
            thinkingBuilder.toString()
                .replace(Regex("(?i)</?think>"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .replace(Regex("[ \\t]{2,}"), " ")
                .trim()
        )
        val answer = answerBuilder.toString()
            .replace(Regex("(?i)</?think>"), " ")
            .trim()

        return ThinkAnswerParts(thinking = thinking, answer = answer)
    }

    private fun buildAssistantDisplayPayload(raw: String): AssistantDisplayPayload {
        val parts = splitThinkAndAnswer(raw)

        val thinking = sanitizeAssistantResponse(parts.thinking)
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        val answer = humanizeTechnicalTerms(
            sanitizeAssistantResponse(parts.answer)
        ).trim()

        return AssistantDisplayPayload(thinking = thinking, answer = answer)
    }

    private fun upsertStreamingAssistantBubble(
        existingId: String?,
        text: String,
        isThinking: Boolean
    ): String {
        if (existingId != null) {
            val idx = chatMessages.indexOfFirst { it.id == existingId }
            if (idx >= 0) {
                chatMessages[idx] = chatMessages[idx].copy(
                    text = text,
                    isThinking = isThinking,
                    feedbackEligible = false,
                    canContinue = false,
                    responseGuidanceType = null
                )
                return existingId
            }
        }

        val message = ChatMessage(
            text = text,
            isUser = false,
            feedbackEligible = false,
            isThinking = isThinking
        )
        chatMessages.add(message)
        return message.id
    }

    private fun removeMessageById(id: String?) {
        if (id == null) return
        val idx = chatMessages.indexOfFirst { it.id == id }
        if (idx >= 0) {
            chatMessages.removeAt(idx)
        }
    }

    /**
     * Evita que el modelo devuelva conversaciones inventadas con prefijos de rol.
     */
    private fun sanitizeAssistantResponse(response: String): String {
        val original = response
            .replace(Regex("<\\|[^>]+\\|>"), " ")
            .trim()
        if (original.isBlank()) return original

        // Strip echoed prompt fragments the model may have copied
        val promptStripped = original
            .replace(Regex("(?i)^(Responde con PRECISION usando SOLO los siguientes datos[.\\s]*)", RegexOption.MULTILINE), "")
            .replace(Regex("(?i)^(DATOS DISPONIBLES:)", RegexOption.MULTILINE), "")
            .replace(Regex("(?i)^(TEXTO FACTUAL:)", RegexOption.MULTILINE), "")
            .replace(Regex("(?i)^(CONSULTA DEL AGRICULTOR:)", RegexOption.MULTILINE), "")
            .replace(Regex("(?i)^(CONSULTA:)", RegexOption.MULTILINE), "")
            .replace(Regex("(?i)^(Usa los datos de abajo para responder[.\\s]*)", RegexOption.MULTILINE), "")
            .trim()

        val cleanedLines = mutableListOf<String>()
        var hasContent = false

        for (rawLine in (if (promptStripped.isNotBlank()) promptStripped else original).lines()) {
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

        val sanitized = stripUserFacingMarkdown(cleanedLines.joinToString("\n").trim())
        return if (sanitized.isNotBlank()) sanitized else stripUserFacingMarkdown(original)
    }

    /**
     * Remueve decoradores de Markdown para evitar que el usuario vea marcas como **texto**.
     */
    private fun stripUserFacingMarkdown(text: String): String {
        if (text.isBlank()) return text

        var normalized = text
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("__(.*?)__"), "$1")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1")
            .replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")
            .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")

        normalized = normalized
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        return normalized
    }

    /**
     * Limpia tecnicismos internos para que la salida sea totalmente conversacional.
     */
    private fun humanizeTechnicalTerms(response: String): String {
        if (response.isBlank()) return response

        var normalized = response
            .replace(Regex("base de conocimiento", RegexOption.IGNORE_CASE), "informacion disponible")
            .replace(Regex("informacion de referencia", RegexOption.IGNORE_CASE), "informacion disponible")
            .replace(Regex("contexto de referencia", RegexOption.IGNORE_CASE), "informacion disponible")
            .replace(Regex("\\bKB\\b", RegexOption.IGNORE_CASE), "informacion")
            .replace(Regex("\\bRAG\\b", RegexOption.IGNORE_CASE), "consulta")
            .replace(Regex("\\bLLM\\b", RegexOption.IGNORE_CASE), "asistente")
            .replace(Regex("con base en", RegexOption.IGNORE_CASE), "con la informacion disponible")
            .replace(
                Regex("^\\s*El\\s+f[oó]sforo\\s+debe\\s+abonosarse\\s+cuando\\s+el\\s+contenido\\s+es\\s+menor\\s+de\\s+30\\s+ppm\\.", RegexOption.IGNORE_CASE),
                "Si el fosforo es menor de 30 ppm, se debe corregir."
            )
            .replace(Regex("\\babonosarse\\b", RegexOption.IGNORE_CASE), "abonarse")
            .replace(Regex("\\babonar\\s+organico\\b", RegexOption.IGNORE_CASE), "aplicar abono organico")
            .replace(Regex("\\bhectaria\\b", RegexOption.IGNORE_CASE), "hectarea")
            .replace(
                Regex("para afinar mas la recomendacion faltan datos en la\\s+(kb|informacion)\\s+sobre\\s*", RegexOption.IGNORE_CASE),
                "Para afinar mejor la recomendacion necesito mas datos sobre "
            )
            .replace(
                Regex("^\\s*con la informacion disponible sobre\\s+", RegexOption.IGNORE_CASE),
                ""
            )
            .replace(
                Regex("^\\s*con base en la\\s+(kb|informacion disponible)\\s+sobre\\s+", RegexOption.IGNORE_CASE),
                ""
            )


        normalized = normalized
            .replace(Regex("(?<=[A-Za-zÁÉÍÓÚáéíóúÑñ])(?=\\d)"), " ")
            .replace(Regex("(?<=\\d)(?=[A-Za-zÁÉÍÓÚáéíóúÑñ])"), " ")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .replace(" .", ".")
            .replace(" ,", ",")
            .trim()

        return normalized
    }

    private fun computeTokenBudgetPlan(
        userQuery: String,
        hasKbContext: Boolean,
        kbSupportScore: Float,
        kbCoverage: Float,
        kbUnknownRatio: Float,
        contextLength: Int,
        forcedContext: Boolean,
        continuationLike: Boolean = false,
        isGreeting: Boolean = false
    ): TokenBudgetPlan {
        if (isGreeting) {
            return TokenBudgetPlan(GREETING_MAX_TOKENS, "greeting")
        }

        if (STRICT_TERMINAL_PARITY_MODE) {
            val parityTokens = maxOf(advancedMaxTokens, PARITY_MIN_MAX_TOKENS)
            return TokenBudgetPlan(parityTokens, "parity_mode")
        }

        val baseConfigured = advancedMaxTokens.coerceIn(ADAPTIVE_MIN_MAX_TOKENS, ADAPTIVE_MAX_MAX_TOKENS)
        val normalized = normalizeForTokenBudget(userQuery)
        val asksForGuidance = asksForPracticalGuidance(normalized)
        val longFormHints = listOf(
            "paso a paso", "detall", "detalle", "profund", "plan completo", "cronograma",
            "calendario", "manual", "compar", "explica", "amplia", "continua", "continuar",
            "y que mas", "guia completa", "tabla", "todo"
        )
        val asksLongForm = normalized.length > 140 || longFormHints.any { normalized.contains(it) }

        val targetTokens = when {
            continuationLike || forcedContext || asksLongForm -> 1100
            hasKbContext && asksForGuidance && normalized.length <= 90 -> 300
            hasKbContext && asksForGuidance -> 380
            hasKbContext && kbSupportScore >= 0.70f && kbCoverage >= 0.55f && normalized.length <= 70 -> 340
            hasKbContext -> 560
            asksForGuidance -> 260
            else -> 220
        }

        val chosen = minOf(baseConfigured, targetTokens)
            .coerceIn(ADAPTIVE_MIN_MAX_TOKENS, ADAPTIVE_MAX_MAX_TOKENS)
        val reason = when {
            continuationLike || forcedContext || asksLongForm -> "extended_needed"
            hasKbContext && asksForGuidance -> "kb_guidance"
            hasKbContext -> "kb_precise"
            asksForGuidance -> "no_kb_guidance_limited"
            else -> "no_kb_precise"
        }
        return TokenBudgetPlan(chosen, reason)
    }

    private fun normalizeForTokenBudget(text: String): String {
        return text.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ñ", "n")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun asksForPracticalGuidance(normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return false
        val stepPattern = Regex("\\b(pasos?|paso\\s+a\\s+paso|instrucciones?|procedimiento|guia|plan)\\b")
        if (stepPattern.containsMatchIn(normalizedQuery)) return true

        val questionCue = Regex("\\b(que|como|cuando|cuanto|cual|cuales)\\b")
            .containsMatchIn(normalizedQuery)
        val actionCue = Regex(
            "\\b(aplic(?:o|a|ar|arlo|arla)|hago|debo|manej(?:o|ar)|control(?:o|ar)|trat(?:o|ar)|fertiliz(?:o|ar)|abon(?:o|ar)|recomiend(?:a|as|an))\\b"
        ).containsMatchIn(normalizedQuery)

        return questionCue && actionCue
    }

    private fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || !isModelReady || isProcessing) return
        chatMessages.add(ChatMessage(text = userMessage, isUser = true))
        AppLogger.log("MainActivity", "Mensaje: '$userMessage'")
        
        val canContinuePreviousAnswer = chatMessages
            .lastOrNull { !it.isUser && !it.isThinking }
            ?.canContinue == true
        val isContinuationIntent = canContinuePreviousAnswer && isContinuationMessage(userMessage)

        if (!canContinuePreviousAnswer && isContinuationMessage(userMessage)) {
            AppLogger.log("MainActivity", "Texto de continuacion detectado pero ignorado porque no hay respuesta pendiente de continuar")
        }

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
            var streamingThinkingMessageId: String? = null
            var streamingAnswerMessageId: String? = null
            var thinkingBubbleHasModelContent = false
            
            // Mostrar estado según modo disponible
            uiStatus = "Generando respuesta local..."
            
            try {
                val responseMeta = findResponseWithMeta(
                    userQuery = effectiveQuery,
                    skipKbDirect = isContinuationIntent,
                    forcedContext = forcedContinuationContext,
                    onLocalLlmChunk = stream@{ partial ->
                        val displayPayload = buildAssistantDisplayPayload(partial)
                        val thinkingPartial = displayPayload.thinking
                        val answerPartial = displayPayload.answer

                        if (thinkingPartial.length < 8 && answerPartial.length < 8) return@stream

                        withContext(Dispatchers.Main) {
                            if (streamingThinkingMessageId == null) {
                                streamingThinkingMessageId = upsertStreamingAssistantBubble(
                                    existingId = null,
                                    text = "Pensando...",
                                    isThinking = true
                                )
                            }

                            if (thinkingPartial.length >= 8) {
                                thinkingBubbleHasModelContent = true
                                streamingThinkingMessageId = upsertStreamingAssistantBubble(
                                    existingId = streamingThinkingMessageId,
                                    text = thinkingPartial,
                                    isThinking = true
                                )
                            }

                            if (answerPartial.length >= 8) {
                                streamingAnswerMessageId = upsertStreamingAssistantBubble(
                                    existingId = streamingAnswerMessageId,
                                    text = answerPartial,
                                    isThinking = false
                                )
                                lastResponse = answerPartial
                            } else if (thinkingPartial.isNotBlank()) {
                                lastResponse = thinkingPartial
                            }
                        }
                    }
                )
                val finalDisplayPayload = buildAssistantDisplayPayload(responseMeta.response)
                val thinkingResponse = finalDisplayPayload.thinking
                val userFacingResponse = finalDisplayPayload.answer

                if (thinkingResponse.isNotBlank()) {
                    thinkingBubbleHasModelContent = true
                    streamingThinkingMessageId = upsertStreamingAssistantBubble(
                        existingId = streamingThinkingMessageId,
                        text = thinkingResponse,
                        isThinking = true
                    )
                } else if (!thinkingBubbleHasModelContent) {
                    removeMessageById(streamingThinkingMessageId)
                    streamingThinkingMessageId = null
                }

                // If LLM produced no response, don't add an empty bubble
                if (userFacingResponse.isBlank()) {
                    removeMessageById(streamingAnswerMessageId)
                    streamingAnswerMessageId = null

                    if (!thinkingBubbleHasModelContent || thinkingResponse.isBlank()) {
                        removeMessageById(streamingThinkingMessageId)
                        streamingThinkingMessageId = null
                    }

                    AppLogger.log("MainActivity", "Empty LLM response, no message shown")
                    uiStatus = "LLM local listo"
                    return@launch
                }

                val qualityHistory = if (streamingAnswerMessageId != null || streamingThinkingMessageId != null) {
                    chatMessages.filterNot {
                        it.id == streamingAnswerMessageId ||
                        it.id == streamingThinkingMessageId ||
                        it.isThinking
                    }
                } else {
                    chatMessages.filterNot { it.isThinking }
                }
                
                // ═══════════════════════════════════════════════════════════════
                // SISTEMA DE AUTOCONSCIENCIA - Evaluar calidad antes de entregar
                // ═══════════════════════════════════════════════════════════════
                val qualityReport = evaluateResponseQuality(
                    response = userFacingResponse,
                    userQuery = if (isContinuationIntent) effectiveQuery else userMessage,
                    chatHistory = qualityHistory,
                    kbSupported = responseMeta.kbSupported,
                    kbCoverage = responseMeta.kbCoverage,
                    kbSupportScore = responseMeta.kbSupportScore,
                    enforcedKbAbstention = responseMeta.enforcedKbAbstention
                )
                
                // Mejorar respuesta si es necesario
                val improvedResponse = improveResponseIfNeeded(userFacingResponse, qualityReport, userMessage)
                
                // Determinar si puede continuar basándose en autoconsciencia
                val canContinue = responseMeta.usedLlm && isLlamaEnabled && isLlamaLoaded && responseMeta.kbSupported &&
                                  (!qualityReport.isComplete || qualityReport.qualityScore < 0.7f)

                val responseWithFollowUp = improvedResponse
                val responseGuidanceType = if (responseMeta.kbSupported) {
                    ResponseGuidanceType.CASE_BASED
                } else {
                    ResponseGuidanceType.GENERAL_GUIDANCE
                }
                val existingStreamIndex = streamingAnswerMessageId?.let { id ->
                    chatMessages.indexOfFirst { it.id == id }
                } ?: -1
                val assistantMessage = if (existingStreamIndex >= 0) {
                    val updated = chatMessages[existingStreamIndex].copy(
                        text = responseWithFollowUp,
                        canContinue = canContinue,
                        feedbackEligible = true,
                        responseGuidanceType = responseGuidanceType,
                        isThinking = false
                    )
                    chatMessages[existingStreamIndex] = updated
                    updated
                } else {
                    ChatMessage(
                        text = responseWithFollowUp,
                        isUser = false,
                        canContinue = canContinue,
                        feedbackEligible = true,
                        responseGuidanceType = responseGuidanceType,
                        isThinking = false
                    ).also { chatMessages.add(it) }
                }
                lastResponse = responseWithFollowUp

                registerAssistantResponseForFeedback(
                    message = assistantMessage,
                    userQuery = if (isContinuationIntent) effectiveQuery else userMessage,
                    responseMeta = responseMeta
                )
                
                // Log resumen de calidad
                AppLogger.log("MainActivity", "📊 Calidad: ${String.format("%.0f", qualityReport.qualityScore * 100)}% | " +
                    "Completa: ${qualityReport.isComplete} | Coherente: ${qualityReport.isCoherent} | " +
                    "Puede continuar: $canContinue")
                
                AppLogger.log("MainActivity", "Respuesta: ${responseWithFollowUp.take(50)}...")
                
                // Actualizar status final
                uiStatus = "LLM local activo"
                
                voiceHelper?.speak(responseWithFollowUp)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error en sendMessage", e)
                removeMessageById(streamingAnswerMessageId)
                removeMessageById(streamingThinkingMessageId)
                uiStatus = "Error"
            } finally {
                isProcessing = false
            }
        }
    }

    private fun registerAssistantResponseForFeedback(
        message: ChatMessage,
        userQuery: String,
        responseMeta: ResponseMeta
    ) {
        feedbackTurnCounter += 1
        val responseLabel = message.responseGuidanceType?.label ?: "Sin clasificar"
        val context = FeedbackMessageContext(
            userQuery = userQuery,
            assistantResponse = message.text,
            responseLabel = responseLabel,
            usedLlm = responseMeta.usedLlm,
            kbSupported = responseMeta.kbSupported,
            kbSupportScore = responseMeta.kbSupportScore,
            kbCoverage = responseMeta.kbCoverage,
            kbUnknownRatio = responseMeta.kbUnknownRatio,
            turnIndex = feedbackTurnCounter
        )
        feedbackContextByMessage[message.id] = context
        messageFeedbackStates[message.id] = MessageFeedbackState(updatedAt = System.currentTimeMillis())

        lifecycleScope.launch {
            runCatching {
                feedbackStore.recordAssistantResponse(
                    sessionId = feedbackSessionId,
                    messageId = message.id,
                    turnIndex = context.turnIndex,
                    userQuery = context.userQuery,
                    assistantResponse = context.assistantResponse,
                    responseLabel = context.responseLabel,
                    usedLlm = context.usedLlm,
                    kbSupported = context.kbSupported,
                    kbSupportScore = context.kbSupportScore,
                    kbCoverage = context.kbCoverage,
                    kbUnknownRatio = context.kbUnknownRatio
                )
            }.onFailure { error ->
                AppLogger.log("Feedback", "No se pudo guardar assistant_response: ${error.message}")
            }
        }
    }

    private fun onHelpfulFeedback(messageId: String, helpful: Boolean) {
        val current = messageFeedbackStates[messageId] ?: MessageFeedbackState()
        if (current.helpful != null) return
        val updated = current.copy(helpful = helpful, updatedAt = System.currentTimeMillis())
        messageFeedbackStates[messageId] = updated
        persistFeedbackUpdate(messageId, updated)
    }

    private fun onClarityFeedback(messageId: String, clear: Boolean) {
        val current = messageFeedbackStates[messageId] ?: MessageFeedbackState()
        if (current.clear != null) return
        val updated = current.copy(clear = clear, updatedAt = System.currentTimeMillis())
        messageFeedbackStates[messageId] = updated
        persistFeedbackUpdate(messageId, updated)
    }

    private fun onApplyTodayFeedback(messageId: String, wouldApplyToday: Boolean) {
        val current = messageFeedbackStates[messageId] ?: MessageFeedbackState()
        if (current.wouldApplyToday != null) return
        val updated = current.copy(wouldApplyToday = wouldApplyToday, updatedAt = System.currentTimeMillis())
        messageFeedbackStates[messageId] = updated
        persistFeedbackUpdate(messageId, updated)
    }

    private fun persistFeedbackUpdate(messageId: String, state: MessageFeedbackState) {
        val context = feedbackContextByMessage[messageId] ?: return
        lifecycleScope.launch {
            runCatching {
                feedbackStore.recordFeedbackUpdate(
                    sessionId = feedbackSessionId,
                    messageId = messageId,
                    responseLabel = context.responseLabel,
                    userQuery = context.userQuery,
                    assistantResponse = context.assistantResponse,
                    helpful = state.helpful,
                    clear = state.clear,
                    wouldApplyToday = state.wouldApplyToday
                )
            }.onFailure { error ->
                AppLogger.log("Feedback", "No se pudo guardar feedback_update: ${error.message}")
            }
        }
    }

    private fun appendMissingContextFollowUpIfNeeded(
        response: String,
        userQuery: String,
        responseMeta: ResponseMeta
    ): String {
        return response
    }

    private fun buildContextFollowUpQuestion(userQuery: String): String? {
        return null
    }

    private fun isContinuationMessage(text: String): Boolean {
        val normalized = text.lowercase().trim()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace(Regex("[¿?¡!.,;:]"), "")
            .replace(Regex("\\s+"), " ")

        if (normalized.isBlank()) return false

        val tokenCount = normalized.split(" ").size
        if (tokenCount > 4) return false

        val exact = setOf(
            "continua", "continue", "sigue", "mas", "amplia", "expande",
            "continua por favor", "sigue por favor", "mas detalles", "mas informacion"
        )

        if (normalized in exact) return true

        val shortPrefix = tokenCount <= 3 && (
            normalized.startsWith("continua ") ||
            normalized.startsWith("sigue ")
        )

        return shortPrefix
    }

    /**
     * Construye el historial de conversación como pares (usuario, asistente)
     * para enviar como multi-turn a los LLMs.
     */
    private fun buildConversationHistory(maxTurns: Int): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        val messages = chatMessages.toList()
        for (i in messages.indices) {
            val current = messages[i]
            if (!current.isUser || current.text.isBlank()) continue

            val nextAssistant = messages
                .drop(i + 1)
                .firstOrNull { !it.isUser && !it.isThinking && it.text.isNotBlank() }

            if (nextAssistant != null) {
                pairs.add(current.text.take(300) to nextAssistant.text.take(500))
            }
        }
        return pairs.takeLast(maxTurns)
    }

    /**
     * Manejo directo de small-talk para mantener una experiencia humana y fluida.
     */
    private fun buildSmallTalkResponse(userQuery: String): String? {
        return null
    }

    private fun buildForcedContinuationContext(): String? {
        val kb = lastContext?.trim().orEmpty()
        if (kb.isBlank()) return null
        return kb
    }
    
    /**
     * Continúa la última respuesta del LLM pidiendo más detalles
     */
    private fun continueLastResponse() {
        if (!isLlamaEnabled || !isLlamaLoaded || isProcessing) return
        if (lastUserQuery.isBlank()) return
        
        AppLogger.log("MainActivity", "Continuando respuesta para: '$lastUserQuery'")
        
        lifecycleScope.launch {
            isProcessing = true
            uiStatus = "Expandiendo respuesta..."
            
            try {
                // Crear prompt para continuar con mas detalle
                val continuePrompt = "Más sobre: $lastUserQuery"
                val continuationMaxTokens = computeTokenBudgetPlan(
                    userQuery = continuePrompt,
                    hasKbContext = !lastContext.isNullOrBlank(),
                    kbSupportScore = 0.55f,
                    kbCoverage = 0.45f,
                    kbUnknownRatio = 0.20f,
                    contextLength = lastContext?.length ?: 0,
                    forcedContext = !lastContext.isNullOrBlank(),
                    continuationLike = true
                ).maxTokens
                
                var streamingThinkingMessageId: String? = null
                var streamingAnswerMessageId: String? = null
                val result = llamaService?.generateAgriResponseStreaming(
                    userQuery = continuePrompt,
                    contextFromKB = lastContext,
                    maxTokens = continuationMaxTokens,
                    maxContextLength = advancedContextLength,
                    systemPrompt = advancedSystemPrompt,
                    onPartialResponse = { partial ->
                        val displayPayload = buildAssistantDisplayPayload(partial)
                        val thinkingPartial = displayPayload.thinking
                        val answerPartial = displayPayload.answer
                        withContext(Dispatchers.Main) {
                            if (thinkingPartial.length >= 8) {
                                streamingThinkingMessageId = upsertStreamingAssistantBubble(
                                    existingId = streamingThinkingMessageId,
                                    text = thinkingPartial,
                                    isThinking = true
                                )
                            }
                            if (answerPartial.length >= 8) {
                                streamingAnswerMessageId = upsertStreamingAssistantBubble(
                                    existingId = streamingAnswerMessageId,
                                    text = answerPartial,
                                    isThinking = false
                                )
                                lastResponse = answerPartial
                            }
                        }
                    }
                )
                
                result?.fold(
                    onSuccess = { response ->
                        val displayPayload = buildAssistantDisplayPayload(response)
                        val thinking = displayPayload.thinking
                        val cleanResponse = displayPayload.answer

                        if (thinking.isNotBlank()) {
                            chatMessages.add(
                                ChatMessage(
                                    text = thinking,
                                    isUser = false,
                                    isThinking = true,
                                    feedbackEligible = false
                                )
                            )
                        }

                        if (cleanResponse.length > 10) {
                            // Verificar si esta continuación está completa
                            val isComplete = isResponseComplete(cleanResponse)
                            val existingStreamIndex = streamingAnswerMessageId?.let { id ->
                                chatMessages.indexOfFirst { it.id == id }
                            } ?: -1
                            if (existingStreamIndex >= 0) {
                                chatMessages[existingStreamIndex] = chatMessages[existingStreamIndex].copy(
                                    text = cleanResponse,
                                    canContinue = !isComplete,
                                    feedbackEligible = false,
                                    isThinking = false
                                )
                            } else {
                                chatMessages.add(
                                    ChatMessage(
                                        text = cleanResponse,
                                        isUser = false,
                                        canContinue = !isComplete,
                                        isThinking = false
                                    )
                                )
                            }
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
        forcedContext: String? = null,
        onLocalLlmChunk: (suspend (String) -> Unit)? = null
    ): ResponseMeta = withContext(Dispatchers.IO) {
        Log.d("MainActivity", "findResponse: localOnly=true, isLlamaEnabled=$isLlamaEnabled, isLlamaLoaded=$isLlamaLoaded")
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
        val effectiveDetectGreetings = if (STRICT_TERMINAL_PARITY_MODE) true else advancedDetectGreetings
        val retrievalMinScore = KB_RETRIEVAL_MIN_SCORE
        val llmAvailable = isLlamaEnabled && isLlamaLoaded && llamaService != null

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
        val kbDirectResponse = buildKbDirectResponseFromRag(ragContext)

        val combinedKBContext = ragContext?.combinedContext
        val bestMatch = ragContext?.contexts?.firstOrNull()
        val groundingAssessment = ragContext?.groundingAssessment
        val kbSupportScore = groundingAssessment?.supportScore ?: 0f
        val kbCoverage = groundingAssessment?.lexicalCoverage ?: 0f
        val kbUnknownRatio = groundingAssessment?.unknownTokenRatio ?: 1f
        val normalizedQuery = userQuery.lowercase().trim()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("¿", "")
            .replace("?", "")
        val isSimpleGreeting = effectiveDetectGreetings && (
            normalizedQuery in listOf("hola", "hey", "buenas", "buenos dias", "buenas tardes", "buenas noches", "gracias", "adios", "chao", "hasta luego") ||
                (normalizedQuery.length < 15 && (
                    normalizedQuery.startsWith("hola") ||
                        normalizedQuery.startsWith("hey") ||
                        normalizedQuery.startsWith("gracias")
                    ))
            )
        val likelyAgriculturalQuery = isLikelyAgriculturalQuery(userQuery)
        val hasGroundedKbSupport = bestMatch != null &&
            (groundingAssessment?.hasStrongSupport == true) &&
            kbSupportScore >= MIN_SUPPORT_SCORE_FOR_GROUNDED &&
            kbCoverage >= MIN_LEXICAL_COVERAGE_FOR_GROUNDED &&
            kbUnknownRatio <= MAX_UNKNOWN_RATIO_FOR_GROUNDED &&
            bestMatch.similarityScore >= effectiveSimilarityThreshold
        val semanticRelatedMinScore = maxOf(KB_RETRIEVAL_MIN_SCORE + 0.08f, 0.23f)
        val hasRelatedKbSignal = bestMatch != null &&
            bestMatch.similarityScore >= semanticRelatedMinScore &&
            kbSupportScore >= 0.30f
        val hasKbContext = hasRelatedKbSignal && !combinedKBContext.isNullOrBlank()

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

        val routeResult = ResponseRoutingPolicy.decide(
            ResponseRoutingPolicy.Input(
                hasRelatedKbSignal = hasRelatedKbSignal,
                bestSimilarityScore = bestMatch?.similarityScore ?: 0f,
                kbSupportScore = kbSupportScore,
                kbCoverage = kbCoverage,
                kbUnknownRatio = kbUnknownRatio
            )
        )

        AppLogger.log(
            "MainActivity",
            "Decision route=${routeResult.decision} reason=${routeResult.reason} kbScore=${String.format("%.2f", bestMatch?.similarityScore ?: 0f)} support=${String.format("%.2f", kbSupportScore)} coverage=${String.format("%.2f", kbCoverage)} unknown=${String.format("%.2f", kbUnknownRatio)}"
        )

        if (!llmAvailable) {
            AppLogger.log(
                "MainActivity",
                "Decision: LLM unavailable; no visible non-LLM fallback support=${String.format("%.2f", kbSupportScore)} coverage=${String.format("%.2f", kbCoverage)} unknown=${String.format("%.2f", kbUnknownRatio)} related=$hasRelatedKbSignal"
            )
            return@withContext ResponseMeta(
                response = "",
                usedLlm = false,
                kbSupported = false,
                kbSupportScore = kbSupportScore,
                kbCoverage = kbCoverage,
                kbUnknownRatio = kbUnknownRatio,
                enforcedKbAbstention = true
            )
        }

        // --- Historial de conversación como pares (user, assistant) ---
        val conversationHistory: List<Pair<String, String>> = if (effectiveChatHistoryEnabled) {
            buildConversationHistory(effectiveChatHistorySize)
        } else {
            emptyList()
        }

        // --- Contexto KB limpio (sin historial plano) ---
        val asksPracticalGuidance = asksForPracticalGuidance(normalizeForTokenBudget(userQuery))
        val kbContextForPrompt = combinedKBContext?.take(effectiveContextLength)
        val forcedContextForPrompt = forcedContext?.take(effectiveContextLength)
        val contextToPass = forcedContextForPrompt
            ?: if (hasKbContext && !kbContextForPrompt.isNullOrBlank()) kbContextForPrompt else null

        val mode = when {
            hasKbContext && conversationHistory.isNotEmpty() -> "RAG+Chat"
            hasKbContext -> "RAG"
            conversationHistory.isNotEmpty() -> "Chat"
            else -> "LLM_only"
        }
        AppLogger.log(
            "MainActivity",
            "Decision: LLM mode=$mode kbScore=${String.format("%.2f", bestMatch?.similarityScore ?: 0f)} support=${String.format("%.2f", kbSupportScore)} ctxLen=${contextToPass?.length ?: 0} skipKbDirect=$skipKbDirect forcedCtx=${forcedContext != null}"
        )
        AppLogger.log(
            "MainActivity",
            "Context budget: max=$effectiveContextLength kbLen=${kbContextForPrompt?.length ?: 0} histTurns=${conversationHistory.size} forcedLen=${forcedContextForPrompt?.length ?: 0} finalLen=${contextToPass?.length ?: 0}"
        )
        val tokenBudgetPlan = computeTokenBudgetPlan(
            userQuery = userQuery,
            hasKbContext = hasKbContext,
            kbSupportScore = kbSupportScore,
            kbCoverage = kbCoverage,
            kbUnknownRatio = kbUnknownRatio,
            contextLength = contextToPass?.length ?: 0,
            forcedContext = forcedContext != null,
            continuationLike = skipKbDirect,
            isGreeting = isSimpleGreeting
        )
        val effectiveMaxTokens = tokenBudgetPlan.maxTokens
        AppLogger.log(
            "MainActivity",
            "Token budget: max=$effectiveMaxTokens reason=${tokenBudgetPlan.reason} base=${advancedMaxTokens.coerceIn(ADAPTIVE_MIN_MAX_TOKENS, ADAPTIVE_MAX_MAX_TOKENS)}"
        )

        withContext(Dispatchers.Main) {
            uiStatus = "Generando respuesta..."
        }

        if (isLlamaEnabled && isLlamaLoaded && llamaService != null) {
            val guidanceStylePrompt = if (asksPracticalGuidance) {
                "Para consultas operativas, usa 2 a 4 pasos numerados: 1) Condicion exacta, 2) Accion exacta, 3) Momento y dosis solo si existen en los datos. No hagas comentarios sobre si la pregunta es importante o no."
            } else {
                ""
            }
            val finalSystemPrompt = if (isSimpleGreeting) {
                "Eres FarmifAI. Responde SOLO con un saludo corto de maximo 10 palabras. Ejemplo: Hola! En que te puedo ayudar? NO agregues explicaciones, ofertas de ayuda detalladas ni listas."
            } else if (hasKbContext) {
                val confidenceDirective = if (routeResult.decision == ResponseRoutingPolicy.Decision.LLM_GROUNDED_HIGH_CONFIDENCE) {
                    "La evidencia recuperada es suficientemente relacionada. Responde con precision y conserva las relaciones factuales."
                } else {
                    "La evidencia recuperada es debil o incompleta. Prioriza precision sobre cobertura; si no puedes sostener una recomendacion concreta, dilo con naturalidad y sin inventar."
                }
                "$effectiveSystemPrompt\nUsa SOLO los datos disponibles. No inventes ni completes con conocimiento externo. Conserva numeros, unidades y relaciones exactamente como aparecen (dosis, umbrales, tiempos, densidades). Si aparecen reglas de umbral (menor/mayor, <, >, <=, >=), conserva exactamente la direccion de la desigualdad y su accion asociada; nunca inviertas menor por mayor ni aplica por no aplica. No menciones terminos internos como KB, RAG, contexto, modelo o sistema. $confidenceDirective $guidanceStylePrompt"
            } else {
                "$effectiveSystemPrompt\nNo hay evidencia recuperada suficiente para esta consulta. Responde como FarmifAI en maximo 2 frases, admitiendo que faltan datos suficientes para responder con precision. No des dosis, productos, umbrales ni pasos tecnicos inventados."
            }

            val contextForLlm = if (isSimpleGreeting) null else contextToPass

            val shouldUseStrictGroundedFirst = false

            if (hasGroundedKbSupport && !kbDirectResponse.isNullOrBlank() && asksPracticalGuidance && onLocalLlmChunk != null) {
                AppLogger.log("MainActivity", "Streaming activo: se omite preflight estricto para evitar latencia")
            }

            if (shouldUseStrictGroundedFirst) {
                AppLogger.log("MainActivity", "LLM strict grounded first para respuesta instructiva con KB fuerte")
                val focusedFactualText = buildFocusedFactualTextForLlm(kbDirectResponse.orEmpty(), userQuery)
                val requiredFacets = inferRequiredResponseFacets(
                    userQuery = userQuery,
                    factualText = focusedFactualText
                )

                val strictResponse = generateGroundedRewriteWithLlm(
                    userQuery = userQuery,
                    factualResponse = kbDirectResponse.orEmpty(),
                    systemPrompt = effectiveSystemPrompt,
                    maxTokens = effectiveMaxTokens
                )
                if (!strictResponse.isNullOrBlank()) {
                    val strictAnchored = isResponseAnchoredToContext(
                        response = strictResponse,
                        userQuery = userQuery,
                        kbContext = focusedFactualText
                    )
                    val strictLexicallySupported = isLexicallySupportedByFactualText(
                        response = strictResponse,
                        factualText = focusedFactualText,
                        minSupportedRatio = 0.45f
                    )
                    val strictNumbersSupported = responseNumbersAreSupported(
                        response = strictResponse,
                        factualText = focusedFactualText
                    )
                    val strictThresholdConsistency = responseThresholdRulesAreConsistent(
                        response = strictResponse,
                        factualText = focusedFactualText
                    )
                    val strictFacetCoverage = responseCoversRequiredFacets(
                        response = strictResponse,
                        requiredFacets = requiredFacets
                    )
                    AppLogger.log(
                        "MainActivity",
                        "LLM strict grounding anchored=$strictAnchored lexical=$strictLexicallySupported numbers=$strictNumbersSupported threshold=$strictThresholdConsistency facets=$strictFacetCoverage"
                    )
                    if (strictAnchored && strictLexicallySupported && strictNumbersSupported &&
                        strictFacetCoverage && strictThresholdConsistency
                    ) {
                        return@withContext ResponseMeta(
                            response = strictResponse,
                            usedLlm = true,
                            kbSupported = true,
                            kbSupportScore = kbSupportScore,
                            kbCoverage = kbCoverage,
                            kbUnknownRatio = kbUnknownRatio,
                            enforcedKbAbstention = false
                        )
                    }
                }

                val repairedResponse = generateGroundedRepairWithLlm(
                    userQuery = userQuery,
                    factualResponse = kbDirectResponse.orEmpty(),
                    previousResponse = strictResponse.orEmpty(),
                    systemPrompt = effectiveSystemPrompt,
                    maxTokens = effectiveMaxTokens
                )
                if (!repairedResponse.isNullOrBlank()) {
                    val repairedAnchored = isResponseAnchoredToContext(
                        response = repairedResponse,
                        userQuery = userQuery,
                        kbContext = focusedFactualText
                    )
                    val repairedLexicallySupported = isLexicallySupportedByFactualText(
                        response = repairedResponse,
                        factualText = focusedFactualText
                    )
                    val repairedNumbersSupported = responseNumbersAreSupported(
                        response = repairedResponse,
                        factualText = focusedFactualText
                    )
                    val repairedThresholdConsistency = responseThresholdRulesAreConsistent(
                        response = repairedResponse,
                        factualText = focusedFactualText
                    )
                    val repairedFacetCoverage = responseCoversRequiredFacets(
                        response = repairedResponse,
                        requiredFacets = requiredFacets
                    )
                    val repairedHasUnsupportedInference = hasUnsupportedInferencePhrases(repairedResponse)
                    AppLogger.log(
                        "MainActivity",
                        "LLM repair grounding anchored=$repairedAnchored lexical=$repairedLexicallySupported numbers=$repairedNumbersSupported threshold=$repairedThresholdConsistency facets=$repairedFacetCoverage inference=$repairedHasUnsupportedInference"
                    )
                    if (repairedAnchored && repairedLexicallySupported && repairedNumbersSupported &&
                        repairedFacetCoverage && repairedThresholdConsistency && !repairedHasUnsupportedInference
                    ) {
                        return@withContext ResponseMeta(
                            response = repairedResponse,
                            usedLlm = true,
                            kbSupported = true,
                            kbSupportScore = kbSupportScore,
                            kbCoverage = kbCoverage,
                            kbUnknownRatio = kbUnknownRatio,
                            enforcedKbAbstention = false
                        )
                    }
                }

                val constrainedResponse = generateGroundedConstrainedWithLlm(
                    userQuery = userQuery,
                    factualResponse = kbDirectResponse.orEmpty(),
                    systemPrompt = effectiveSystemPrompt,
                    maxTokens = effectiveMaxTokens
                )
                if (!constrainedResponse.isNullOrBlank()) {
                    val constrainedAnchored = isResponseAnchoredToContext(
                        response = constrainedResponse,
                        userQuery = userQuery,
                        kbContext = focusedFactualText
                    )
                    val constrainedLexicallySupported = isLexicallySupportedByFactualText(
                        response = constrainedResponse,
                        factualText = focusedFactualText,
                        minSupportedRatio = 0.40f
                    )
                    val constrainedNumbersSupported = responseNumbersAreSupported(
                        response = constrainedResponse,
                        factualText = focusedFactualText
                    )
                    val constrainedThresholdConsistency = responseThresholdRulesAreConsistent(
                        response = constrainedResponse,
                        factualText = focusedFactualText
                    )
                    val constrainedFacetCoverage = responseCoversRequiredFacets(
                        response = constrainedResponse,
                        requiredFacets = requiredFacets
                    )
                    val constrainedHasUnsupportedInference = hasUnsupportedInferencePhrases(constrainedResponse)
                    AppLogger.log(
                        "MainActivity",
                        "LLM constrained grounding anchored=$constrainedAnchored lexical=$constrainedLexicallySupported numbers=$constrainedNumbersSupported threshold=$constrainedThresholdConsistency facets=$constrainedFacetCoverage inference=$constrainedHasUnsupportedInference"
                    )
                    if (constrainedAnchored && constrainedLexicallySupported &&
                        constrainedNumbersSupported && constrainedFacetCoverage &&
                        constrainedThresholdConsistency && !constrainedHasUnsupportedInference
                    ) {
                        return@withContext ResponseMeta(
                            response = constrainedResponse,
                            usedLlm = true,
                            kbSupported = true,
                            kbSupportScore = kbSupportScore,
                            kbCoverage = kbCoverage,
                            kbUnknownRatio = kbUnknownRatio,
                            enforcedKbAbstention = false
                        )
                    }
                }

                val noReliableKbResponse = generateUnsafeToAnswerWithLlm(
                    userQuery = userQuery,
                    systemPrompt = effectiveSystemPrompt,
                    maxTokens = effectiveMaxTokens
                )
                if (!noReliableKbResponse.isNullOrBlank()) {
                    AppLogger.log("MainActivity", "LLM strict grounded first no fue aceptado; se responde con abstencion generada por LLM")
                    return@withContext ResponseMeta(
                        response = noReliableKbResponse,
                        usedLlm = true,
                        kbSupported = false,
                        kbSupportScore = kbSupportScore,
                        kbCoverage = kbCoverage,
                        kbUnknownRatio = kbUnknownRatio,
                        enforcedKbAbstention = true
                    )
                }

                AppLogger.log("MainActivity", "LLM strict grounded first no produjo salida valida")
                return@withContext ResponseMeta(
                    response = "",
                    usedLlm = true,
                    kbSupported = false,
                    kbSupportScore = kbSupportScore,
                    kbCoverage = kbCoverage,
                    kbUnknownRatio = kbUnknownRatio,
                    enforcedKbAbstention = true
                )
            }

            try {
                val shouldUseHistory = !isSimpleGreeting && (forcedContext != null || skipKbDirect)
                val llamaHistory = if (shouldUseHistory) conversationHistory else emptyList()
                var streamingChunkCount = 0
                var loggedFirstStreamingChunk = false
                val result = llamaService!!.generateAgriResponseStreaming(
                    userQuery = userQuery,
                    contextFromKB = contextForLlm,
                    maxTokens = effectiveMaxTokens,
                    maxContextLength = effectiveContextLength,
                    systemPrompt = finalSystemPrompt,
                    conversationHistory = llamaHistory,
                    onPartialResponse = { partial ->
                        val rawPartial = partial.trim()
                        if (rawPartial.length >= 5) {
                            streamingChunkCount += 1
                            if (!loggedFirstStreamingChunk) {
                                loggedFirstStreamingChunk = true
                                AppLogger.log("MainActivity", "Streaming primer chunk recibido len=${rawPartial.length}")
                            }
                            onLocalLlmChunk?.invoke(rawPartial)
                        }
                    }
                )

                result.fold(
	                    onSuccess = { response ->
	                        val cleanResponse = response.trim()
		                        if (cleanResponse.length > 10) {
		                            val anchored = !hasKbContext || kbContextForPrompt.isNullOrBlank() || isResponseAnchoredToContext(cleanResponse, userQuery, kbContextForPrompt)
		                            val focusedFactualText = if (
		                                hasKbContext && asksPracticalGuidance && !kbDirectResponse.isNullOrBlank()
		                            ) {
		                                buildFocusedFactualTextForLlm(
		                                    kbDirectResponse.orEmpty(),
		                                    userQuery
		                                )
		                            } else {
		                                ""
		                            }
		                            val thresholdConsistent = focusedFactualText.isBlank() || responseThresholdRulesAreConsistent(
		                                response = cleanResponse,
		                                factualText = focusedFactualText
		                            )
		                            if (!anchored) {
		                                AppLogger.log("MainActivity", "LLM response con ancla KB débil; se mantiene salida de una sola inferencia")
		                            }
		                            if (!thresholdConsistent) {
		                                AppLogger.log("MainActivity", "LLM response con inconsistencia umbral-accion; se mantiene salida de una sola inferencia")
		                            }

	                            AppLogger.log("MainActivity", "LLM response: ${cleanResponse.length} chars mode=$mode")
	                            if (onLocalLlmChunk != null) {
	                                AppLogger.log("MainActivity", "Streaming chunks totales=$streamingChunkCount")
	                            }
	                            return@withContext ResponseMeta(
	                                response = cleanResponse,
	                                usedLlm = true,
		                                kbSupported = hasKbContext && anchored && thresholdConsistent,
	                                kbSupportScore = kbSupportScore,
	                                kbCoverage = kbCoverage,
                                kbUnknownRatio = kbUnknownRatio,
                                enforcedKbAbstention = false
                            )
                        } else {
                            AppLogger.log("MainActivity", "LLM response too short (${cleanResponse.length}) mode=$mode")
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
            return@withContext ResponseMeta(
                response = "",
                usedLlm = false,
                kbSupported = kbSignal,
                kbSupportScore = kbSupportScore,
                kbCoverage = kbCoverage,
                kbUnknownRatio = kbUnknownRatio,
                enforcedKbAbstention = !kbSignal
            )
        }

        AppLogger.log(
            "MainActivity",
            "No reliable LLM response -> abstain mode=$mode kb=$hasKbContext related=$hasRelatedKbSignal llmAvailable=$llmAvailable"
        )

        withContext(Dispatchers.Main) {
            uiStatus = "No se pudo generar respuesta confiable"
        }

        val kbSignal = hasKbContext || hasRelatedKbSignal

        return@withContext ResponseMeta(
            response = "",
            usedLlm = false,
            kbSupported = kbSignal,
            kbSupportScore = kbSupportScore,
            kbCoverage = kbCoverage,
            kbUnknownRatio = kbUnknownRatio,
            enforcedKbAbstention = !kbSignal
        )
    }

    private fun buildKbDirectResponseFromRag(ragContext: SemanticSearchHelper.ContextResult?): String? {
        val contexts = ragContext?.contexts.orEmpty()
        val primary = contexts.firstOrNull()?.answer?.trim().orEmpty()
        if (primary.isBlank()) return null
        return primary
    }

    private fun buildSingleKbContextForPrompt(
        match: SemanticSearchHelper.MatchResult,
        rawResponse: String
    ): String {
        val compactResponse = trimToLineBudget(rawResponse.trim(), maxChars = 900)
        return buildString {
            append("Informacion agricola relevante:\n")
            append("[1] ${match.category.uppercase()}\n")
            append(compactResponse)
        }.trim()
    }

    private fun formatKbDirectResponse(rawResponse: String): String {
        val lines = rawResponse
            .trim()
            .lines()
            .mapNotNull { rawLine ->
                val line = rawLine.trim()
                if (line.isBlank()) return@mapNotNull null

                when {
                    line.startsWith("Condicion:", ignoreCase = true) ->
                        "Cuando aplica: ${line.substringAfter(":").trim()}"
                    line.startsWith("Accion:", ignoreCase = true) ->
                        "Que hacer: ${line.substringAfter(":").trim()}"
                    line.startsWith("Efecto esperado:", ignoreCase = true) ->
                        "Para que sirve: ${line.substringAfter(":").trim()}"
                    line.startsWith("Riesgo si se ignora:", ignoreCase = true) ->
                        "Si no se hace: ${line.substringAfter(":").trim()}"
                    line.startsWith("Aplicabilidad:", ignoreCase = true) ->
                        "Uso practico: ${line.substringAfter(":").trim()}"
                    line.equals("Datos cuantitativos:", ignoreCase = true) ->
                        "Datos clave:"
                    line.startsWith("- ") ->
                        "• ${line.removePrefix("- ").trim()}"
                    else -> line
                }
            }

        val normalized = lines.joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        return humanizeTechnicalTerms(stripUserFacingMarkdown(normalized)).trim()
    }

    private suspend fun generateGroundedRewriteWithLlm(
        userQuery: String,
        factualResponse: String,
        systemPrompt: String,
        maxTokens: Int
    ): String? {
        val factualText = buildFocusedFactualTextForLlm(factualResponse, userQuery)
        if (factualText.isBlank()) return null

        val rewriteSystemPrompt = "$systemPrompt\n" +
            "Tarea obligatoria: redacta la respuesta usando exclusivamente el TEXTO FACTUAL. " +
            "No agregues causas, cifras, definiciones ni recomendaciones que no esten en ese texto. " +
            "No agregues sintomas, explicaciones fisiologicas ni consecuencias que no aparezcan en el texto. " +
            "Conserva el alcance exacto del texto factual; si el texto habla de germinador, almacigo, semilla o cafetal, dilo asi. " +
            "Si la pregunta usa un alcance distinto al del texto factual, aclara ese limite de forma natural. " +
            "Si la pregunta menciona un nutrimento o condicion especifica, centra la respuesta en ese punto y no agregues otros nutrimentos salvo que sean indispensables. " +
            "Si la pregunta menciona una densidad, dosis, edad o umbral especifico, no menciones otros valores alternativos. " +
            "Si hay reglas de umbral, conserva literalmente la direccion de desigualdad y su accion asociada; nunca inviertas menor/mayor ni aplica/no aplica. " +
            "Si existe una seccion REGLAS DE UMBRAL, priorizala de forma textual. " +
            "No emitas juicios de importancia o relevancia del dato pedido; entrega solo la respuesta factual. " +
            "Nunca menciones una recomendacion con dosis incompleta: conserva umbral, dosis y momento completos o omite esa linea. " +
            "Cuando haya pares de datos, por ejemplo densidad y numero de brotes, conserva cada par exactamente como aparece. " +
            "No hagas inferencias con frases como 'si tiene menos', 'si tiene mas', 'esto indica' o 'por eso' salvo que el texto las diga literalmente. " +
            "Si el usuario pide indicaciones, responde solo con 3 a 6 pasos cortos numerados, sin parrafo adicional, usando momento, dosis y condicion solo cuando aparezcan. " +
            "Responde en espanol claro y no menciones el TEXTO FACTUAL."

        val result = llamaService?.generateAgriResponse(
            userQuery = userQuery,
            contextFromKB = "TEXTO FACTUAL:\n$factualText",
            maxTokens = maxTokens.coerceIn(160, 360),
            maxContextLength = 1200,
            systemPrompt = rewriteSystemPrompt,
            conversationHistory = emptyList()
        ) ?: return null

        return result.getOrNull()
            ?.trim()
            ?.takeIf { it.length > 10 && !isMalformedLlmResponse(it) }
    }

    private suspend fun generateGroundedRepairWithLlm(
        userQuery: String,
        factualResponse: String,
        previousResponse: String,
        systemPrompt: String,
        maxTokens: Int
    ): String? {
        val factualText = buildFocusedFactualTextForLlm(factualResponse, userQuery)
        if (factualText.isBlank()) return null

        val repairPrompt = "$systemPrompt\n" +
            "Tarea obligatoria: corrige la RESPUESTA ANTERIOR usando exclusivamente el TEXTO FACTUAL. " +
            "Elimina cualquier fragmento no respaldado por el TEXTO FACTUAL. " +
            "No cambies numeros ni unidades que si esten en el TEXTO FACTUAL. " +
            "Si hay reglas de umbral, conserva literalmente la direccion de desigualdad y su accion asociada; no inviertas menor/mayor ni aplica/no aplica. " +
            "Si falta informacion para responder una parte de la pregunta, dilo de forma breve sin inventar. " +
            "Si el usuario pidio indicaciones, entrega 3 a 6 pasos cortos y precisos."

        val result = llamaService?.generateAgriResponse(
            userQuery = userQuery,
            contextFromKB = "TEXTO FACTUAL:\n$factualText\n\nRESPUESTA ANTERIOR:\n$previousResponse",
            maxTokens = maxTokens.coerceIn(160, 380),
            maxContextLength = 1300,
            systemPrompt = repairPrompt,
            conversationHistory = emptyList()
        ) ?: return null

        return result.getOrNull()
            ?.trim()
            ?.takeIf { it.length > 10 && !isMalformedLlmResponse(it) }
    }

    private suspend fun generateGroundedConstrainedWithLlm(
        userQuery: String,
        factualResponse: String,
        systemPrompt: String,
        maxTokens: Int
    ): String? {
        val factualText = buildFocusedFactualTextForLlm(factualResponse, userQuery)
        if (factualText.isBlank()) return null

        val constrainedPrompt = "$systemPrompt\n" +
            "Tarea obligatoria: responde con estilo extractivo guiado usando solo el TEXTO FACTUAL. " +
            "No introduzcas terminos tecnicos nuevos ni explicaciones externas. " +
            "Si hay reglas de umbral, conserva literalmente la direccion de desigualdad y su accion asociada; no inviertas menor/mayor ni aplica/no aplica. " +
            "Si el usuario pide indicaciones, da 3 a 6 pasos cortos con accion, momento, umbral y dosis solo cuando existan en el TEXTO FACTUAL. " +
            "Si falta una pieza de informacion, dilo en una frase breve sin inventar."

        val result = llamaService?.generateAgriResponse(
            userQuery = userQuery,
            contextFromKB = "TEXTO FACTUAL:\n$factualText",
            maxTokens = maxTokens.coerceIn(140, 320),
            maxContextLength = 1200,
            systemPrompt = constrainedPrompt,
            conversationHistory = emptyList()
        ) ?: return null

        return result.getOrNull()
            ?.trim()
            ?.takeIf { it.length > 10 && !isMalformedLlmResponse(it) }
    }

    private data class RankedFactualLine(
        val index: Int,
        val line: String,
        val score: Float,
        val facets: Set<FactualFacet>,
        val tokenOverlap: Float,
        val numberOverlap: Float
    )

    private enum class FactualFacet {
        ACTION,
        TIMING,
        AMOUNT,
        THRESHOLD
    }

    private enum class ThresholdDirection {
        LT,
        LTE,
        GT,
        GTE
    }

    private enum class ThresholdAction {
        APPLY,
        AVOID
    }

    private data class ThresholdRule(
        val value: String,
        val directions: Set<ThresholdDirection>,
        val action: ThresholdAction?
    )

    private fun buildFocusedFactualTextForLlm(
        rawResponse: String,
        userQuery: String
    ): String {
        val formatted = formatKbDirectResponse(rawResponse)
        val lines = formatted.lines().map { it.trim() }.filter { it.isNotBlank() }
        val dataLines = lines.filter { it.startsWith("•") }
        if (dataLines.isEmpty()) return formatted

        val queryTokens = extractInformativeTokens(userQuery)
        val queryNumbers = extractComparableNumbers(userQuery)
        val queryAnchorTokens = queryTokens.filterNot {
            it in setOf(
                "si", "cuando", "como", "que", "hago", "hacer", "debo",
                "aplico", "aplicar", "dosis", "cantidad", "umbral", "menor", "mayor",
                "ppm", "zoca", "suelo", "planta", "sitio", "mes", "meses"
            )
        }.toSet()
        val normalizedQuery = normalizeComparableText(userQuery)
        val wantsRisk = Regex("\\b(riesgo|pasa|ignora|ignorar|problema|consecuencia|afecta)\\b")
            .containsMatchIn(normalizedQuery)
        val wantsEffect = Regex("\\b(para que|sirve|beneficio|efecto|logra|objetivo)\\b")
            .containsMatchIn(normalizedQuery)
        val requiredFacets = inferRequiredResponseFacets(userQuery, formatted)
        val guidance = asksForPracticalGuidance(normalizeForTokenBudget(userQuery))

        val ranked = dataLines.mapIndexed { index, line ->
            val lineTokens = extractInformativeTokens(line)
            val lineNumbers = extractComparableNumbers(line)
            val facets = inferFacetsFromText(line, isQuery = false)

            val tokenOverlap = if (queryTokens.isNotEmpty()) {
                lineTokens.count { it in queryTokens }.toFloat() / queryTokens.size.toFloat()
            } else {
                0f
            }
            val numberOverlap = if (queryNumbers.isNotEmpty()) {
                lineNumbers.count { it in queryNumbers }.toFloat() / queryNumbers.size.toFloat()
            } else {
                0f
            }
            val entityOverlap = if (queryAnchorTokens.isNotEmpty()) {
                lineTokens.count { it in queryAnchorTokens }.toFloat() / queryAnchorTokens.size.toFloat()
            } else {
                tokenOverlap
            }
            val facetCoverage = if (requiredFacets.isNotEmpty()) {
                facets.count { it in requiredFacets }.toFloat() / requiredFacets.size.toFloat()
            } else {
                0f
            }

            val score = tokenOverlap * 0.52f + entityOverlap * 0.20f +
                numberOverlap * 0.18f + facetCoverage * 0.10f +
                if (lineNumbers.isNotEmpty()) 0.02f else 0f

            RankedFactualLine(
                index = index,
                line = line,
                score = score,
                facets = facets,
                tokenOverlap = tokenOverlap,
                numberOverlap = numberOverlap
            )
        }.sortedByDescending { it.score }

        val selected = mutableListOf<RankedFactualLine>()
        val minScore = if (queryTokens.isNotEmpty() || queryNumbers.isNotEmpty()) 0.06f else 0f
        val baseLimit = if (guidance) 4 else 3

        for (candidate in ranked) {
            if (selected.size >= baseLimit) break
            if (candidate.score >= minScore || selected.isEmpty()) {
                selected.add(candidate)
            }
        }
        if (selected.isEmpty() && ranked.isNotEmpty()) {
            selected.add(ranked.first())
        }

        val selectedIndexes = selected.map { it.index }.toMutableSet()
        val coveredFacets = selected.flatMap { it.facets }.toSet()
        val missingFacets = requiredFacets - coveredFacets
        val hasAnchorConstraint = queryAnchorTokens.isNotEmpty()
        val anchorIndices = selected
            .filter { it.tokenOverlap > 0f || it.numberOverlap > 0f }
            .ifEmpty { selected }
            .map { it.index }
        for (facet in missingFacets) {
            val candidate = ranked
                .asSequence()
                .filter { rank ->
                    !selectedIndexes.contains(rank.index) &&
                        facet in rank.facets &&
                        (!hasAnchorConstraint ||
                            extractInformativeTokens(rank.line).any { it in queryAnchorTokens })
                }
                .maxByOrNull { rank ->
                    val distance = anchorIndices.minOfOrNull { anchor ->
                        if (anchor >= rank.index) anchor - rank.index else rank.index - anchor
                    } ?: 99
                    val proximityBonus = when {
                        distance <= 1 -> 0.22f
                        distance == 2 -> 0.14f
                        distance <= 4 -> 0.08f
                        else -> 0f
                    }
                    rank.score + proximityBonus
                }
            if (candidate != null && selected.size < 5) {
                selected.add(candidate)
                selectedIndexes.add(candidate.index)
            }
        }

        if (queryNumbers.isNotEmpty()) {
            val anchors = selected.toList()
            for (anchor in anchors) {
                val currentCovered = selected.flatMap { it.facets }.toSet()
                val missingNow = requiredFacets - currentCovered
                val neighbor = ranked
                    .asSequence()
                    .filter { rank ->
                        !selectedIndexes.contains(rank.index) &&
                            (rank.index == anchor.index - 1 || rank.index == anchor.index + 1) &&
                            (!hasAnchorConstraint ||
                                extractInformativeTokens(rank.line).any { it in queryAnchorTokens }) &&
                            (rank.numberOverlap > 0f || rank.facets.any { it in requiredFacets })
                    }
                    .maxByOrNull { rank ->
                        val facetGain = rank.facets.count { it in missingNow }.toFloat()
                        rank.score + facetGain * 0.2f
                    }
                if (neighbor != null && selected.size < 5) {
                    selected.add(neighbor)
                    selectedIndexes.add(neighbor.index)
                }
            }
        }

        val focusedData = selected
            .sortedBy { it.index }
            .map { it.line }
            .distinct()

        if (focusedData.isEmpty()) return formatted

        val headerLines = mutableListOf<String>()
        var freeTextLines = 0
        val maxFreeTextLines = 1
        for (line in lines) {
            if (line == "Datos clave:" || line.startsWith("•")) continue
            val headerTokens = extractInformativeTokens(line)
            val headerNumbers = extractComparableNumbers(line)
            val headerFacets = inferFacetsFromText(line, isQuery = false)
            val headerRelevant = headerTokens.any { it in queryTokens } ||
                headerNumbers.any { it in queryNumbers } ||
                headerFacets.any { it in requiredFacets }
            val labeled = line.startsWith("Cuando aplica:", ignoreCase = true) ||
                line.startsWith("Que hacer:", ignoreCase = true) ||
                line.startsWith("Uso practico:", ignoreCase = true)
            val optionalEffect = wantsEffect && line.startsWith("Para que sirve:", ignoreCase = true)
            val optionalRisk = wantsRisk && line.startsWith("Si no se hace:", ignoreCase = true)
            val narrativeClauses = if (guidance || queryNumbers.isNotEmpty()) {
                extractRelevantNarrativeClauses(
                    line = line,
                    queryTokens = queryTokens,
                    queryAnchorTokens = queryAnchorTokens,
                    queryNumbers = queryNumbers,
                    requiredFacets = requiredFacets
                )
            } else {
                emptyList()
            }

            if ((labeled && headerRelevant) || optionalEffect || optionalRisk) {
                headerLines.add(line)
            } else if (narrativeClauses.isNotEmpty()) {
                headerLines.addAll(narrativeClauses)
                freeTextLines += 1
            } else {
                val thresholdRelevantFreeText =
                    headerRelevant && FactualFacet.THRESHOLD in headerFacets
                if (freeTextLines < maxFreeTextLines || thresholdRelevantFreeText) {
                    headerLines.add(line)
                }
                freeTextLines += 1
            }
        }

        val thresholdHints = buildThresholdRuleHints(headerLines + focusedData)

        return buildString {
            headerLines.forEach { appendLine(it) }
            if (thresholdHints.isNotEmpty()) {
                appendLine("Reglas de umbral:")
                thresholdHints.forEach { appendLine(it) }
            }
            appendLine("Datos clave:")
            focusedData.forEach { appendLine(it) }
        }.trim()
    }

    private fun buildThresholdRuleHints(lines: List<String>): List<String> {
        val hints = mutableListOf<String>()
        var pendingCondition: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim().removePrefix("•").trim()
            if (line.isBlank()) continue

            val normalized = normalizeComparableText(line)
            val hasThreshold = detectThresholdDirections(normalized).isNotEmpty() &&
                extractComparableNumbers(normalized).isNotEmpty()
            val action = detectThresholdAction(normalized)

            if (line.startsWith("Cuando aplica:", ignoreCase = true) && hasThreshold) {
                pendingCondition = line.substringAfter(":").trim()
            }

            if (line.startsWith("Que hacer:", ignoreCase = true) && action != null) {
                val actionClause = line.substringAfter(":").trim()
                val conditionClause = pendingCondition
                if (!conditionClause.isNullOrBlank() && actionClause.isNotBlank()) {
                    val merged = if (
                        conditionClause.startsWith("si ", ignoreCase = true) ||
                            conditionClause.startsWith("cuando ", ignoreCase = true)
                    ) {
                        "$conditionClause, $actionClause"
                    } else {
                        "Si $conditionClause, $actionClause"
                    }
                    hints.add(merged)
                }
            }

            if (hasThreshold && action != null) {
                hints.add(line)
            }
        }

        return hints
            .map { it.trim().trimEnd('.') }
            .filter { it.length >= 12 }
            .distinct()
            .take(4)
    }

    private fun extractRelevantNarrativeClauses(
        line: String,
        queryTokens: Set<String>,
        queryAnchorTokens: Set<String>,
        queryNumbers: Set<String>,
        requiredFacets: Set<FactualFacet>
    ): List<String> {
        val clauses = line
            .split(Regex("\\s*;\\s*|,\\s*(?=si\\s+)|\\s+y\\s+(?=si\\s+)"))
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotBlank() }
        if (clauses.isEmpty()) return emptyList()

        return clauses
            .filter { clause ->
                val clauseTokens = extractInformativeTokens(clause)
                val clauseNumbers = extractComparableNumbers(clause)
                val clauseFacets = inferFacetsFromText(clause, isQuery = false)
                val anchorHit = clauseTokens.any { it in queryAnchorTokens }
                val lexicalRelevant = if (queryAnchorTokens.isNotEmpty()) {
                    anchorHit
                } else {
                    clauseTokens.any { it in queryTokens }
                }
                val numericRelevant = if (clauseNumbers.any { it in queryNumbers }) {
                    queryAnchorTokens.isEmpty() || anchorHit
                } else {
                    false
                }
                val facetRelevant = clauseFacets.any { it in requiredFacets } &&
                    (queryAnchorTokens.isEmpty() || anchorHit)

                lexicalRelevant || numericRelevant || facetRelevant ||
                    (queryNumbers.isNotEmpty() && FactualFacet.THRESHOLD in clauseFacets && lexicalRelevant)
            }
            .distinct()
            .take(2)
    }

    private fun trimToLineBudget(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text

        val out = StringBuilder()
        for (line in text.lines()) {
            val normalizedLine = line.trim()
            if (normalizedLine.isBlank()) continue
            val extraChars = if (out.isEmpty()) normalizedLine.length else normalizedLine.length + 1
            if (out.length + extraChars > maxChars) break
            if (out.isNotEmpty()) out.append('\n')
            out.append(normalizedLine)
        }

        if (out.isNotEmpty()) return out.toString().trim()
        return text.take(maxChars).trim()
    }

    private suspend fun generateNoReliableKbResponseWithLlm(
        userQuery: String,
        systemPrompt: String,
        maxTokens: Int
    ): String? {
        val abstentionPrompt = "$systemPrompt\n" +
            "Tarea obligatoria: redacta una respuesta honesta y breve. " +
            "No hay evidencia suficiente en los datos disponibles para responder con precision. " +
            "No des dosis, fechas, productos, causas ni recomendaciones tecnicas inventadas. " +
            "Di en lenguaje natural que faltan datos suficientes y pide el dato clave si corresponde. " +
            "Maximo 2 frases."

        val result = llamaService?.generateAgriResponse(
            userQuery = userQuery,
            contextFromKB = null,
            maxTokens = maxTokens.coerceIn(80, 180),
            maxContextLength = 600,
            systemPrompt = abstentionPrompt,
            conversationHistory = emptyList()
        ) ?: return null

        val candidate = result.getOrNull()
            ?.trim()
            ?.takeIf { it.length > 10 && !isMalformedLlmResponse(it) }
        if (!candidate.isNullOrBlank() && isSafeAbstentionResponse(candidate)) {
            return candidate
        }

        val reinforcedPrompt = "$systemPrompt\n" +
            "Responde en 1 o 2 frases. " +
            "La primera frase debe empezar con: 'No tengo datos suficientes en la informacion disponible para responder con precision.' " +
            "No incluyas dosis, fechas, productos ni recomendaciones tecnicas."

        val reinforcedResult = llamaService?.generateAgriResponse(
            userQuery = userQuery,
            contextFromKB = null,
            maxTokens = maxTokens.coerceIn(60, 120),
            maxContextLength = 400,
            systemPrompt = reinforcedPrompt,
            conversationHistory = emptyList()
        ) ?: return null

        return reinforcedResult.getOrNull()
            ?.trim()
            ?.takeIf { it.length > 10 && !isMalformedLlmResponse(it) }
    }

    private suspend fun generateUnsafeToAnswerWithLlm(
        userQuery: String,
        systemPrompt: String,
        maxTokens: Int
    ): String? {
        val abstentionPrompt = "$systemPrompt\n" +
            "Tarea obligatoria: redacta una respuesta breve y honesta. " +
            "Los datos disponibles parecen relacionados, pero no se pudo generar una respuesta suficientemente precisa sin riesgo de alterar cifras o relaciones. " +
            "No des dosis, fechas, productos, causas ni recomendaciones tecnicas. " +
            "Pide reformular con el dato clave o consultar la ficha tecnica. Maximo 2 frases."

        val result = llamaService?.generateAgriResponse(
            userQuery = userQuery,
            contextFromKB = null,
            maxTokens = maxTokens.coerceIn(80, 180),
            maxContextLength = 600,
            systemPrompt = abstentionPrompt,
            conversationHistory = emptyList()
        ) ?: return null

        val candidate = result.getOrNull()
            ?.trim()
            ?.takeIf { it.length > 10 && !isMalformedLlmResponse(it) }
        if (!candidate.isNullOrBlank() && isSafeAbstentionResponse(candidate)) {
            return candidate
        }

        val reinforcedPrompt = "$systemPrompt\n" +
            "Responde en 1 o 2 frases. " +
            "La primera frase debe empezar con: 'No tengo datos suficientes en la informacion disponible para responder con precision.' " +
            "No incluyas dosis, fechas, productos ni recomendaciones tecnicas."

        val reinforcedResult = llamaService?.generateAgriResponse(
            userQuery = userQuery,
            contextFromKB = null,
            maxTokens = maxTokens.coerceIn(60, 120),
            maxContextLength = 400,
            systemPrompt = reinforcedPrompt,
            conversationHistory = emptyList()
        ) ?: return null

        return reinforcedResult.getOrNull()
            ?.trim()
            ?.takeIf { it.length > 10 && !isMalformedLlmResponse(it) }
    }

    private fun isSafeAbstentionResponse(response: String): Boolean {
        val normalized = normalizeComparableText(response)
        val transparencySignals = listOf(
            "no tengo datos suficientes",
            "no hay evidencia suficiente",
            "no puedo responder con precision",
            "prefiero no inventar",
            "faltan datos suficientes"
        )
        val hasTransparency = transparencySignals.any { normalized.contains(it) }
        val forbiddenTechnicalClaims = Regex(
            "\\b(aplique|aplicar|use|usar|dosis|g/planta|g/l|ppm|mezcle|enema|fungicida|insecticida)\\b"
        ).containsMatchIn(normalized)

        return hasTransparency && !forbiddenTechnicalClaims
    }

    private fun isLexicallySupportedByFactualText(
        response: String,
        factualText: String,
        minSupportedRatio: Float = 0.34f
    ): Boolean {
        val responseTokens = extractInformativeTokens(response)
        if (responseTokens.isEmpty()) return false

        val factualTokens = extractInformativeTokens(factualText)
        if (factualTokens.isEmpty()) return false

        val supportedRatio = responseTokens.count { it in factualTokens }.toFloat() / responseTokens.size.toFloat()
        return supportedRatio >= minSupportedRatio
    }

    private fun responseNumbersAreSupported(
        response: String,
        factualText: String
    ): Boolean {
        val responseNumbers = extractComparableNumbers(response)
        if (responseNumbers.isEmpty()) return true

        val factualNumbers = extractComparableNumbers(factualText)
        return responseNumbers.all { it in factualNumbers }
    }

    private fun responseThresholdRulesAreConsistent(
        response: String,
        factualText: String
    ): Boolean {
        val responseRules = extractThresholdRules(response)
        if (responseRules.isEmpty()) return true

        val factualRules = extractThresholdRules(factualText)
        if (factualRules.isEmpty()) return true

        for (responseRule in responseRules) {
            val sameValue = factualRules.filter { it.value == responseRule.value }
            if (sameValue.isEmpty()) continue

            val sameDirection = sameValue.filter { factualRule ->
                factualRule.directions.intersect(responseRule.directions).isNotEmpty()
            }
            if (responseRule.directions.isNotEmpty() && sameDirection.isEmpty()) {
                return false
            }

            if (responseRule.action != null) {
                val directionScoped = if (sameDirection.isNotEmpty()) sameDirection else sameValue
                val factualActions = directionScoped.mapNotNull { it.action }.toSet()
                if (factualActions.isNotEmpty() && responseRule.action !in factualActions) {
                    return false
                }
            }
        }

        return true
    }

    private fun extractThresholdRules(text: String): List<ThresholdRule> {
        val segments = text
            .lines()
            .flatMap { line ->
                line.split(Regex("[.;]"))
                    .map { it.trim() }
            }
            .filter { it.isNotBlank() }

        if (segments.isEmpty()) return emptyList()

        val rules = mutableListOf<ThresholdRule>()
        for (segment in segments) {
            val normalized = normalizeComparableText(segment)
            val directions = detectThresholdDirections(normalized)
            if (directions.isEmpty()) continue

            val numbers = extractComparableNumbers(normalized)
            if (numbers.isEmpty()) continue

            val action = detectThresholdAction(normalized)
            numbers.forEach { number ->
                rules.add(
                    ThresholdRule(
                        value = canonicalComparableNumber(number),
                        directions = directions,
                        action = action
                    )
                )
            }
        }

        return rules
    }

    private fun detectThresholdDirections(normalizedText: String): Set<ThresholdDirection> {
        val directions = mutableSetOf<ThresholdDirection>()

        if (Regex("<=|\\b(menor\\s+o\\s+igual\\s+(?:a|que)|igual\\s+o\\s+menor|a\\s+lo\\s+sumo)\\b")
                .containsMatchIn(normalizedText)
        ) {
            directions.add(ThresholdDirection.LTE)
        }
        if (Regex(">=|\\b(mayor\\s+o\\s+igual\\s+(?:a|que)|igual\\s+o\\s+mayor|al\\s+menos)\\b")
                .containsMatchIn(normalizedText)
        ) {
            directions.add(ThresholdDirection.GTE)
        }
        if (Regex("(?<![<>])<(?![=>])|\\b(menor\\s+de|por\\s+debajo\\s+de|inferior\\s+a|menos\\s+de)\\b")
                .containsMatchIn(normalizedText)
        ) {
            directions.add(ThresholdDirection.LT)
        }
        if (Regex("(?<![<>])>(?![=>])|\\b(mayor\\s+de|por\\s+encima\\s+de|superior\\s+a|mas\\s+de)\\b")
                .containsMatchIn(normalizedText)
        ) {
            directions.add(ThresholdDirection.GT)
        }

        return directions
    }

    private fun detectThresholdAction(normalizedText: String): ThresholdAction? {
        val avoidPattern = Regex(
            "\\b(no\\s+(?:aplic(?:ar|a|o|arlo|arla)|usar|use|recomienda|adicionar|agregar|aport(?:ar|a|e|o))|evit(?:ar|e|a|o)|suspend(?:er|a|e|o)|omitir|no\\s+conviene)\\b"
        )
        if (avoidPattern.containsMatchIn(normalizedText)) {
            return ThresholdAction.AVOID
        }

        val applyPattern = Regex(
            "\\b(aplic(?:ar|a|o|arlo|arla)|usar|use|recomienda|adicionar|agregar|aport(?:ar|a|e|o)|dar|suministrar)\\b"
        )
        if (applyPattern.containsMatchIn(normalizedText)) {
            return ThresholdAction.APPLY
        }

        return null
    }

    private fun canonicalComparableNumber(number: String): String {
        val cleaned = number.trim()
        return cleaned
            .removeSuffix(".0")
            .removeSuffix(".00")
            .ifBlank { cleaned }
    }

    private fun hasUnsupportedInferencePhrases(response: String): Boolean {
        val normalized = normalizeComparableText(response)
        return listOf(
            "si tiene menos",
            "si tiene mas",
            "rango medio",
            "mejora la productividad",
            "protege la planta"
        ).any { normalized.contains(it) }
    }

    private fun inferRequiredResponseFacets(
        userQuery: String,
        factualText: String
    ): Set<FactualFacet> {
        val queryFacets = inferFacetsFromText(userQuery, isQuery = true)
        if (queryFacets.isEmpty()) return emptySet()

        val factualFacets = inferFacetsFromText(factualText, isQuery = false)
        val required = queryFacets.intersect(factualFacets).toMutableSet()

        val normalizedQuery = normalizeComparableText(userQuery)
        val asksGuidance = asksForPracticalGuidance(normalizeForTokenBudget(userQuery))
        val asksAction = FactualFacet.ACTION in queryFacets
        val factualHasAmount = FactualFacet.AMOUNT in factualFacets
        val factualHasTiming = FactualFacet.TIMING in factualFacets
        val asksConcreteAction = Regex(
            "\\b(aplic(?:o|a|ar|arlo|arla)|abono|abonar|fertiliz(?:o|ar)|dosis|cantidad|cuanto)\\b"
        ).containsMatchIn(normalizedQuery)

        if (asksAction && factualHasAmount && asksConcreteAction) {
            required.add(FactualFacet.AMOUNT)
        }

        // En consultas operativas, si el factual trae dosis/momento, la salida debe cubrirlos.
        if ((asksGuidance || asksAction) && factualHasAmount) {
            required.add(FactualFacet.AMOUNT)
        }
        if ((asksGuidance || asksAction) && factualHasTiming) {
            required.add(FactualFacet.TIMING)
        }

        return required
    }

    private fun responseCoversRequiredFacets(
        response: String,
        requiredFacets: Set<FactualFacet>
    ): Boolean {
        if (requiredFacets.isEmpty()) return true
        val responseFacets = inferFacetsFromText(response, isQuery = false)

        if (FactualFacet.AMOUNT in requiredFacets && FactualFacet.AMOUNT !in responseFacets) {
            return false
        }
        if (FactualFacet.TIMING in requiredFacets && FactualFacet.TIMING !in responseFacets) {
            return false
        }

        val matched = requiredFacets.count { it in responseFacets }
        val coverage = matched.toFloat() / requiredFacets.size.toFloat()
        val minCoverage = when {
            requiredFacets.size <= 2 -> 1.0f
            requiredFacets.size == 3 -> 0.67f
            else -> 0.60f
        }

        val hasOperationalFacet = FactualFacet.ACTION in responseFacets ||
            FactualFacet.TIMING in responseFacets ||
            FactualFacet.AMOUNT in responseFacets

        return coverage >= minCoverage && hasOperationalFacet
    }

    private fun inferFacetsFromText(
        text: String,
        isQuery: Boolean
    ): Set<FactualFacet> {
        val normalized = normalizeComparableText(text)
        if (normalized.isBlank()) return emptySet()

        val facets = mutableSetOf<FactualFacet>()
        val actionPattern = Regex(
            "\\b(que\\s+hacer|accion|aplic(?:o|a|ar|arlo|arla)|hago|debo|manej(?:o|ar)|control(?:o|ar)|trat(?:o|ar)|fertiliz(?:o|ar)|abon(?:o|ar)|seleccion(?:ar|o|a)|dejar|deje|program(?:ar|e)|renov(?:ar|e)|zoque(?:o|ar)|pod(?:a|ar|o)|recolect(?:o|ar|e)|adicion(?:ar|a|e|o)|agreg(?:ar|a|e|o)|aport(?:ar|a|e|o)|correg(?:ir|irse|a|e|o))\\b"
        )
        val timingPattern = Regex(
            "\\b(cuando|momento|epoca|al\\s+terminar|despues\\s+de|a\\s+los\\s+\\d+\\s+(mes|meses|dia|dias|semana|semanas|ano|anos|cosecha|cosechas))\\b"
        )
        val amountPattern = Regex(
            "\\b(dosis|cantidad|cuanto|\\d+(?:\\.\\d+)?\\s*(g|kg|l|ml)(?:\\s*(?:/|por)\\s*(planta|sitio|ha|l|hectarea|arbol|100\\s*g|100g))?|\\d+(?:\\.\\d+)?\\s*%)\\b"
        )
        val thresholdPattern = Regex(
            "(<=|>=|<|>)|\\b(menor\\s+de|mayor\\s+de|por\\s+debajo\\s+de|por\\s+encima\\s+de|umbral)\\b"
        )

        if (actionPattern.containsMatchIn(normalized)) {
            facets.add(FactualFacet.ACTION)
        }
        if (timingPattern.containsMatchIn(normalized)) {
            facets.add(FactualFacet.TIMING)
        }
        if (amountPattern.containsMatchIn(normalized)) {
            facets.add(FactualFacet.AMOUNT)
        }
        if (thresholdPattern.containsMatchIn(normalized) && Regex("\\d").containsMatchIn(normalized)) {
            facets.add(FactualFacet.THRESHOLD)
        }

        if (!isQuery && normalized.startsWith("que hacer:")) {
            facets.add(FactualFacet.ACTION)
        }
        if (!isQuery && normalized.startsWith("cuando aplica:")) {
            facets.add(FactualFacet.TIMING)
        }

        return facets
    }

    private fun extractComparableNumbers(text: String): Set<String> {
        val normalized = normalizeComparableText(text)
            .replace(Regex("(?m)^\\s*\\d+[.)]\\s+"), " ")
        return Regex("\\b\\d+(?:\\.\\d+)?\\b")
            .findAll(normalized)
            .map { it.value.trim('.') }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun normalizeComparableText(text: String): String {
        return text.lowercase()
            .replace(Regex("(?<=\\d)\\.(?=\\d{3}(\\D|$))"), "")
            .replace(",", ".")
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
    }

    private fun isResponseAnchoredToContext(
        response: String,
        userQuery: String,
        kbContext: String
    ): Boolean {
        val helper = semanticSearchHelper
        val semanticScore = helper?.scoreResponseGrounding(
            responseText = response,
            userQuery = userQuery,
            contextText = kbContext
        )

        val contextTokens = extractInformativeTokens(kbContext)
        val responseTokens = extractInformativeTokens(response)
        val queryTokens = extractInformativeTokens(userQuery)
            .filterNot {
                it in setOf(
                    "cafe", "cultivo", "siembra", "manejo", "control",
                    "combatir", "tratar", "tratamiento", "hacer", "como", "que"
                )
            }
            .toSet()

        val contextualResponseCoverage = if (responseTokens.isNotEmpty()) {
            responseTokens.count { it in contextTokens }.toFloat() / responseTokens.size.toFloat()
        } else {
            0f
        }
        val queryAnchorCoverage = if (queryTokens.isNotEmpty()) {
            queryTokens.count { it in responseTokens }.toFloat() / queryTokens.size.toFloat()
        } else {
            1f
        }

        val semanticOk = (semanticScore ?: 0f) >= 0.34f
        val lexicalOk = contextualResponseCoverage >= 0.26f && queryAnchorCoverage >= 0.34f
        val anchored = if (semanticScore != null) {
            semanticOk && lexicalOk
        } else {
            lexicalOk
        }
        AppLogger.log(
            "MainActivity",
            "Semantic grounding score=${String.format("%.2f", semanticScore ?: 0f)} " +
                "ctxCov=${String.format("%.2f", contextualResponseCoverage)} " +
                "queryCov=${String.format("%.2f", queryAnchorCoverage)} anchored=$anchored"
        )
        return anchored
    }

    private fun extractInformativeTokens(text: String): Set<String> {
        val normalized = normalizeComparableText(text)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return emptySet()
        val stopWords = setOf(
            "de", "del", "la", "las", "el", "los", "y", "o", "a", "en", "con", "por",
            "para", "al", "un", "una", "unos", "unas", "que", "me", "te", "se",
            "mi", "tu", "su", "sobre", "acerca", "como", "cuando", "donde", "porque",
            "dame", "paso", "pasos", "despues"
        )
        return normalized
            .split(Regex("\\s+"))
            .map { canonicalGroundingToken(it) }
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

    private fun canonicalGroundingToken(token: String): String {
        return when (token) {
            "zoca", "zoqueo", "recepa" -> "zoca"
            "chupon", "chupones", "brote", "brotes", "rebrote", "rebrotes" -> "brote"
            "seleccionar", "seleccione", "selecciona", "seleccion", "preseleccion" -> "seleccion"
            "fertilizar", "fertilizacion", "abonar", "abono", "abonos" -> "fertilizacion"
            "fosforo", "fosforos" -> "fosforo"
            else -> token
        }
    }

    private fun isLikelyAgriculturalQuery(text: String): Boolean {
        val tokens = extractInformativeTokens(text)
        if (tokens.isEmpty()) return false
        val agriKeywords = setOf(
            "cultivo", "siembra", "sembrar", "plantar", "cosecha", "regar", "riego",
            "fertilizar", "fertilizante", "abono", "plaga", "enfermedad", "hongo",
            "pulgon", "mosca", "gusano", "roya", "tizon", "suelo", "huerto", "campo",
            "hoja", "fruto", "raiz", "tallo", "flor", "floracion", "brote", "plantula",
            "semilla", "germinacion", "mancha", "amarillo", "amarilla", "marchitez",
            "seca", "secamiento", "pudricion", "insecto", "maleza", "herbicida",
            "fungicida", "insecticida", "bioinsumo", "compost", "drenaje", "humedad",
            "invernadero", "parcela", "finca", "agricultor", "diagnostico", "sintoma",
            "tomate", "maiz", "papa", "frijol", "cafe", "cebolla", "yuca", "platano",
            "aguacate", "lechuga", "zanahoria", "arroz", "banano"
        )
        return tokens.any { token ->
            token in agriKeywords ||
                token.startsWith("cultiv") ||
                token.startsWith("sembr") ||
                token.startsWith("fertiliz") ||
                token.startsWith("rieg") ||
                token.startsWith("plag") ||
                token.startsWith("enferm") ||
                token.startsWith("hong") ||
                token.startsWith("malez") ||
                token.startsWith("insect") ||
                token.startsWith("sintom")
        }
    }

    private fun isNoInfoStyleResponse(text: String): Boolean {
        val lower = text.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace(Regex("\\s+"), " ")
            .trim()
        val explicitNoInfoPatterns = listOf(
            Regex("^no\\s+tengo\\s+informacion\\b"),
            Regex("^no\\s+tengo\\s+suficiente\\s+informacion\\b"),
            Regex("^no\\s+dispongo\\s+de\\s+informacion\\b"),
            Regex("^no\\s+cuento\\s+con\\s+informacion\\b"),
            Regex("^no\\s+tengo\\s+datos\\b"),
            Regex("^no\\s+tengo\\s+datos\\s+suficientes\\b"),
            Regex("^no\\s+puedo\\s+responder\\b"),
            Regex("^prefiero\\s+no\\s+inventar\\b")
        )
        return explicitNoInfoPatterns.any { it.containsMatchIn(lower) }
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
        lifecycleScope.launch {
            llamaService?.unload()
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
    isLlamaEnabled: Boolean,
    isLlamaLoaded: Boolean,
    llamaModelStatusText: String,
    llamaModelPathText: String,
    isLlamaChecking: Boolean,
    llamaModelMissing: Boolean,
    showSettingsDialog: Boolean,
    isDiagnosticReady: Boolean,
    isDiagnosing: Boolean,
    capturedBitmap: Bitmap?,
    lastDiagnosis: DiseaseResult?,
    feedbackStates: Map<String, MessageFeedbackState>,
    onSendMessage: (String) -> Unit,
    onMicClick: () -> Unit,
    onModeChange: (AppMode) -> Unit,
    onHelpfulFeedback: (String, Boolean) -> Unit,
    onClarityFeedback: (String, Boolean) -> Unit,
    onApplyTodayFeedback: (String, Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onDismissSettings: () -> Unit,
    onToggleLlama: (Boolean) -> Unit,
    onCheckLlamaModel: () -> Unit,
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
                    lastResponse, statusMessage, isModelReady, isProcessing, isListening,
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
                    isDiagnosticReady = isDiagnosticReady,
                    feedbackStates = feedbackStates,
                    onSendMessage = onSendMessage,
                    onMicClick = onMicClick,
                    onHelpfulFeedback = onHelpfulFeedback,
                    onClarityFeedback = onClarityFeedback,
                    onApplyTodayFeedback = onApplyTodayFeedback,
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
                isLlamaEnabled = isLlamaEnabled,
                isLlamaLoaded = isLlamaLoaded,
                llamaModelStatusText = llamaModelStatusText,
                llamaModelPathText = llamaModelPathText,
                isLlamaChecking = isLlamaChecking,
                llamaModelMissing = llamaModelMissing,
                onToggleLlama = onToggleLlama,
                onCheckLlamaModel = onCheckLlamaModel,
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
                LocalModeIndicator(onSettingsClick)
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
    isDiagnosticReady: Boolean,
    feedbackStates: Map<String, MessageFeedbackState>,
    onSendMessage: (String) -> Unit,
    onMicClick: () -> Unit,
    onHelpfulFeedback: (String, Boolean) -> Unit,
    onClarityFeedback: (String, Boolean) -> Unit,
    onApplyTodayFeedback: (String, Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onShowLogs: () -> Unit,
    onSwitchToVoice: () -> Unit,
    onSwitchToCamera: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val feedbackExpandedState = remember { mutableStateMapOf<String, Boolean>() }

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
                    LocalModeIndicator(onSettingsClick)
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
            items(messages, key = { it.id }) { message ->
                ModernMessageBubble(
                    message = message,
                    feedbackState = feedbackStates[message.id],
                    feedbackExpanded = feedbackExpandedState[message.id] == true,
                    onToggleFeedback = {
                        if (message.feedbackEligible && !message.isUser) {
                            feedbackExpandedState[message.id] = !(feedbackExpandedState[message.id] ?: false)
                        }
                    },
                    onHelpfulFeedback = onHelpfulFeedback,
                    onClarityFeedback = onClarityFeedback,
                    onApplyTodayFeedback = onApplyTodayFeedback
                )
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
fun ModernMessageBubble(
    message: ChatMessage,
    feedbackState: MessageFeedbackState? = null,
    feedbackExpanded: Boolean = false,
    onToggleFeedback: () -> Unit = {},
    onHelpfulFeedback: (String, Boolean) -> Unit = { _, _ -> },
    onClarityFeedback: (String, Boolean) -> Unit = { _, _ -> },
    onApplyTodayFeedback: (String, Boolean) -> Unit = { _, _ -> }
) {
    val isThinkingBubble = message.isThinking && !message.isUser
    val canToggleFeedback = !message.isUser && message.feedbackEligible && !isThinkingBubble
    val bubbleModifier = Modifier
        .widthIn(max = 300.dp)
        .padding(4.dp)
        .then(
            if (canToggleFeedback && !feedbackExpanded) {
                Modifier.clickable { onToggleFeedback() }
            } else {
                Modifier
            }
        )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = when {
                message.isUser -> AgroColors.PrimaryLight
                isThinkingBubble -> AgroColors.SurfaceLight
                else -> AgroColors.Surface
            },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (message.isUser) 20.dp else 4.dp, bottomEnd = if (message.isUser) 4.dp else 20.dp),
            modifier = bubbleModifier,
            border = when {
                message.isUser -> null
                isThinkingBubble -> androidx.compose.foundation.BorderStroke(1.dp, AgroColors.Accent.copy(alpha = 0.45f))
                else -> androidx.compose.foundation.BorderStroke(1.dp, AgroColors.SurfaceLight)
            }
        ) {
            Column(Modifier.padding(14.dp)) {
                if (isThinkingBubble) {
                    Text(
                        "Pensando...",
                        style = MaterialTheme.typography.labelMedium,
                        color = AgroColors.Accent,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                }

                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AgroColors.TextPrimary,
                    lineHeight = 22.sp,
                    fontStyle = if (isThinkingBubble) FontStyle.Italic else FontStyle.Normal
                )

                if (!message.isUser && !isThinkingBubble && message.responseGuidanceType != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AgroColors.SurfaceLight
                    ) {
                        Text(
                            text = message.responseGuidanceType.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = AgroColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = canToggleFeedback && feedbackExpanded) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "¿Te sirvio esta respuesta?",
                            style = MaterialTheme.typography.labelMedium,
                            color = AgroColors.TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = feedbackState?.helpful == true,
                                onClick = { onHelpfulFeedback(message.id, true) },
                                enabled = feedbackState?.helpful == null,
                                label = { Text("Me sirvio") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = AgroColors.SurfaceLight,
                                    labelColor = Color.White,
                                    selectedContainerColor = AgroColors.Accent,
                                    selectedLabelColor = Color.White,
                                    disabledContainerColor = AgroColors.SurfaceLight.copy(alpha = 0.9f),
                                    disabledLabelColor = Color.White.copy(alpha = 0.85f)
                                )
                            )
                            FilterChip(
                                selected = feedbackState?.helpful == false,
                                onClick = { onHelpfulFeedback(message.id, false) },
                                enabled = feedbackState?.helpful == null,
                                label = { Text("No me sirvio") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = AgroColors.SurfaceLight,
                                    labelColor = Color.White,
                                    selectedContainerColor = Color(0xFFE57373),
                                    selectedLabelColor = Color.White,
                                    disabledContainerColor = AgroColors.SurfaceLight.copy(alpha = 0.9f),
                                    disabledLabelColor = Color.White.copy(alpha = 0.85f)
                                )
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        FeedbackBinaryQuestion(
                            question = "¿Fue clara?",
                            selectedValue = feedbackState?.clear,
                            enabled = feedbackState?.clear == null,
                            onSelect = { onClarityFeedback(message.id, it) }
                        )
                        Spacer(Modifier.height(6.dp))
                        FeedbackBinaryQuestion(
                            question = "¿La aplicarias hoy?",
                            selectedValue = feedbackState?.wouldApplyToday,
                            enabled = feedbackState?.wouldApplyToday == null,
                            onSelect = { onApplyTodayFeedback(message.id, it) }
                        )
                    }
                }
            }
        }

        if (canToggleFeedback) {
            Row(
                modifier = Modifier
                    .padding(start = 10.dp, end = 10.dp, bottom = 2.dp)
                    .clickable { onToggleFeedback() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (feedbackExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (feedbackExpanded) "Ocultar feedback" else "Mostrar feedback",
                    tint = AgroColors.TextSecondary.copy(alpha = 0.55f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun FeedbackBinaryQuestion(
    question: String,
    selectedValue: Boolean?,
    enabled: Boolean = true,
    onSelect: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.labelSmall,
            color = AgroColors.TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = selectedValue == true,
                onClick = { onSelect(true) },
                enabled = enabled,
                label = { Text("Si") },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = AgroColors.SurfaceLight,
                    labelColor = Color.White,
                    selectedContainerColor = AgroColors.Accent,
                    selectedLabelColor = Color.White,
                    disabledContainerColor = AgroColors.SurfaceLight.copy(alpha = 0.9f),
                    disabledLabelColor = Color.White.copy(alpha = 0.85f)
                )
            )
            FilterChip(
                selected = selectedValue == false,
                onClick = { onSelect(false) },
                enabled = enabled,
                label = { Text("No") },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = AgroColors.SurfaceLight,
                    labelColor = Color.White,
                    selectedContainerColor = Color(0xFFE57373),
                    selectedLabelColor = Color.White,
                    disabledContainerColor = AgroColors.SurfaceLight.copy(alpha = 0.9f),
                    disabledLabelColor = Color.White.copy(alpha = 0.85f)
                )
            )
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
fun LocalModeIndicator(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AgroColors.SurfaceLight)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = "Modo local",
            tint = AgroColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Modo local",
            style = MaterialTheme.typography.labelSmall,
            color = AgroColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    isLlamaEnabled: Boolean,
    isLlamaLoaded: Boolean,
    llamaModelStatusText: String,
    llamaModelPathText: String,
    isLlamaChecking: Boolean,
    llamaModelMissing: Boolean,
    onToggleLlama: (Boolean) -> Unit,
    onCheckLlamaModel: () -> Unit,
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
    val context = LocalContext.current
    var selectedLanguage by remember { 
        mutableStateOf(
            context.getSharedPreferences("farmifai_prefs", Context.MODE_PRIVATE)
                .getString("language", "es") ?: "es"
        )
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
                
                // Sección LLM Local (GGUF)
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
                                            "LLM Local (qwen3.5_FarmifAI2.0/GGUF)",
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
                                            llamaModelMissing -> Color(0xFFE57373)
                                            isLlamaChecking -> AgroColors.TextSecondary
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

                            if (llamaModelMissing && !isLlamaChecking) {
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = onCheckLlamaModel,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AgroColors.Accent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Descargar/Reintentar modelo qwen3.5_FarmifAI2.0")
                                }
                            }

                            if (isLlamaChecking) {
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
                                "Si falla la descarga, copia manualmente un .gguf a: $llamaModelPathText"
                            } else {
                                "Descarga qwen3.5_FarmifAI2.0 desde este panel o copia un modelo .gguf en la carpeta de la app"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AgroColors.TextSecondary
                        )
                    }
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
                                Text("Longitud de respuestas (prioriza completitud)", style = MaterialTheme.typography.labelMedium, color = AgroColors.TextSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Slider(
                                        value = localMaxTokens.toFloat(),
                                        onValueChange = {
                                            localMaxTokens = it.toInt().coerceIn(120, 1200)
                                        },
                                        valueRange = 120f..1200f,
                                        steps = 26,
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
                            textAlign = TextAlign.Center,
                            modifier = Modifier.alpha(tipAlpha)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

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
