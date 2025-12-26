# FarmifAI - Resumen Tecnico

**Asistente agricola con IA 100% offline para Android**

Repositorio: [https://github.com/Bryan-Andres-Suarez-Sanchez/AgroChat_Project](https://github.com/Bryan-Andres-Suarez-Sanchez/AgroChat_Project)

---

## 1. Proposito

FarmifAI es una aplicacion movil disenada para agricultores en zonas rurales de Colombia, donde la conectividad a internet es limitada o inexistente. La aplicacion proporciona:

- **Asistencia agricola por voz y texto** sin conexion a internet
- **Diagnostico visual de enfermedades** mediante la camara del dispositivo
- **Base de conocimiento local** sobre cultivos, plagas, riego y fertilizacion
- **Generacion de respuestas inteligentes** usando modelos de lenguaje locales

El objetivo principal es democratizar el acceso a informacion agricola tecnica para pequenos productores.

---

## 2. Caracteristicas Clave

### 2.1 Funcionamiento 100% Offline

Toda la inferencia ocurre en el dispositivo movil:

| Componente | Tecnologia | Modelo |
|------------|------------|--------|
| STT (Voz a Texto) | Vosk | `model-es-small` (~50MB) |
| TTS (Texto a Voz) | Android TTS | Motor del sistema |
| Busqueda Semantica | MindSpore Lite | `sentence_encoder.ms` (MiniLM, 384 dims) |
| LLM Local | llama.cpp | Llama 3.2 1B Instruct Q4_K_M (~750MB) |
| Vision | MindSpore Lite | `plant_disease_model.ms` (MobileNetV2) |

### 2.2 Interaccion por Voz

El usuario puede hablar directamente a la aplicacion. El flujo es:

```
Voz del usuario --> Vosk STT --> Texto --> RAG --> LLM --> Respuesta --> TTS
```

- Reconocimiento de voz offline con Vosk (modelo espanol)
- Sintesis de voz con el motor TTS del sistema Android
- Modo conversacion: la app escucha automaticamente tras responder

### 2.3 Diagnostico Visual de Enfermedades

El usuario puede tomar una foto de una hoja y obtener un diagnostico inmediato.

**Cultivos soportados (21 clases):**

| Cultivo | Clases | Enfermedades |
|---------|--------|--------------|
| Cafe | 4 | Cercospora, Minador, Phoma, Roya |
| Maiz | 4 | Mancha gris, Roya comun, Saludable, Tizon norteno |
| Papa | 3 | Saludable, Tizon tardio, Tizon temprano |
| Pimiento | 2 | Mancha bacteriana, Saludable |
| Tomate | 8 | Mancha bacteriana, Mancha diana, Moho foliar, Saludable, Septoriosis, Tizon tardio, Tizon temprano, Virus mosaico |

El diagnostico se integra con el sistema RAG para ofrecer tratamientos.

### 2.4 Sistema RAG (Retrieval Augmented Generation)

La aplicacion usa busqueda semantica para encontrar contexto relevante antes de generar respuestas:

1. La pregunta del usuario se convierte en un embedding (384 dims)
2. Se compara contra embeddings pre-calculados de la base de conocimiento
3. Se seleccionan los top-3 contextos mas similares (coseno >= 0.4)
4. El LLM genera una respuesta usando ese contexto

**Base de conocimiento:**
- 134 entradas
- 517 preguntas indexadas
- Categorias: cultivo, plagas, fertilizacion, riego, siembra, diagnostico, cosecha

---

## 3. Arquitectura de Despliegue (Vista del Usuario)

```
+------------------------------------------------------------------+
|                        DISPOSITIVO ANDROID                        |
|  +------------------------------------------------------------+  |
|  |                      FarmifAI App                          |  |
|  |  +------------------+  +------------------+  +----------+  |  |
|  |  |   VoiceHelper    |  | SemanticSearch   |  |  Camera  |  |  |
|  |  |   (Vosk + TTS)   |  |    Helper        |  |  Helper  |  |  |
|  |  +--------+---------+  +--------+---------+  +----+-----+  |  |
|  |           |                     |                 |        |  |
|  |           v                     v                 v        |  |
|  |  +------------------+  +------------------+  +----------+  |  |
|  |  |  model-es-small  |  | sentence_encoder |  |  plant   |  |  |
|  |  |  (Vosk ASR)      |  |   .ms (MiniLM)   |  | disease  |  |  |
|  |  +------------------+  +------------------+  |  _model  |  |  |
|  |                                |             |   .ms    |  |  |
|  |                                v             +----------+  |  |
|  |                       +------------------+                 |  |
|  |                       | kb_embeddings.npy|                 |  |
|  |                       | (517 x 384 f32)  |                 |  |
|  |                       +------------------+                 |  |
|  |                                |                           |  |
|  |                                v                           |  |
|  |                       +------------------+                 |  |
|  |                       |   LlamaService   |                 |  |
|  |                       |   (llama.cpp)    |                 |  |
|  |                       +--------+---------+                 |  |
|  |                                |                           |  |
|  |                                v                           |  |
|  |                       +------------------+                 |  |
|  |                       | Llama-3.2-1B.gguf|                 |  |
|  |                       |   (~750MB)       |                 |  |
|  |                       +------------------+                 |  |
|  +------------------------------------------------------------+  |
+------------------------------------------------------------------+
```

**Almacenamiento en dispositivo:**

| Ubicacion | Contenido | Tamano |
|-----------|-----------|--------|
| `assets/` | Modelos MindSpore, KB, tokenizer, Vosk | ~300MB |
| `/sdcard/Android/data/.../files/` | Modelo GGUF (Llama) | ~750MB |

---

## 4. Arquitectura de Entrenamiento

### 4.1 Pipeline de Entrenamiento de Vision

```
+------------------------------------------------------------------+
|                    ENTORNO DE ENTRENAMIENTO                       |
|                   (Google Colab / Azure ML)                       |
|  +------------------------------------------------------------+  |
|  |                                                            |  |
|  |  +------------------+     +------------------+             |  |
|  |  | PlantVillage DS  | --> |                  |             |  |
|  |  | (38 clases)      |     |                  |             |  |
|  |  +------------------+     |   MobileNetV2    |             |  |
|  |                           |   Fine-tuning    |             |  |
|  |  +------------------+     |                  |             |  |
|  |  | Colombia Crops   | --> |  - Transfer L.   |             |  |
|  |  | (Cafe, etc.)     |     |  - 2 fases       |             |  |
|  |  +------------------+     |  - Dropout 0.4   |             |  |
|  |                           +--------+---------+             |  |
|  |                                    |                       |  |
|  |                                    v                       |  |
|  |                           +------------------+             |  |
|  |                           | SavedModel (TF)  |             |  |
|  |                           +--------+---------+             |  |
|  |                                    |                       |  |
|  |                                    v                       |  |
|  |                           +------------------+             |  |
|  |                           | MindSpore Conv.  |             |  |
|  |                           | (TF -> .ms)      |             |  |
|  |                           +--------+---------+             |  |
|  |                                    |                       |  |
|  |                                    v                       |  |
|  |                           +------------------+             |  |
|  |                           | plant_disease    |             |  |
|  |                           | _model.ms        |             |  |
|  |                           +------------------+             |  |
|  +------------------------------------------------------------+  |
+------------------------------------------------------------------+
```

**Configuracion de entrenamiento:**

```python
CONFIG = {
    'IMG_SIZE': 224,
    'EPOCHS_PHASE1': 25,      # Solo clasificador
    'EPOCHS_PHASE2': 35,      # Fine-tuning completo
    'LEARNING_RATE_1': 1e-3,
    'LEARNING_RATE_2': 1e-5,
    'LABEL_SMOOTHING': 0.1,
    'DROPOUT_RATE': 0.4,
    'L2_REG': 0.01,
}
```

### 4.2 Pipeline de Generacion de Embeddings

```
+------------------------------------------------------------------+
|                    ENTORNO LOCAL (Python)                         |
|  +------------------------------------------------------------+  |
|  |                                                            |  |
|  |  +------------------+     +------------------+             |  |
|  |  | agrochat_kb.json | --> | Tokenizer        |             |  |
|  |  | (517 preguntas)  |     | (MiniLM)         |             |  |
|  |  +------------------+     +--------+---------+             |  |
|  |                                    |                       |  |
|  |                                    v                       |  |
|  |                           +------------------+             |  |
|  |                           | paraphrase-      |             |  |
|  |                           | multilingual-    |             |  |
|  |                           | MiniLM-L12-v2    |             |  |
|  |                           +--------+---------+             |  |
|  |                                    |                       |  |
|  |                                    | Mean Pooling + L2 Norm|
|  |                                    v                       |  |
|  |                           +------------------+             |  |
|  |                           | kb_embeddings.npy|             |  |
|  |                           | (517 x 384 f32)  |             |  |
|  |                           +------------------+             |  |
|  +------------------------------------------------------------+  |
+------------------------------------------------------------------+
```

---

## 5. Datasets Utilizados

### 5.1 Vision - Clasificacion de Enfermedades

| Dataset | Clases | Imagenes | Uso |
|---------|--------|----------|-----|
| PlantVillage | 38 | ~54,000 | Base pre-entrenamiento |
| Colombia Crops | 21 | ~15,000 | Fine-tuning regional |
| Cassava (Yuca) | 5 | ~21,000 | Clases adicionales |

**Clases del modelo final (colombia_v1.0):**

```json
{
  "version": "colombia_v1.0",
  "num_classes": 21,
  "input_size": 224,
  "labels": [
    {"id": 0, "name": "Cafe___Cercospora", "crop": "Cafe"},
    {"id": 1, "name": "Cafe___Minador", "crop": "Cafe"},
    {"id": 2, "name": "Cafe___Phoma", "crop": "Cafe"},
    {"id": 3, "name": "Cafe___Roya", "crop": "Cafe"},
    {"id": 4, "name": "Maiz___Mancha_gris", "crop": "Maiz"},
    ...
  ]
}
```

### 5.2 NLP - Base de Conocimiento

| Archivo | Contenido | Formato |
|---------|-----------|---------|
| `agrochat_knowledge_base.json` | 134 entradas, 517 preguntas | JSON |
| `kb_embeddings.npy` | 517 embeddings de 384 dims | NumPy |
| `sentence_tokenizer.json` | Vocabulario MiniLM | JSON |

**Estructura de una entrada KB:**

```json
{
  "id": 10,
  "category": "cultivo",
  "questions": [
    "Como cultivar tomate?",
    "Como sembrar tomate?",
    "Cultivo de tomate"
  ],
  "answer": "**Cultivo de tomate**\n\n**Siembra:**\n- Profundidad: 1 cm\n..."
}
```

### 5.3 Voz - Modelo STT

| Modelo | Idioma | Tamano | Fuente |
|--------|--------|--------|--------|
| `model-es-small` | Espanol | ~50MB | Vosk |

---

## 6. Codigo Clave

### 6.1 Busqueda Semantica (SemanticSearchHelper.kt)

```kotlin
/**
 * SemanticSearchHelper - Busqueda semantica para AgroChat
 * 
 * 1. Carga embeddings pre-calculados de la base de conocimiento
 * 2. Usa MindSpore Lite para generar embeddings de preguntas del usuario
 * 3. Encuentra la respuesta mas similar usando similitud coseno
 */
class SemanticSearchHelper(private val context: Context) {
    
    companion object {
        private const val EMBEDDING_DIM = 384  // MiniLM produce embeddings de 384 dims
        private const val MAX_SEQ_LENGTH = 128
    }
    
    /**
     * Encuentra los top-K contextos mas relevantes para una pregunta
     */
    fun findTopKContexts(query: String, topK: Int = 3, minScore: Float = 0.4f): ContextResult {
        val queryEmbedding = computeEmbedding(query)
        
        val results = kbEmbeddings.mapIndexed { i, emb ->
            i to cosineSimilarity(queryEmbedding, emb)
        }.filter { it.second >= minScore }
         .sortedByDescending { it.second }
         .take(topK)
         .map { (idx, score) ->
             MatchResult(
                 answer = kbEntries[kbEntryIds[idx]]!!.answer,
                 matchedQuestion = kbQuestions[idx],
                 similarityScore = score,
                 category = kbEntries[kbEntryIds[idx]]!!.category,
                 entryId = kbEntryIds[idx]
             )
         }
        
        return ContextResult(results, buildCombinedContext(results))
    }
    
    /**
     * Calcula similitud coseno entre dos vectores
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return dot / (sqrt(normA) * sqrt(normB) + 1e-8f)
    }
}
```

### 6.2 Servicio LLM Local (LlamaService.kt)

```kotlin
/**
 * LlamaService - Servicio de LLM local usando llama.cpp
 * Permite respuestas inteligentes sin conexion usando modelos GGUF
 */
class LlamaService private constructor() {
    
    companion object {
        private const val DEFAULT_MODEL_FILENAME = "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        private const val MAX_TOKENS = 150
    }
    
    private val llama: LLamaAndroid = LLamaAndroid.instance()
    
    /**
     * Genera respuesta usando el modelo local
     */
    suspend fun generate(prompt: String): Flow<String> = llama.generate(prompt)
    
    /**
     * Construye el prompt en formato Llama 3.2 Instruct
     */
    fun buildPrompt(context: String, userQuery: String): String {
        val systemPrompt = """Eres FarmifAI, un asistente agricola experto.
Responde de forma clara y concisa en espanol.
Usa la informacion del contexto proporcionado."""
        
        return """<|begin_of_text|><|start_header_id|>system<|end_header_id|>

$systemPrompt<|eot_id|><|start_header_id|>user<|end_header_id|>

Contexto: $context

Pregunta: $userQuery<|eot_id|><|start_header_id|>assistant<|end_header_id|}"""
    }
}
```

### 6.3 Clasificador de Enfermedades (PlantDiseaseClassifier.kt)

```kotlin
/**
 * Clasificador de enfermedades de plantas usando MindSpore Lite.
 * Usa MobileNetV2 entrenado en PlantVillage + Colombia Crops.
 */
class PlantDiseaseClassifier(private val context: Context) {
    
    companion object {
        private const val MODEL_FILE = "plant_disease_model.ms"
        private const val INPUT_SIZE = 224
        private const val MIN_CONFIDENCE = 0.03f
    }
    
    /**
     * Clasifica una imagen de hoja
     */
    fun classify(bitmap: Bitmap): DiseaseResult? {
        // 1. Preprocesar imagen a 224x224 RGB normalizado
        val inputBuffer = preprocessImage(bitmap)
        
        // 2. Ejecutar inferencia MindSpore
        val outputBuffer = MindSporeHelper.runInference(modelHandle, inputBuffer)
        
        // 3. Obtener clase con mayor probabilidad
        val probabilities = softmax(outputBuffer)
        val maxIdx = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
        
        if (probabilities[maxIdx] < MIN_CONFIDENCE) return null
        
        val label = labels[maxIdx]
        return DiseaseResult(
            id = label.id,
            name = label.name,
            displayName = label.display,
            crop = label.crop,
            confidence = probabilities[maxIdx],
            isHealthy = label.name.contains("Saludable")
        )
    }
    
    /**
     * Preprocesa imagen para el modelo
     */
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = scaled.getPixel(x, y)
                // Normalizar a [-1, 1]
                buffer.putFloat(((pixel shr 16 and 0xFF) - 127.5f) / 127.5f)
                buffer.putFloat(((pixel shr 8 and 0xFF) - 127.5f) / 127.5f)
                buffer.putFloat(((pixel and 0xFF) - 127.5f) / 127.5f)
            }
        }
        return buffer
    }
}
```

### 6.4 Helper de Voz (VoiceHelper.kt)

```kotlin
/**
 * VoiceHelper - Maneja Speech-to-Text (Vosk) y Text-to-Speech
 * Funciona 100% OFFLINE sin Google
 */
class VoiceHelper(private val context: Context) {
    
    companion object {
        private const val VOSK_MODEL = "model-es-small"
    }
    
    private var voskModel: Model? = null
    private var speechService: SpeechService? = null
    private var tts: TextToSpeech? = null
    
    /**
     * Inicia reconocimiento de voz
     */
    fun startListening() {
        val recognizer = Recognizer(voskModel, 16000.0f)
        speechService = SpeechService(recognizer, 16000.0f)
        speechService?.startListening(object : RecognitionListener {
            override fun onResult(hypothesis: String?) {
                hypothesis?.let {
                    val json = JSONObject(it)
                    val text = json.optString("text", "")
                    if (text.isNotBlank()) onResult?.invoke(text)
                }
            }
            override fun onPartialResult(hypothesis: String?) {
                hypothesis?.let {
                    val json = JSONObject(it)
                    val partial = json.optString("partial", "")
                    if (partial.isNotBlank()) onPartialResult?.invoke(partial)
                }
            }
        })
    }
    
    /**
     * Sintetiza texto a voz
     */
    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_id")
    }
}
```

### 6.5 Generacion de Embeddings (Python)

```python
def encode_with_mean_pooling(tokenizer, model, texts, batch_size=32):
    """
    Generar embeddings usando mean pooling (sentence-transformers).
    """
    import torch
    
    all_embeddings = []
    
    for i in range(0, len(texts), batch_size):
        batch_texts = texts[i:i + batch_size]
        
        inputs = tokenizer(
            batch_texts,
            padding="max_length",
            truncation=True,
            max_length=128,
            return_tensors="pt"
        )
        
        with torch.no_grad():
            outputs = model(**inputs)
            
            # Mean pooling sobre tokens validos
            attention_mask = inputs['attention_mask']
            token_embeddings = outputs.last_hidden_state
            
            input_mask_expanded = attention_mask.unsqueeze(-1).expand(
                token_embeddings.size()
            ).float()
            
            sum_embeddings = torch.sum(token_embeddings * input_mask_expanded, 1)
            sum_mask = torch.clamp(input_mask_expanded.sum(1), min=1e-9)
            embeddings = sum_embeddings / sum_mask
            
            # Normalizar L2
            embeddings = torch.nn.functional.normalize(embeddings, p=2, dim=1)
            
            all_embeddings.append(embeddings.cpu().numpy())
    
    return np.vstack(all_embeddings)
```

---

## 7. Estructura del Proyecto

```
AgroChat_Project/
|-- app/
|   |-- src/main/
|   |   |-- java/edu/unicauca/app/agrochat/
|   |   |   |-- MainActivity.kt           # UI principal (Compose)
|   |   |   |-- llm/
|   |   |   |   |-- LlamaService.kt       # LLM local (llama.cpp)
|   |   |   |   |-- GroqService.kt        # LLM online (opcional)
|   |   |   |-- mindspore/
|   |   |   |   |-- SemanticSearchHelper.kt  # RAG + embeddings
|   |   |   |   |-- MindSporeHelper.kt    # Wrapper JNI
|   |   |   |-- vision/
|   |   |   |   |-- PlantDiseaseClassifier.kt  # Clasificacion visual
|   |   |   |   |-- CameraHelper.kt       # Captura de camara
|   |   |   |-- voice/
|   |   |       |-- VoiceHelper.kt        # STT + TTS
|   |   |-- assets/
|   |       |-- sentence_encoder.ms       # Modelo embeddings (224MB)
|   |       |-- sentence_tokenizer.json   # Tokenizer
|   |       |-- kb_embeddings.npy         # Embeddings KB (517x384)
|   |       |-- agrochat_knowledge_base.json  # Base de conocimiento
|   |       |-- plant_disease_model.ms    # Modelo vision
|   |       |-- plant_disease_labels.json # 21 clases
|   |       |-- model-es-small/           # Vosk STT
|-- tools/
|   |-- training_script/
|   |   |-- train_colombia.py             # Entrenamiento vision
|   |-- push_llama_model_to_device.sh     # Deploy modelo GGUF
|-- generate_mindspore_compatible_embeddings.py  # Gen. embeddings
|-- README.md
```

---

## 8. Requisitos del Sistema

### Desarrollo

- Android Studio + JDK 17
- NDK 25.1.8937393
- CMake 3.22.1
- Python 3.10+ (para embeddings)

### Dispositivo

- Android 8.0+ (API 26)
- Arquitectura: arm64-v8a (recomendado)
- RAM: 4GB minimo (6GB recomendado)
- Almacenamiento: ~1.2GB para modelos

---

## 9. Instrucciones de Compilacion

```bash
# Clonar repositorio
git clone https://github.com/Bryan-Andres-Suarez-Sanchez/AgroChat_Project.git
cd AgroChat_Project

# Descargar assets grandes (Git LFS)
git lfs install
git lfs pull

# Compilar APK debug
./gradlew assembleDebug

# Instalar en dispositivo
./gradlew installDebug

# Copiar modelo LLM al dispositivo
adb push /ruta/Llama-3.2-1B-Instruct-Q4_K_M.gguf \
    /sdcard/Android/data/edu.unicauca.app.agrochat/files/

# Iniciar aplicacion
adb shell am start -n edu.unicauca.app.agrochat/.MainActivity
```

---

## 10. Actualizacion de la Base de Conocimiento

Para agregar nuevas preguntas o modificar respuestas:

```bash
# 1. Editar base de conocimiento
nano app/src/main/assets/agrochat_knowledge_base.json

# 2. Regenerar embeddings
python3 -m venv .venv
source .venv/bin/activate
pip install numpy torch transformers sentence-transformers

python generate_mindspore_compatible_embeddings.py

# 3. Recompilar e instalar
./gradlew installDebug
```

---

## 11. Metricas de Rendimiento

| Metrica | Valor |
|---------|-------|
| Precision busqueda semantica (Top-1) | 94.4% |
| Precision busqueda semantica (Top-3) | 98.1% |
| Tiempo inferencia LLM | 2-5 seg (150 tokens) |
| Tiempo clasificacion imagen | 200-400 ms |
| Tiempo STT | Tiempo real |
| Tamano APK | ~70MB |
| Tamano total con modelos | ~1.2GB |

---

## 12. Licencia y Creditos

- **MindSpore Lite**: Apache 2.0 (Huawei)
- **llama.cpp**: MIT License
- **Vosk**: Apache 2.0
- **PlantVillage Dataset**: CC BY-SA 4.0
- **Llama 3.2**: Meta AI License

---

*Documento generado: 26 de diciembre de 2025*
