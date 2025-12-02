package edu.unicauca.app.agrochat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import edu.unicauca.app.agrochat.llm.GroqService
import edu.unicauca.app.agrochat.llm.LlamaService
import edu.unicauca.app.agrochat.mindspore.SemanticSearchHelper
import edu.unicauca.app.agrochat.ui.theme.AgroChatTheme
import edu.unicauca.app.agrochat.voice.VoiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val timestamp: Long = System.currentTimeMillis()
)

enum class AppMode { VOICE, CHAT }

class MainActivity : ComponentActivity() {

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
    private val SIMILARITY_THRESHOLD = 0.55f
    
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hasAudioPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // Cargar preferencias
        loadPreferences()
        
        // Inicializar Groq
        initializeGroq()
        
        // Inicializar Llama local
        initializeLlama()
        
        lifecycleScope.launch { initializeSemanticSearch() }
        if (hasAudioPermission) initializeVoice()

        setContent {
            AgroChatTheme {
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
                    showSettingsDialog = showSettingsDialog,
                    onSendMessage = { sendMessage(it) },
                    onMicClick = { handleMicClick() },
                    onModeChange = { currentMode = it },
                    onSettingsClick = { showSettingsDialog = true },
                    onDismissSettings = { showSettingsDialog = false },
                    onSaveApiKey = { key -> saveGroqApiKey(key) },
                    onToggleLlama = { enabled -> toggleLlama(enabled) }
                )
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
        
        // Verificar si el modelo está disponible y cargarlo automáticamente
        if (llamaService?.isModelAvailable(applicationContext) == true) {
            Log.i("MainActivity", "Modelo Llama disponible (${llamaService?.getModelSizeMB(applicationContext)}MB), cargando automáticamente...")
            
            lifecycleScope.launch {
                try {
                    uiStatus = "Cargando LLM local..."
                    val result = llamaService?.load(applicationContext)
                    result?.onSuccess {
                        isLlamaLoaded = true
                        Log.i("MainActivity", "✓ Llama cargado exitosamente")
                        
                        // Si no hay internet, mostrar que Llama está listo
                        updateOnlineStatus()
                        if (!isOnlineMode && isLlamaEnabled) {
                            withContext(Dispatchers.Main) {
                                uiStatus = "Llama listo ✓"
                                Toast.makeText(
                                    applicationContext, 
                                    "🦙 LLM Local activado automáticamente (offline)", 
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }?.onFailure { e ->
                        Log.w("MainActivity", "Error cargando Llama: ${e.message}")
                        isLlamaLoaded = false
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error inicializando Llama", e)
                    isLlamaLoaded = false
                }
            }
        } else {
            Log.i("MainActivity", "Modelo Llama no disponible - usando solo búsqueda semántica")
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
        try {
            semanticSearchHelper = SemanticSearchHelper(applicationContext)
            val success = withContext(Dispatchers.IO) { semanticSearchHelper?.initialize() ?: false }
            if (success) {
                isModelReady = true
                uiStatus = "Toca para hablar"
                lastResponse = "¡Hola! Soy AgroChat 🌱\nTu asistente agrícola con IA.\n\nPregúntame sobre cultivos, plagas, fertilizantes o cualquier tema agrícola."
            } else {
                uiStatus = "Error al cargar"
            }
        } catch (e: Throwable) {
            uiStatus = "Error: ${e.message}"
        }
    }

    private fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || !isModelReady || isProcessing) return
        chatMessages.add(ChatMessage(userMessage, isUser = true))

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
                val response = findResponse(userMessage)
                chatMessages.add(ChatMessage(response, isUser = false))
                lastResponse = response
                
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

    private suspend fun findResponse(userQuery: String): String = withContext(Dispatchers.IO) {
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
                    Log.d("MainActivity", "✓ Groq respondió exitosamente")
                    return@withContext response
                },
                onFailure = { error ->
                    Log.w("MainActivity", "✗ Groq falló: ${error.message}, usando fallback offline")
                    // Continuar con LLM local o búsqueda offline
                }
            )
        }
        
        // 2. RAG: Obtener múltiples contextos relevantes de la KB (Top-3)
        val ragContext = semanticSearchHelper?.findTopKContexts(
            userQuery = userQuery,
            topK = 3,
            minScore = 0.4f
        )
        
        val combinedKBContext = ragContext?.combinedContext
        val bestMatch = ragContext?.contexts?.firstOrNull()
        
        Log.d("MainActivity", "RAG: ${ragContext?.contexts?.size ?: 0} contextos encontrados")
        
        // 3. Intentar con Llama local si está cargado Y habilitado (offline pero inteligente)
        if (isLlamaEnabled && isLlamaLoaded && llamaService != null) {
            Log.d("MainActivity", "→ Usando Llama LLM (offline local) con RAG")
            
            try {
                val result = llamaService!!.generateAgriResponse(
                    userQuery = userQuery,
                    contextFromKB = combinedKBContext,  // Múltiples contextos
                    maxTokens = 350  // Aumentado para respuestas más completas
                )
                
                result.fold(
                    onSuccess = { response ->
                        val cleanResponse = response.trim()
                        // Verificar que la respuesta tenga contenido significativo (más de 10 chars)
                        if (cleanResponse.length > 10) {
                            Log.d("MainActivity", "✓ Llama respondió exitosamente (${cleanResponse.length} chars)")
                            return@withContext cleanResponse
                        } else {
                            Log.w("MainActivity", "✗ Llama respuesta muy corta: '$cleanResponse', usando fallback")
                        }
                    },
                    onFailure = { error ->
                        Log.w("MainActivity", "✗ Llama falló: ${error.message}, usando búsqueda semántica")
                    }
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "✗ Error con Llama: ${e.message}", e)
                // Continuar con búsqueda semántica
            }
        }
        
        // 4. Fallback final: búsqueda semántica pura (offline)
        Log.d("MainActivity", "→ Usando MindSpore búsqueda semántica (offline)")
        if (bestMatch == null) return@withContext "No pude procesar tu pregunta."
        if (bestMatch.similarityScore < SIMILARITY_THRESHOLD) {
            return@withContext "No encontré información sobre eso.\n\nPuedo ayudarte con:\n• Cultivos\n• Plagas\n• Fertilización\n• Riego"
        }
        Log.d("MainActivity", "✓ Usando respuesta de KB (score: ${bestMatch.similarityScore})")
        return@withContext bestMatch.answer
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceHelper?.release()
        semanticSearchHelper?.release()
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
    showSettingsDialog: Boolean,
    onSendMessage: (String) -> Unit,
    onMicClick: () -> Unit,
    onModeChange: (AppMode) -> Unit,
    onSettingsClick: () -> Unit,
    onDismissSettings: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onToggleLlama: (Boolean) -> Unit
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
                AppMode.VOICE -> VoiceModeScreen(lastResponse, statusMessage, isModelReady, isProcessing, isListening, isOnlineMode, onMicClick, onSettingsClick) { onModeChange(AppMode.CHAT) }
                AppMode.CHAT -> ChatModeScreen(messages, statusMessage, isModelReady, isProcessing, isListening, isOnlineMode, onSendMessage, onMicClick, onSettingsClick) { onModeChange(AppMode.VOICE) }
            }
        }
        
