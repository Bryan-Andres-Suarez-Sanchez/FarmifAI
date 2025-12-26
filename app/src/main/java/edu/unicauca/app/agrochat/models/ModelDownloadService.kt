package edu.unicauca.app.agrochat.models

import android.content.Context
import android.util.Log
import edu.unicauca.app.agrochat.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * ModelDownloadService - Gestiona la descarga de modelos MindSpore
 * Permite descargar modelos grandes desde un repositorio remoto
 * para reducir el tamaño de la APK
 */
class ModelDownloadService private constructor() {

    companion object {
        private const val TAG = "ModelDownloadService"
        
        // URLs de descarga de modelos desde GitHub (raw URLs con LFS)
        // Repo: https://github.com/pazussa/models_FarmifAI
        private const val BASE_URL = "https://media.githubusercontent.com/media/pazussa/models_FarmifAI/main"
        
        // Modelos disponibles para descarga
        val MODELS = mapOf(
            "sentence_encoder.ms" to ModelInfo(
                filename = "sentence_encoder.ms",
                url = "$BASE_URL/sentence_encoder.ms",
                sizeBytes = 235_222_480L,  // ~225MB
                description = "Modelo de embeddings semánticos",
                required = true
            ),
            "plant_disease_model.ms" to ModelInfo(
                filename = "plant_disease_model.ms",
                url = "$BASE_URL/plant_disease_model.ms",
                sizeBytes = 12_028_752L,  // ~12MB
                description = "Modelo de diagnóstico de enfermedades",
                required = true
            ),
            "sentence_tokenizer.json" to ModelInfo(
                filename = "sentence_tokenizer.json",
                url = "$BASE_URL/sentence_tokenizer.json",
                sizeBytes = 17_082_999L,  // ~17MB
                description = "Tokenizador para embeddings",
                required = true
            ),
            // Vosk model for Spanish speech recognition
            "model-es-small/am/final.mdl" to ModelInfo(
                filename = "model-es-small/am/final.mdl",
                url = "$BASE_URL/model-es-small/am/final.mdl",
                sizeBytes = 16_098_256L,
                description = "Vosk AM model",
                required = true
            ),
            "model-es-small/conf/mfcc.conf" to ModelInfo(
                filename = "model-es-small/conf/mfcc.conf",
                url = "$BASE_URL/model-es-small/conf/mfcc.conf",
                sizeBytes = 155L,
                description = "Vosk MFCC config",
                required = true
            ),
            "model-es-small/conf/model.conf" to ModelInfo(
                filename = "model-es-small/conf/model.conf",
                url = "$BASE_URL/model-es-small/conf/model.conf",
                sizeBytes = 289L,
                description = "Vosk model config",
                required = true
            ),
            "model-es-small/graph/Gr.fst" to ModelInfo(
                filename = "model-es-small/graph/Gr.fst",
                url = "$BASE_URL/model-es-small/graph/Gr.fst",
                sizeBytes = 21_818_215L,
                description = "Vosk grammar FST",
                required = true
            ),
            "model-es-small/graph/HCLr.fst" to ModelInfo(
                filename = "model-es-small/graph/HCLr.fst",
                url = "$BASE_URL/model-es-small/graph/HCLr.fst",
                sizeBytes = 12_226_498L,
                description = "Vosk HCL FST",
                required = true
            ),
            "model-es-small/graph/disambig_tid.int" to ModelInfo(
                filename = "model-es-small/graph/disambig_tid.int",
                url = "$BASE_URL/model-es-small/graph/disambig_tid.int",
                sizeBytes = 50L,
                description = "Vosk disambig",
                required = true
            ),
            "model-es-small/graph/phones/word_boundary.int" to ModelInfo(
                filename = "model-es-small/graph/phones/word_boundary.int",
                url = "$BASE_URL/model-es-small/graph/phones/word_boundary.int",
                sizeBytes = 1_131L,
                description = "Vosk word boundary",
                required = true
            ),
            "model-es-small/ivector/final.dubm" to ModelInfo(
                filename = "model-es-small/ivector/final.dubm",
                url = "$BASE_URL/model-es-small/ivector/final.dubm",
                sizeBytes = 168_048L,
                description = "Vosk ivector dubm",
                required = true
            ),
            "model-es-small/ivector/final.ie" to ModelInfo(
                filename = "model-es-small/ivector/final.ie",
                url = "$BASE_URL/model-es-small/ivector/final.ie",
                sizeBytes = 9_927_287L,
                description = "Vosk ivector ie",
                required = true
            ),
            "model-es-small/ivector/final.mat" to ModelInfo(
                filename = "model-es-small/ivector/final.mat",
                url = "$BASE_URL/model-es-small/ivector/final.mat",
                sizeBytes = 44_975L,
                description = "Vosk ivector mat",
                required = true
            ),
            "model-es-small/ivector/global_cmvn.stats" to ModelInfo(
                filename = "model-es-small/ivector/global_cmvn.stats",
                url = "$BASE_URL/model-es-small/ivector/global_cmvn.stats",
                sizeBytes = 1_081L,
                description = "Vosk ivector cmvn stats",
                required = true
            ),
            "model-es-small/ivector/online_cmvn.conf" to ModelInfo(
                filename = "model-es-small/ivector/online_cmvn.conf",
                url = "$BASE_URL/model-es-small/ivector/online_cmvn.conf",
                sizeBytes = 95L,
                description = "Vosk ivector cmvn config",
                required = true
            ),
            "model-es-small/ivector/splice.conf" to ModelInfo(
                filename = "model-es-small/ivector/splice.conf",
                url = "$BASE_URL/model-es-small/ivector/splice.conf",
                sizeBytes = 35L,
                description = "Vosk ivector splice config",
                required = true
            )
        )
        
        @Volatile
        private var instance: ModelDownloadService? = null
        
        fun getInstance(): ModelDownloadService {
            return instance ?: synchronized(this) {
                instance ?: ModelDownloadService().also { instance = it }
            }
        }
    }
    
