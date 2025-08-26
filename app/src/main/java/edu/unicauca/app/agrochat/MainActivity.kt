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
    private var inferenceResultStatus by mutableStateOf<String?>(null)

    // --- CONFIGURACIÓN DEL MODELO Y PRUEBA ---
    private val MODEL_ASSET_NAME = "gpt2_fp16_model.ms"
    private val NUM_THREADS = 2

    // --- CONFIGURACIÓN DE ENTRADA DE PRUEBA PARA INFERENCIA ---
    private val ACTUAL_TOKENS_FOR_TEST = intArrayOf(50256, 220, 198, 198) // Ejemplo: "<|endoftext|> \n\n"

    // El modelo espera una entrada de esta longitud (según el log de error anterior)
    // CORRECCIÓN: Eliminado 'const'
    private val EXPECTED_INPUT_LENGTH = 64

    // ID del token de padding.
    // TODO: VERIFICA Y USA EL ID DE PADDING CORRECTO PARA TU MODELO/TOKENIZADOR GPT-2.
    // 50256 (<|endoftext|>) a veces se usa, otras veces es 0 o un ID específico.
    // CORRECCIÓN: Eliminado 'const'
    private val PADDING_TOKEN_ID = 50256

    // SAMPLE_TOKEN_IDS ahora se genera con padding para tener la longitud correcta
    private val SAMPLE_TOKEN_IDS: IntArray by lazy {
        Log.d("MainActivity", "Generando SAMPLE_TOKEN_IDS con longitud $EXPECTED_INPUT_LENGTH y padding $PADDING_TOKEN_ID")
        val paddedTokens = IntArray(EXPECTED_INPUT_LENGTH) { PADDING_TOKEN_ID } // Inicializar todo con padding

        // Copiar los tokens reales al inicio del array
        ACTUAL_TOKENS_FOR_TEST.copyInto(
            destination = paddedTokens,
            destinationOffset = 0,
            startIndex = 0,
            endIndex = minOf(ACTUAL_TOKENS_FOR_TEST.size, EXPECTED_INPUT_LENGTH) // No exceder el buffer
        )
        paddedTokens
    }
    // --- FIN DE CONFIGURACIÓN DE ENTRADA DE PRUEBA ---


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            uiStatus = "Cargando modelo '$MODEL_ASSET_NAME'..."
            Log.d("MainActivity", uiStatus)

            val loadedHandle = withContext(Dispatchers.IO) {
                MindSporeHelper.loadModelFromAssets(
                    applicationContext,
                    MODEL_ASSET_NAME,
                    NUM_THREADS
                )
            }
            modelHandle = loadedHandle

            if (modelHandle != 0L) {
                uiStatus = "Modelo '$MODEL_ASSET_NAME' cargado. Handle: $modelHandle"
                Log.i("MainActivity", uiStatus)

                inferenceResultStatus = "Ejecutando prueba de inferencia..."
                Log.d("MainActivity", "Preparando para la prueba de inferencia con ${SAMPLE_TOKEN_IDS.size} IDs: ${SAMPLE_TOKEN_IDS.joinToString()}")

                val logitsOutput = withContext(Dispatchers.Default) {
                    MindSporeHelper.predictWithTokenIds(modelHandle, SAMPLE_TOKEN_IDS)
                }

                if (logitsOutput != null) {
                    val resultSummary = "Prueba de Inferencia Exitosa. Logits obtenidos: ${logitsOutput.size}. Primeros 5: ${logitsOutput.take(5).joinToString(", ", "[", "]") { String.format("%.3f", it) }}..."
                    inferenceResultStatus = resultSummary
                    Log.i("MainActivity", resultSummary)
                } else {
                    val errorMsg = "Prueba de Inferencia FALLÓ. (Verifica logs de MSJNI_CPP_API para detalles)"
                    inferenceResultStatus = errorMsg
                    Log.e("MainActivity", errorMsg)
                }

            } else {
                uiStatus = "FALLO al cargar el modelo '$MODEL_ASSET_NAME'."
                Log.e("MainActivity", uiStatus)
            }
        }

        setContent {
            AgroChatTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        statusMessage = uiStatus,
                        inferenceMessage = inferenceResultStatus,
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
            val success = MindSporeHelper.unloadModel(modelHandle)
            if (success) {
                Log.i("MainActivity", "Modelo liberado exitosamente.")
            } else {
                Log.w("MainActivity", "Fallo al liberar el modelo.")
            }
            modelHandle = 0L
        }
    }
}

@Composable
fun MainScreen(statusMessage: String, inferenceMessage: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = statusMessage)
            if (inferenceMessage != null) {
                Text(text = inferenceMessage)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AgroChatTheme {
        MainScreen(
            "Estado de prueba para la vista previa",
            "Resultado de inferencia de prueba..."
        )
    }
}