        // Diálogo de configuración
        if (showSettingsDialog) {
            SettingsDialog(
                onDismiss = onDismissSettings,
                onSaveApiKey = onSaveApiKey,
                isLlamaEnabled = isLlamaEnabled,
                isLlamaLoaded = isLlamaLoaded,
                onToggleLlama = onToggleLlama
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
    onMicClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchToChat: () -> Unit
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
                Text("🌱", fontSize = 36.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("AgroChat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AgroColors.TextPrimary)
                    Text("Asistente Agrícola", style = MaterialTheme.typography.bodySmall, color = AgroColors.TextSecondary)
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
        
        // Botón para cambiar a chat
        FloatingActionButton(
            onClick = onSwitchToChat,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .navigationBarsPadding(),
            containerColor = AgroColors.SurfaceLight,
            contentColor = AgroColors.TextPrimary
        ) {
            Icon(Icons.Default.ChatBubble, "Modo Chat")
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
    onSendMessage: (String) -> Unit,
    onMicClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchToVoice: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Column(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxWidth(), color = AgroColors.Surface, tonalElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    Text("🌱", fontSize = 32.sp)
                    Column {
                        Text("AgroChat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AgroColors.TextPrimary)
                        Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = AgroColors.TextSecondary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnlineIndicator(isOnlineMode, onSettingsClick)
                    IconButton(onClick = onSwitchToVoice, Modifier.size(48.dp).background(AgroColors.SurfaceLight, CircleShape)) {
                        Icon(Icons.Default.RecordVoiceOver, "Modo Voz", tint = AgroColors.Accent)
                    }
                }
            }
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            if (messages.isEmpty()) item { EmptyStateChat() }
            items(messages) { ModernMessageBubble(it) }
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
        Text("¡Bienvenido a AgroChat!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AgroColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Pregunta sobre cultivos, plagas, fertilizantes o cualquier tema agrícola", style = MaterialTheme.typography.bodyMedium, color = AgroColors.TextSecondary, textAlign = TextAlign.Center)
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
    Row(Modifier.fillMaxWidth(), if (message.isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (message.isUser) AgroColors.PrimaryLight else AgroColors.Surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (message.isUser) 20.dp else 4.dp, bottomEnd = if (message.isUser) 4.dp else 20.dp),
            modifier = Modifier.widthIn(max = 300.dp).padding(4.dp),
            border = if (!message.isUser) androidx.compose.foundation.BorderStroke(1.dp, AgroColors.SurfaceLight) else null
        ) {
            Text(message.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge, color = AgroColors.TextPrimary, lineHeight = 22.sp)
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
    onToggleLlama: (Boolean) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AgroColors.Surface,
        titleContentColor = AgroColors.TextPrimary,
        textContentColor = AgroColors.TextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = AgroColors.Accent)
                Spacer(Modifier.width(12.dp))
                Text("Configuración")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Sección LLM Local (Llama)
                Surface(
                    color = AgroColors.SurfaceLight,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                                if (isLlamaLoaded) "Modelo cargado (770MB)" else "Modelo no disponible",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isLlamaLoaded) AgroColors.Accent else AgroColors.TextSecondary
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
                }
                
                if (isLlamaEnabled && isLlamaLoaded) {
                    Text(
                        "✓ Respuestas inteligentes offline con IA generativa",
                        style = MaterialTheme.typography.bodySmall,
                        color = AgroColors.Accent
                    )
                } else if (!isLlamaLoaded) {
                    Text(
                        "Copia el modelo GGUF a la carpeta de la app para habilitar",
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


