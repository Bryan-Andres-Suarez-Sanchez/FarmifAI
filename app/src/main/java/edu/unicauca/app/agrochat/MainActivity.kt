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

// Características de la app para mostrar durante la descarga
object AppFeatures {
    val features = listOf(
        "🧠 IA Offline - Funciona sin internet",
        "🌱 Asesoría agrícola personalizada",
        "📸 Diagnóstico visual de enfermedades",
        "🎯 Reconocimiento de voz en español",
        "🦙 LLM local para respuestas inteligentes",
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
    private var isLlamaEnabled by mutableStateOf(true)  // Toggle para LLM local
    private var llamaModelStatusText by mutableStateOf("Modelo no disponible")
    private var llamaModelPathText by mutableStateOf("")
    private var isLlamaDownloading by mutableStateOf(false)  // Evitar descargas duplicadas
    private var llamaDownloadFailed by mutableStateOf(false)  // Para mostrar botón de reintentar
    private val SIMILARITY_THRESHOLD = 0.40f  // Reducido para aceptar más matches
    
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
            // Inicializar Llama local
            initializeLlama()

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
                        onContinueResponse = { continueLastResponse() },
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
                        onDismissLogs = { showLogsDialog = false }
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
        val llamaService = LlamaService.getInstance()
        
        // Es primera instalación si faltan modelos MindSpore O el modelo LLM
        val needsMindSpore = !service.areAllModelsAvailable(applicationContext)
        val needsLlama = !llamaService.isModelAvailable(applicationContext)
        
        isFirstLaunch = needsMindSpore || needsLlama
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
        val llamaService = LlamaService.getInstance()
        
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
        
        // Modelo LLM
        if (!llamaService.isModelAvailable(applicationContext)) {
            downloadItems.add(DownloadItem(
                name = "Asistente Inteligente",
                description = "Modelo de lenguaje para respuestas naturales",
                sizeMB = 750
            ))
        }
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
                item.name.contains("Asistente") -> downloadLlamaModel(index)
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
            initializeLlama()
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
                val (response, _) = findResponseWithMeta(query)
                
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
        isLlamaEnabled = prefs.getBoolean(KEY_LLAMA_ENABLED, true)
    }
    
