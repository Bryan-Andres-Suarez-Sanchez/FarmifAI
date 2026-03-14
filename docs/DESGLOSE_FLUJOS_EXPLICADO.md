# Desglose explicado de los flujos (chat/voz, diagnóstico, MindSpore nativo)

Este documento explica en lenguaje simple los puntos de `docs/ESTRUCTURA_PROYECTO.md` que suelen generar confusión.

## 1) Glosario rápido

| Término | Qué significa en este proyecto |
|---|---|
| `KB` | **Knowledge Base** (base de conocimiento). Es un JSON con información agrícola: `app/src/main/assets/agrochat_knowledge_base.json`. |
| `Top-K` | “Las K mejores coincidencias”. Si `K=3`, trae las 3 respuestas más parecidas a tu pregunta. |
| `RAG` | **Retrieval Augmented Generation**. Primero busca contexto útil en la KB y luego se lo pasa al LLM para responder mejor. |
| `LLM` | Modelo de lenguaje que genera texto (por ejemplo Llama o Groq). |
| `STT` | **Speech To Text**: voz -> texto (Vosk). |
| `TTS` | **Text To Speech**: texto -> voz (voz del sistema Android). |
| `API key` | Llave de acceso para usar un servicio online (aquí Groq). |
| `Inferencia` | Ejecutar un modelo ya entrenado para obtener resultado (no entrenar). |
| `JNI` | Capa puente entre Kotlin/Java y C/C++. |
| `Runtime` | Librerías nativas necesarias para ejecutar modelos (`.so`, headers, etc.). |

## 2) Flujo 3.1 explicado (pregunta en chat/voz)

## 2.1 Qué pasa cuando hablas o escribes

1. `MainActivity.kt` recibe el texto final del usuario (`sendMessage(userMessage: String)`; y desde voz por el callback definido en `initializeVoice()`).
2. Si el texto viene por voz, `VoiceHelper.kt` lo transcribe con Vosk (STT) y lo envía a `MainActivity` (`startListening()`, `processResult(hypothesis: String?)`, y luego `onResult = { ... sendMessage(it) }` dentro de `initializeVoice()`).
3. `MainActivity` decide cómo responder según disponibilidad de internet/modelos (`updateOnlineStatus()`, `findResponse(userQuery: String)`).

## 2.2 Orden real de decisión (prioridades)

1. Intenta modo online con `GroqService.kt` si (`updateOnlineStatus()`, `isAvailable()`, `query(userMessage: String, conversationHistory: List<Pair<String, String>> = emptyList())`):
   - Hay internet (`isOnline()`).
   - Hay API key configurada (`saveGroqApiKey(key: String)`, `setApiKey(key: String)`).
2. Si no se puede usar Groq:
   - `SemanticSearchHelper.kt` busca en KB los contextos más cercanos (`findTopKContexts(userQuery: String, topK: Int = 3, minScore: Float = 0.4f)`, `buildCombinedContext(results: List<MatchResult>)`).
3. Si Llama local está habilitado y cargado:
   - `LlamaService.kt` usa esos contextos RAG para generar respuesta más natural (`initializeLlama()`, `load(context: Context)`, `generateAgriResponse(userQuery: String, contextFromKB: String? = null, maxTokens: Int = MAX_TOKENS)`).
4. Si Llama no está disponible:
   - Se usa la mejor respuesta directa de la KB (búsqueda semántica pura) (`findResponse(userQuery: String)` con fallback a `bestMatch.answer`).
5. Al final, `VoiceHelper.kt` puede leer en voz alta la respuesta (TTS) (`speak(text: String)`).

## 2.3 Ejemplo de Top-K y KB

Pregunta usuario: `¿Cómo controlo plagas en papa?`

`SemanticSearchHelper` puede encontrar (`findTopKContexts(userQuery: String, topK: Int = 3, minScore: Float = 0.4f)`):

1. Entrada KB sobre “plagas comunes en papa”.
2. Entrada KB sobre “manejo integrado de plagas”.
3. Entrada KB sobre “frecuencia de aplicación de control”.

Eso es **Top-3**.  
Luego, con RAG, el LLM responde usando esa información combinada (`generateAgriResponse(userQuery: String, contextFromKB: String? = null, maxTokens: Int = MAX_TOKENS)`).

## 3) Flujo 3.2 explicado (diagnóstico visual)

1. `CameraHelper.kt` obtiene una imagen (`startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView, callback: CameraCallback? = null)`, `captureImage(callback: CaptureCallback)`, y desde galería `openGallery()`):
   - Desde cámara (CameraX), o
   - Desde galería.
