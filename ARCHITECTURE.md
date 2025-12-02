# ARApp - Arquitectura Técnica 🏗️

Este documento describe la arquitectura completa del sistema ARApp, incluyendo todos los componentes, flujos de datos y cómo interactúan entre sí. Está diseñado para que cualquier desarrollador (o GitHub Copilot) pueda entender y modificar cualquier parte del sistema.

---

## 📊 Diagrama de Arquitectura General

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              ARApp - Android                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                         UI Layer (Jetpack Compose)                        │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │   │
│  │  │   Chat UI   │  │  Voice UI   │  │  Settings   │  │   Status    │      │   │
│  │  │  Messages   │  │  Mic Button │  │  API Key    │  │  Online/    │      │   │
│  │  │  TextField  │  │  Waveform   │  │  Modal      │  │  Offline    │      │   │
│  │  └──────┬──────┘  └──────┬──────┘  └─────────────┘  └─────────────┘      │   │
│  │         │                │                                                │   │
│  │         └────────┬───────┘                                                │   │
│  │                  ▼                                                        │   │
│  │  ┌──────────────────────────────────────────────────────────────────┐    │   │
│  │  │                    MainActivity.kt                                │    │   │
│  │  │  - Gestiona estado de UI (messages, isLoading, isOnline)         │    │   │
│  │  │  - Coordina entre Voice, RAG y LLM                               │    │   │
│  │  │  - Maneja ciclo de vida de componentes                           │    │   │
│  │  └──────────────────────────────────────────────────────────────────┘    │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
│                                        │                                         │
│                    ┌───────────────────┼───────────────────┐                    │
│                    ▼                   ▼                   ▼                    │
│  ┌─────────────────────┐ ┌─────────────────────┐ ┌─────────────────────┐        │
│  │    Voice Layer      │ │     RAG Layer       │ │     LLM Layer       │        │
│  │   VoiceHelper.kt    │ │ SemanticSearchHelper│ │  GroqService.kt     │        │
│  │                     │ │        .kt          │ │  LlamaService.kt    │        │
│  │  ┌───────────────┐  │ │                     │ │                     │        │
│  │  │  Vosk STT     │  │ │ ┌─────────────────┐ │ │ ┌─────────────────┐ │        │
│  │  │  (Offline)    │  │ │ │ Query Embedding │ │ │ │  Groq API       │ │        │
│  │  │  Español      │  │ │ │  (MindSpore)    │ │ │ │  (Online)       │ │        │
│  │  └───────────────┘  │ │ └────────┬────────┘ │ │ └─────────────────┘ │        │
│  │  ┌───────────────┐  │ │          │          │ │ ┌─────────────────┐ │        │
│  │  │  Android TTS  │  │ │          ▼          │ │ │  llama.cpp      │ │        │
│  │  │  (Offline)    │  │ │ ┌─────────────────┐ │ │ │  (Offline)      │ │        │
│  │  └───────────────┘  │ │ │ Cosine Similarity│ │ │ │  Llama 3.2 1B  │ │        │
│  └─────────────────────┘ │ │ vs KB Embeddings │ │ │ └─────────────────┘ │        │
│                          │ └────────┬────────┘ │ └─────────────────────┘        │
│                          │          │          │                                 │
│                          │          ▼          │                                 │
│                          │ ┌─────────────────┐ │                                 │
│                          │ │  Top-K Context  │ │                                 │
│                          │ │  Retrieval      │ │                                 │
│                          │ └─────────────────┘ │                                 │
│                          └─────────────────────┘                                 │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                          Native Layer (C++/JNI)                           │   │
│  │  ┌─────────────────────┐              ┌─────────────────────┐            │   │
│  │  │ MindSporeNetnative  │              │  llama-android.so   │            │   │
│  │  │      .cpp           │              │  (llama.cpp JNI)    │            │   │
│  │  │                     │              │                     │            │   │
│  │  │ - loadModel()       │              │ - load_model()      │            │   │
│  │  │ - runNetSentence    │              │ - completion()      │            │   │
│  │  │   Encoder()         │              │ - free_model()      │            │   │
│  │  │ - unloadModel()     │              │                     │            │   │
│  │  └─────────────────────┘              └─────────────────────┘            │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                             Assets (Modelos)                              │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐ │   │
│  │  │sentence_    │ │kb_embeddings│ │model-es-    │ │Llama-3.2-1B-       │ │   │
│  │  │encoder.ms   │ │.npy         │ │small/       │ │Instruct-Q4_K_M.gguf│ │   │
│  │  │(224MB)      │ │(222KB)      │ │(~50MB)      │ │(770MB)             │ │   │
│  │  │MindSpore    │ │145x384 float│ │Vosk STT     │ │LLM cuantizado      │ │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────────────┘ │   │
│  │  ┌─────────────────────────┐ ┌───────────────────────────────────────┐   │   │
│  │  │agrochat_knowledge_      │ │sentence_tokenizer.json                │   │   │
│  │  │base.json                │ │(Tokenizer para MindSpore)             │   │   │
│  │  │45 entradas, 145 preguntas│ │paraphrase-multilingual-MiniLM-L12-v2│   │   │
│  │  └─────────────────────────┘ └───────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Flujo de Procesamiento de una Pregunta

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         FLUJO: Usuario hace una pregunta                         │
└─────────────────────────────────────────────────────────────────────────────────┘

