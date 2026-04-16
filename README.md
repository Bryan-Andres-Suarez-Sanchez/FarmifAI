# FarmifAI

## English

FarmifAI is an Android field assistant for agriculture. It combines conversational support, local knowledge retrieval, local GGUF generation, and on-device plant disease diagnosis.

The consolidated technical report is maintained in:

[docs/FarmifAI_Informe_Avances.docx](docs/FarmifAI_Informe_Avances.docx)

### Project Status

- Main branch: `master`.
- Runtime mode: on-device and local-first in the current app flow.
- Target generative model family: Qwen 3.5 GGUF.
- LLM runtime: `llama.cpp` through Android JNI.
- Retrieval: local KB + 384-d embeddings, with lexical local fallback.
- Vision: MindSpore Lite classifier for plant diseases.
- Feedback: local JSONL persistence in app private storage.

### Core Capabilities

- Agricultural chat with local context in Spanish.
- RAG over local files in `app/src/main/assets/kb_nueva/extract`.
- Explicit reasoning separation for `<think>...</think>` traces.
- Routing policy with `KB_DIRECT`, `LLM_WITH_KB`, `LLM_GENERAL`, and `ABSTAIN`.
- Plant disease diagnosis with 21 classes across coffee, corn, potato, pepper, and tomato.
- Voice input using Vosk when local voice model is provisioned.
- TTS output via Android system engine.

### Verified Evidence Snapshot

| Component | Verified evidence |
|---|---|
| Knowledge base | 12 JSONL files, 293 agronomic records |
| Embeddings | `kb_embeddings.npy`, shape `(2842, 384)`, `float32` |
| Retrieval mapping | `kb_embeddings_mapping.json` |
| Local LLM | `LlamaService.kt` prefers `Qwen3.5-0.8B-Q4_K_M.gguf` |
| Reasoning handling | `MainActivity.kt` separates `<think>` and final answer |
| Vision | `plant_disease_labels.json` declares 21 classes and `plant_disease_model_old.ms` is loaded from assets |
| Routing tests | `app/src/test/java/edu/unicauca/app/agrochat/routing/ResponseRoutingPolicyTest.kt` |

### Technical Report Evidence Quality

The technical report includes embedded figures (inside the DOCX) and keeps a code-to-evidence narrative. Current embedded evidence set:

- System architecture
- Local RAG pipeline
- Visual inference pipeline
- Training architecture
- Two-phase training flow

### Requirements

- Android Studio (recent stable).
- JDK 11.
- Android SDK and NDK configured.
- Android device or emulator API 24+.
- Local GGUF model compatible with Qwen 3.5.

### Build

Build debug APK:

```bash
./gradlew :app:assembleDebug
```

Run unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Helper script:

```bash
./scripts/build_apk.sh debug
```

### Local Model Provisioning

- Copy a preferred GGUF model to the app external private folder. Recommended filename: `Qwen3.5-0.8B-Q4_K_M.gguf`.
- For optional semantic encoder acceleration, provision `sentence_encoder.ms` and `sentence_tokenizer.json` under `files/models`.
- If encoder is not available, lexical fallback retrieval remains active.
- For offline STT with Vosk, provision `model-es-small` locally.

### Repository Structure

```text
AgroChat_Project/
  app/           # Android app, local assets, inference code
  docs/          # Consolidated project technical report
  nlp_dev/       # NLP processing and experimentation tools
  tools/         # Vision training/export tools
  scripts/       # Build, install, and deployment scripts
  pc_rag_clone/  # Experimental clone and llama.cpp vendor
  README.md
```

### Scope And Limitations

FarmifAI is intended for preliminary and educational agricultural guidance. It does not replace certified agronomist judgment for high-risk decisions. Response coverage depends on local KB content and provisioned on-device models.

## Espanol

FarmifAI es una aplicacion Android de asistencia agricola para trabajo en campo. Integra consulta conversacional, recuperacion aumentada por conocimiento local, generacion GGUF y diagnostico visual en dispositivo.

El informe tecnico consolidado del proyecto se mantiene en:

[docs/FarmifAI_Informe_Avances.docx](docs/FarmifAI_Informe_Avances.docx)

