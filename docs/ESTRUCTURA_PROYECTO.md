# Estructura y flujo del proyecto AgroChat

Mapa de ubicacion rapido: `docs/MAPA_ARCHIVOS.md`
Desglose didactico de flujos y terminos: `docs/DESGLOSE_FLUJOS_EXPLICADO.md`

## 1) Rama mas actualizada

Consulta realizada con refs locales de Git (no fue posible ejecutar `git fetch --all --prune` por falta de credenciales en este entorno).

Orden por fecha de ultimo commit en rama:

| Rama | Commit | Fecha | Mensaje |
|---|---|---|---|
| `feature/diagnostico-visual` | `0e0e461` | 2025-12-15 10:33:38 -0500 | Consolidate docs, remove legacy files, keep only active assets |
| `Integracion-voz` | `e1ef0be` | 2025-12-02 09:47:14 -0500 | Add comprehensive documentation |
| `origin/C++API-Based` | `9752344` | 2025-09-01 22:48:44 -0500 | Initial implementation of Rust-based tokenizer |
| `origin/Java-API-Based` | `a85c335` | 2025-08-11 10:56:45 -0500 | model inference based on Java API |
| `master` | `5d171d0` | 2025-07-13 18:06:46 -0500 | .gitignore update |

Conclusion: la rama mas actualizada es `feature/diagnostico-visual`.

## 2) Vista general

AgroChat es una app Android (Jetpack Compose) que combina:

- Chat/voz con RAG local usando MindSpore Lite.
- LLM local offline (llama.cpp) y fallback/alternativa a LLM online (Groq).
- Diagnostico visual de enfermedades de plantas (modelo MindSpore).
- STT/TTS offline con Vosk + TextToSpeech de Android.

## 3) Flujo funcional principal

### 3.1 Flujo de pregunta en chat/voz

1. `MainActivity.kt` recibe texto (escrito o transcrito por `VoiceHelper.kt`).
2. Si hay internet + API key: intenta `GroqService.kt`.
3. Si Groq falla o no esta disponible:
   - `SemanticSearchHelper.kt` recupera Top-K contexto de KB.
   - Si Llama local esta activo/cargado: `LlamaService.kt` genera respuesta con contexto RAG.
   - Si no: responde con match semantico directo de KB.
4. `VoiceHelper.kt` sintetiza la respuesta por TTS (si aplica).

### 3.2 Flujo de diagnostico visual

1. `CameraHelper.kt` captura imagen (CameraX) o se toma desde galeria.
2. `PlantDiseaseClassifier.kt` preprocesa (224x224 RGB) y ejecuta inferencia con `MindSporeHelper.kt`.
3. Se muestra `DiseaseResult` en UI.
4. Si usuario pide tratamiento: `MainActivity.kt` convierte el diagnostico en query RAG y vuelve al flujo de chat.

### 3.3 Flujo nativo de inferencia MindSpore

1. Kotlin llama a `MindSporeHelper.kt`.
2. JNI en `MindSporeNetnative.cpp` carga modelo y ejecuta `Predict`.
3. Runtime usa librerias de `app/src/main/cpp/mindspore-lite-2.4.10-android-aarch64/`.

## 4) Estructura del repo (resumen)

```text
AgroChat_Project/
  app/                     # App Android
  gradle/                  # Wrapper/versiones Gradle
  nlp_dev/                 # Scripts NLP y datasets de trabajo
  tools/                   # Scripts de entrenamiento/export/conversion
  generate_embeddings.py
  generate_mindspore_compatible_embeddings.py
  README.md
```

## 5) Que hace cada archivo (inventario util)

Nota: el repo contiene cientos de archivos de terceros vendorizados (MindSpore headers/libs). Se detallan 100% los archivos funcionales propios y se agrupan dependencias externas para no mezclar codigo de negocio con vendor code.

### 5.1 Raiz y build

