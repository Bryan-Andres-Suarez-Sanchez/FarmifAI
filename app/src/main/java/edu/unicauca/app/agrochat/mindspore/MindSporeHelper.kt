package edu.unicauca.app.agrochat

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Objeto que sirve como puente JNI a las funciones nativas de MindSpore
 * y proporciona funciones de utilidad para cargar modelos e inferencia.
 * Las implementaciones de las funciones 'external' están en MindSporeNetnative.cpp,
 * y se cargan desde la biblioteca nativa "msjni".
 */
object MindSporeHelper {

    private const val TAG = "MindSporeHelper"

    init {
        try {
            System.loadLibrary("msjni")
            Log.i(TAG, "Biblioteca nativa 'msjni' cargada exitosamente.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Error CRÍTICO: No se pudo cargar la biblioteca nativa 'msjni'", e)
        }
    }

    // --- Funciones Nativas (Puente JNI) ---
    private external fun loadModel(modelBuffer: ByteBuffer, numThread: Int): Long
    external fun runNetFloat(envPtr: Long, input: FloatArray): FloatArray?
    external fun runNetIds(envPtr: Long, ids: IntArray): FloatArray? // La que usaremos para SLM
    external fun runNetSentenceEncoder(envPtr: Long, inputIds: IntArray, attentionMask: IntArray): FloatArray? // Para Sentence Encoder
    external fun unloadModel(envPtr: Long): Boolean


    // --- Funciones de Utilidad (Lógica en Kotlin) ---