### Estado Del Proyecto

- Rama principal: `master`.
- Modo de ejecucion: local en dispositivo y enfoque local-first en el flujo actual.
- Modelo generativo objetivo: Qwen 3.5 en GGUF.
- Runtime LLM: `llama.cpp` via JNI Android.
- Recuperacion: KB local + embeddings de 384 dimensiones con fallback lexical.
- Vision: clasificador MindSpore Lite para enfermedades.
- Feedback: persistencia local en JSONL.

### Capacidades Principales

- Chat agricola con contexto local.
- RAG sobre `app/src/main/assets/kb_nueva/extract`.
- Separacion de razonamiento `<think>` y respuesta final.
- Politica de enrutamiento `KB_DIRECT`, `LLM_WITH_KB`, `LLM_GENERAL`, `ABSTAIN`.
- Diagnostico visual con 21 clases para cafe, maiz, papa, pimiento y tomate.
- Entrada por voz con Vosk y salida por TTS del sistema.

### Resumen De Evidencia Verificada

| Componente | Evidencia verificada |
|---|---|
| Base de conocimiento | 12 archivos JSONL, 293 registros agronomicos |
| Embeddings | `kb_embeddings.npy`, forma `(2842, 384)`, `float32` |
| Mapeo de recuperacion | `kb_embeddings_mapping.json` |
| LLM local | `LlamaService.kt` prioriza `Qwen3.5-0.8B-Q4_K_M.gguf` |
| Manejo de razonamiento | `MainActivity.kt` separa `<think>` y respuesta final |
| Vision | `plant_disease_labels.json` declara 21 clases y `plant_disease_model_old.ms` se carga desde assets |
| Pruebas de enrutamiento | `app/src/test/java/edu/unicauca/app/agrochat/routing/ResponseRoutingPolicyTest.kt` |

### Calidad De Evidencia Del Informe Tecnico

El informe tecnico incluye figuras incrustadas (dentro del DOCX) y mantiene una narrativa trazable entre codigo y evidencia. El conjunto actual de evidencias graficas incluye:

- Arquitectura del sistema
- Pipeline RAG local
- Pipeline de inferencia visual
- Arquitectura de entrenamiento
- Flujo de entrenamiento en dos fases

### Requisitos

- Android Studio (estable reciente).
- JDK 11.
- Android SDK y NDK configurados.
- Dispositivo Android o emulador API 24+.
- Modelo GGUF local compatible con Qwen 3.5.

### Compilacion

Compilar APK debug:

```bash
./gradlew :app:assembleDebug
```

Ejecutar pruebas unitarias:

```bash
./gradlew :app:testDebugUnitTest
```

Script auxiliar:

```bash
./scripts/build_apk.sh debug
```

### Provisionamiento Local De Modelos

- Copiar un modelo GGUF preferido a la carpeta externa privada de la app. Nombre recomendado: `Qwen3.5-0.8B-Q4_K_M.gguf`.
- Para aceleracion opcional del encoder semantico, provisionar `sentence_encoder.ms` y `sentence_tokenizer.json` en `files/models`.
- Si el encoder no esta disponible, el fallback lexical sigue activo.
- Para STT offline con Vosk, provisionar `model-es-small` de forma local.

### Estructura Del Repositorio

```text
AgroChat_Project/
  app/           # App Android, assets locales y codigo de inferencia
  docs/          # Informe tecnico consolidado del proyecto
  nlp_dev/       # Herramientas de procesamiento y experimentacion NLP
  tools/         # Herramientas de entrenamiento/exportacion de vision
  scripts/       # Scripts de build, instalacion y despliegue local
  pc_rag_clone/  # Clon de experimentacion y vendor llama.cpp
  README.md
```

### Alcance Y Limitaciones

FarmifAI esta orientado a asistencia agricola preliminar y educativa. No reemplaza el criterio de un agronomo certificado en decisiones de alto riesgo. La cobertura de respuestas depende del contenido de la KB local y de los modelos provisionados en el dispositivo.

### Referencia Tecnica

Para detalles completos de arquitectura, evidencia y estado de avance:

[docs/FarmifAI_Informe_Avances.docx](docs/FarmifAI_Informe_Avances.docx)
