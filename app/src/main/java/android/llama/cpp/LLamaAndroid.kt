package android.llama.cpp

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * LLamaAndroid - Wrapper JNI para llama.cpp
 * Basado en el ejemplo oficial de llama.cpp para Android
 * 
 * NOTA: Se recrean batch y sampler entre consultas para evitar
 * corrupción de memoria que causa crashes en consultas consecutivas.
 */
class LLamaAndroid private constructor() {
    
    companion object {
        private const val TAG = "LLamaAndroid"
        // El logging nativo token-a-token impacta mucho el rendimiento en Android.
        private const val ENABLE_NATIVE_LOG_TO_ANDROID = false
        
        // Tamaño del batch - debe ser suficiente para prompt + contexto RAG
        private const val BATCH_SIZE = 1024
        
        private class IntVar(value: Int) {
            @Volatile
            var value: Int = value
                private set

            fun inc() {
                synchronized(this) {
                    value += 1
                }
            }
        }

        private sealed interface State {
            data object Idle : State
            data class Loaded(
                val model: Long, 
                val context: Long
            ) : State
        }

        // Singleton
        private val _instance: LLamaAndroid = LLamaAndroid()
        fun instance(): LLamaAndroid = _instance
    }
    
    // Estado actual - solo modelo y contexto (batch/sampler se crean por consulta)
    @Volatile
    private var currentState: State = State.Idle
    