| Archivo | Funcion |
|---|---|
| `.gitattributes` | Configura Git LFS para `.ms`, `.aar`, `.gguf`. |
| `.gitignore` | Excluye builds, entornos, datasets y artefactos locales. |
| `README.md` | Guia de uso, build, assets y flujo de modelos. |
| `build.gradle.kts` | Build global de Gradle (plugins). |
| `settings.gradle.kts` | Configura modulos/repositorios (`:app`). |
| `gradle.properties` | Ajustes de memoria/threads de Gradle. |
| `gradle/libs.versions.toml` | Catalogo de versiones y dependencias. |
| `gradle/wrapper/gradle-wrapper.properties` | Version del wrapper (`gradle-8.13`). |
| `gradlew` | Wrapper Gradle para Unix. |
| `gradlew.bat` | Wrapper Gradle para Windows. |
| `.github/copilot-instructions.md` | Guias de arquitectura y convenciones para asistentes. |
| `generate_embeddings.py` | Regenera `kb_embeddings.npy` con sentence-transformers. |
| `generate_mindspore_compatible_embeddings.py` | Genera embeddings pooler/mean y mapping para compatibilidad MindSpore. |
| `icon_square.svg` | Asset grafico auxiliar. |
| `jhb.svg` | Asset grafico auxiliar. |

### 5.2 Modulo Android (`app/`)

| Archivo | Funcion |
|---|---|
| `app/build.gradle.kts` | Configuracion Android, Compose, CMake, dependencias (Vosk, CameraX, ONNX, etc.). |
| `app/CMakeLists.txt` | Build nativo JNI (`msjni`) + import de libs precompiladas. |
| `app/proguard-rules.pro` | Reglas ProGuard (actualmente base). |
| `app/src/main/AndroidManifest.xml` | Permisos (microfono, red, camara), actividad principal. |

### 5.3 Kotlin app (logica principal)

| Archivo | Funcion |
|---|---|
| `app/src/main/java/edu/unicauca/app/agrochat/MainActivity.kt` | Orquestador principal: UI Compose, estado global, inicializacion de voz/RAG/LLM/camara, decision de motor de respuesta. |
| `app/src/main/java/edu/unicauca/app/agrochat/llm/LlamaService.kt` | Servicio LLM local offline; autodetecta `.gguf`, carga modelo y genera respuestas con prompt RAG. |
| `app/src/main/java/edu/unicauca/app/agrochat/llm/GroqService.kt` | Cliente HTTP para Groq API (modo online). |
| `app/src/main/java/android/llama/cpp/LLamaAndroid.kt` | Wrapper JNI de llama.cpp; maneja carga de modelo, generacion streaming y cleanup nativo. |
| `app/src/main/java/edu/unicauca/app/agrochat/voice/VoiceHelper.kt` | STT offline (Vosk), TTS Android, callbacks de escucha/habla. |
| `app/src/main/java/edu/unicauca/app/agrochat/vision/CameraHelper.kt` | Integracion CameraX para preview/captura y conversion a `Bitmap`. |
| `app/src/main/java/edu/unicauca/app/agrochat/vision/PlantDiseaseClassifier.kt` | Clasificacion de enfermedad vegetal con MindSpore; preproceso, postproceso y score. |
| `app/src/main/java/edu/unicauca/app/agrochat/mindspore/SemanticSearchHelper.kt` | Carga KB+embeddings, calcula similitud (coseno/texto), recupera Top-K contexto para RAG. |
| `app/src/main/java/edu/unicauca/app/agrochat/mindspore/MindSporeHelper.kt` | Puente Kotlin-JNI para carga/inferencia/liberacion de modelos MindSpore. |
| `app/src/main/java/edu/unicauca/app/agrochat/mindspore/NativeTokenizerHelper.kt` | Clase `UniversalNativeTokenizer`; wrapper JNI para tokenizador nativo (`libhf_tokenizer_android.so`). |
| `app/src/main/java/edu/unicauca/app/agrochat/ui/theme/Color.kt` | Paleta de colores base del tema Compose. |
| `app/src/main/java/edu/unicauca/app/agrochat/ui/theme/Theme.kt` | Definicion de `MaterialTheme` para la app. |
| `app/src/main/java/edu/unicauca/app/agrochat/ui/theme/Type.kt` | Tipografia Compose. |

