# GitHub Copilot Instructions for ARApp

Este archivo contiene instrucciones para GitHub Copilot sobre cómo trabajar con este proyecto.

## Resumen del Proyecto

ARApp es una aplicación Android de asistente agrícola con IA completamente offline. Usa:
- **MindSpore Lite** para embeddings semánticos
- **llama.cpp** para LLM local (Llama 3.2 1B)
- **Vosk** para reconocimiento de voz offline
- **Jetpack Compose** para la UI

## Arquitectura Principal

```
Usuario → Voz/Texto → Tokenización → MindSpore Embedding → 
Búsqueda Coseno → RAG Context → LLM → Respuesta → TTS
```

## Archivos Clave

### UI y Coordinación
- `MainActivity.kt` - UI principal, coordina voice/rag/llm

### Sistema RAG (Retrieval Augmented Generation)
- `SemanticSearchHelper.kt` - Tokenización, embeddings, búsqueda semántica
- `MindSporeHelper.kt` - Wrapper JNI para MindSpore
- `MindSporeNetnative.cpp` - Código C++ nativo

### LLM
- `LlamaService.kt` - LLM offline con llama.cpp
- `GroqService.kt` - LLM online (Groq API)

### Voz
- `VoiceHelper.kt` - STT (Vosk) y TTS (Android)

### Assets
- `sentence_encoder.ms` - Modelo MindSpore (224MB)
- `kb_embeddings.npy` - Embeddings pre-calculados (145x384 float32)
- `agrochat_knowledge_base.json` - Base de conocimiento
- `sentence_tokenizer.json` - Tokenizer

## Reglas Importantes

### Embeddings
- El modelo usa **pooler_output** (384 dims), NO mean pooling
- Los embeddings deben normalizarse con L2 norm
- Formato NPY: (N, 384) float32

### MindSpore
- Inputs: `input_ids` [1, 128] int64, `attention_mask` [1, 128] int64
- Outputs: `last_hidden_state` [1, 128, 384], `pooler_output` [1, 384]
- Usar `output[1]` (pooler_output) para embeddings

### Base de Conocimiento
- Cada entrada tiene: id, category, questions[], answer, keywords[]
- Los embeddings se mapean a las questions en orden secuencial
- Múltiples questions pueden apuntar a la misma respuesta

### LLM Prompt Format (Llama 3.2)
```
<|begin_of_text|><|start_header_id|>system<|end_header_id|>

{system_prompt}<|eot_id|><|start_header_id|>user<|end_header_id|>

{user_message}<|eot_id|><|start_header_id|>assistant<|end_header_id|>
```

### Búsqueda Semántica
- Threshold mínimo: 0.4 (score coseno)
- Top-K: 3 contextos
- Fallback: similitud de texto si MindSpore falla

## Cómo Hacer Cambios Comunes

### Agregar nueva pregunta a KB
1. Editar `agrochat_knowledge_base.json`
2. Ejecutar `python generate_mindspore_compatible_embeddings.py`
3. Recompilar app

### Modificar el prompt del LLM
1. Editar `buildSystemPrompt()` en `SemanticSearchHelper.kt`
2. O modificar `LlamaService.kt` para formato de prompt

### Cambiar threshold de similitud
1. Modificar `minScore` en `findTopKContexts()` de `SemanticSearchHelper.kt`

### Depurar embeddings
```kotlin
Log.d(TAG, "Embedding: ${embedding.take(8).joinToString()}")
```

## Patrones de Código

### Llamar a MindSpore
```kotlin
val inputIds = tokenize(text)
val attentionMask = IntArray(128) { if (it < inputIds.size) 1 else 0 }
val embedding = mindSporeHelper.predictSentenceEncoder(modelHandle, inputIds, attentionMask)
```

### Búsqueda semántica
```kotlin
val queryEmbedding = computeEmbedding(userQuery)
val results = kbEmbeddings.mapIndexed { i, emb ->
    i to cosineSimilarity(queryEmbedding, emb)
}.filter { it.second >= minScore }
 .sortedByDescending { it.second }
 .take(topK)
```

### Generar con LLM
```kotlin
val context = semanticSearch.findTopKContexts(query)
val prompt = buildPrompt(context.combinedContext, query)
val response = llamaService.generate(prompt, maxTokens = 256)
```

## Testing

```bash
# Logs de búsqueda semántica
adb logcat -s SemanticSearchHelper | grep "Búsqueda"

# Logs de MindSpore
adb logcat -s MSJNI_CPP_API

# Logs de LLM
adb logcat -s LlamaService llama-android
```

## Dependencias Clave

- MindSpore Lite 2.6.0 (AAR en libs/)
- llama.cpp (libllama-android.so en jniLibs/)
- Vosk 0.3.47
- OkHttp 4.12.0
- Jetpack Compose BOM 2024.04.01
