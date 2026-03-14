# Mapa de ubicacion de archivos (referenciados en la estructura)

Este mapa te dice en que carpeta esta cada archivo mencionado en `docs/ESTRUCTURA_PROYECTO.md`.

## 1) Vista rapida por carpetas

```text
AgroChat_Project/
├── .github/
├── app/
│   └── src/main/{java,cpp,assets,jniLibs,res}
├── docs/
├── gradle/
├── nlp_dev/
├── tools/
└── (archivos de build en raiz)
```

## 2) Archivos en la raiz del proyecto

| Archivo | Ruta |
|---|---|
| `.gitattributes` | `.gitattributes` |
| `.gitignore` | `.gitignore` |
| `README.md` | `README.md` |
| `build.gradle.kts` | `build.gradle.kts` |
| `settings.gradle.kts` | `settings.gradle.kts` |
| `gradle.properties` | `gradle.properties` |
| `gradlew` | `gradlew` |
| `gradlew.bat` | `gradlew.bat` |
| `generate_embeddings.py` | `generate_embeddings.py` |
| `generate_mindspore_compatible_embeddings.py` | `generate_mindspore_compatible_embeddings.py` |
| `icon_square.svg` | `icon_square.svg` |
| `jhb.svg` | `jhb.svg` |

## 3) Gradle y configuracion

| Archivo | Ruta |
|---|---|
| Catalogo de versiones | `gradle/libs.versions.toml` |
| Wrapper properties | `gradle/wrapper/gradle-wrapper.properties` |
| Instrucciones Copilot | `.github/copilot-instructions.md` |

## 4) App Android (modulo `app/`)

| Archivo | Ruta |
|---|---|
| Build del modulo | `app/build.gradle.kts` |
| CMake nativo | `app/CMakeLists.txt` |
| ProGuard | `app/proguard-rules.pro` |
| Manifest | `app/src/main/AndroidManifest.xml` |

### 4.1 Codigo Kotlin (main)

| Archivo | Ruta |
|---|---|
| Main activity | `app/src/main/java/edu/unicauca/app/agrochat/MainActivity.kt` |
| LLM local | `app/src/main/java/edu/unicauca/app/agrochat/llm/LlamaService.kt` |
| LLM online | `app/src/main/java/edu/unicauca/app/agrochat/llm/GroqService.kt` |
| Wrapper llama JNI | `app/src/main/java/android/llama/cpp/LLamaAndroid.kt` |
| Voz (STT/TTS) | `app/src/main/java/edu/unicauca/app/agrochat/voice/VoiceHelper.kt` |
| Camara | `app/src/main/java/edu/unicauca/app/agrochat/vision/CameraHelper.kt` |
| Diagnostico visual | `app/src/main/java/edu/unicauca/app/agrochat/vision/PlantDiseaseClassifier.kt` |
| MindSpore helper | `app/src/main/java/edu/unicauca/app/agrochat/mindspore/MindSporeHelper.kt` |
| Tokenizer nativo helper | `app/src/main/java/edu/unicauca/app/agrochat/mindspore/NativeTokenizerHelper.kt` |
| Busqueda semantica | `app/src/main/java/edu/unicauca/app/agrochat/mindspore/SemanticSearchHelper.kt` |
| Theme Color | `app/src/main/java/edu/unicauca/app/agrochat/ui/theme/Color.kt` |
| Theme | `app/src/main/java/edu/unicauca/app/agrochat/ui/theme/Theme.kt` |
| Typography | `app/src/main/java/edu/unicauca/app/agrochat/ui/theme/Type.kt` |

### 4.2 Nativo (C/C++)

| Archivo / carpeta | Ruta |
|---|---|
| JNI MindSpore C++ | `app/src/main/cpp/MindSporeNetnative.cpp` |
| Header (vacio) | `app/src/main/cpp/MindSporeNetnative.h` |
| Runtime vendorizado MindSpore | `app/src/main/cpp/mindspore-lite-2.4.10-android-aarch64/` |
| AAR MindSpore | `app/libs/mindspore-lite-full-2.6.0.aar` |

### 4.3 Assets (modelos y KB)

| Archivo / carpeta | Ruta |
|---|---|
| KB | `app/src/main/assets/agrochat_knowledge_base.json` |
| Embeddings activos | `app/src/main/assets/kb_embeddings.npy` |
| Mapping embeddings | `app/src/main/assets/kb_embeddings_mapping.json` |
| Embeddings mean | `app/src/main/assets/kb_embeddings_mean.npy` |
| Embeddings pooler | `app/src/main/assets/kb_embeddings_pooler.npy` |
| Sentence encoder | `app/src/main/assets/sentence_encoder.ms` |
| Sentence tokenizer | `app/src/main/assets/sentence_tokenizer.json` |
| Modelo diagnostico | `app/src/main/assets/plant_disease_model.ms` |
| Modelo diagnostico antiguo | `app/src/main/assets/plant_disease_model_old.ms` |
| Labels diagnostico | `app/src/main/assets/plant_disease_labels.json` |
| Modelo Vosk (carpeta) | `app/src/main/assets/model-es-small/` |

### 4.4 Librerias nativas (`jniLibs`)