    private fun copyAssetFileToInternalStorage(
        context: Context,
        assetName: String,
        outputFileNameInInternalStorage: String,
        overwrite: Boolean = false
    ): String? {
        val outputFile = File(context.filesDir, outputFileNameInInternalStorage)
        // ... (lógica de copia como estaba)
        if (outputFile.exists() && !overwrite) {
            Log.i(TAG, "El archivo '${outputFile.name}' ya existe. Usando existente: ${outputFile.absolutePath}")
            return outputFile.absolutePath
        }
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            Log.d(TAG, "Copiando '$assetName' desde assets a '${outputFile.absolutePath}'...")
            inputStream = context.assets.open(assetName)
            outputStream = FileOutputStream(outputFile)
            val buffer = ByteArray(1024 * 8)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            outputStream.flush()
            Log.i(TAG, "Archivo '$assetName' copiado exitosamente a '${outputFile.absolutePath}'")
            return outputFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Error al copiar '$assetName' a '${outputFile.name}': ${e.message}", e)
            if (outputFile.exists()) { outputFile.delete() }
            return null
        } finally {
            try { inputStream?.close() } catch (e: IOException) { Log.w(TAG, "Error cerrando inputStream: ${e.message}") }
            try { outputStream?.close() } catch (e: IOException) { Log.w(TAG, "Error cerrando outputStream: ${e.message}") }
        }
    }

    fun loadModelFromAssets(
        context: Context,
        assetModelPath: String,
        numThreads: Int = 2
    ): Long {
        Log.i(TAG, "Solicitud para cargar modelo desde assets: '$assetModelPath' (copia y mapeo)")
        val internalModelFileName = File(assetModelPath).name
        // ... (lógica de copia y mapeo como estaba) ...
        val modelPathInInternalStorage = copyAssetFileToInternalStorage(
            context,
            assetModelPath,
            internalModelFileName,
            overwrite = false
        )

        if (modelPathInInternalStorage == null) {
            Log.e(TAG, "Fallo al copiar/ubicar el modelo '$assetModelPath' en almacenamiento interno.")
            return 0L
        }

        var fileChannel: FileChannel? = null
        var raf: RandomAccessFile? = null // Necesitamos declarar raf aquí para cerrarlo en finally
        try {
            Log.d(TAG, "Intentando mapear en memoria el archivo: $modelPathInInternalStorage")
            raf = RandomAccessFile(File(modelPathInInternalStorage), "r")
            fileChannel = raf.channel

            val mappedByteBuffer: MappedByteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                0,
                fileChannel.size()
            )

            if (mappedByteBuffer == null) {
                Log.e(TAG, "Falló el mapeo en memoria del archivo: $modelPathInInternalStorage (MappedByteBuffer es null)")
                return 0L
            }

            Log.i(TAG, "Archivo mapeado exitosamente. Tamaño: ${fileChannel.size()}. Llamando a JNI loadModel...")
            val modelHandle = loadModel(mappedByteBuffer, numThreads)

            if (modelHandle == 0L) {
                Log.e(TAG, "JNI loadModel falló al cargar el modelo desde MappedByteBuffer.")
            } else {
                Log.i(TAG, "JNI loadModel exitoso con MappedByteBuffer. Handle: $modelHandle")
            }
            return modelHandle

        } catch (e: IOException) {
            Log.e(TAG, "IOException durante el mapeo o carga del modelo desde '$modelPathInInternalStorage': ${e.message}", e)
            return 0L
        } catch (e: Exception) {
            Log.e(TAG, "Excepción inesperada durante el mapeo o carga desde '$modelPathInInternalStorage': ${e.message}", e)
            return 0L
        } finally {
            try {
                fileChannel?.close()
                raf?.close() // Asegurarse de cerrar RandomAccessFile también
            } catch (e: IOException) {
                Log.w(TAG, "Error cerrando FileChannel o RandomAccessFile: ${e.message}")
            }
        }
    }

    // --- NUEVA FUNCIÓN DE INFERENCIA PARA SLM ---
    /**
     * Ejecuta la inferencia en el modelo SLM cargado usando IDs de token como entrada.
     *
     * @param modelHandle El handle nativo del modelo cargado (obtenido de `loadModelFromAssets`).
     * @param tokenIds Un `IntArray` que representa la secuencia de IDs de token de entrada.
     * @return Un `FloatArray` que contiene los logits de salida del modelo, o `null` si la
     *         inferencia falla o el handle del modelo no es válido.
     */
    fun predictWithTokenIds(modelHandle: Long, tokenIds: IntArray): FloatArray? {
        if (modelHandle == 0L) {
            Log.e(TAG, "predictWithTokenIds: Handle del modelo no válido (0L). No se puede ejecutar la inferencia.")
            return null
        }
        if (tokenIds.isEmpty()) {
            Log.w(TAG, "predictWithTokenIds: El array de tokenIds de entrada está vacío.")
            // Dependiendo del modelo, esto podría ser un error o podría devolver algo.
            // Por seguridad, retornamos null o podríamos lanzar una excepción.
            return null
        }

        Log.d(TAG, "predictWithTokenIds: Ejecutando inferencia con ${tokenIds.size} IDs de token. Handle: $modelHandle")

        try {
            // Llamar directamente a la función JNI runNetIds
            val outputLogits = runNetIds(modelHandle, tokenIds)

            if (outputLogits == null) {
                Log.e(TAG, "predictWithTokenIds: runNetIds (JNI) devolvió null. Falló la inferencia.")
            } else {
                Log.i(TAG, "predictWithTokenIds: Inferencia exitosa. Tamaño de salida de logits: ${outputLogits.size}")
            }
            return outputLogits
        } catch (e: Exception) {
            // Capturar cualquier excepción inesperada de la llamada JNI
            Log.e(TAG, "predictWithTokenIds: Excepción durante la llamada a runNetIds (JNI): ${e.message}", e)
            return null
        }
    }

    /**
     * Ejecuta la inferencia en un modelo Sentence Encoder que requiere input_ids y attention_mask.
     *
     * @param modelHandle El handle nativo del modelo cargado.
     * @param inputIds Un `IntArray` con los IDs de token.
     * @param attentionMask Un `IntArray` con la máscara de atención (1s para tokens reales, 0s para padding).
     * @return Un `FloatArray` con el embedding de salida, o `null` si falla.
     */
    fun predictSentenceEncoder(modelHandle: Long, inputIds: IntArray, attentionMask: IntArray): FloatArray? {
        if (modelHandle == 0L) {
            Log.e(TAG, "predictSentenceEncoder: Handle del modelo no válido (0L).")
            return null
        }
        if (inputIds.isEmpty()) {
            Log.w(TAG, "predictSentenceEncoder: El array de inputIds está vacío.")
            return null
        }
        if (inputIds.size != attentionMask.size) {
            Log.e(TAG, "predictSentenceEncoder: inputIds y attentionMask deben tener el mismo tamaño.")
            return null
        }

        Log.d(TAG, "predictSentenceEncoder: Ejecutando con ${inputIds.size} tokens. Handle: $modelHandle")

        try {
            val output = runNetSentenceEncoder(modelHandle, inputIds, attentionMask)

            if (output == null) {
                Log.e(TAG, "predictSentenceEncoder: runNetSentenceEncoder (JNI) devolvió null.")
            } else {
                Log.i(TAG, "predictSentenceEncoder: Éxito. Tamaño de salida: ${output.size}")
            }
            return output
        } catch (e: Exception) {
            Log.e(TAG, "predictSentenceEncoder: Excepción durante JNI: ${e.message}", e)
            return null
        }
    }
}


    