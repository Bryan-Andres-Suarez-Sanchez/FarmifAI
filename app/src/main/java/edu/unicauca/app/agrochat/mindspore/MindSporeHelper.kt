package edu.unicauca.app.agrochat.mindspore

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.mindspore.Model // Asegúrate de que esta sea la importación correcta
import com.mindspore.config.CpuBindMode // Asegúrate de que esta sea la importación correcta
import com.mindspore.config.DeviceType // Asegúrate de que esta sea la importación correcta
import com.mindspore.config.MSContext // Asegúrate de que esta sea la importación correcta
import com.mindspore.config.Version // Asegúrate de que esta sea la importación correcta
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MindSporeHelper(private val androidContext: Context) {

    companion object {
        private const val TAG = "MindSporeHelper"

        // Bloque de inicialización estática para cargar librerías
        init {
            try {
                System.loadLibrary("mindspore-lite-jni") // Asegúrate que el nombre es exacto
                // Si hay otras librerías core que sabes que se necesitan, cárgalas aquí también.
                // System.loadLibrary("otra-libreria-mindspore")
                Log.i(TAG, "Librerías nativas MindSpore cargadas explícitamente (intento).")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Error al cargar librerías nativas MindSpore explícitamente.", e)
            }
        }
    }

    private var msModel: Model? = null
    private var msContext: MSContext? = null // Lo haremos no nulo después de la inicialización exitosa
    private val modelNameInAssets = "gpt2_fp16_model.ms" // Asegúrate de que este es el nombre correcto
    private var modelFileOnDevice: File? = null

    @Throws(IOException::class)
    private fun copyAssetToCache(assetFileName: String): File {
        val assetManager: AssetManager = androidContext.assets
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        val outputFile: File

        try {
            inputStream = assetManager.open(assetFileName)
            val cacheDir = androidContext.cacheDir
            outputFile = File(cacheDir, assetFileName)

            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "El modelo '$assetFileName' ya existe en caché: ${outputFile.absolutePath}")
                return outputFile
            }

            outputStream = FileOutputStream(outputFile)
            val buffer = ByteArray(4 * 1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            outputStream.flush()
            Log.d(TAG, "Modelo '$assetFileName' copiado a caché: ${outputFile.absolutePath}")
            return outputFile
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    private fun loadModelFileToByteBuffer(modelFile: File): MappedByteBuffer? {
        var randomAccessFile: RandomAccessFile? = null
        var fileChannel: FileChannel? = null
        try {
            randomAccessFile = RandomAccessFile(modelFile, "r")
            fileChannel = randomAccessFile.channel
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
            mappedByteBuffer?.load()
            Log.d(TAG, "Modelo '${modelFile.name}' cargado en MappedByteBuffer desde: ${modelFile.absolutePath}")
            return mappedByteBuffer
        } catch (e: IOException) {
            Log.e(TAG, "IOException al cargar modelo '${modelFile.name}' en MappedByteBuffer", e)
            return null
        } finally {
            fileChannel?.close() // Se cerrará randomAccessFile también si el canal se obtuvo de él
            randomAccessFile?.close() // Cerrar explícitamente es más seguro
        }
    }

    /**
     * Paso 2 (según la documentación): Crear y configurar MSContext para CPU.
     */
    private fun createAndConfigureMSContextForCPU(): MSContext? {
        val tempContext = MSContext()
        try {
            // Configurar el número de hilos y el modo de afinidad de CPU
            // Usaremos 2 hilos y afinidad HIGHER_CPU como en el ejemplo, ajusta si es necesario
            tempContext.init(2, CpuBindMode.HIGHER_CPU) // Asegúrate que CpuBindMode.HIGHER_CPU existe

            // Añadir información del dispositivo CPU y habilitar float16 si es soportado
            // El segundo parámetro de addDeviceInfo es isEnableFloat16
            val addDeviceSuccess = tempContext.addDeviceInfo(DeviceType.DT_CPU, true)
            if (!addDeviceSuccess) {
                Log.e(TAG, "Fallo al añadir dispositivo CPU al MSContext.")
                tempContext.free() // Liberar si falla
                return null
            }
            Log.d(TAG, "MSContext creado y configurado para CPU (Float16 habilitado si es soportado).")
            return tempContext
        } catch (e: Exception) {
            // Algunas versiones de MindSpore podrían lanzar excepciones aquí si los parámetros son inválidos
            // o si hay problemas con las librerías nativas.
            Log.e(TAG, "Excepción al inicializar o configurar MSContext: ${e.message}", e)
            tempContext.free() // Liberar si falla
            return null
        }
    }


    suspend fun initialize(): Boolean {
        if (msModel != null && msContext != null) {
            Log.i(TAG, "MindSporeHelper ya inicializado.")
            return true
        }

        // Liberar recursos previos si los hubiera (aunque msModel y msContext deberían ser null aquí)
        msContext?.free()
        msContext = null
        msModel?.free()
        msModel = null

        return withContext(Dispatchers.IO) {
            // Paso 1a: Copiar el modelo
            val copiedModelFile = try {
                copyAssetToCache(modelNameInAssets)
            } catch (e: IOException) {
                Log.e(TAG, "Fallo al copiar el modelo desde assets.", e)
                return@withContext false
            }
            modelFileOnDevice = copiedModelFile

            // Paso 1b: Cargar el modelo en MappedByteBuffer
            val modelByteBuffer = loadModelFileToByteBuffer(copiedModelFile)
            if (modelByteBuffer == null) {
                Log.e(TAG, "Fallo al cargar el modelo en MappedByteBuffer.")
                return@withContext false
            }

            // Paso 2: Crear y configurar MSContext
            val tempContext = createAndConfigureMSContextForCPU()
            if (tempContext == null) {
                // El log de error ya se hizo dentro de createAndConfigureMSContextForCPU
                Log.e(TAG, "Fallo al crear MSContext. No se puede continuar con la inicialización del modelo.")
                return@withContext false
            }
            // Asignar al miembro de la clase SOLO DESPUÉS de una configuración exitosa
            // y antes de usarlo en model.build
            // PERO lo asignaremos a this.msContext solo si model.build es exitoso.

            // Paso 3: Cargar y compilar (construir) el modelo
            val model = Model() // Crear una nueva instancia de Model
// val modelType = ModelType.MINDIR_LITE // Ya no es necesario si se ignora

            val modelTypeInt = 0 // ¡PRUEBA ESTO CON PRECAUCIÓN Y VERIFICA!

            Log.d(TAG, "Intentando construir el modelo con modelType (Int): $modelTypeInt")
            val buildSuccess = try {
                model.build(modelByteBuffer, modelTypeInt, tempContext) // Usa modelTypeInt
            } catch (e: Exception) {
                Log.e(TAG, "Excepción durante model.build: ${e.message}", e)
                false
            }

            if (buildSuccess) {
                msModel = model
                msContext = tempContext
                Log.i(TAG, "Modelo construido (compilado) exitosamente. MindSporeHelper listo.")
                true
            } else {
                Log.e(TAG, "Fallo al construir (compilar) el modelo.")
                tempContext.free()
                return@withContext false
            }


            if (buildSuccess) {
                msModel = model // Asignar el modelo construido al miembro de la clase
                msContext = tempContext // Asignar el contexto al miembro de la clase
                Log.i(TAG, "Modelo construido (compilado) exitosamente. MindSporeHelper listo.")
                true // Inicialización exitosa
            } else {
                Log.e(TAG, "Fallo al construir (compilar) el modelo.")
                tempContext.free() // Liberar el contexto si la compilación del modelo falla
                // msModel ya es null o no se asignó
                return@withContext false
            }
        }
    }

    fun release() {
        Log.d(TAG, "Liberando recursos de MindSporeHelper.")
        msModel?.free() // El método free libera los recursos nativos del modelo
        msModel = null
        msContext?.free() // El método free libera los recursos nativos del contexto
        msContext = null
        Log.d(TAG, "Recursos de MindSporeHelper liberados.")
    }

    // --- Aquí irán los métodos para la inferencia ---
    // fun predict(...)
}




