package edu.unicauca.app.agrochat.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import edu.unicauca.app.agrochat.AppLogger
import edu.unicauca.app.agrochat.MindSporeHelper
import edu.unicauca.app.agrochat.models.LocalModelRegistry
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Resultado de la clasificación de enfermedad de planta
 */
data class DiseaseResult(
    val id: Int,
    val name: String,
    val displayName: String,
    val crop: String,
    val confidence: Float,
    val isHealthy: Boolean
) {
    /**
     * Genera una query optimizada para búsqueda RAG
     */
    fun toRagQuery(): String {
        return if (isHealthy) {
            "Cuidados preventivos para $crop sano mantenimiento"
        } else {
            "$displayName tratamiento control $crop enfermedad"
        }
    }
}

/**
 * Clasificador de enfermedades de plantas usando MindSpore Lite.
 * 
 * Usa un modelo MobileNetV2 entrenado en el dataset PlantVillage
 * para clasificar imágenes de hojas en 38 categorías (14 cultivos × enfermedades).
 */
class PlantDiseaseClassifier(private val context: Context) {
    
    companion object {
        private const val TAG = "PlantDiseaseClassifier"
        private const val MODEL_FILE = "plant_disease_model.ms"
        private const val LABELS_FILE = "plant_disease_labels.json"
        private const val INPUT_SIZE = 224
        private const val PIXEL_SIZE = 3  // RGB
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f
        private const val MIN_CONFIDENCE = 0.03f  // Mínima confianza para reportar resultado (3%)
    }
    
    private var modelHandle: Long = 0L
    private var labels: List<LabelInfo> = emptyList()
    private var isInitialized = false
    
    /**
     * Información de cada etiqueta/clase del modelo
     */
    data class LabelInfo(
        val id: Int,
        val name: String,       // Nombre técnico (ej: "Tomato___Early_blight")
        val display: String,    // Nombre en español (ej: "Tizón temprano tomate")
        val crop: String        // Cultivo (ej: "Tomate")
    )
    
    /**
     * Inicializa el clasificador cargando el modelo y las etiquetas.
     * 
     * @return true si la inicialización fue exitosa
     */
    fun initialize(): Boolean {
        if (isInitialized) {
            AppLogger.log(TAG, "Ya inicializado")
            return true
        }
        
        return try {
            AppLogger.log(TAG, "Inicializando clasificador...")
            
            // Cargar etiquetas
            labels = loadLabels()
            if (labels.isEmpty()) {
                AppLogger.log(TAG, "❌ No se cargaron etiquetas")
                return false
            }
            AppLogger.log(TAG, "✓ ${labels.size} etiquetas cargadas")
            
            val localRegistry = LocalModelRegistry.getInstance()
            val modelPath = localRegistry.getModelPath(context, MODEL_FILE)
            if (modelPath == null) {
                AppLogger.log(TAG, "❌ Modelo requerido no encontrado en almacenamiento interno: $MODEL_FILE")
                return false
            }

            AppLogger.log(TAG, "Cargando modelo MindSpore local desde files/models: $modelPath")
            modelHandle = MindSporeHelper.loadModelFromFilePath(modelPath, numThreads = 2)
            
            if (modelHandle == 0L) {
                AppLogger.log(TAG, "❌ MindSpore devolvió handle=0")
                return false
            }
            
            isInitialized = true
            AppLogger.log(TAG, "✓ Clasificador OK (handle=$modelHandle, ${labels.size} clases)")
            true
            
        } catch (e: Exception) {
            AppLogger.log(TAG, "❌ Error: ${e.message}")
            false
        }
    }
    