    private val runLoop: CoroutineDispatcher = Executors.newSingleThreadExecutor {
        thread(start = false, name = "Llama-RunLoop") {
            Log.d(TAG, "Hilo dedicado para código nativo: ${Thread.currentThread().name}")
            
            try {
                // Cargar librerías nativas
                System.loadLibrary("llama-android")
                
                // Configurar logging para Android
                if (ENABLE_NATIVE_LOG_TO_ANDROID) {
                    log_to_android()
                }
                backend_init()
                
                Log.d(TAG, "Sistema: ${system_info()}")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Error cargando librerías nativas: ${e.message}")
            }
            
            it.run()
        }.apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, exception ->
                Log.e(TAG, "Excepción no manejada", exception)
            }
        }
    }.asCoroutineDispatcher()
    
    // Número máximo de tokens a generar
    private val nlen: Int = 256
    
    // === Métodos nativos JNI ===
    private external fun log_to_android()
    private external fun load_model(filename: String): Long
    private external fun free_model(model: Long)
    private external fun new_context(model: Long): Long
    private external fun free_context(context: Long)
    private external fun backend_init()
    private external fun backend_free()
    private external fun new_batch(nTokens: Int, embd: Int, nSeqMax: Int): Long
    private external fun free_batch(batch: Long)
    private external fun new_sampler(): Long
    private external fun free_sampler(sampler: Long)
    private external fun system_info(): String
    
    private external fun completion_init(
        context: Long,
        batch: Long,
        text: String,
        formatChat: Boolean,
        nLen: Int
    ): Int
    
    private external fun completion_loop(
        context: Long,
        batch: Long,
        sampler: Long,
        nLen: Int,
        ncur: IntVar
    ): String?
    
    private external fun kv_cache_clear(context: Long)
    
    /**
     * Verifica si el modelo está cargado
     */
    fun isLoaded(): Boolean = currentState is State.Loaded
    
    /**
     * Obtiene información del sistema
     */
    suspend fun getSystemInfo(): String = withContext(runLoop) {
        try { system_info() } catch (e: Exception) { "Error: ${e.message}" }
    }
    
    /**
     * Carga el modelo GGUF desde la ruta especificada
     */
    suspend fun load(pathToModel: String) {
        withContext(runLoop) {
            when (currentState) {
                is State.Idle -> {
                    Log.i(TAG, "Cargando modelo desde: $pathToModel")
                    
                    val model = load_model(pathToModel)
                    if (model == 0L) throw IllegalStateException("load_model() falló")

                    val context = new_context(model)
                    if (context == 0L) {
                        free_model(model)
                        throw IllegalStateException("new_context() falló")
                    }

                    Log.i(TAG, "Modelo cargado exitosamente")
                    currentState = State.Loaded(model, context)
                }
                else -> {
                    Log.w(TAG, "Modelo ya está cargado, ignorando carga")
                }
            }
        }
    }
    
    /**
     * Envía un mensaje y devuelve un Flow de tokens generados
     * @param maxTokens Tokens ADICIONALES a generar (no el total)
     */
    fun send(message: String, formatChat: Boolean = true, maxTokens: Int = nlen): Flow<String> = flow {
        when (val state = currentState) {
            is State.Loaded -> {
                // Limpiar KV cache antes de la consulta
                kv_cache_clear(state.context)
                
                // Crear batch y sampler frescos para cada consulta
                val batch = new_batch(BATCH_SIZE, 0, 1)
                if (batch == 0L) throw IllegalStateException("new_batch() falló")
                
                val sampler = new_sampler()
                if (sampler == 0L) {
                    free_batch(batch)
                    throw IllegalStateException("new_sampler() falló")
                }
                
                try {
                    val promptTokens = completion_init(state.context, batch, message, formatChat, maxTokens)
                    val ncur = IntVar(promptTokens)
                    val totalLimit = promptTokens + maxTokens
                    
                    while (ncur.value <= totalLimit) {
                        val str = completion_loop(
                            state.context, 
                            batch, 
                            sampler, 
                            totalLimit, 
                            ncur
                        )
                        if (str == null) break
                        emit(str)
                    }
                } finally {
                    // Siempre liberar batch y sampler
                    free_sampler(sampler)
                    free_batch(batch)
                    kv_cache_clear(state.context)
                }
            }
            else -> { /* No-op si no está cargado */ }
        }
    }.flowOn(runLoop)
    
    /**
     * Genera una respuesta completa (sin streaming)
     * @param maxTokens Tokens ADICIONALES a generar (no el total incluyendo prompt)
     */
    suspend fun sendComplete(message: String, formatChat: Boolean = true, maxTokens: Int = nlen): String {
        return withContext(runLoop) {
            when (val state = currentState) {
                is State.Loaded -> {
                    // Limpiar KV cache antes de la consulta
                    kv_cache_clear(state.context)
                    
                    // Crear batch y sampler frescos para cada consulta
                    val batch = new_batch(BATCH_SIZE, 0, 1)
                    if (batch == 0L) throw IllegalStateException("new_batch() falló")
                    
                    val sampler = new_sampler()
                    if (sampler == 0L) {
                        free_batch(batch)
                        throw IllegalStateException("new_sampler() falló")
                    }
                    
                    try {
                        val response = StringBuilder()
                        val promptTokens = completion_init(state.context, batch, message, formatChat, maxTokens)
                        val ncur = IntVar(promptTokens)
                        
                        // El límite total es: tokens del prompt + tokens adicionales a generar
                        val totalLimit = promptTokens + maxTokens
                        
                        Log.d(TAG, "completion_init: promptTokens=$promptTokens, maxTokens=$maxTokens, totalLimit=$totalLimit")
                        
                        var tokenCount = 0
                        while (ncur.value <= totalLimit) {
                            val str = completion_loop(
                                state.context,
                                batch,
                                sampler,
                                totalLimit,  // Usar el límite total
                                ncur
                            )
                            if (str == null) {
                                Log.d(TAG, "completion_loop returned null after $tokenCount tokens")
                                break
                            }
                            response.append(str)
                            tokenCount++
                        }
                        
                        Log.d(TAG, "Generated $tokenCount tokens, response length: ${response.length}")
                        response.toString()
                    } finally {
                        // Siempre liberar batch y sampler
                        free_sampler(sampler)
                        free_batch(batch)
                        kv_cache_clear(state.context)
                    }
                }
                else -> throw IllegalStateException("Modelo no cargado")
            }
        }
    }
    
    /**
     * Descarga el modelo y libera recursos
     */
    suspend fun unload() {
        withContext(runLoop) {
            when (val state = currentState) {
                is State.Loaded -> {
                    Log.i(TAG, "Liberando recursos del modelo...")
                    free_context(state.context)
                    free_model(state.model)
                    currentState = State.Idle
                    Log.i(TAG, "Recursos liberados")
                }
                else -> { /* No-op */ }
            }
        }
    }
}
