## English

## Demo

- Demo video: [pending]
- Latest downloadable APK (debug): [FarmifAI-debug-v1.0-20260324_201415.apk](https://github.com/Bryan-Andres-Suarez-Sanchez/FarmifAI/releases/download/apk-kb-records-20260324/FarmifAI-debug-v1.0-20260324_201415.apk)
- Releases page (all published APKs): [https://github.com/Bryan-Andres-Suarez-Sanchez/FarmifAI/releases/latest](https://github.com/Bryan-Andres-Suarez-Sanchez/FarmifAI/releases/latest)
- Note: paths under `app/build/outputs/apk/` are local build artifacts and are not versioned in git.
- Versioned field-validation file (scanned forms): [docs_backup/Encuestas_agricultores.pdf](docs_backup/Encuestas_agricultores.pdf)
- Versioned app screenshots:
  - [docs_backup/capturasapp/interfaz_conversacional.jpeg](docs_backup/capturasapp/interfaz_conversacional.jpeg)
  - [docs_backup/capturasapp/diagnosticovisual.png](docs_backup/capturasapp/diagnosticovisual.png)
  - [docs_backup/capturasapp/diagnosticovis.jpeg](docs_backup/capturasapp/diagnosticovis.jpeg)
  - [docs_backup/capturasapp/recomendaciones.jpeg](docs_backup/capturasapp/recomendaciones.jpeg)
- Technical report: [docs/FarmifAI_Informe_Avances_ajustado.docx](docs/FarmifAI_Informe_Avances_ajustado.docx)
- Visual resources:
  - [docs/images/fig_system_architecture.png](docs/images/fig_system_architecture.png)
  - [docs/images/fig_rag_pipeline.png](docs/images/fig_rag_pipeline.png)
  - [docs/images/fig_vision_inference.png](docs/images/fig_vision_inference.png)
  - [docs/images/fig_training_architecture.png](docs/images/fig_training_architecture.png)
  - [docs/images/fig_two_phase_training.png](docs/images/fig_two_phase_training.png)

## Quick Evaluation Path (under 5 minutes)

1. Build the debug APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```
2. Install it on a connected Android device:
   ```bash
   adb install -r app/build/outputs/apk/debug/*.apk
   ```
3. Confirm that a local GGUF model exists (recommended: `Qwen3.5-0.8B.Q4_K_M.gguf`).
4. Turn on airplane mode and open the app.
5. Test these queries:
   - `what is a weed`
   - `what are good agricultural practices`
   - `how to reduce coffee rust`

## Problem

The production chain of many crops in Colombia and around the world faces a critical gap
in local information for making timely agronomic decisions. The altitudinal and
microclimatic variability of territories makes it difficult for regional information to faithfully
represent the conditions of each crop, while limited rural connectivity
restricts continuous access to conventional digital tools. As a
result, crop management is often carried out without
sufficient support from data and crop-specific knowledge,
affecting the ability to prevent plant health risks, respond to climate events,
and optimize management practices.

Rural producers need technical recommendations in areas with intermittent connectivity. A cloud-dependent assistant may fail precisely at the moment it is needed in the field.

In this context, operating without a network and with local resources is not optional: it is a condition for real usefulness.

## Solution

FarmifAI is an Android app that integrates local retrieval (RAG), local generation (GGUF + llama.cpp), voice interaction, and on-device visual classification. The current branch is focused on maintaining the main assistance flow with local components.

## Key Features

- Agricultural queries in Spanish with local context.
- Local RAG over curated JSONL records.
- Local generation with GGUF variants of Qwen.
- Explicit response routing (`KB_DIRECT`, `LLM_WITH_KB`, `LLM_GENERAL`, `ABSTAIN`).
- Local visual diagnosis for 21 crop/disease classes.
- Voice support with offline STT (Vosk when provisioned) and Android TTS.

## System Architecture

![Architecture](docs/images/fig_system_architecture.png)

- UI layer: chat, voice, and camera.
- Orchestration layer: query processing, routing, and fallback.
- Data layer: local KB, embeddings, mappings, and labels.
- Inference layer: local semantic retrieval + local LLM + local vision.

## AI / Model Details

### Base Model

- Preferred local model name: `Qwen3.5-0.8B.Q4_K_M.gguf`.
- Additional preferences: Q5/Q4_0/Q3 variants and a fallback Qwen2.5 name.

### Quantization / Format

- LLM format: quantized GGUF variants.
- Embeddings: `kb_embeddings.npy`, shape `(2842, 384)`, type `float32`.

### On-Device Runtime

- LLM: `llama.cpp` via Android JNI.
- Vision: MindSpore Lite via JNI bridge.

### Knowledge Source

- Local records: `app/src/main/assets/kb_nueva/extract/*.jsonl`
- Local mapping: `app/src/main/assets/kb_embeddings_mapping.json`

### Intended Use

- Spanish-language agricultural assistance for field use.
- Technical support for crop, pest, and disease management.

### Out of Scope

- Does not replace the judgment of a certified agronomist in high-risk decisions.
- It is not a legal, medical, or regulatory authority.

### Known Limitations

- A local GGUF model must be provisioned for full generation.
- Latency depends on the device and the selected quantization.
- Evaluation artifacts prioritize reproducible evidence versioned in the repository.

### Evaluation Results (verifiable in repository)

| Metric | Value | Evidence |
|---|---|---|
| KB files | 12 JSONL | `app/src/main/assets/kb_nueva/extract` |
| KB records | 293 total | JSONL line count |
| Embeddings matrix | `(2842, 384)` | `app/src/main/assets/kb_embeddings.npy` |
| Embeddings type | `float32` | `app/src/main/assets/kb_embeddings.npy` |
| Routing tests | Available | `app/src/test/java/edu/unicauca/app/agrochat/routing/ResponseRoutingPolicyTest.kt` |
| Unit test files | 2 | `app/src/test` |

### Versioned Historical Evaluation Evidence (docs_backup)

- Visual model metrics documented in [docs_backup/FarmifAI_Paper_ES.tex](docs_backup/FarmifAI_Paper_ES.tex): Top-1 `92.3%`, Top-3 `98.1%`, inference `200-400ms` on mid-range Snapdragon 6xx devices.
- Component latency/memory table documented in [docs_backup/FarmifAI_Paper_ES.tex](docs_backup/FarmifAI_Paper_ES.tex).
- Deployment workflow documented in [docs_backup/fig_deployment_workflow.pdf](docs_backup/fig_deployment_workflow.pdf).
- References to ModelArts/OBS usage documented in [docs_backup/PRESENTATION_30S_SCRIPTS_EN_ES.txt](docs_backup/PRESENTATION_30S_SCRIPTS_EN_ES.txt).

## Offline-first Design

- The main assistance flow is oriented toward local KB + local inference.
- The current `AndroidManifest.xml` declares only microphone and camera permissions.
- No internet permission is declared in the app’s current manifest.

## Privacy, Security, and Permissions

- Main-flow processing happens on-device.
- Currently declared permissions:
  - `android.permission.RECORD_AUDIO` for voice input.
  - `android.permission.CAMERA` for visual diagnosis.
- Permission scope is limited to active functionalities.

## Installation

### Quick Validation

1. Build and install the debug APK.
2. Provision the local model file.
3. Run the quick evaluation path from this guide.

### For Developers

- Android Studio (recent stable version).
- JDK 11.
- Android SDK and NDK configured.

## Build from Source

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./scripts/build_apk.sh debug
```

## Repository Structure

```text
AgroChat_Project/
  app/           # Android app, local assets, and inference code
  docs/          # Technical report and documentation attachments
  docs/images/   # Architecture and pipeline visuals
  nlp_dev/       # NLP processing and experimentation tools
  tools/         # Vision training/export tools
  scripts/       # Build, installation, and deployment scripts
  pc_rag_clone/  # Experimental clone and llama.cpp vendor
  README.md
```

## License

The license file at the repository root is still pending definition.

## Citation

The citation metadata file (`CITATION.cff`) is still pending.

## Acknowledgments

- `llama.cpp` for the local inference runtime for GGUF.
- MindSpore Lite for on-device model execution.
- Vosk for local speech recognition support.
---

## Español

## Demo

- Video demo: [pendiente]
- Ultima APK descargable (debug): [FarmifAI-debug-v1.0-20260324_201415.apk](https://github.com/Bryan-Andres-Suarez-Sanchez/FarmifAI/releases/download/apk-kb-records-20260324/FarmifAI-debug-v1.0-20260324_201415.apk)
- Pagina de releases (todos los APK publicados): [https://github.com/Bryan-Andres-Suarez-Sanchez/FarmifAI/releases/latest](https://github.com/Bryan-Andres-Suarez-Sanchez/FarmifAI/releases/latest)
- Nota: las rutas bajo `app/build/outputs/apk/` son artefactos locales de compilacion y no se versionan en git.
- Archivo versionado de validacion en campo (formularios escaneados): [docs_backup/Encuestas_agricultores.pdf](docs_backup/Encuestas_agricultores.pdf)
- Capturas versionadas de uso de la app:
  - [docs_backup/capturasapp/interfaz_conversacional.jpeg](docs_backup/capturasapp/interfaz_conversacional.jpeg)
  - [docs_backup/capturasapp/diagnosticovisual.png](docs_backup/capturasapp/diagnosticovisual.png)
  - [docs_backup/capturasapp/diagnosticovis.jpeg](docs_backup/capturasapp/diagnosticovis.jpeg)
  - [docs_backup/capturasapp/recomendaciones.jpeg](docs_backup/capturasapp/recomendaciones.jpeg)
- Informe tecnico: [docs/FarmifAI_Informe_Avances_ajustado.docx](docs/FarmifAI_Informe_Avances_ajustado.docx)
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
3. Confirmar que existe un modelo GGUF local (recomendado: `Qwen3.5-0.8B.Q4_K_M.gguf`).
4. Activar modo avion y abrir la app.
5. Probar estas consultas:
   - `que es una arvense`
   - `que son las buenas practicas agricolas`
   - `como reducir la roya en cafe`

## Problema

La cadena productiva de muchos cultivos en Colombia y el mundo enfrentan una brecha crítica
de información local para la toma de decisiones agronómicas oportunas. La variabilidad
altitudinal y microclimática de los terrritorios dificultan que la información regional represente
fielmente las condiciones de cada cultivo, mientras que la limitada conectividad rural
restringe el acceso continuo a herramientas digitales convencionales. Como
consecuencia, el manejo de los cultivos suelen realizarse sin soporte
suficiente en datos y conocimiento fiel del cultivo, afectando la capacidad de prevenir
riesgos sanitarios, responder a eventos climáticos y optimizar prácticas de manejo.


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

- Nombre preferido de modelo local: `Qwen3.5-0.8B.Q4_K_M.gguf`.
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
- Apoyo tecnico  para manejo de cultivos, plagas y enfermedades.

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

### Evidencia Historica Versionada De Evaluacion (docs_backup)

- Metricas del modelo visual documentadas en [docs_backup/FarmifAI_Paper_ES.tex](docs_backup/FarmifAI_Paper_ES.tex): Top-1 `92.3%`, Top-3 `98.1%`, inferencia `200-400ms` en Snapdragon 6xx de gama media.
- Tabla de latencia/memoria por componente documentada en [docs_backup/FarmifAI_Paper_ES.tex](docs_backup/FarmifAI_Paper_ES.tex).
- Flujo de despliegue documentado en [docs_backup/fig_deployment_workflow.pdf](docs_backup/fig_deployment_workflow.pdf).
- Referencias de uso de ModelArts/OBS documentadas en [docs_backup/PRESENTATION_30S_SCRIPTS_EN_ES.txt](docs_backup/PRESENTATION_30S_SCRIPTS_EN_ES.txt).

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

### Validacion Rapida

1. Compilar e instalar APK debug.
2. Provisionar archivo de modelo local.
3. Ejecutar la ruta de evaluacion rapida de esta guia.

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



## Licencia

El archivo de licencia en la raiz del repositorio esta pendiente de definicion.

## Citacion

El archivo de metadatos de citacion (`CITATION.cff`) esta pendiente.

## Agradecimientos

- `llama.cpp` por el runtime de inferencia local para GGUF.
- MindSpore Lite por ejecucion de modelos en dispositivo.
- Vosk por soporte de reconocimiento de voz local.