    /**
     * Carga las etiquetas desde el archivo JSON
     */
    private fun loadLabels(): List<LabelInfo> {
        return try {
            val jsonString = context.assets.open(LABELS_FILE).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val labelsArray = jsonObject.getJSONArray("labels")
            
            (0 until labelsArray.length()).map { i ->
                val label = labelsArray.getJSONObject(i)
                LabelInfo(
                    id = label.getInt("id"),
                    name = label.getString("name"),
                    display = label.getString("display"),
                    crop = label.getString("crop")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando etiquetas: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Clasifica una imagen de hoja/planta para detectar enfermedades.
     * 
     * @param bitmap La imagen a clasificar (cualquier tamaño, se redimensiona internamente)
     * @return DiseaseResult con la enfermedad detectada, o null si falla o confianza muy baja
     */
    fun classify(bitmap: Bitmap): DiseaseResult? {
        if (!isInitialized) {
            Log.e(TAG, "Clasificador no inicializado. Llama a initialize() primero.")
            return null
        }
        
        return try {
            val startTime = System.currentTimeMillis()
            
            // 1. Redimensionar imagen a 224x224
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            Log.d(TAG, "Imagen redimensionada a ${INPUT_SIZE}x${INPUT_SIZE}")
            
            // 2. Preprocesar imagen a FloatArray normalizado
            val inputData = preprocessImage(resizedBitmap)
            
            // 3. Ejecutar inferencia con MindSpore
            val outputData = MindSporeHelper.runNetFloat(modelHandle, inputData)
            
            if (outputData == null) {
                Log.e(TAG, "La inferencia devolvió null")
                return null
            }

            if (outputData.size != labels.size) {
                Log.w(TAG, "⚠ Tamaño de salida (${outputData.size}) != labels (${labels.size}). Verifica que modelo y labels correspondan.")
            }
            
            // 4. Convertir salida a probabilidades
            // Algunos modelos ya incluyen softmax en la última capa. Si aplicamos softmax de nuevo,
            // las probabilidades se "aplanan" y la confianza cae artificialmente.
            val outputPreview = outputData.take(5).joinToString()
            val probabilities = toProbabilities(outputData)
            Log.d(
                TAG,
                "Output size: ${outputData.size}, first 5 raw: $outputPreview, probsSum: ${String.format("%.4f", probabilities.sum())}"
            )
            
            // 5. Encontrar la clase con mayor probabilidad
            var maxIndex = 0
            var maxProb = probabilities[0]
            for (i in 1 until probabilities.size) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i]
                    maxIndex = i
                }
            }
            
            // Log top 3 predictions for debugging
            val top3 = probabilities.mapIndexed { i, p -> i to p }
                .sortedByDescending { it.second }
                .take(3)
            Log.d(TAG, "Top 3: ${top3.map { "${it.first}:${String.format("%.2f", it.second * 100)}%" }.joinToString()}")
            
            val elapsedTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Inferencia completada en ${elapsedTime}ms")
            
            // 5. Verificar confianza mínima
            if (maxProb < MIN_CONFIDENCE) {
                Log.w(TAG, "Confianza muy baja: $maxProb. No se puede determinar enfermedad.")
                return null
            }
            
            // 6. Obtener información de la etiqueta
            val labelInfo = labels.getOrNull(maxIndex)
            if (labelInfo == null) {
                Log.e(TAG, "Índice de clase $maxIndex fuera de rango")
                return null
            }
            
            val result = DiseaseResult(
                id = labelInfo.id,
                name = labelInfo.name,
                displayName = labelInfo.display,
                crop = labelInfo.crop,
                confidence = maxProb,
                isHealthy = labelInfo.name.contains("healthy", ignoreCase = true)
            )
            
            Log.i(TAG, "✅ Resultado: ${result.displayName} (${(result.confidence * 100).toInt()}%)")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clasificando imagen: ${e.message}", e)
            null
        }
    }
    
    /**
     * Preprocesa la imagen para el modelo:
     * - Extrae valores RGB
     * - Normaliza a rango [0, 1] (modelo entrenado con rescale=1/255)
     * 
     * @param bitmap Imagen de 224x224
     * @return FloatArray de tamaño 224*224*3
     */
    private fun preprocessImage(bitmap: Bitmap): FloatArray {
        val floatArray = FloatArray(INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE)
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        
        bitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        
        var pixelIndex = 0
        for (pixelValue in intValues) {
            // Extraer canales RGB y normalizar a [0, 1] (divide by 255)
            floatArray[pixelIndex++] = (pixelValue shr 16 and 0xFF) / 255.0f  // R
            floatArray[pixelIndex++] = (pixelValue shr 8 and 0xFF) / 255.0f   // G
            floatArray[pixelIndex++] = (pixelValue and 0xFF) / 255.0f         // B
        }
        
        return floatArray
    }
    
    /**
     * Aplica softmax a los logits para convertirlos en probabilidades.
     * Softmax: exp(x_i) / sum(exp(x_j))
     */
    private fun softmax(logits: FloatArray): FloatArray {
        // Encontrar el máximo para estabilidad numérica
        val maxLogit = logits.maxOrNull() ?: 0f
        
        // Calcular exp(x - max) para cada elemento
        val expValues = FloatArray(logits.size) { i ->
            kotlin.math.exp((logits[i] - maxLogit).toDouble()).toFloat()
        }
        
        // Sumar todos los exp
        val sumExp = expValues.sum()
        
        // Dividir cada exp por la suma
        return FloatArray(logits.size) { i ->
            expValues[i] / sumExp
        }
    }

    /**
     * Convierte la salida del modelo a probabilidades.
     * - Si la salida ya parece ser probabilidades (0..1 y suma≈1), se usa tal cual.
     * - Si no, se asume que son logits y se aplica softmax.
     */
    private fun toProbabilities(output: FloatArray): FloatArray {
        if (output.isEmpty()) return output

        val min = output.minOrNull() ?: 0f
        val max = output.maxOrNull() ?: 0f
        val sum = output.sum()

        val looksLikeProbabilities =
            min >= -1e-3f && max <= 1.0f + 1e-3f && kotlin.math.abs(sum - 1.0f) <= 0.02f

        return if (looksLikeProbabilities) {
            output
        } else {
            softmax(output)
        }
    }
    
    /**
     * Verifica si el clasificador está listo para usar
     */
    fun isReady(): Boolean = isInitialized && modelHandle != 0L
    
    /**
     * Obtiene la lista de cultivos soportados
     */
    fun getSupportedCrops(): List<String> {
        return labels.map { it.crop }.distinct()
    }
    
    /**
     * Libera recursos del modelo
     */
    fun release() {
        if (modelHandle != 0L) {
            MindSporeHelper.unloadModel(modelHandle)
            modelHandle = 0L
        }
        isInitialized = false
        Log.d(TAG, "Recursos liberados")
    }
}
