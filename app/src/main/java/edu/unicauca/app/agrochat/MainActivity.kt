package edu.unicauca.app.agrochat

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import edu.unicauca.app.agrochat.ui.theme.AgroChatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var modelHandle by mutableStateOf(0L)
    private var uiStatus by mutableStateOf("Inicializando...")
    private var inferenceInputText by mutableStateOf<String?>(null)
    private var inferenceOutputTokens by mutableStateOf<String?>(null)
    private var decodedOutputText by mutableStateOf<String?>(null)
    private var debugDirectDecodedOutput by mutableStateOf<String?>(null) // Para el debug

    private val MODEL_ASSET_NAME = "gpt2_fp16_model.ms"
    private val NUM_THREADS = 2

    private var tokenizer: UniversalNativeTokenizer? = null

    private val SAMPLE_INPUT_TEXT = "Hello, I'm a language model,"
    private val EXPECTED_INPUT_LENGTH = 64
    private val MAX_NEW_TOKENS_TO_GENERATE = 30


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // --- 1. INICIALIZAR TOKENIZADOR ---
        Log.i("MainActivity", "=== INICIALIZANDO UniversalNativeTokenizer ===")
        try {
            tokenizer = UniversalNativeTokenizer(applicationContext, "tokenizer.json")
            if (tokenizer?.isReady() == true) {
                val tokenizerInfo = "Tokenizador listo. PAD ID: ${tokenizer?.padTokenId}, EOS ID: ${tokenizer?.eosTokenId}, UNK ID: ${tokenizer?.unkTokenId}, Vocab Size: ${tokenizer?.vocabSize ?: "N/A"}"
                Log.i("MainActivity", tokenizerInfo)
                uiStatus = tokenizerInfo
            } else {
                val errorMsg = "FALLO al inicializar UniversalNativeTokenizer o no está listo."
                Log.e("MainActivity", errorMsg)
                uiStatus = errorMsg
            }
        } catch (e: Throwable) {
            val errorMsg = "EXCEPCIÓN CRÍTICA al instanciar UniversalNativeTokenizer: ${e.message}"
            Log.e("MainActivity", errorMsg, e)
            uiStatus = errorMsg
            tokenizer = null
        }
        Log.i("MainActivity", "=== FIN INICIALIZACIÓN UniversalNativeTokenizer ===")

        lifecycleScope.launch {
            if (tokenizer?.isReady() != true) {
                uiStatus = "Error: Tokenizador no está listo. No se puede continuar."
                Log.e("MainActivity", uiStatus)
                return@launch
            }

            val currentPaddingTokenId = tokenizer!!.padTokenId
            // Es crucial que currentPaddingTokenId sea el correcto.
            // Para GPT-2, a menudo se usa el mismo que EOS (e.g., 50256).
            // Si padTokenId es -1 o un valor de error, esto será un problema.
            if (currentPaddingTokenId == -1 || tokenizer!!.unkTokenId == currentPaddingTokenId) {
                Log.e("MainActivity", "PADDING TOKEN ID NO VÁLIDO O ES UNK: $currentPaddingTokenId. ¡La inferencia probablemente fallará o será incorrecta!")
                // Considera un fallback si sabes cuál es el ID de padding correcto para tu modelo,
                // ej. val safePaddingTokenId = if(currentPaddingTokenId == -1) 50256 else currentPaddingTokenId
                // Pero es mejor que el tokenizador lo proporcione correctamente.
            }
            Log.i("MainActivity", "Usando PADDING_TOKEN_ID: $currentPaddingTokenId para el modelo.")

            inferenceInputText = "Entrada: '$SAMPLE_INPUT_TEXT'"

            // --- A. Codificar Texto de Entrada (Prompt) ---
            Log.d("MainActivity", "Codificando prompt: '$SAMPLE_INPUT_TEXT'")
            // addSpecialTokens=true: Para GPT-2, esto a menudo añade el token de inicio de secuencia si no está.
            // Si tu tokenizer ya añade implícitamente BOS/EOS o si no se necesita, podría ser false.
            // Por ahora, lo mantenemos en true.
            val promptTokenIds = tokenizer!!.encode(SAMPLE_INPUT_TEXT, addSpecialTokens = true)

            if (promptTokenIds.isEmpty() || (promptTokenIds.size == 1 && promptTokenIds[0] == tokenizer!!.unkTokenId)) {
                uiStatus = "Error: No se pudieron obtener tokens válidos para '$SAMPLE_INPUT_TEXT'"
                Log.e("MainActivity", uiStatus)
                return@launch
            }
            Log.i("MainActivity", "Tokens del prompt (${promptTokenIds.size}): ${promptTokenIds.joinToString()}")

            // --- Preparar la entrada para el modelo con padding (Right-padding) ---
            val tokensToFeed = IntArray(EXPECTED_INPUT_LENGTH) { currentPaddingTokenId }
            val numPromptTokensToCopy = minOf(promptTokenIds.size, EXPECTED_INPUT_LENGTH)

            // Copia los tokens del prompt al inicio del array 'tokensToFeed'.
            // El resto del array (si hay espacio) permanecerá con 'currentPaddingTokenId'.
            // Esto es right-padding.
            System.arraycopy(promptTokenIds, 0, tokensToFeed, 0, numPromptTokensToCopy)

            Log.i("MainActivity", "Tokens de entrada al modelo (${tokensToFeed.size}) (Right-padded): ${tokensToFeed.joinToString()}")

            // --- B. Carga del Modelo ---
            uiStatus = "Cargando modelo '$MODEL_ASSET_NAME'..."
            val loadedModelHandle = withContext(Dispatchers.IO) {
                MindSporeHelper.loadModelFromAssets(applicationContext, MODEL_ASSET_NAME, NUM_THREADS)
            }
            modelHandle = loadedModelHandle

            if (modelHandle == 0L) {
                uiStatus = "FALLO al cargar el modelo '$MODEL_ASSET_NAME'."
                Log.e("MainActivity", uiStatus)
                return@launch
            }
            uiStatus = "Modelo '$MODEL_ASSET_NAME' cargado. Handle: $modelHandle"
            Log.i("MainActivity", uiStatus)

            // --- C. Ejecutar Inferencia ---
            Log.d("MainActivity", "Ejecutando inferencia con ${tokensToFeed.size} tokens.")
            val logitsOutput: FloatArray? = withContext(Dispatchers.Default) {
                MindSporeHelper.predictWithTokenIds(modelHandle, tokensToFeed)
            }

            // --- D. Procesar Logits y Decodificar Salida Generada ---
            if (logitsOutput != null && logitsOutput.isNotEmpty()) {
                Log.i("MainActivity", "Inferencia Exitosa. Forma de Logits obtenidos: ${logitsOutput.size}.")

                val currentVocabSize = tokenizer!!.vocabSize
                if (currentVocabSize == null) Log.w("MainActivity", "VocabSize es null en tokenizador. Usando fallback.")
                val vocabSizeForArgmax = currentVocabSize ?: 50257

                val numOutputSequenceTokens = logitsOutput.size / vocabSizeForArgmax

                if (numOutputSequenceTokens == EXPECTED_INPUT_LENGTH && logitsOutput.size % vocabSizeForArgmax == 0) {
                    val fullPredictedTokenIds = IntArray(numOutputSequenceTokens)
                    for (i in 0 until numOutputSequenceTokens) {
                        var maxLogit = -Float.MAX_VALUE
                        var bestTokenId = 0
                        val logitStartIndex = i * vocabSizeForArgmax
                        for (j in 0 until vocabSizeForArgmax) {
                            val logit = logitsOutput[logitStartIndex + j]
                            if (logit > maxLogit) { maxLogit = logit; bestTokenId = j }
                        }
                        fullPredictedTokenIds[i] = bestTokenId
                    }
                    Log.i("MainActivity", "Secuencia completa de tokens predichos: ${fullPredictedTokenIds.joinToString()}")

                    // --- [DEBUG] Decodificar los primeros N tokens de la salida directamente ---
                    val tokensToDecodeDirectly = fullPredictedTokenIds.take(MAX_NEW_TOKENS_TO_GENERATE).toIntArray()
                    debugDirectDecodedOutput = tokenizer!!.decode(tokensToDecodeDirectly, skipSpecialTokens = true)
                    Log.i("MainActivity", "[DEBUG] Texto decodificado directamente de los primeros ${tokensToDecodeDirectly.size} tokens de salida: '$debugDirectDecodedOutput'")


                    // --- Extracción del texto generado (después del prompt) ---
                    val promptLengthActual = promptTokenIds.size
                    var startOfGeneratedTextIndex = promptLengthActual
                    var promptMatched = true

                    if (promptLengthActual <= fullPredictedTokenIds.size) {
                        for (k in 0 until promptLengthActual) {
                            if (fullPredictedTokenIds[k] != promptTokenIds[k]) {
                                promptMatched = false
                                Log.w("MainActivity", "El modelo no hizo eco del prompt exactamente en el token $k. Predicción del modelo: ${fullPredictedTokenIds[k]}, Prompt esperado: ${promptTokenIds[k]}.")
                                break
                            }
                        }
                    } else {
                        promptMatched = false
                    }

                    if (promptMatched) {
                        Log.i("MainActivity", "El modelo parece haber hecho eco del prompt. La generación comienza después del token $promptLengthActual.")
                    } else {
                        Log.i("MainActivity", "No hubo un eco perfecto del prompt. La generación (para extracción) se tomará después de la longitud del prompt original ($promptLengthActual).")
                        // En este caso de "no eco", la variable 'debugDirectDecodedOutput' es más informativa
                        // de lo que el modelo está generando en bruto.
                    }

                    var extractedGeneratedTokenIds = IntArray(0)
                    if (startOfGeneratedTextIndex < fullPredictedTokenIds.size) {
                        val potentialGeneratedTokens = fullPredictedTokenIds.sliceArray(startOfGeneratedTextIndex until fullPredictedTokenIds.size)
                        val eosTokenId = tokenizer!!.eosTokenId
                        val eosIndexInPotential = potentialGeneratedTokens.indexOf(eosTokenId)

                        if (eosIndexInPotential != -1) {
                            extractedGeneratedTokenIds = potentialGeneratedTokens.sliceArray(0..eosIndexInPotential)
                            Log.i("MainActivity", "Token EOS encontrado en la generación en el índice relativo $eosIndexInPotential. Longitud: ${extractedGeneratedTokenIds.size}")
                        } else {
                            extractedGeneratedTokenIds = potentialGeneratedTokens.take(MAX_NEW_TOKENS_TO_GENERATE).toIntArray()
                            Log.i("MainActivity", "No se encontró token EOS. Tomando hasta ${extractedGeneratedTokenIds.size} tokens (máx $MAX_NEW_TOKENS_TO_GENERATE).")
                        }
                    } else {
                        Log.w("MainActivity", "Índice de inicio de generación ($startOfGeneratedTextIndex) es >= que la secuencia de salida total. No hay tokens nuevos.")
                    }

                    if (extractedGeneratedTokenIds.isNotEmpty()) {
                        inferenceOutputTokens = "Tokens generados (nuevos, extraídos): ${extractedGeneratedTokenIds.joinToString()}"
                        Log.i("MainActivity", inferenceOutputTokens!!)
                        decodedOutputText = tokenizer!!.decode(extractedGeneratedTokenIds, skipSpecialTokens = true)
                        Log.i("MainActivity", "Texto decodificado (extraído): '$decodedOutputText'")
                        uiStatus = "Generación completada (extraída)."
                    } else {
                        decodedOutputText = "[No se generó texto nuevo (extraído)]"
                        inferenceOutputTokens = "Tokens generados (nuevos, extraídos): Ninguno"
                        uiStatus = "No se generó texto nuevo (extraído)."
                    }

                } else {
                    val errorMsg = "La forma de los logits (${logitsOutput.size}) o la longitud de salida (${numOutputSequenceTokens} vs ${EXPECTED_INPUT_LENGTH}) no es la esperada."
                    Log.e("MainActivity", errorMsg)
                    decodedOutputText = "[Error al procesar logits: $errorMsg]"
                    uiStatus = "Error procesando salida."
                    debugDirectDecodedOutput = "[Error procesando logits]"
                }
            } else {
                val errorMsg = "Inferencia FALLÓ o no devolvió logits."
                Log.e("MainActivity", errorMsg)
                decodedOutputText = "[Inferencia fallida]"
                uiStatus = errorMsg
                debugDirectDecodedOutput = "[Inferencia fallida]"
            }
        }

        setContent {
            AgroChatTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        statusMessage = uiStatus,
                        inputText = inferenceInputText,
                        outputText = decodedOutputText,
                        debugOutput = debugDirectDecodedOutput, // Mostrar salida de debug
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (modelHandle != 0L) {
            Log.d("MainActivity", "Liberando modelo con handle: $modelHandle")
            MindSporeHelper.unloadModel(modelHandle)
            modelHandle = 0L
        }
        tokenizer?.release()
        tokenizer = null
        Log.i("MainActivity", "Recursos liberados en onDestroy.")
    }
}

@Composable
fun MainScreen(statusMessage: String, inputText: String?, outputText: String?, debugOutput: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp) // Reducido un poco
        ) {
            Text(text = "Estado: $statusMessage")
            if (inputText != null) {
                Text(text = "Entrada: '$inputText'")
            }
            if (debugOutput != null) { // Mostrar la salida de debug primero
                Text(text = "Salida Directa (DEBUG): '$debugOutput'")
            }
            if (outputText != null && outputText != debugOutput) { // Mostrar la extraída si es diferente
                Text(text = "Salida Extraída: '$outputText'")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AgroChatTheme {
        MainScreen(
            statusMessage = "Tokenizador listo. Modelo cargado.",
            inputText = "Entrada: 'Hello, I'm a language model,'",
            outputText = "a language for thinking, a language for expressing thoughts.",
            debugOutput = "Hello, I'm a language model, and I'm a good boy."
        )
    }
}