Usuario: "¿Cómo controlar plagas en papa?"
                    │
                    ▼
┌─────────────────────────────────────┐
│ 1. ENTRADA                          │
│    - Texto directo (TextField)      │
│    - O voz → Vosk STT → Texto       │
└─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────┐
│ 2. TOKENIZACIÓN                     │
│    sentence_tokenizer.json          │
│    "¿Cómo controlar plagas en papa?"│
│    → [101, 2437, 8765, ..., 102]    │
│    (128 tokens, padding incluido)   │
└─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────┐
│ 3. EMBEDDING (MindSpore)            │
│    sentence_encoder.ms              │
│    Input: [input_ids, attention_mask]│
│    Output[1]: FloatArray(384)       │
│    → Normalización L2               │
│    → queryEmbedding: [0.04, 0.05...]│
└─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────┐
│ 4. BÚSQUEDA SEMÁNTICA               │
│    kb_embeddings.npy (145 x 384)    │
│                                     │
│    Para cada embedding en KB:       │
│      score = cosine_similarity(     │
│        queryEmbedding,              │
│        kbEmbedding[i]               │
│      )                              │
│                                     │
│    Resultado:                       │
│    - "Control de plagas en papa"    │
│      score: 0.89 ✅                 │
│    - "Plagas de la papa"            │
│      score: 0.85 ✅                 │
│    - "Gusano blanco en papa"        │
│      score: 0.82 ✅                 │
└─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────┐
│ 5. RECUPERACIÓN DE CONTEXTO (RAG)   │
│    agrochat_knowledge_base.json     │
│                                     │
│    Top-3 contextos (score >= 0.4):  │
│                                     │
│    【1】 PLAGAS                     │
│    Tema: Control de plagas en papa  │
│    Info: Las plagas más comunes...  │
│                                     │
│    【2】 PLAGAS                     │
│    Tema: Plagas de la papa          │
│    Info: Las principales plagas...  │
│                                     │
│    【3】 CONTROL                    │
│    Tema: Gusano blanco en papa      │
│    Info: El gusano blanco se...     │
└─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────┐
│ 6. GENERACIÓN DE RESPUESTA (LLM)    │
│                                     │
│    ┌─────────────────────────────┐  │
│    │ ¿Hay internet + API Key?    │  │
│    └──────────┬──────────────────┘  │
│               │                     │
│       ┌───────┴───────┐             │
│       ▼               ▼             │
│    ┌──────┐       ┌──────┐          │
│    │ SÍ  │       │ NO   │          │
│    └──┬───┘       └──┬───┘          │
│       │              │              │
│       ▼              ▼              │
│  ┌─────────┐   ┌─────────────┐      │
│  │ Groq   │   │ llama.cpp   │      │
│  │ API    │   │ Local       │      │
│  │llama-  │   │ Llama-3.2-  │      │
│  │3.3-70b │   │ 1B-Q4       │      │
│  └────┬────┘   └──────┬──────┘      │
│       │               │             │
│       └───────┬───────┘             │
│               ▼                     │
│    Prompt enviado al LLM:           │
│    ┌────────────────────────────┐   │
│    │ <|system|>                 │   │
│    │ Eres AgroChat, asistente   │   │
│    │ agrícola. Usa SOLO la      │   │
│    │ información proporcionada. │   │
│    │                            │   │
│    │ Base de conocimiento:      │   │
│    │ 【1】 PLAGAS               │   │
│    │ Control de plagas en papa  │   │
│    │ Las plagas más comunes...  │   │
│    │ ...                        │   │
│    │                            │   │
│    │ <|user|>                   │   │
│    │ ¿Cómo controlar plagas...? │   │
│    │                            │   │
│    │ <|assistant|>              │   │
│    └────────────────────────────┘   │
└─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────┐
│ 7. POST-PROCESAMIENTO               │
│    - Limpiar tokens especiales      │
│    - Formatear respuesta            │
│    - cleanResponse()                │
└─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────┐
│ 8. SALIDA                           │
│    - Mostrar en chat UI             │
│    - (Opcional) TTS → Audio         │
│                                     │
│    "Las plagas más comunes de la    │
│    papa son: gusano blanco, polilla │
│    guatemalteca y gorgojo andino.   │
│    Para controlarlas, usa rotación  │
│    de cultivos, semillas certifica- │
│    das, trampas de feromonas..."    │
└─────────────────────────────────────┘
```

---

## 🧠 Sistema de Embeddings y MindSpore

### ¿Qué son los Embeddings?

Los embeddings son representaciones vectoriales de texto que capturan su significado semántico. Dos textos similares tendrán embeddings cercanos en el espacio vectorial.

### Modelo: paraphrase-multilingual-MiniLM-L12-v2

```
Características:
- Dimensiones: 384
- Multilingüe: Soporta español, inglés, y 50+ idiomas
- Tamaño: ~224MB (convertido a MindSpore)
- Entrada máxima: 128 tokens
```

### Proceso de Conversión del Modelo

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Pipeline de Conversión del Modelo                     │
└─────────────────────────────────────────────────────────────────────────┘

1. MODELO ORIGINAL (Hugging Face)
   sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
   │
   ▼
2. EXPORTAR A ONNX (Python)
   ┌─────────────────────────────────────────────┐
   │ torch.onnx.export(                          │
   │     model,                                  │
   │     (input_ids, attention_mask),            │
   │     "sentence_encoder.onnx",                │
   │     input_names=["input_ids", "attention_mask"],│
   │     output_names=["last_hidden_state"],     │
   │     opset_version=14                        │
   │ )                                           │
   └─────────────────────────────────────────────┘
   │
   ▼
3. CONVERTIR A MINDSPORE LITE
   ┌─────────────────────────────────────────────┐
   │ converter_lite                              │
   │   --fmk=ONNX                                │
   │   --modelFile=sentence_encoder.onnx         │
   │   --outputFile=sentence_encoder             │
   │   --optimize=general                        │
   │   --fp16=on                                 │
   └─────────────────────────────────────────────┘
   │
   ▼
4. RESULTADO: sentence_encoder.ms (224MB)
   - Optimizado para dispositivos móviles
   - 2 inputs: input_ids, attention_mask
   - 2 outputs: last_hidden_state, pooler_output (384 dims)
```

