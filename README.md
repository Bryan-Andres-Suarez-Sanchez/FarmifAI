# FarmifAI - AI Agricultural Assistant

**100% Offline AI Agricultural Assistant for Android**

Repository: [https://github.com/Bryan-Andres-Suarez-Sanchez/AgroChat_Project](https://github.com/Bryan-Andres-Suarez-Sanchez/AgroChat_Project)

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [System Requirements](#system-requirements)
- [Installation Guide](#installation-guide)
- [User Architecture](#user-architecture)
- [Training Architecture](#training-architecture)
- [Datasets](#datasets)
- [Key Code Snippets](#key-code-snippets)
- [Project Structure](#project-structure)
- [Updating the Knowledge Base](#updating-the-knowledge-base)
- [Performance Metrics](#performance-metrics)
- [License and Credits](#license-and-credits)

---

## Overview

FarmifAI is a mobile application designed for farmers in rural areas of Colombia, where internet connectivity is limited or non-existent. The application provides:

- **Voice and text agricultural assistance** without internet connection
- **Visual disease diagnosis** using the device camera
- **Local knowledge base** covering crops, pests, irrigation, and fertilization
- **Intelligent response generation** using local language models

The main goal is to democratize access to technical agricultural information for small-scale producers.

### Technology Stack

| Component | Technology | Model |
|-----------|------------|-------|
| STT (Speech-to-Text) | Vosk | `model-es-small` (~50MB) |
| TTS (Text-to-Speech) | Android TTS | System engine |
| Semantic Search | MindSpore Lite | `sentence_encoder.ms` (MiniLM, 384 dims) |
| Local LLM | llama.cpp | Llama 3.2 1B Instruct Q4_K_M (~750MB) |
| Vision | MindSpore Lite | `plant_disease_model.ms` (MobileNetV2) |

---

## Key Features

### 1. 100% Offline Operation

All inference happens on the mobile device. No internet connection required for core functionality.

### 2. Voice Interaction

Users can speak directly to the application:

\`\`\`
User Voice --> Vosk STT --> Text --> RAG --> LLM --> Response --> TTS
\`\`\`

- Offline voice recognition with Vosk (Spanish model)
- Voice synthesis with Android TTS engine
- Conversation mode: app automatically listens after responding

### 3. Visual Disease Diagnosis

Users can take a photo of a leaf and get immediate diagnosis.

**Supported crops (21 classes):**

| Crop | Classes | Diseases |
|------|---------|----------|
| Coffee | 4 | Cercospora, Leaf miner, Phoma, Rust |
| Corn | 4 | Gray spot, Common rust, Healthy, Northern blight |
| Potato | 3 | Healthy, Late blight, Early blight |
| Pepper | 2 | Bacterial spot, Healthy |
| Tomato | 8 | Bacterial spot, Target spot, Leaf mold, Healthy, Septoria, Late blight, Early blight, Mosaic virus |

Diagnosis integrates with the RAG system to offer treatments.

### 4. RAG System (Retrieval Augmented Generation)

The app uses semantic search to find relevant context before generating responses:

1. User question is converted to an embedding (384 dims)
2. Compared against pre-calculated knowledge base embeddings
3. Top-3 most similar contexts selected (cosine >= 0.4)
4. LLM generates response using that context

**Knowledge Base:**
- 134 entries
- 517 indexed questions
- Categories: cultivation, pests, fertilization, irrigation, planting, diagnosis, harvest

---

## System Requirements

### Development Environment

- Android Studio Hedgehog+ (2023.1.1+)
- JDK 17
- Android NDK 25.1.8937393
- CMake 3.22.1
- Git LFS
- Python 3.10+ (for embeddings generation)

### Target Device

- Android 8.0+ (API 26)
- Architecture: arm64-v8a (recommended)
- RAM: 4GB minimum (6GB recommended)
- Storage: ~1.2GB for models
- USB debugging enabled (for installation)

---

## Installation Guide

### Step 1: Clone the Repository

\`\`\`bash
# Clone repository
git clone https://github.com/Bryan-Andres-Suarez-Sanchez/AgroChat_Project.git
cd AgroChat_Project

# Install and pull large assets (Git LFS)
git lfs install
git lfs pull
\`\`\`

### Step 2: Checkout the Branch

For the latest version with visual diagnosis:

\`\`\`bash
git checkout demo
# or
git checkout feature/diagnostico-visual
\`\`\`

### Step 3: Setup Android Development Environment

1. **Install Android Studio:**
   - Download from [https://developer.android.com/studio](https://developer.android.com/studio)
   - During installation, ensure Android SDK and NDK are included

2. **Configure NDK and CMake:**
   \`\`\`bash
   # In Android Studio:
   # Tools → SDK Manager → SDK Tools tab
   # Install:
   # - NDK (Side by side) version 25.1.8937393
   # - CMake version 3.22.1
   \`\`\`

3. **Verify JDK 17:**
   \`\`\`bash
   java -version
   # Should show version 17.x.x
   \`\`\`

### Step 4: Build the APK

Using Gradle command line:

\`\`\`bash
# Make gradlew executable
chmod +x ./gradlew

# Build debug APK
./gradlew assembleDebug

# APK will be located at:
# app/build/outputs/apk/debug/FarmifAI-debug-v1.0-<timestamp>.apk
\`\`\`

Or using Android Studio:
- Open project in Android Studio
- Build → Build Bundle(s) / APK(s) → Build APK(s)

### Step 5: Prepare Device

1. **Enable Developer Options:**
   - Settings → About phone
   - Tap "Build number" 7 times

2. **Enable USB Debugging:**
   - Settings → System → Developer options
   - Enable "USB debugging"

3. **Connect device via USB**

4. **Verify connection:**
   \`\`\`bash
   adb devices
   # Should show your device
   \`\`\`

### Step 6: Install the Application

**Option A: Direct installation via ADB**

\`\`\`bash
# Install APK
./gradlew installDebug

# Or manually:
adb install app/build/outputs/apk/debug/FarmifAI-debug-v1.0-*.apk
\`\`\`

**Option B: Copy APK to device and install manually**

\`\`\`bash
# Copy APK to device
adb push app/build/outputs/apk/debug/FarmifAI-debug-v1.0-*.apk /sdcard/Download/

# Then on the device:
# 1. Open file manager
# 2. Navigate to Downloads
# 3. Tap the APK file
# 4. Allow installation from unknown sources if prompted
\`\`\`

### Step 7: Deploy LLM Model

The Llama model is not included in the repository due to its size. You need to deploy it separately.

**Option 1: Download and push manually**

\`\`\`bash
# 1. Download Llama 3.2 1B Instruct GGUF
# From: https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF
# File: Llama-3.2-1B-Instruct-Q4_K_M.gguf (~750MB)

# 2. Push to device using the provided script
./tools/push_llama_model_to_device.sh /path/to/Llama-3.2-1B-Instruct-Q4_K_M.gguf
\`\`\`

**Option 2: Let the app download it**

The app includes an auto-download feature:
1. Launch the app
2. Go to Settings
3. Tap "Download LLM Model"
4. Wait for download to complete (~750MB)

**Model location on device:**
\`\`\`
/sdcard/Android/data/edu.unicauca.app.agrochat/files/Llama-3.2-1B-Instruct-Q4_K_M.gguf
\`\`\`

### Step 8: Launch and Verify

\`\`\`bash
# Start the application
adb shell am start -n edu.unicauca.app.agrochat/.MainActivity

# Monitor logs
adb logcat -s MainActivity LlamaService SemanticSearchHelper PlantDiseaseClassifier VoiceHelper

# Check if model is loaded
adb logcat | grep "Model loaded successfully"
\`\`\`

### Step 9: Grant Permissions

On first launch, the app will request:
- Camera permission (for disease diagnosis)
- Microphone permission (for voice input)
- Storage permission (for model access)

Grant all permissions for full functionality.

---

## User Architecture

\`\`\`
+------------------------------------------------------------------+
|                        ANDROID DEVICE                             |
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
\`\`\`

**Device Storage:**

| Location | Content | Size |
|----------|---------|------|
| \`assets/\` | MindSpore models, KB, tokenizer, Vosk | ~300MB |
| \`/sdcard/Android/data/.../files/\` | Llama GGUF model | ~750MB |

---

## Training Architecture

### Vision Model Training Pipeline

\`\`\`
+------------------------------------------------------------------+
|                    TRAINING ENVIRONMENT                           |
|                   (Google Colab / Azure ML)                       |
|  +------------------------------------------------------------+  |
|  |                                                            |  |
|  |  +------------------+     +------------------+             |  |
|  |  | PlantVillage DS  | --> |                  |             |  |
|  |  | (38 classes)     |     |                  |             |  |
|  |  +------------------+     |   MobileNetV2    |             |  |
|  |                           |   Fine-tuning    |             |  |
|  |  +------------------+     |                  |             |  |
|  |  | Colombia Crops   | --> |  - Transfer L.   |             |  |
|  |  | (Coffee, etc.)   |     |  - 2 phases      |             |  |
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
\`\`\`

### Embeddings Generation Pipeline

\`\`\`
+------------------------------------------------------------------+
|                    LOCAL ENVIRONMENT (Python)                     |
|  +------------------------------------------------------------+  |
|  |                                                            |  |
|  |  +------------------+     +------------------+             |  |
|  |  | agrochat_kb.json | --> | Tokenizer        |             |  |
|  |  | (517 questions)  |     | (MiniLM)         |             |  |
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
\`\`\`

---

## Datasets

### Vision - Disease Classification

| Dataset | Classes | Images | Usage |
|---------|---------|--------|-------|
| PlantVillage | 38 | ~54,000 | Pre-training base |
| Colombia Crops | 21 | ~15,000 | Regional fine-tuning |
| Cassava | 5 | ~21,000 | Additional classes |

**Final model classes (colombia_v1.0):**

\`\`\`json
{
  "version": "colombia_v1.0",
  "num_classes": 21,
  "input_size": 224,
  "labels": [
    {"id": 0, "name": "Cafe___Cercospora", "crop": "Coffee"},
    {"id": 1, "name": "Cafe___Minador", "crop": "Coffee"},
    {"id": 2, "name": "Cafe___Phoma", "crop": "Coffee"},
    {"id": 3, "name": "Cafe___Roya", "crop": "Coffee"},
    ...
  ]
}
\`\`\`

### NLP - Knowledge Base

| File | Content | Format |
|------|---------|--------|
| \`agrochat_knowledge_base.json\` | 134 entries, 517 questions | JSON |
| \`kb_embeddings.npy\` | 517 embeddings of 384 dims | NumPy |
| \`sentence_tokenizer.json\` | MiniLM vocabulary | JSON |

### Voice - STT Model

| Model | Language | Size | Source |
|-------|----------|------|--------|
| \`model-es-small\` | Spanish | ~50MB | Vosk |

---

## Key Code Snippets

### Semantic Search (SemanticSearchHelper.kt)

\`\`\`kotlin
/**
 * Finds the top-K most relevant contexts for a question
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
\`\`\`

### Local LLM Service (LlamaService.kt)

\`\`\`kotlin
/**
 * Generates response using local model
 */
suspend fun generate(prompt: String): Flow<String> = llama.generate(prompt)

/**
 * Builds prompt in Llama 3.2 Instruct format
 */
fun buildPrompt(context: String, userQuery: String): String {
    val systemPrompt = """You are FarmifAI, an expert agricultural assistant.
Respond clearly and concisely in Spanish.
Use the information from the provided context."""
    
    return """<|begin_of_text|><|start_header_id|>system<|end_header_id|>

\$systemPrompt<|eot_id|><|start_header_id|>user<|end_header_id|>

Context: \$context

Question: \$userQuery<|eot_id|><|start_header_id|>assistant<|end_header_id|}"""
}
\`\`\`

### Disease Classifier (PlantDiseaseClassifier.kt)

\`\`\`kotlin
/**
 * Classifies a leaf image
 */
fun classify(bitmap: Bitmap): DiseaseResult? {
    // 1. Preprocess image to 224x224 RGB normalized
    val inputBuffer = preprocessImage(bitmap)
    
    // 2. Run MindSpore inference
    val outputBuffer = MindSporeHelper.runInference(modelHandle, inputBuffer)
    
    // 3. Get class with highest probability
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
        isHealthy = label.name.contains("Healthy")
    )
}
\`\`\`

---

## Project Structure

\`\`\`
AgroChat_Project/
├── app/
│   ├── src/main/
│   │   ├── java/edu/unicauca/app/agrochat/
│   │   │   ├── MainActivity.kt           # Main UI (Compose)
│   │   │   ├── llm/
│   │   │   │   ├── LlamaService.kt       # Local LLM (llama.cpp)
│   │   │   │   └── GroqService.kt        # Online LLM (optional)
│   │   │   ├── mindspore/
│   │   │   │   ├── SemanticSearchHelper.kt  # RAG + embeddings
│   │   │   │   └── MindSporeHelper.kt    # JNI wrapper
│   │   │   ├── vision/
│   │   │   │   ├── PlantDiseaseClassifier.kt  # Visual classification
│   │   │   │   └── CameraHelper.kt       # Camera capture
│   │   │   └── voice/
│   │   │       └── VoiceHelper.kt        # STT + TTS
│   │   └── assets/
│   │       ├── sentence_encoder.ms       # Embeddings model (224MB)
│   │       ├── sentence_tokenizer.json   # Tokenizer
│   │       ├── kb_embeddings.npy         # KB embeddings (517x384)
│   │       ├── agrochat_knowledge_base.json  # Knowledge base
│   │       ├── plant_disease_model.ms    # Vision model
│   │       ├── plant_disease_labels.json # 21 classes
│   │       └── model-es-small/           # Vosk STT
├── tools/
│   ├── training_script/
│   │   └── train_colombia.py             # Vision training
│   └── push_llama_model_to_device.sh     # GGUF model deployment
├── generate_mindspore_compatible_embeddings.py  # Generate embeddings
└── README.md
\`\`\`

---

## Updating the Knowledge Base

To add new questions or modify answers:

\`\`\`bash
# 1. Edit knowledge base
nano app/src/main/assets/agrochat_knowledge_base.json

# 2. Regenerate embeddings
python3 -m venv .venv
source .venv/bin/activate
pip install numpy torch transformers sentence-transformers

python generate_mindspore_compatible_embeddings.py

# 3. Rebuild and install
./gradlew installDebug
\`\`\`

---

## Performance Metrics

| Metric | Value |
|--------|-------|
| Semantic search precision (Top-1) | 94.4% |
| Semantic search precision (Top-3) | 98.1% |
| LLM inference time | 2-5 sec (150 tokens) |
| Image classification time | 200-400 ms |
| STT time | Real-time |
| APK size | ~70MB |
| Total size with models | ~1.2GB |

---

## Troubleshooting

### Model not loading

\`\`\`bash
# Check if model exists
adb shell ls -lh /sdcard/Android/data/edu.unicauca.app.agrochat/files/

# Verify file size (should be ~750MB)
adb shell du -h /sdcard/Android/data/edu.unicauca.app.agrochat/files/*.gguf

# Check logs
adb logcat | grep LlamaService
\`\`\`

### Camera not working

\`\`\`bash
# Verify camera permission
adb shell dumpsys package edu.unicauca.app.agrochat | grep permission

# Grant permission manually
adb shell pm grant edu.unicauca.app.agrochat android.permission.CAMERA
\`\`\`

### Voice recognition issues

\`\`\`bash
# Check microphone permission
adb shell pm grant edu.unicauca.app.agrochat android.permission.RECORD_AUDIO

# Verify Vosk model
adb shell ls -R /data/data/edu.unicauca.app.agrochat/cache/model-es-small/
\`\`\`

---

## License and Credits

- **MindSpore Lite**: Apache 2.0 (Huawei)
- **llama.cpp**: MIT License
- **Vosk**: Apache 2.0
- **PlantVillage Dataset**: CC BY-SA 4.0
- **Llama 3.2**: Meta AI License

---

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---

## Contact

For questions or support, please open an issue in the repository.

---

*Last updated: December 26, 2025*