    data class ModelInfo(
        val filename: String,
        val url: String,
        val sizeBytes: Long,
        val description: String,
        val required: Boolean
    ) {
        val sizeMB: Int get() = (sizeBytes / (1024 * 1024)).toInt()
    }
    
    data class DownloadProgress(
        val modelName: String,
        val progress: Int,
        val downloadedMB: Int,
        val totalMB: Int,
        val status: DownloadStatus
    )
    
    enum class DownloadStatus {
        NOT_STARTED, DOWNLOADING, COMPLETED, FAILED, ALREADY_EXISTS
    }
    
    // Callbacks
    var onProgress: ((DownloadProgress) -> Unit)? = null
    var onComplete: ((String, Boolean) -> Unit)? = null
    
    /**
     * Obtiene el directorio de modelos
     */
    fun getModelsDir(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    /**
     * Verifica si un modelo existe localmente y tiene el tamaño correcto
     */
    fun isModelAvailable(context: Context, modelName: String): Boolean {
        val modelInfo = MODELS[modelName] ?: return false
        val modelFile = File(getModelsDir(context), modelName)
        
        // Verificar que existe y tiene tamaño razonable (al menos 90% del esperado)
        val minSize = (modelInfo.sizeBytes * 0.9).toLong()
        return modelFile.exists() && modelFile.length() >= minSize
    }
    
    /**
     * Obtiene la ruta de un modelo
     */
    fun getModelPath(context: Context, modelName: String): String? {
        val modelFile = File(getModelsDir(context), modelName)
        return if (modelFile.exists() && modelFile.length() > 1024) {
            modelFile.absolutePath
        } else {
            null
        }
    }
    
    /**
     * Verifica si todos los modelos requeridos están disponibles
     */
    fun areAllModelsAvailable(context: Context): Boolean {
        return MODELS.values
            .filter { it.required }
            .all { isModelAvailable(context, it.filename) }
    }
    
    /**
     * Obtiene la lista de modelos que faltan por descargar
     */
    fun getMissingModels(context: Context): List<ModelInfo> {
        return MODELS.values
            .filter { it.required && !isModelAvailable(context, it.filename) }
            .toList()
    }
    
    /**
     * Calcula el tamaño total de descarga pendiente
     */
    fun getTotalDownloadSizeMB(context: Context): Int {
        return getMissingModels(context).sumOf { it.sizeMB }
    }
    
    /**
     * Descarga un modelo específico
     */
    suspend fun downloadModel(context: Context, modelName: String): Result<File> = withContext(Dispatchers.IO) {
        val modelInfo = MODELS[modelName]
            ?: return@withContext Result.failure(Exception("Modelo desconocido: $modelName"))
        
        // Verificar si ya existe
        if (isModelAvailable(context, modelName)) {
            AppLogger.log(TAG, "Model exists: $modelName")
            onProgress?.invoke(DownloadProgress(modelName, 100, modelInfo.sizeMB, modelInfo.sizeMB, DownloadStatus.ALREADY_EXISTS))
            return@withContext Result.success(File(getModelsDir(context), modelName))
        }
        
        val modelsDir = getModelsDir(context)
        val modelFile = File(modelsDir, modelName)
        val tempFile = File(modelsDir, "$modelName.tmp")
        
        // Ensure parent directories exist for nested paths (e.g., model-es-small/am/final.mdl)
        modelFile.parentFile?.mkdirs()
        tempFile.parentFile?.mkdirs()
        
        try {
            AppLogger.log(TAG, "Iniciando descarga: $modelName (${modelInfo.sizeMB}MB)")
            onProgress?.invoke(DownloadProgress(modelName, 0, 0, modelInfo.sizeMB, DownloadStatus.DOWNLOADING))
            
            val url = URL(modelInfo.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            
            // Manejar redirecciones manualmente para GitHub
            var redirectUrl = modelInfo.url
            var redirectCount = 0
            while (redirectCount < 5) {
                val tempConn = URL(redirectUrl).openConnection() as HttpURLConnection
                tempConn.instanceFollowRedirects = false
                val responseCode = tempConn.responseCode
                if (responseCode in 300..399) {
                    redirectUrl = tempConn.getHeaderField("Location")
                    tempConn.disconnect()
                    redirectCount++
                } else {
                    break
                }
            }
            
            val finalConnection = URL(redirectUrl).openConnection() as HttpURLConnection
            finalConnection.connectTimeout = 30_000
            finalConnection.readTimeout = 60_000
            
            val responseCode = finalConnection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                AppLogger.log(TAG, "✗ Error HTTP: $responseCode")
                onProgress?.invoke(DownloadProgress(modelName, 0, 0, modelInfo.sizeMB, DownloadStatus.FAILED))
                return@withContext Result.failure(Exception("Error HTTP: $responseCode"))
            }
            
            val contentLength = finalConnection.contentLengthLong.takeIf { it > 0 } ?: modelInfo.sizeBytes
            
            // Descargar a archivo temporal
            tempFile.parentFile?.mkdirs()
            FileOutputStream(tempFile).use { output ->
                finalConnection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        
                        val progress = ((downloaded * 100) / contentLength).toInt()
                        val downloadedMB = (downloaded / (1024 * 1024)).toInt()
                        val totalMB = (contentLength / (1024 * 1024)).toInt()
                        
                        onProgress?.invoke(DownloadProgress(modelName, progress, downloadedMB, totalMB, DownloadStatus.DOWNLOADING))
                    }
                }
            }
            finalConnection.disconnect()
            
            // Verificar tamaño del archivo descargado
            val downloadedSize = tempFile.length()
            val minExpectedSize = (modelInfo.sizeBytes * 0.9).toLong()
            
            if (downloadedSize < minExpectedSize) {
                tempFile.delete()
                AppLogger.log(TAG, "✗ Archivo descargado muy pequeño: $downloadedSize bytes")
                onProgress?.invoke(DownloadProgress(modelName, 0, 0, modelInfo.sizeMB, DownloadStatus.FAILED))
                return@withContext Result.failure(Exception("Descarga incompleta"))
            }
            
            // Mover archivo temporal a destino final
            modelFile.delete()
            if (!tempFile.renameTo(modelFile)) {
                tempFile.copyTo(modelFile, overwrite = true)
                tempFile.delete()
            }
            
            AppLogger.log(TAG, "Model downloaded: $modelName (${modelFile.length() / (1024*1024)}MB)")
            onProgress?.invoke(DownloadProgress(modelName, 100, modelInfo.sizeMB, modelInfo.sizeMB, DownloadStatus.COMPLETED))
            onComplete?.invoke(modelName, true)
            
            Result.success(modelFile)
            
        } catch (e: Exception) {
            AppLogger.log(TAG, "✗ Error descargando $modelName: ${e.message}")
            tempFile.delete()
            onProgress?.invoke(DownloadProgress(modelName, 0, 0, modelInfo.sizeMB, DownloadStatus.FAILED))
            onComplete?.invoke(modelName, false)
            Result.failure(e)
        }
    }
    
    /**
     * Descarga todos los modelos faltantes
     */
    suspend fun downloadAllMissingModels(context: Context): Boolean {
        val missing = getMissingModels(context)
        if (missing.isEmpty()) {
            AppLogger.log(TAG, "All models available")
            return true
        }
        
        AppLogger.log(TAG, "Descargando ${missing.size} modelos...")
        
        var allSuccess = true
        for (model in missing) {
            val result = downloadModel(context, model.filename)
            if (result.isFailure) {
                allSuccess = false
            }
        }
        
        return allSuccess
    }
    
    /**
     * Elimina todos los modelos descargados (para liberar espacio)
     */
    fun clearDownloadedModels(context: Context) {
        val modelsDir = getModelsDir(context)
        modelsDir.listFiles()?.forEach { it.delete() }
        AppLogger.log(TAG, "Modelos eliminados")
    }
}