### Generación de Embeddings Pre-calculados

```python
# generate_mindspore_compatible_embeddings.py

# IMPORTANTE: Usar pooler_output, NO mean_pooling
# MindSpore devuelve pooler_output en output[1]

from transformers import AutoTokenizer, AutoModel
import torch
import numpy as np

tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
model = AutoModel.from_pretrained(MODEL_NAME)

def encode_with_pooler_output(texts):
    inputs = tokenizer(texts, padding="max_length", 
                       truncation=True, max_length=128,
                       return_tensors="pt")
    
    with torch.no_grad():
        outputs = model(**inputs)
        # Usar pooler_output (NO mean pooling)
        embeddings = outputs.pooler_output
        # Normalizar L2
        embeddings = F.normalize(embeddings, p=2, dim=1)
    
    return embeddings.numpy()

# Generar para todas las preguntas de la KB
embeddings = encode_with_pooler_output(all_questions)
np.save("kb_embeddings.npy", embeddings.astype(np.float32))
```

### Formato del archivo kb_embeddings.npy

```
Formato: NumPy NPY v1.0
Shape: (145, 384)
Dtype: float32
Tamaño: ~222KB

Estructura:
┌─────────────────────────────────────────────────────────────┐
│ Pregunta 0: "Hola"                                          │
│ Embedding: [0.0545, 0.0499, 0.0369, ..., 0.0123] (384 floats)│
├─────────────────────────────────────────────────────────────┤
│ Pregunta 1: "hola"                                          │
│ Embedding: [0.0312, 0.0488, 0.0501, ..., 0.0234] (384 floats)│
├─────────────────────────────────────────────────────────────┤
│ ...                                                         │
├─────────────────────────────────────────────────────────────┤
│ Pregunta 144: "muchas gracias"                              │
│ Embedding: [0.0678, 0.0345, 0.0123, ..., 0.0456] (384 floats)│
└─────────────────────────────────────────────────────────────┘
```