2. `PlantDiseaseClassifier.kt` prepara la imagen (`classify(bitmap: Bitmap)`, `preprocessImage(bitmap: Bitmap)`):
   - La redimensiona a `224x224`.
   - Convierte pixeles a formato numérico que entiende el modelo.
3. Ejecuta inferencia con `MindSporeHelper.kt` sobre `plant_disease_model.ms` (`runNetFloat(envPtr: Long, input: FloatArray)`).
4. Devuelve `DiseaseResult` con (`classify(bitmap: Bitmap)` y manejo de UI en `processCapture(bitmap: Bitmap)`):
   - Enfermedad detectada.
   - Cultivo.
   - Confianza.
   - Si está saludable o no.
5. Si el usuario pide “tratamiento” (`diagnosisToChat(result: DiseaseResult)`, `toRagQuery()`):
   - Se convierte ese diagnóstico en una consulta de texto (query RAG).
   - Vuelve al flujo del chat para recomendar manejo/tratamiento (`findResponse(userQuery: String)`).

## 4) Flujo 3.3 explicado (MindSpore nativo)

Este flujo responde a “¿cómo pasa de Kotlin a C++ para correr modelos?”.

1. Kotlin llama `MindSporeHelper.kt` (`loadModelFromAssets(context: Context, assetModelPath: String, numThreads: Int = 2)`, `predictSentenceEncoder(modelHandle: Long, inputIds: IntArray, attentionMask: IntArray)`, `predictWithTokenIds(modelHandle: Long, tokenIds: IntArray)`).
2. `MindSporeHelper.kt` invoca funciones nativas por JNI (`loadModel(modelBuffer: ByteBuffer, numThread: Int)`, `runNetFloat(envPtr: Long, input: FloatArray)`, `runNetIds(envPtr: Long, ids: IntArray)`, `runNetSentenceEncoder(envPtr: Long, inputIds: IntArray, attentionMask: IntArray)`, `unloadModel(envPtr: Long)`).
3. Esas funciones están implementadas en `app/src/main/cpp/MindSporeNetnative.cpp` (`Java_edu_unicauca_app_agrochat_MindSporeHelper_loadModel(...)`, `Java_edu_unicauca_app_agrochat_MindSporeHelper_runNetFloat(...)`, `Java_edu_unicauca_app_agrochat_MindSporeHelper_runNetIds(...)`, `Java_edu_unicauca_app_agrochat_MindSporeHelper_runNetSentenceEncoder(...)`, `Java_edu_unicauca_app_agrochat_MindSporeHelper_unloadModel(...)`).
4. `MindSporeNetnative.cpp` usa la API C++ de MindSpore para:
   - Cargar modelo (`Java_edu_unicauca_app_agrochat_MindSporeHelper_loadModel(...)`).
   - Preparar tensores de entrada (`Java_edu_unicauca_app_agrochat_MindSporeHelper_runNetFloat(...)`, `Java_edu_unicauca_app_agrochat_MindSporeHelper_runNetIds(...)`, `Java_edu_unicauca_app_agrochat_MindSporeHelper_runNetSentenceEncoder(...)`).
   - Ejecutar `Predict` (dentro de esas mismas funciones JNI).
   - Devolver salida a Kotlin (retorno de esas funciones JNI).
5. Todo esto depende del runtime vendorizado en:
   - `app/src/main/cpp/mindspore-lite-2.4.10-android-aarch64/`

## 5) Qué debes recordar (resumen corto)

1. `KB` es el “libro base” de respuestas agrícolas.
2. `Top-K` es “tomar las K coincidencias más parecidas”.
3. `RAG` es “buscar primero en KB, luego generar”.
4. Chat/voz siempre intenta primero online (si se puede), luego offline local.
5. Diagnóstico visual usa imagen + modelo MindSpore.
6. JNI es solo el puente técnico para ejecutar C++ desde Kotlin.

## 6) Archivos clave para abrir mientras lees

1. `app/src/main/java/edu/unicauca/app/agrochat/MainActivity.kt`
2. `app/src/main/java/edu/unicauca/app/agrochat/mindspore/SemanticSearchHelper.kt`
3. `app/src/main/java/edu/unicauca/app/agrochat/llm/LlamaService.kt`
4. `app/src/main/java/edu/unicauca/app/agrochat/llm/GroqService.kt`
5. `app/src/main/java/edu/unicauca/app/agrochat/voice/VoiceHelper.kt`
6. `app/src/main/java/edu/unicauca/app/agrochat/vision/PlantDiseaseClassifier.kt`
7. `app/src/main/cpp/MindSporeNetnative.cpp`