### 5.4 Nativo (C/C++)

| Archivo | Funcion |
|---|---|
| `app/src/main/cpp/MindSporeNetnative.cpp` | Implementacion JNI C++: `loadModel`, `runNetFloat`, `runNetIds`, `runNetSentenceEncoder`, `unloadModel`. |
| `app/src/main/cpp/MindSporeNetnative.h` | Placeholder vacio (no contiene declaracion activa). |
| `app/src/main/cpp/mindspore-lite-2.4.10-android-aarch64/` | Runtime y headers vendorizados de MindSpore Lite (tercero). |

### 5.5 Librerias nativas precompiladas

| Ruta | Funcion |
|---|---|
| `app/libs/mindspore-lite-full-2.6.0.aar` | AAR de MindSpore Lite. |
| `app/src/main/jniLibs/arm64-v8a/libllama-android.so` | Binding JNI llama.cpp en Android. |
| `app/src/main/jniLibs/arm64-v8a/libllama.so` | Runtime llama.cpp. |
| `app/src/main/jniLibs/arm64-v8a/libggml*.so` | Backend numerico de llama.cpp. |
| `app/src/main/jniLibs/arm64-v8a/libhf_tokenizer_android.so` | Tokenizador nativo (Rust/C). |
| `app/src/main/jniLibs/armeabi-v7a/libhf_tokenizer_android.so` | Variante ABI 32-bit del tokenizador. |

### 5.6 Assets de runtime

| Archivo / carpeta | Funcion |
|---|---|
| `app/src/main/assets/agrochat_knowledge_base.json` | Base de conocimiento para respuestas RAG. |
| `app/src/main/assets/kb_embeddings.npy` | Embeddings activos de preguntas KB. |
| `app/src/main/assets/kb_embeddings_pooler.npy` | Variante embeddings usando pooler output. |
| `app/src/main/assets/kb_embeddings_mean.npy` | Variante embeddings con mean pooling. |
| `app/src/main/assets/kb_embeddings_mapping.json` | Mapeo de preguntas -> entradas y metadata de embeddings. |
| `app/src/main/assets/sentence_encoder.ms` | Modelo de embeddings (MindSpore). |
| `app/src/main/assets/sentence_tokenizer.json` | Tokenizer para sentence encoder. |
| `app/src/main/assets/plant_disease_model.ms` | Modelo de diagnostico visual activo. |
| `app/src/main/assets/plant_disease_model_old.ms` | Modelo previo (legacy). |
| `app/src/main/assets/plant_disease_labels.json` | Etiquetas de clases para diagnostico visual. |
| `app/src/main/assets/model-es-small/` | Modelo Vosk STT offline en espanol (acustico, grafo, ivector, configs). |

### 5.7 Recursos Android

| Archivo / carpeta | Funcion |
|---|---|
| `app/src/main/res/values/strings.xml` | Strings globales (nombre app). |
| `app/src/main/res/values/colors.xml` | Colores XML clasicos. |
| `app/src/main/res/values/themes.xml` | Tema Android base. |
| `app/src/main/res/xml/backup_rules.xml` | Reglas de backup completo. |
| `app/src/main/res/xml/data_extraction_rules.xml` | Reglas Android 12+ para backup/data extraction. |
| `app/src/main/res/drawable/*` | Drawables del launcher. |
| `app/src/main/res/mipmap-*/*` | Iconos launcher por densidad. |

### 5.8 Tests

| Archivo | Funcion |
|---|---|
| `app/src/test/java/edu/unicauca/app/agrochat/ExampleUnitTest.kt` | Test unitario ejemplo. |
| `app/src/androidTest/java/edu/unicauca/app/agrochat/ExampleInstrumentedTest.kt` | Test instrumentado ejemplo. |

### 5.9 NLP dev (`nlp_dev/`)