---

## 📚 Base de Conocimiento (Knowledge Base)

### Estructura de agrochat_knowledge_base.json

```json
{
  "version": "1.0",
  "entries": [
    {
      "id": 1,
      "category": "saludo",
      "questions": [
        "Hola",
        "hola",
        "Buenos días",
        "Buenas tardes"
      ],
      "answer": "¡Hola! Soy AgroChat, tu asistente agrícola...",
      "keywords": ["hola", "saludo", "buenos"]
    },
    {
      "id": 37,
      "category": "plagas",
      "questions": [
        "¿Cómo controlar plagas en papa?",
        "Plagas de la papa",
        "Gusano blanco en papa",
        "Polilla de la papa",
        "Control de plagas en papa",
        "Plagas en papa"
      ],
      "answer": "Las plagas de papa más comunes son: gusano blanco...",
      "keywords": ["plaga", "papa", "gusano", "polilla", "control"]
    }
    // ... más entradas
  ]
}
```

### Mapeo Embeddings ↔ Entradas

```
kb_embeddings.npy          agrochat_knowledge_base.json
┌───────────────┐          ┌─────────────────────────────┐
│ Index 0       │ ───────► │ Entry 1, Question 0: "Hola" │
│ Index 1       │ ───────► │ Entry 1, Question 1: "hola" │
│ Index 2       │ ───────► │ Entry 1, Question 2: "Buenos días"│
│ ...           │          │ ...                         │
│ Index 73      │ ───────► │ Entry 37, Question 0: "¿Cómo controlar plagas en papa?"│
│ Index 74      │ ───────► │ Entry 37, Question 1: "Plagas de la papa"│
│ ...           │          │ ...                         │
│ Index 144     │ ───────► │ Entry 45, Question N        │
└───────────────┘          └─────────────────────────────┘
```

---

## 🎤 Sistema de Voz

### Speech-to-Text (STT) con Vosk

```kotlin
// VoiceHelper.kt

class VoiceHelper(context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var model: Model? = null
    
    fun initialize() {
        // Cargar modelo desde assets/model-es-small/
        model = Model(modelPath)
        recognizer = SpeechRecognizer(model, 16000.0f)
    }
    
    fun startListening(onResult: (String) -> Unit) {
        // Iniciar grabación de audio
        audioRecord.startRecording()
        
        // Procesar audio en tiempo real
        while (isListening) {
            val buffer = ShortArray(4096)
            audioRecord.read(buffer, 0, buffer.size)
            
            if (recognizer.acceptWaveForm(buffer, buffer.size)) {
                val result = recognizer.result
                // Parsear JSON: {"text": "hola cómo estás"}
                onResult(parseResult(result))
            }
        }
    }
}
```

### Text-to-Speech (TTS)

```kotlin
// Usa TextToSpeech nativo de Android
private val tts = TextToSpeech(context) { status ->
    if (status == TextToSpeech.SUCCESS) {
        tts.language = Locale("es", "ES")
    }
}

fun speak(text: String) {
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
}
```

---

## 🤖 Sistema LLM

### LLM Online (Groq)

```kotlin
// GroqService.kt

class GroqService(private val apiKey: String) {
    private val client = OkHttpClient()
    private val baseUrl = "https://api.groq.com/openai/v1/chat/completions"
    
    suspend fun chat(messages: List<Message>): String {
        val request = ChatRequest(
            model = "llama-3.3-70b-versatile",
            messages = messages,
            temperature = 0.7,
            max_tokens = 512
        )
        
        // Llamada HTTP a la API
        val response = client.post(baseUrl, request)
        return response.choices[0].message.content
    }
}
```