    private fun toggleLlama(enabled: Boolean) {
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
                    Log.i("MainActivity", "✅ Modelo descargado: ${file.absolutePath}")
                    llamaModelStatusText = "Descargado: ${file.name}"
                    isLlamaDownloading = false
                    loadLlamaModel()
                }?.onFailure { e ->
                    Log.e("MainActivity", "❌ Error descargando: ${e.message}")
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
        lifecycleScope.launch {
            try {
                uiStatus = "Cargando LLM local..."
                llamaModelStatusText = "Cargando..."
                
                val result = llamaService?.load(applicationContext)
                result?.onSuccess {
                    isLlamaLoaded = true
                    llamaModelStatusText = "Cargado: ${llamaService?.getModelFilename(applicationContext)} (${llamaService?.getModelSizeMB(applicationContext)}MB)"
                    Log.i("MainActivity", "✓ Llama cargado exitosamente")
                    
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
            val success = withContext(Dispatchers.IO) { semanticSearchHelper?.initialize() ?: false }
            if (success) {
                isModelReady = true
                uiStatus = "Toca para hablar"
                lastResponse = "¡Hola! Soy FarmifAI\nTu asistente agrícola con IA.\n\nPregúntame sobre cultivos, plagas, fertilizantes o cualquier tema agrícola."
                AppLogger.log("MainActivity", "✅ SemanticSearch inicializado")
            } else {
                uiStatus = "Error al cargar"
                AppLogger.log("MainActivity", "❌ SemanticSearch falló")
            }
        } catch (e: Throwable) {
            uiStatus = "Error: ${e.message}"
            AppLogger.log("MainActivity", "❌ Error SemanticSearch: ${e.message}")
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
        
        // Si es muy larga (>150 chars), probablemente está completa
        if (trimmed.length > 150) return true
        
        // Si termina con puntuación final, está completa
        val lastChar = trimmed.last()
        if (lastChar in listOf('.', '!', '?', ':', ')', '"', '»')) return true
        
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

    private fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || !isModelReady || isProcessing) return
        chatMessages.add(ChatMessage(userMessage, isUser = true))
        AppLogger.log("MainActivity", "Mensaje: '$userMessage'")
        
        // Guardar query para posible "Continuar"
        lastUserQuery = userMessage

        lifecycleScope.launch {
            isProcessing = true
            
            // Actualizar estado de conexión ANTES de procesar
            updateOnlineStatus()
            
            // Mostrar estado según modo disponible
            uiStatus = when {
                isOnlineMode -> "Consultando IA online..."
                isLlamaEnabled && isLlamaLoaded -> "Generando respuesta local..."
                else -> "Buscando información..."
            }
            
            try {
                val (response, usedLlm) = findResponseWithMeta(userMessage)
                // Si usó LLM local Y la respuesta parece incompleta, permitir "Continuar"
                val isResponseComplete = isResponseComplete(response)
                val canContinue = usedLlm && isLlamaEnabled && isLlamaLoaded && !isResponseComplete
                chatMessages.add(ChatMessage(response, isUser = false, canContinue = canContinue))
                lastResponse = response
                AppLogger.log("MainActivity", "Respuesta: ${response.take(50)}...")
                
                // Actualizar status final
                uiStatus = when {
                    isOnlineMode -> "Online ✓"
                    isLlamaEnabled && isLlamaLoaded -> "Llama ✓"
                    else -> "Offline"
                }
                
                voiceHelper?.speak(response)
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
                // Obtener la última respuesta del bot
                val lastBotResponse = chatMessages.lastOrNull { !it.isUser }?.text ?: ""
                
                // Crear prompt para continuar - simple y corto
                val continuePrompt = "Más sobre: $lastUserQuery"
                
                val result = llamaService?.generateAgriResponse(
                    userQuery = continuePrompt,
                    contextFromKB = lastContext,
                    maxTokens = 150  // Más tokens para respuesta extensa
                )
                
                result?.fold(
                    onSuccess = { response ->
                        val cleanResponse = response.trim()
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

    private suspend fun findResponseWithMeta(userQuery: String): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        var usedLlm = false
        Log.d("MainActivity", "findResponse: isOnlineMode=$isOnlineMode, isLlamaEnabled=$isLlamaEnabled, isLlamaLoaded=$isLlamaLoaded")
        
        // 1. Primero intentar con Groq si está disponible (online)
        if (isOnlineMode && groqService?.isAvailable() == true) {
            Log.d("MainActivity", "→ Usando Groq LLM (online)")
            
            // Preparar historial de conversación
            val history = chatMessages
                .filter { it.text.isNotBlank() }
                .chunked(2)
                .filter { it.size == 2 && it[0].isUser && !it[1].isUser }
                .map { it[0].text to it[1].text }
                .takeLast(5)
            
            val result = groqService!!.query(userQuery, history)
            
            result.fold(
                onSuccess = { response ->
                    AppLogger.log("MainActivity", "✓ Groq OK")
                    return@withContext Pair(response, false)
                },
                onFailure = { error ->
                    AppLogger.log("MainActivity", "✗ Groq falló: ${error.message}")
                    // Continuar con LLM local o búsqueda offline
                }
            )
        }
        
        // 2. RAG: Obtener múltiples contextos relevantes de la KB (Top-3)
        AppLogger.log("MainActivity", "Buscando RAG para: '$userQuery'")
        val ragContext = semanticSearchHelper?.findTopKContexts(
            userQuery = userQuery,
            topK = 3,
            minScore = 0.35f  // Threshold más bajo para capturar más matches
        )
        
        val combinedKBContext = ragContext?.combinedContext
        val bestMatch = ragContext?.contexts?.firstOrNull()
        
        // Guardar contexto para posible "Continuar"
        lastContext = combinedKBContext
        
        AppLogger.log("MainActivity", "RAG: ${ragContext?.contexts?.size ?: 0} contextos, best=${bestMatch?.similarityScore ?: 0f}")
        
        // 3. Intentar con Llama local si está cargado Y habilitado (offline pero inteligente)
        if (isLlamaEnabled && isLlamaLoaded && llamaService != null) {
            AppLogger.log("MainActivity", "→ Usando Llama LLM")
            usedLlm = true
            
            try {
                val result = llamaService!!.generateAgriResponse(
                    userQuery = userQuery,
                    contextFromKB = combinedKBContext,  // Múltiples contextos
                    maxTokens = 150  // Más tokens para respuestas extensas
                )
                
                result.fold(
                    onSuccess = { response ->
                        val cleanResponse = response.trim()
                        // Verificar que la respuesta tenga contenido significativo (más de 10 chars)
                        if (cleanResponse.length > 10) {
                            AppLogger.log("MainActivity", "✓ Llama OK (${cleanResponse.length} chars)")
                            return@withContext Pair(cleanResponse, true)
                        } else {
                            AppLogger.log("MainActivity", "✗ Llama respuesta corta")
                        }
                    },
                    onFailure = { error ->
                        AppLogger.log("MainActivity", "✗ Llama falló: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                AppLogger.log("MainActivity", "❌ Error Llama: ${e.message}")
                // Continuar con búsqueda semántica
            }
        }
        
        // 3.5 RESPUESTA RÁPIDA: Si el score es muy alto (>0.75) y NO hay LLM disponible,
        // usar KB directamente sin LLM (modo ultra-rápido, por ejemplo para "roya del café").
        if (bestMatch != null && bestMatch.similarityScore >= 0.75f && (!isLlamaEnabled || !isLlamaLoaded || llamaService == null)) {
            AppLogger.log("MainActivity", "⚡ Respuesta rápida KB (sin LLM): score=${bestMatch.similarityScore}")
            return@withContext Pair(bestMatch.answer, false)  // false = no usó LLM, no mostrar continuar
        }
        
        // 4. Fallback final: búsqueda semántica pura (offline)
        AppLogger.log("MainActivity", "→ Fallback: búsqueda semántica")
        if (bestMatch == null) {
            AppLogger.log("MainActivity", "❌ No hay match en KB")
            return@withContext Pair("No pude procesar tu pregunta. Intenta reformularla.", false)
        }
        if (bestMatch.similarityScore < SIMILARITY_THRESHOLD) {
            AppLogger.log("MainActivity", "⚠ Score bajo: ${bestMatch.similarityScore} < $SIMILARITY_THRESHOLD")
            // Dar respuesta genérica pero amigable
            return@withContext Pair("¡Hola! Soy tu asistente agrícola. Puedo ayudarte con:\n\n• Cultivos y siembra\n• Control de plagas\n• Riego\n• Fertilización\n\n¿Qué te gustaría saber?", false)
        }
        AppLogger.log("MainActivity", "✓ KB match: ${bestMatch.matchedQuestion} (${bestMatch.similarityScore})")
        return@withContext Pair(bestMatch.answer, false)
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
    onContinueResponse: () -> Unit,
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
    onDismissLogs: () -> Unit
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
                    onContinueResponse = onContinueResponse,
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
                onShowLogs = onShowLogs
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
    onContinueResponse: () -> Unit,
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
                            style = MaterialTheme.typography.titleLarge,
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
                ModernMessageBubble(
                    message = message,
                    onContinue = if (message.canContinue && !isProcessing) onContinueResponse else null
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
fun ModernMessageBubble(message: ChatMessage, onContinue: (() -> Unit)? = null) {
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
                
                // Botón "Continuar" para respuestas del LLM
                if (onContinue != null && !message.isUser && message.canContinue) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onContinue() },
                        color = AgroColors.Accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("➕", fontSize = 12.sp)
                            Text(
                                "Continuar",
                                style = MaterialTheme.typography.labelSmall,
                                color = AgroColors.Accent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
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
            text = if (isOnline) "LLM" else "Local",
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
    onShowLogs: () -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    val context = LocalContext.current
    var selectedLanguage by remember { 
        mutableStateOf(
            context.getSharedPreferences("farmifai_prefs", Context.MODE_PRIVATE)
                .getString("language", "es") ?: "es"
        )
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
                onClick = { onSaveApiKey(apiKey) },
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
                Text(
                    "Enfoca una hoja de la planta y toca para capturar",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AgroColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
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
                                        Log.d("CameraPreview", "✅ Cámara lista")
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
                                Log.d("CameraPreview", "✅ Imagen capturada: ${bitmap.width}x${bitmap.height}")
                                onCapture(bitmap)
                            }
                            override fun onCaptureError(message: String) {
                                isCapturing = false
                                Log.e("CameraPreview", "❌ Error capturando: $message")
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