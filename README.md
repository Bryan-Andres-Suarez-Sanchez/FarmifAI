# FarmifAI (AgroChat Mobile)

![Android](https://img.shields.io/badge/Android-API%2024%2B-3DDC84)
![Mode](https://img.shields.io/badge/Mode-Offline--first-2E7D32)
![LLM](https://img.shields.io/badge/LLM-Qwen%203.5%20GGUF-455A64)
![KB](https://img.shields.io/badge/KB-293%20records-1565C0)

> Offline-first Android assistant for agriculture that uses a local LLM and local RAG to answer field questions in Spanish.

![System Architecture](docs/images/fig_system_architecture.png)

---

## English

## Demo

- Video demo: pending public upload.
- Latest APK (debug, generated): [app/build/outputs/apk/debug/FarmifAI-debug-v1.0-20260416_130249.apk](app/build/outputs/apk/debug/FarmifAI-debug-v1.0-20260416_130249.apk)
- Latest APK (release, generated): [app/build/outputs/apk/release/FarmifAI-release-v1.0-20260416_004157.apk](app/build/outputs/apk/release/FarmifAI-release-v1.0-20260416_004157.apk)
- Technical report: [docs/FarmifAI_Informe_Avances.docx](docs/FarmifAI_Informe_Avances.docx)
- Technical visuals:
  - [docs/images/fig_system_architecture.png](docs/images/fig_system_architecture.png)
  - [docs/images/fig_rag_pipeline.png](docs/images/fig_rag_pipeline.png)
  - [docs/images/fig_vision_inference.png](docs/images/fig_vision_inference.png)
  - [docs/images/fig_training_architecture.png](docs/images/fig_training_architecture.png)
  - [docs/images/fig_two_phase_training.png](docs/images/fig_two_phase_training.png)

## Quick Evaluation Path (under 5 minutes)

1. Build a debug APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```
2. Install on a connected Android device:
   ```bash
   adb install -r app/build/outputs/apk/debug/*.apk
   ```
3. Ensure a local GGUF model is provisioned (recommended: `Qwen3.5-0.8B-Q4_K_M.gguf`).
4. Enable airplane mode and open the app.
5. Try these prompts in Spanish:
   - `que es una arvense`
   - `que son las buenas practicas agricolas`
   - `como reducir la roya en cafe`

## Problem

Smallholder farmers often need practical recommendations in areas with unstable connectivity. Cloud-only assistants can fail exactly when decisions are needed in the field.

For agricultural support, reliability, low latency, and local data availability are critical. An offline-first design is therefore a functional requirement, not just an optimization.

## Solution

FarmifAI is an Android app that combines local retrieval (RAG), local generation (GGUF + llama.cpp), voice interaction, and on-device plant disease classification. The current branch is organized to keep core inference and guidance available on-device.

## Key Features

- Spanish agricultural Q&A with local context.
- Local RAG over curated JSONL knowledge records.
- Local LLM generation using Qwen GGUF variants.
- Explicit response routing (`KB_DIRECT`, `LLM_WITH_KB`, `LLM_GENERAL`, `ABSTAIN`).
- On-device vision diagnosis for 21 crop-disease classes.
- Voice interaction with offline STT support (Vosk when provisioned) and Android TTS.

## System Architecture

![Architecture](docs/images/fig_system_architecture.png)

- UI layer: chat, voice, and camera flows.
- Domain/orchestration layer: query processing, response routing, fallback behavior.
- Data layer: local KB records, embeddings, mappings, and labels.
- Inference layer: local semantic retrieval + local GGUF generation + local vision model.

## AI / Model Details

### Base Model

- Preferred local model filename: `Qwen3.5-0.8B-Q4_K_M.gguf`.
- Additional local filename preferences include Q5/Q4_0/Q3 variants and one Qwen2.5 fallback filename.

### Quantization / Format

- LLM format: GGUF quantized variants.
- Retrieval embeddings: `kb_embeddings.npy`, shape `(2842, 384)`, dtype `float32`.

### On-device Runtime

- LLM runtime: `llama.cpp` through Android JNI.
- Vision runtime: MindSpore Lite via JNI bridge.

### Knowledge Source

- Local records: `app/src/main/assets/kb_nueva/extract/*.jsonl`
- Local mapping: `app/src/main/assets/kb_embeddings_mapping.json`

### Intended Use

- Field-oriented agricultural guidance in Spanish.
- Practical first-pass assistance for crop management, pests, and diseases.

### Out-of-scope Use

- Not a replacement for certified agronomist judgment in high-risk decisions.
- Not a legal, medical, or regulatory authority.

### Known Limitations

- A local GGUF model must be provisioned for full local generation.
- Latency depends on device class and quantization choice.
- Evaluation artifacts currently prioritize reproducible repository evidence.

### Evaluation Results (Repository-verifiable)

| Metric | Value | Evidence |
|---|---|---|
| KB files | 12 JSONL | `app/src/main/assets/kb_nueva/extract` |
| KB records | 293 total | JSONL line count |
| Embedding matrix | `(2842, 384)` | `app/src/main/assets/kb_embeddings.npy` |
| Embedding dtype | `float32` | `app/src/main/assets/kb_embeddings.npy` |
| Routing tests | Present | `app/src/test/java/edu/unicauca/app/agrochat/routing/ResponseRoutingPolicyTest.kt` |
| Unit test files | 2 | `app/src/test` |

## Offline-first Design

- Core guidance path is designed around local KB + local inference components.
- Current `AndroidManifest.xml` declares only microphone and camera permissions.
- No internet permission is declared in the current app manifest.

## Privacy, Security and Permissions

- Data processing for core flow is local to device runtime.
- Permissions currently declared:
  - `android.permission.RECORD_AUDIO` for voice input.
  - `android.permission.CAMERA` for visual diagnosis.
- Permission scope is limited to enabled app features.

## Installation

### For Judges

1. Build and install the debug APK.
2. Provision local model file.
3. Run the quick evaluation path above.

### For Developers

- Android Studio (stable recent version).
- JDK 11.
- Android SDK and NDK configured.

## Build From Source

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./scripts/build_apk.sh debug
```

## Repository Structure

```text
AgroChat_Project/
  app/           # Android app, local assets, inference code
  docs/          # Technical report and documentation assets
  docs/images/   # Extracted architecture/pipeline visuals
  nlp_dev/       # NLP processing and experimentation tools
  tools/         # Vision training/export tools
  scripts/       # Build, install, and deployment scripts
  pc_rag_clone/  # Experimental clone and llama.cpp vendor
  README.md
```

## Limitations

- No tracked prebuilt APK or public demo video yet.
- License and citation metadata are not yet standardized at repository root.
- Device-level latency benchmark matrix is not yet published as a standalone dataset.

## Future Work

- Publish a short public demo video and reproducible benchmark sheet.
- Add packaged judge-ready installation artifacts.
- Add repository-level citation and security metadata files.

## License

License file at repository root is pending definition.

## Citation

Citation metadata file (`CITATION.cff`) is pending.

## Acknowledgements

- `llama.cpp` for local GGUF inference runtime.
- MindSpore Lite for on-device model execution.
- Vosk for local speech recognition support.

---

## Espanol

## Demo

- Video demo: pendiente de publicacion.
- Ultima APK (debug, generada): [app/build/outputs/apk/debug/FarmifAI-debug-v1.0-20260416_130249.apk](app/build/outputs/apk/debug/FarmifAI-debug-v1.0-20260416_130249.apk)
- Ultima APK (release, generada): [app/build/outputs/apk/release/FarmifAI-release-v1.0-20260416_004157.apk](app/build/outputs/apk/release/FarmifAI-release-v1.0-20260416_004157.apk)
- Informe tecnico: [docs/FarmifAI_Informe_Avances.docx](docs/FarmifAI_Informe_Avances.docx)
- Recursos visuales:
  - [docs/images/fig_system_architecture.png](docs/images/fig_system_architecture.png)
  - [docs/images/fig_rag_pipeline.png](docs/images/fig_rag_pipeline.png)
  - [docs/images/fig_vision_inference.png](docs/images/fig_vision_inference.png)
  - [docs/images/fig_training_architecture.png](docs/images/fig_training_architecture.png)
  - [docs/images/fig_two_phase_training.png](docs/images/fig_two_phase_training.png)

## Ruta De Evaluacion Rapida (menos de 5 minutos)

1. Compilar APK debug:
   ```bash
   ./gradlew :app:assembleDebug
   ```
2. Instalar en un dispositivo Android conectado:
   ```bash
   adb install -r app/build/outputs/apk/debug/*.apk
   ```
3. Confirmar que existe un modelo GGUF local (recomendado: `Qwen3.5-0.8B-Q4_K_M.gguf`).
4. Activar modo avion y abrir la app.
5. Probar estas consultas:
   - `que es una arvense`
   - `que son las buenas practicas agricolas`
   - `como reducir la roya en cafe`

## Problema

Los productores rurales necesitan recomendaciones tecnicas en zonas con conectividad intermitente. Un asistente dependiente de nube puede fallar justo en el momento de uso en campo.

En este contexto, operar sin red y con recursos locales no es opcional: es una condicion de utilidad real.

## Solucion

FarmifAI es una app Android que integra recuperacion local (RAG), generacion local (GGUF + llama.cpp), interaccion por voz y clasificacion visual en dispositivo. La rama actual esta orientada a mantener el flujo principal de ayuda con componentes locales.

## Funcionalidades Clave

- Consultas agricolas en espanol con contexto local.
- RAG local sobre registros JSONL curados.
- Generacion local con variantes GGUF de Qwen.
- Enrutamiento explicito de respuesta (`KB_DIRECT`, `LLM_WITH_KB`, `LLM_GENERAL`, `ABSTAIN`).
- Diagnostico visual local para 21 clases de cultivo/enfermedad.
- Voz con soporte STT offline (Vosk cuando esta provisionado) y TTS de Android.

## Arquitectura Del Sistema

![Arquitectura](docs/images/fig_system_architecture.png)

- Capa UI: chat, voz y camara.
- Capa de orquestacion: procesamiento de consulta, enrutamiento y fallback.
- Capa de datos: KB local, embeddings, mapeos y etiquetas.
- Capa de inferencia: recuperacion semantica local + LLM local + vision local.

## Detalles De IA / Modelo

### Modelo Base

- Nombre preferido de modelo local: `Qwen3.5-0.8B-Q4_K_M.gguf`.
- Preferencias adicionales: variantes Q5/Q4_0/Q3 y un nombre fallback de Qwen2.5.

### Cuantizacion / Formato

- Formato LLM: variantes GGUF cuantizadas.
- Embeddings: `kb_embeddings.npy`, forma `(2842, 384)`, tipo `float32`.

### Runtime En Dispositivo

- LLM: `llama.cpp` via JNI Android.
- Vision: MindSpore Lite via puente JNI.

### Fuente De Conocimiento

- Registros locales: `app/src/main/assets/kb_nueva/extract/*.jsonl`
- Mapeo local: `app/src/main/assets/kb_embeddings_mapping.json`

### Uso Previsto

- Asistencia agricola en espanol para uso en campo.
- Apoyo tecnico inicial para manejo de cultivos, plagas y enfermedades.

### Fuera De Alcance

- No reemplaza el criterio de un agronomo certificado en decisiones de alto riesgo.
- No es autoridad legal, medica ni regulatoria.

### Limitaciones Conocidas

- Se requiere provisionar modelo GGUF local para generacion completa.
- La latencia depende del dispositivo y de la cuantizacion elegida.
- Los artefactos de evaluacion priorizan evidencia reproducible versionada en repo.

### Resultados De Evaluacion (verificables en repositorio)

| Metrica | Valor | Evidencia |
|---|---|---|
| Archivos de KB | 12 JSONL | `app/src/main/assets/kb_nueva/extract` |
| Registros de KB | 293 total | Conteo de lineas JSONL |
| Matriz de embeddings | `(2842, 384)` | `app/src/main/assets/kb_embeddings.npy` |
| Tipo de embeddings | `float32` | `app/src/main/assets/kb_embeddings.npy` |
| Pruebas de enrutamiento | Disponibles | `app/src/test/java/edu/unicauca/app/agrochat/routing/ResponseRoutingPolicyTest.kt` |
| Archivos de pruebas unitarias | 2 | `app/src/test` |

## Diseno Offline-first

- El flujo principal de asistencia esta orientado a KB local + inferencia local.
- El `AndroidManifest.xml` actual declara solo permisos de microfono y camara.
- No se declara permiso de internet en el manifest actual de la app.

## Privacidad, Seguridad Y Permisos

- El procesamiento del flujo principal ocurre en dispositivo.
- Permisos declarados actualmente:
  - `android.permission.RECORD_AUDIO` para entrada por voz.
  - `android.permission.CAMERA` para diagnostico visual.
- El alcance de permisos se limita a funcionalidades activas.

## Instalacion

### Para Jurados

1. Compilar e instalar APK debug.
2. Provisionar archivo de modelo local.
3. Ejecutar la ruta de evaluacion rapida.

### Para Desarrolladores

- Android Studio (estable reciente).
- JDK 11.
- Android SDK y NDK configurados.

## Compilar Desde Codigo Fuente

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./scripts/build_apk.sh debug
```

## Estructura Del Repositorio

```text
AgroChat_Project/
  app/           # App Android, assets locales y codigo de inferencia
  docs/          # Informe tecnico y anexos de documentacion
  docs/images/   # Visuales de arquitectura y pipelines
  nlp_dev/       # Herramientas de procesamiento y experimentacion NLP
  tools/         # Herramientas de entrenamiento/exportacion de vision
  scripts/       # Scripts de build, instalacion y despliegue
  pc_rag_clone/  # Clon experimental y vendor llama.cpp
  README.md
```

## Limitaciones

- Aun no hay APK precompilado versionado ni video demo publico.
- Licencia y metadatos de citacion aun no estandarizados en la raiz del repo.
- Falta publicar una matriz completa de benchmarks por dispositivo.

## Trabajo Futuro

- Publicar video demo corto y hoja de benchmarks reproducibles.
- Publicar artefactos listos para evaluacion de jurados.
- Agregar archivos de metadatos de citacion y seguridad a nivel raiz.

## Licencia

El archivo de licencia en la raiz del repositorio esta pendiente de definicion.

## Citacion

El archivo de metadatos de citacion (`CITATION.cff`) esta pendiente.

## Agradecimientos

- `llama.cpp` por el runtime de inferencia local para GGUF.
- MindSpore Lite por ejecucion de modelos en dispositivo.
- Vosk por soporte de reconocimiento de voz local.