### LLM Offline (llama.cpp)

```kotlin
// LlamaService.kt

class LlamaService(context: Context) {
    private var modelPtr: Long = 0
    private var contextPtr: Long = 0
    private var batchPtr: Long = 0
    
    // Cargar bibliotecas nativas
    init {
        System.loadLibrary("llama-android")
    }
    
    // Métodos nativos (JNI)
    private external fun loadModel(path: String, params: ModelParams): Long
    private external fun createContext(model: Long, params: ContextParams): Long
    private external fun tokenize(context: Long, text: String): IntArray
    private external fun decode(context: Long, batch: Long): Int
    private external fun getLogits(context: Long): FloatArray
    private external fun sampleToken(context: Long, params: SamplerParams): Int
    private external fun tokenToPiece(model: Long, token: Int): String
    
    fun generate(prompt: String, maxTokens: Int = 256): String {
        val tokens = tokenize(contextPtr, prompt)
        val result = StringBuilder()
        
        for (i in 0 until maxTokens) {
            // Decodificar tokens actuales
            decode(contextPtr, batchPtr)
            
            // Muestrear siguiente token
            val nextToken = sampleToken(contextPtr, samplerParams)
            
            // Verificar token de fin
            if (nextToken == eosToken) break
            
            // Convertir token a texto
            result.append(tokenToPiece(modelPtr, nextToken))
        }
        
        return result.toString()
    }
}
```

### Formato del Prompt para Llama

```
<|begin_of_text|><|start_header_id|>system<|end_header_id|>

Eres AgroChat, un asistente agrícola experto. Responde SOLO usando la información 
de la base de conocimiento proporcionada. Si no encuentras la respuesta, di que 
no tienes esa información.

Base de conocimiento agrícola relevante:

【1】 PLAGAS
Tema: Control de plagas en papa
Información: Las plagas de papa más comunes son: gusano blanco, polilla 
guatemalteca y gorgojo andino. Controla con rotación de cultivos, semillas 
certificadas, trampas de feromonas y Bacillus thuringiensis.

【2】 PLAGAS
Tema: Plagas de la papa
Información: Las principales plagas son: polilla de la papa, gorgojo, pulgones 
y gusano blanco. Usa rotación de cultivos, elimina rastrojos, aplica insecticidas 
biológicos.

---

Instrucciones:
- Responde en español
- Sé conciso pero informativo
- Si la pregunta no está relacionada con agricultura, indica amablemente tu especialidad<|eot_id|><|start_header_id|>user<|end_header_id|>

¿Cómo controlar plagas en papa?<|eot_id|><|start_header_id|>assistant<|end_header_id|>

```

---

## 🔧 Código Nativo (C++/JNI)

### MindSporeNetnative.cpp

```cpp
// Función principal para ejecutar el sentence encoder

extern "C" JNIEXPORT jfloatArray JNICALL
Java_edu_unicauca_app_agrochat_MindSporeHelper_runNetSentenceEncoder(
    JNIEnv *env, jobject thiz,
    jlong handle, jintArray jinput_ids, jintArray jattention_mask) {
    
    auto *p_env = reinterpret_cast<MSNativeEnvCpp *>(handle);
    std::shared_ptr<mindspore::Model> model = p_env->model;
    
    // 1. Obtener tensores de entrada
    std::vector<mindspore::MSTensor> inputs = model->GetInputs();
    // inputs[0] = input_ids [1, 128]
    // inputs[1] = attention_mask [1, 128]
    
    // 2. Copiar datos desde Java
    // ... (código de copia)
    
    // 3. Ejecutar inferencia
    std::vector<mindspore::MSTensor> outputs;
    model->Predict(inputs, &outputs);
    
    // 4. Seleccionar output correcto
    // outputs[0] = last_hidden_state [1, 128, 384]
    // outputs[1] = pooler_output [1, 384] ← ESTE ES EL QUE USAMOS
    
    const mindspore::MSTensor *best_output = nullptr;
    for (auto &out : outputs) {
        if (out.ElementNum() == 384) {  // Buscar el pooled embedding
            best_output = &out;
            break;
        }
    }
    
    // 5. Retornar como FloatArray
    jfloatArray result = env->NewFloatArray(384);
    env->SetFloatArrayRegion(result, 0, 384, output_data);
    return result;
}
```