| Archivo | Funcion |
|---|---|
| `nlp_dev/data/datasets/agrochat_knowledge_base.json` | Dataset KB base para proceso NLP. |
| `nlp_dev/data/datasets/agricultura_completo.json` | Dataset agricola extendido. |
| `nlp_dev/scripts/export_sentence_encoder.py` | Pipeline de export sentence encoder -> ONNX -> MindSpore + embeddings. |
| `nlp_dev/scripts/export_simple.py` | Export simplificado GPT-2 a ONNX y paquete Android. |
| `nlp_dev/scripts/generate_embeddings_onnx.py` | Genera embeddings KB con ONNX Runtime. |
| `nlp_dev/scripts/quick_train_simple.py` | Entrenamiento rapido de modelo causal de prueba. |

### 5.10 Herramientas de entrenamiento/inferencia (`tools/`)

| Archivo | Funcion |
|---|---|
| `tools/download_plant_model.py` | Construye modelo base MobileNetV2 y exporta ONNX; intenta convertir a MindSpore. |
| `tools/download_plantvillage_model.py` | Descarga modelo TFLite preentrenado PlantVillage desde URL publica. |
| `tools/export_trained_model.py` | Exporta checkpoint Keras entrenado a TFLite. |
| `tools/get_pretrained_model.py` | Descarga modelos preentrenados (ONNX/H5) desde fuentes externas. |
| `tools/prepare_plant_disease_model.py` | Orquestador integral: dataset, train, export ONNX, conversion MindSpore, labels. |
| `tools/train_local_full.py` | Entrenamiento completo TensorFlow ajustado para 8GB RAM. |
| `tools/train_plantvillage.py` | Entrenamiento ligero PlantVillage + export ONNX. |
| `tools/train_plantvillage_light.py` | Valida entorno ligero para entrenamiento. |
| `tools/push_llama_model_to_device.sh` | Copia modelo `.gguf` al storage de la app en Android via `adb`. |
| `tools/Train_Colombia_Colab.ipynb` | Notebook de entrenamiento para dataset Colombia. |
| `tools/Train_PlantVillage_Colab.ipynb` | Notebook de entrenamiento PlantVillage. |
| `tools/plant_disease_labels.json` | Labels auxiliares para entrenamiento/export. |
| `tools/real_apple_disease.jpg` | Imagen de ejemplo/prueba. |
| `tools/real_grape_disease.jpg` | Imagen de ejemplo/prueba. |
| `tools/real_potato_blight.jpg` | Imagen de ejemplo/prueba. |
| `tools/training_script/train_colombia.py` | Script de entrenamiento para Azure ML Job. |
| `tools/training_script/requirements.txt` | Dependencias pip para training script Azure. |
| `tools/training_script/conda.yaml` | Entorno conda para training script Azure. |

## 6) Dependencias vendorizadas (agrupadas)

Estas carpetas no se describen archivo por archivo porque son de terceros y muy voluminosas:

- `app/src/main/cpp/mindspore-lite-2.4.10-android-aarch64/` (runtime/libs/headers MindSpore).
- Librerias binarias en `app/src/main/jniLibs/`.
- Assets grandes de modelos en `app/src/main/assets/model-es-small/`.

## 7) Organizacion aplicada en esta tarea

Para mejorar comprension sin riesgo funcional:

1. Se agrego carpeta `docs/`.
2. Se creo este documento (`docs/ESTRUCTURA_PROYECTO.md`).
3. Se agrego un generador Python de arquitectura (`docs/generate_architecture_diagram.py`) para mantener el diagrama versionable y reproducible.

No se movio codigo productivo ni assets de runtime, para evitar romper rutas de carga en Android/JNI.

## 8) Diagrama de arquitectura

Ejecutar:

```bash
python3 docs/generate_architecture_diagram.py
```

Archivos generados:

- `docs/ARQUITECTURA.md` (Mermaid embebido).
- `docs/architecture.mmd` (fuente Mermaid).
- `docs/architecture.dot` (Graphviz DOT).
- `docs/architecture.svg` (si `dot` de Graphviz esta instalado).