| Archivo | Ruta |
|---|---|
| `libhf_tokenizer_android.so` (arm64) | `app/src/main/jniLibs/arm64-v8a/libhf_tokenizer_android.so` |
| `libhf_tokenizer_android.so` (armeabi-v7a) | `app/src/main/jniLibs/armeabi-v7a/libhf_tokenizer_android.so` |
| `libllama-android.so` | `app/src/main/jniLibs/arm64-v8a/libllama-android.so` |
| `libllama.so` | `app/src/main/jniLibs/arm64-v8a/libllama.so` |
| `libggml-base.so` | `app/src/main/jniLibs/arm64-v8a/libggml-base.so` |
| `libggml-cpu.so` | `app/src/main/jniLibs/arm64-v8a/libggml-cpu.so` |
| `libggml.so` | `app/src/main/jniLibs/arm64-v8a/libggml.so` |

### 4.5 Recursos Android (`res/`)

| Grupo | Ruta |
|---|---|
| Drawable launcher | `app/src/main/res/drawable/ic_launcher_background.xml` |
| Drawable launcher | `app/src/main/res/drawable/ic_launcher_foreground.xml` |
| Strings | `app/src/main/res/values/strings.xml` |
| Colors | `app/src/main/res/values/colors.xml` |
| Themes | `app/src/main/res/values/themes.xml` |
| Backup rules | `app/src/main/res/xml/backup_rules.xml` |
| Data extraction rules | `app/src/main/res/xml/data_extraction_rules.xml` |

Mipmap (expansion de `app/src/main/res/mipmap-*/*`):

- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `app/src/main/res/mipmap-hdpi/ic_launcher.png`
- `app/src/main/res/mipmap-hdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-mdpi/ic_launcher.png`
- `app/src/main/res/mipmap-mdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-xhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xhdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png`

### 4.6 Tests

| Archivo | Ruta |
|---|---|
| Test instrumentado | `app/src/androidTest/java/edu/unicauca/app/agrochat/ExampleInstrumentedTest.kt` |
| Test unitario | `app/src/test/java/edu/unicauca/app/agrochat/ExampleUnitTest.kt` |

## 5) NLP dev

| Archivo / carpeta | Ruta |
|---|---|
| Carpeta NLP | `nlp_dev/` |
| Dataset agricola completo | `nlp_dev/data/datasets/agricultura_completo.json` |
| Dataset KB | `nlp_dev/data/datasets/agrochat_knowledge_base.json` |
| Export sentence encoder | `nlp_dev/scripts/export_sentence_encoder.py` |
| Export simple | `nlp_dev/scripts/export_simple.py` |
| Embeddings ONNX | `nlp_dev/scripts/generate_embeddings_onnx.py` |
| Quick train | `nlp_dev/scripts/quick_train_simple.py` |

## 6) Tools (entrenamiento, export y utilidades)

| Archivo / carpeta | Ruta |
|---|---|
| Carpeta tools | `tools/` |
| Notebook Colombia | `tools/Train_Colombia_Colab.ipynb` |
| Notebook PlantVillage | `tools/Train_PlantVillage_Colab.ipynb` |
| Download plant model | `tools/download_plant_model.py` |
| Download plantvillage model | `tools/download_plantvillage_model.py` |
| Export trained model | `tools/export_trained_model.py` |
| Get pretrained model | `tools/get_pretrained_model.py` |
| Labels (tools) | `tools/plant_disease_labels.json` |
| Prepare model | `tools/prepare_plant_disease_model.py` |
| Push GGUF a Android | `tools/push_llama_model_to_device.sh` |
| Imagen prueba apple | `tools/real_apple_disease.jpg` |
| Imagen prueba grape | `tools/real_grape_disease.jpg` |
| Imagen prueba potato | `tools/real_potato_blight.jpg` |
| Train local full | `tools/train_local_full.py` |
| Train plantvillage | `tools/train_plantvillage.py` |
| Train plantvillage light | `tools/train_plantvillage_light.py` |
| Conda training script | `tools/training_script/conda.yaml` |
| Requirements training script | `tools/training_script/requirements.txt` |
| Entrenamiento Azure | `tools/training_script/train_colombia.py` |

## 7) Documentacion generada (`docs/`)

| Archivo | Ruta |
|---|---|
| Estructura del proyecto | `docs/ESTRUCTURA_PROYECTO.md` |
| Diagrama (markdown) | `docs/ARQUITECTURA.md` |
| Mermaid fuente | `docs/architecture.mmd` |
| Graphviz DOT | `docs/architecture.dot` |
| SVG del diagrama | `docs/architecture.svg` |
| Generador de diagrama | `docs/generate_architecture_diagram.py` |
| Este mapa | `docs/MAPA_ARCHIVOS.md` |

## 8) Ruta externa (no esta en el repo)

| Recurso | Ruta |
|---|---|
| Modelo `.gguf` en dispositivo Android | `/sdcard/Android/data/edu.unicauca.app.agrochat/files/` |

## 9) Comandos utiles para encontrar archivos rapido

```bash
# Buscar por nombre parcial
rg --files | rg 'MainActivity|LlamaService|SemanticSearchHelper'

# Ver solo rutas de assets
find app/src/main/assets -maxdepth 2 -type f | sort

# Ver solo rutas Kotlin
find app/src/main/java -type f | sort
```