---

## 📂 Archivos Clave y sus Funciones

| Archivo | Ubicación | Función |
|---------|-----------|---------|
| `MainActivity.kt` | `app/.../agrochat/` | UI principal, coordina todos los componentes |
| `SemanticSearchHelper.kt` | `app/.../mindspore/` | RAG: tokenización, embeddings, búsqueda |
| `MindSporeHelper.kt` | `app/.../mindspore/` | Wrapper Kotlin para JNI de MindSpore |
| `MindSporeNetnative.cpp` | `app/.../cpp/` | Código nativo para MindSpore Lite |
| `LlamaService.kt` | `app/.../llm/` | LLM offline con llama.cpp |
| `GroqService.kt` | `app/.../llm/` | Cliente API para Groq (online) |
| `VoiceHelper.kt` | `app/.../voice/` | STT (Vosk) y TTS (Android) |
| `sentence_encoder.ms` | `assets/` | Modelo MindSpore para embeddings |
| `kb_embeddings.npy` | `assets/` | Embeddings pre-calculados (145x384) |
| `agrochat_knowledge_base.json` | `assets/` | Base de conocimiento agrícola |
| `sentence_tokenizer.json` | `assets/` | Tokenizer para el modelo |
| `Llama-3.2-1B-Instruct-Q4_K_M.gguf` | `assets/` | Modelo LLM cuantizado |
| `model-es-small/` | `assets/` | Modelo Vosk para STT español |

---

## 🔄 Cómo Actualizar Componentes

### Agregar nuevas preguntas a la KB

1. Editar `app/src/main/assets/agrochat_knowledge_base.json`
2. Agregar nueva entrada o preguntas a entrada existente
3. Regenerar embeddings:

```bash
cd /path/to/project
source embedding_venv/bin/activate  # O crear venv con pip install transformers torch

python generate_mindspore_compatible_embeddings.py
```

4. Recompilar la app

### Cambiar el modelo de embeddings

1. Modificar `MODEL_NAME` en `generate_mindspore_compatible_embeddings.py`
2. Exportar nuevo modelo a ONNX (ver `nlp_dev/scripts/export_sentence_encoder.py`)
3. Convertir a MindSpore con `converter_lite`
4. Actualizar `EMBEDDING_DIM` en `SemanticSearchHelper.kt` si cambió
5. Regenerar `kb_embeddings.npy`

### Cambiar el LLM offline

1. Descargar nuevo modelo GGUF compatible con llama.cpp
2. Reemplazar archivo en `assets/`
3. Actualizar `MODEL_FILE` en `LlamaService.kt`
4. Ajustar parámetros si es necesario (context size, etc.)

---

## 🐛 Debugging

### Logs útiles

```bash
# Búsqueda semántica
adb logcat -s SemanticSearchHelper

# MindSpore nativo
adb logcat -s MSJNI_CPP_API

# LLM
adb logcat -s LlamaService llama-android

# Voz
adb logcat -s VoiceHelper Vosk
```

### Verificar embeddings

```python
import numpy as np

emb = np.load("kb_embeddings.npy")
print(f"Shape: {emb.shape}")  # Debe ser (N, 384)
print(f"Norm sample: {np.linalg.norm(emb[0])}")  # Debe ser ~1.0
```

---

## 📈 Métricas de Rendimiento

| Operación | Tiempo Típico | Dispositivo |
|-----------|---------------|-------------|
| Carga de MindSpore | ~3s | Redmi Note 12 |
| Tokenización | ~5ms | - |
| Embedding (MindSpore) | ~300ms | - |
| Búsqueda en 145 embeddings | ~30ms | - |
| Generación LLM (256 tokens) | ~15-30s | - |
| STT (Vosk) | Real-time | - |

---

## 🔗 Referencias

- [MindSpore Lite Documentation](https://www.mindspore.cn/lite/docs/en/master/index.html)
- [llama.cpp](https://github.com/ggerganov/llama.cpp)
- [Vosk Speech Recognition](https://alphacephei.com/vosk/)
- [Sentence Transformers](https://www.sbert.net/)
- [Groq API](https://console.groq.com/docs)
