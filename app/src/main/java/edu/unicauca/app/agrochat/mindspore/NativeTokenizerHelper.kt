package edu.unicauca.app.agrochat // O el paquete donde quieras que esté

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class UniversalNativeTokenizer(
    private val context: Context,
    private val tokenizerAssetFileName: String = "tokenizer.json" // Nombre del archivo .json en assets
) {
        companion object {
            private const val TAG = "UniversalNativeTokenizer"
            private const val INVALID_NATIVE_HANDLE = 0L
            private const val NATIVE_OPERATION_ERROR_INT = -1

            init {
                Log.d(TAG, "Bloque init del companion object: ANTES de System.loadLibrary.") // <--- AÑADE ESTO
                try {
                    System.loadLibrary("hf_tokenizer_android")
                    Log.i(TAG, "Librería JNI 'hf_tokenizer_android' cargada globalmente.")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Error CRÍTICO al cargar la librería JNI 'hf_tokenizer_android'. Mensaje de la excepción: ${e.message}", e) // <--- MENSAJE IMPORTANTE
                    Log.e(TAG, "Causa de la excepción: ${e.cause}") // <--- AÑADE ESTO TAMBIÉN
                    // Considera lanzar una excepción aquí...
                }
                Log.d(TAG, "Bloque init del companion object: DESPUÉS de System.loadLibrary.") // <--- AÑADE ESTO
            }
        }


        private var tokenizerHandle: Long = INVALID_NATIVE_HANDLE
    private var isInitializedSuccessfully: Boolean = false

    // Propiedades para los IDs de tokens especiales
    // Se inicializarán en el bloque init
    val padTokenId: Int
    val eosTokenId: Int
    val unkTokenId: Int
    val vocabSize: Int?

    // --- MÉTODOS NATIVOS EXTERNOS (JNI) ---
    // Java_edu_unicauca_app_agrochat_UniversalNativeTokenizer_nativeDecode
    private external fun nativeLoadTokenizerFromFile(tokenizerJsonPath: String): Long
    private external fun nativeEncode(tokenizerHandle: Long, textToEncode: String /*, addSpecialTokens: Boolean // Eliminado si Rust no lo usa*/): IntArray? // CAMBIO: addSpecialTokens eliminado si no se usa
    private external fun nativeDecode(tokenizerHandle: Long, tokenIdsArray: IntArray, skipSpecialTokens: Boolean): String?
    private external fun nativeFreeTokenizer(tokenizerHandle: Long)
    private external fun nativeGetPadTokenId(tokenizerHandle: Long): Int
    private external fun nativeGetEosTokenId(tokenizerHandle: Long): Int
    private external fun nativeGetUnkTokenId(tokenizerHandle: Long): Int
    private external fun nativeGetVocabSize(tokenizerHandle: Long): Int
    internal fun callNativeLoadTokenizerFromFileForTest(tokenizerJsonPath: String): Long {
        Log.d(TAG, "callNativeLoadTokenizerFromFileForTest: Delegando a private external fun")
        return nativeLoadTokenizerFromFile(tokenizerJsonPath)
    }

    internal fun callNativeGetPadTokenIdForTest(tokenizerHandle: Long): Int {
        Log.d(TAG, "callNativeGetPadTokenIdForTest: Delegando a private external fun con handle: $tokenizerHandle")
        return nativeGetPadTokenId(tokenizerHandle)
    }

    init {
        Log.i(TAG, "Inicializando UniversalNativeTokenizer para '$tokenizerAssetFileName'")
        var tempHandle = INVALID_NATIVE_HANDLE
        var tempPadId = NATIVE_OPERATION_ERROR_INT
        var tempEosId = NATIVE_OPERATION_ERROR_INT
        var tempUnkId = NATIVE_OPERATION_ERROR_INT
        var tempVocabSize = NATIVE_OPERATION_ERROR_INT

        val pathInCache = getPathToTokenizerJsonFromAssets(context, tokenizerAssetFileName)

        if (pathInCache == null) {
            Log.e(TAG, "No se pudo obtener la ruta al $tokenizerAssetFileName desde assets. Tokenizador no se cargará.")
        } else {
            Log.i(TAG, "Intentando cargar tokenizador nativo desde: $pathInCache")
            try {
                tempHandle = nativeLoadTokenizerFromFile(pathInCache)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "UnsatisfiedLinkError al llamar a nativeLoadTokenizerFromFile. ¿Librería cargada y función JNI definida?", e)
                // tempHandle permanece INVALID_NATIVE_HANDLE
            }

            if (tempHandle != INVALID_NATIVE_HANDLE) {
                tokenizerHandle = tempHandle
                // Inicialmente asumimos éxito si el handle se carga.
                // Se podría refinar más adelante si la obtención de IDs es crítica.
                isInitializedSuccessfully = true
                Log.i(TAG, "Tokenizador nativo cargado preliminarmente. Handle: $tokenizerHandle")

                // Intentar obtener los IDs de tokens especiales y vocab_size
                try {
                    tempPadId = nativeGetPadTokenId(tokenizerHandle)
                    tempEosId = nativeGetEosTokenId(tokenizerHandle)
                    tempUnkId = nativeGetUnkTokenId(tokenizerHandle)
                    tempVocabSize = nativeGetVocabSize(tokenizerHandle)

                    Log.i(TAG, "Valores brutos de JNI -> PAD: $tempPadId, EOS: $tempEosId, UNK: $tempUnkId, VocabSize: $tempVocabSize")

                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "Una o más funciones JNI para obtener IDs/VocabSize no están implementadas o enlazadas. Usando fallbacks para todos.", e)
                    // Si alguna falla, es mejor usar fallbacks para todos por consistencia o marcar error general.
                    // Aquí, ya que tempPadId etc. se inicializaron a NATIVE_OPERATION_ERROR_INT, los chequeos posteriores los manejarán.
                }
            } else {
                Log.e(TAG, "Fallo al cargar el tokenizador nativo (handle es 0).")
                // isInitializedSuccessfully permanece false
            }
        }

        // Asignar IDs, usando estimaciones si JNI falló o devolvió error
        padTokenId = if (tempPadId == NATIVE_OPERATION_ERROR_INT) estimatePadId() else tempPadId
        eosTokenId = if (tempEosId == NATIVE_OPERATION_ERROR_INT) estimateEosId() else tempEosId
        unkTokenId = if (tempUnkId == NATIVE_OPERATION_ERROR_INT) estimateUnkId() else tempUnkId
        vocabSize = if (tempVocabSize == NATIVE_OPERATION_ERROR_INT) null else tempVocabSize // CAMBIO: Manejo de -1 para vocabSize

        // CAMBIO: Considerar si la obtención de ciertos IDs es crítica para isInitializedSuccessfully
        if (tokenizerHandle == INVALID_NATIVE_HANDLE) { // Si el handle nunca se cargó, definitivamente no está inicializado.
            isInitializedSuccessfully = false
        }
        // Opcional: si un ID específico (ej. UNK) es esencial y no se pudo obtener (ni estimar a un valor válido)
        // if (unkTokenId == ALGUN_VALOR_INVALIDO_DESPUES_DE_ESTIMACION) isInitializedSuccessfully = false

        if (isInitializedSuccessfully) {
            Log.i(TAG, "UniversalNativeTokenizer inicializado. PAD: $padTokenId, EOS: $eosTokenId, UNK: $unkTokenId, VocabSize: ${vocabSize ?: "No disponible"}")
        } else {
            Log.e(TAG, "¡UniversalNativeTokenizer NO se inicializó correctamente! PAD: $padTokenId (puede ser estimado), EOS: $eosTokenId (puede ser estimado), UNK: $unkTokenId (puede ser estimado), VocabSize: ${vocabSize ?: "No disponible"}")
        }
    }

    private fun getPathToTokenizerJsonFromAssets(context: Context, assetFileName: String): String? {
        // Primero intentar cargar desde el directorio de modelos descargados
        val modelsDir = File(context.filesDir, "models")
        val downloadedFile = File(modelsDir, assetFileName)
        if (downloadedFile.exists() && downloadedFile.length() > 1024) {
            Log.i(TAG, "$assetFileName encontrado en modelos descargados: ${downloadedFile.absolutePath}")
            return downloadedFile.absolutePath
        }
        
        // Fallback: copiar desde assets si existe ahí
        val outFile = File(context.cacheDir, assetFileName)
        try {
            context.assets.open(assetFileName).use { inputStream ->
                FileOutputStream(outFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.i(TAG, "$assetFileName copiado de assets a: ${outFile.absolutePath}")
            return outFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Error al copiar $assetFileName desde assets: ${e.message}", e)
            Log.e(TAG, "El tokenizador no está disponible ni en modelos descargados ni en assets")
            return null
        }
    }

    // Funciones de estimación si JNI no provee los IDs o devuelve error
    private fun estimatePadId(): Int { Log.w(TAG, "Estimando PAD ID."); return 0 } // Valor común, o el que prefieras
    private fun estimateEosId(): Int { Log.w(TAG, "Estimando EOS ID."); return 2 } // Valor común, o el que prefieras
    private fun estimateUnkId(): Int { Log.w(TAG, "Estimando UNK ID."); return 1 } // Valor común, o el que prefieras


    fun isReady(): Boolean = isInitializedSuccessfully && tokenizerHandle != INVALID_NATIVE_HANDLE

    /**
     * Codifica el texto a IDs de token.
     * @param text El texto a codificar.
     * @param addSpecialTokens (Opcional, y actualmente ignorado por la implementación JNI de ejemplo)
     *                         Si la implementación JNI lo soporta, para añadir tokens especiales.
     * @return Un IntArray con los IDs, o un array con UNK ID si falla o no está listo.
     */
    fun encode(text: String, addSpecialTokens: Boolean = true): IntArray {
        if (!isReady()) {
            Log.e(TAG, "Tokenizador no listo. No se puede codificar '$text'. Devolviendo UNK.")
            return intArrayOf(unkTokenId) // Asegúrate que unkTokenId tenga un valor sensato aquí
        }

        // CAMBIO: Si el parámetro addSpecialTokens no tiene efecto en JNI, puedes:
        // 1. Eliminarlo de la firma y de la llamada a nativeEncode.
        // 2. Mantenerlo y seguir advirtiendo (como está ahora).
        // 3. Modificar JNI para que lo use.
        if (addSpecialTokens != true && tokenizerAssetFileName.contains("bert")) { // Ejemplo de advertencia más específica
            Log.w(TAG,"El flag 'addSpecialTokens=${addSpecialTokens}' se está estableciendo, pero la implementación JNI actual de encode podría no respetarlo y siempre añadir tokens especiales si el modelo los usa (ej. BERT).")
        }


        val tokenIds = try {
            // nativeEncode(tokenizerHandle, text, addSpecialTokens) // Si JNI lo usara
            nativeEncode(tokenizerHandle, text)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "UnsatisfiedLinkError al llamar a nativeEncode.", e)
            null
        }

        return tokenIds ?: run {
            Log.e(TAG, "La codificación nativa falló para el texto: '$text'. Devolviendo UNK.")
            intArrayOf(unkTokenId)
        }
    }

    /**
     * Decodifica los IDs de token a texto.
     * @param tokenIds El IntArray de IDs a decodificar.
     * @param skipSpecialTokens (Opcional) Si la implementación JNI lo soporta, para omitir tokens especiales.
     * @return Un String con el texto decodificado, o un mensaje de error si falla o no está listo.
     */
    fun decode(tokenIds: IntArray, skipSpecialTokens: Boolean = true): String {
        if (!isReady()) {
            Log.e(TAG, "Tokenizador no listo. No se puede decodificar ${tokenIds.joinToString()}.")
            return "[Error: Tokenizador no inicializado]"
        }

        // El parámetro skipSpecialTokens SÍ se usa en tu JNI
        // No es necesario un Log.w aquí a menos que quieras indicar su valor por defecto.

        val decodedText = try {
            nativeDecode(tokenizerHandle, tokenIds, skipSpecialTokens)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "UnsatisfiedLinkError al llamar a nativeDecode.", e)
            null
        }

        return decodedText ?: run {
            Log.e(TAG, "La decodificación nativa falló para los IDs: ${tokenIds.joinToString()}")
            "[Error de decodificación nativa]"
        }
    }

    /**
     * Libera los recursos del tokenizador nativo.
     * Es MUY IMPORTANTE llamar a esto cuando el tokenizador ya no se necesite.
     */
    fun release() {
        if (tokenizerHandle != INVALID_NATIVE_HANDLE) {
            Log.i(TAG, "Liberando tokenizador nativo. Handle: $tokenizerHandle")
            try {
                nativeFreeTokenizer(tokenizerHandle)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "UnsatisfiedLinkError al llamar a nativeFreeTokenizer.", e)
                // Aunque falle el llamado JNI, igual marcamos como liberado en Kotlin.
            } finally {
                tokenizerHandle = INVALID_NATIVE_HANDLE
                isInitializedSuccessfully = false // Ya no está listo
                Log.i(TAG, "Tokenizador marcado como liberado en Kotlin.")
            }
        } else {
            Log.w(TAG, "Se intentó liberar un tokenizador que no estaba cargado o ya fue liberado.")
        }
    }

    // Opcional: Sobrescribir finalize para intentar liberar si el programador olvida llamar a release().
    // No es una garantía, pero es una buena práctica como último recurso.
    protected fun finalize() {
        try {
            if (tokenizerHandle != INVALID_NATIVE_HANDLE) {
                Log.w(TAG, "Tokenizador no liberado explícitamente. Intentando liberar en finalize(). Handle: $tokenizerHandle")
                release()
            }
        } finally {
            // super.finalize() // En Java. En Kotlin no es necesario llamar a super.finalize() directamente si heredas de Any.
        }
    }
}




