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

## Model Implementation, Training, Inference & Deployment

### 1. Vision Model (Plant Disease Classification)

#### Implementation

The vision model uses **MobileNetV2** as the backbone architecture:

- **Base Model**: MobileNetV2 pre-trained on ImageNet
- **Input**: 224x224x3 RGB images
- **Output**: 21-class softmax probabilities
- **Framework**: TensorFlow/Keras (training) → MindSpore Lite (inference)

**Architecture modifications:**

\`\`\`python
# Base model with frozen initial layers
base_model = MobileNetV2(input_shape=(224, 224, 3), 
                         include_top=False, 
                         weights='imagenet')

# Custom classification head
x = GlobalAveragePooling2D()(base_model.output)
x = BatchNormalization()(x)
x = Dropout(0.4)(x)
x = Dense(512, activation='relu', kernel_regularizer=l2(0.01))(x)
x = Dropout(0.4)(x)
output = Dense(NUM_CLASSES, activation='softmax')(x)

model = Model(inputs=base_model.input, outputs=output)
\`\`\`

#### Training

**Two-phase training approach:**

**Phase 1: Feature Extractor Training (25 epochs)**
\`\`\`python
# Freeze base model layers
for layer in base_model.layers:
    layer.trainable = False

# Train only classification head
optimizer = Adam(learning_rate=1e-3)
model.compile(
    optimizer=optimizer,
    loss='categorical_crossentropy',
    metrics=['accuracy', 'top_3_accuracy']
)

# Data augmentation
train_datagen = ImageDataGenerator(
    rescale=1./255,
    rotation_range=40,
    width_shift_range=0.3,
    height_shift_range=0.3,
    shear_range=0.2,
    zoom_range=0.3,
    horizontal_flip=True,
    vertical_flip=True,
    brightness_range=[0.6, 1.4],
    fill_mode='reflect'
)
\`\`\`

**Phase 2: Fine-tuning (35 epochs)**
\`\`\`python
# Unfreeze all layers for fine-tuning
for layer in base_model.layers:
    layer.trainable = True

# Lower learning rate for fine-tuning
optimizer = Adam(learning_rate=1e-5)
model.compile(
    optimizer=optimizer,
    loss=CategoricalCrossentropy(label_smoothing=0.1),
    metrics=['accuracy']
)
\`\`\`

**Training script:**
\`\`\`bash
# Train on Google Colab or Azure ML
python tools/training_script/train_colombia.py

# Output: SavedModel format
# Location: ./outputs/colombia_v1.0/
\`\`\`

#### Inference

**On-device inference pipeline:**

1. **Image Preprocessing:**
   \`\`\`kotlin
   fun preprocessImage(bitmap: Bitmap): ByteBuffer {
       val scaled = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
       val buffer = ByteBuffer.allocateDirect(224 * 224 * 3 * 4)
       buffer.order(ByteOrder.nativeOrder())
       
       for (y in 0 until 224) {
           for (x in 0 until 224) {
               val pixel = scaled.getPixel(x, y)
               // Normalize to [-1, 1]
               buffer.putFloat(((pixel shr 16 and 0xFF) - 127.5f) / 127.5f)
               buffer.putFloat(((pixel shr 8 and 0xFF) - 127.5f) / 127.5f)
               buffer.putFloat(((pixel and 0xFF) - 127.5f) / 127.5f)
           }
       }
       return buffer
   }
   \`\`\`

2. **MindSpore Lite Inference:**
   \`\`\`kotlin
   // Load model
   modelHandle = MindSporeHelper.loadModel(context, MODEL_FILE)
   
   // Run inference
   val outputBuffer = MindSporeHelper.runInference(modelHandle, inputBuffer)
   
   // Post-process
   val probabilities = softmax(outputBuffer)
   val topClass = probabilities.indices.maxByOrNull { probabilities[it] }
   \`\`\`

**Inference time:** 200-400ms on mid-range devices (Snapdragon 6xx series)

#### Deployment

**Step 1: Convert TensorFlow to MindSpore**

\`\`\`bash
# Using MindSpore Converter
python tools/export_trained_model.py \\
  --model_path outputs/colombia_v1.0/ \\
  --output plant_disease_model.ms \\
  --framework TF

# Verify conversion
python verify_mindspore_model.py plant_disease_model.ms
\`\`\`

**Step 2: Deploy to App Assets**

\`\`\`bash
# Copy model and labels
cp plant_disease_model.ms app/src/main/assets/
cp plant_disease_labels.json app/src/main/assets/

# Commit with Git LFS
git lfs track "*.ms"
git add plant_disease_model.ms
git commit -m "chore: update vision model"
\`\`\`

**Step 3: Version Control**

\`\`\`json
// plant_disease_labels.json includes version tracking
{
  "version": "colombia_v1.0",
  "date": "2025-12-09",
  "num_classes": 21,
  "input_size": 224,
  "labels": [...]
}
\`\`\`

---

### 2. NLP Model (Semantic Search & RAG)

#### Implementation

**Sentence Encoder Model:**

- **Architecture**: paraphrase-multilingual-MiniLM-L12-v2
- **Input**: Text sequences (max 128 tokens)
- **Output**: 384-dimensional embeddings
- **Pooling**: Mean pooling over token embeddings

\`\`\`python
from transformers import AutoTokenizer, AutoModel

MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
model = AutoModel.from_pretrained(MODEL_NAME)
\`\`\`

#### Training

The sentence encoder is **pre-trained** (not trained from scratch). We use it for:

1. **Knowledge Base Embedding Generation** (offline):
   \`\`\`python
   def encode_questions(questions):
       inputs = tokenizer(
           questions,
           padding="max_length",
           truncation=True,
           max_length=128,
           return_tensors="pt"
       )
       
       with torch.no_grad():
           outputs = model(**inputs)
           
           # Mean pooling
           attention_mask = inputs['attention_mask']
           token_embeddings = outputs.last_hidden_state
           input_mask_expanded = attention_mask.unsqueeze(-1).expand(
               token_embeddings.size()
           ).float()
           
           sum_embeddings = torch.sum(token_embeddings * input_mask_expanded, 1)
           sum_mask = torch.clamp(input_mask_expanded.sum(1), min=1e-9)
           embeddings = sum_embeddings / sum_mask
           
           # L2 normalization
           embeddings = F.normalize(embeddings, p=2, dim=1)
           
       return embeddings.cpu().numpy()
   \`\`\`

2. **Generate embeddings for 517 questions**:
   \`\`\`bash
   python generate_mindspore_compatible_embeddings.py
   
   # Output: kb_embeddings.npy (517 x 384 float32)
   \`\`\`

#### Inference

**Runtime semantic search:**

1. **Tokenize user query**:
   \`\`\`kotlin
   val inputIds = tokenizer.encode(userQuery, maxLength = 128)
   val attentionMask = IntArray(128) { if (it < inputIds.size) 1 else 0 }
   \`\`\`

2. **Generate query embedding**:
   \`\`\`kotlin
   val embedding = MindSporeHelper.predictSentenceEncoder(
       modelHandle, 
       inputIds, 
       attentionMask
   )
   // Output: FloatArray(384) normalized
   \`\`\`

3. **Cosine similarity search**:
   \`\`\`kotlin
   fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
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
   
   val topResults = kbEmbeddings.mapIndexed { i, emb ->
       i to cosineSimilarity(queryEmbedding, emb)
   }.filter { it.second >= 0.4f }
    .sortedByDescending { it.second }
    .take(3)
   \`\`\`

**Inference time:** 50-150ms per query

#### Deployment

**Step 1: Export encoder to MindSpore**

\`\`\`python
# Export sentence encoder
from mindspore import export, context
import mindspore.nn as nn

# Convert PyTorch model to MindSpore
# (or use pre-converted model)
export(model, inputs, file_name="sentence_encoder", file_format="MINDIR")
\`\`\`

**Step 2: Generate and package embeddings**

\`\`\`bash
# Generate embeddings
python generate_mindspore_compatible_embeddings.py

# Verify dimensions
python - <<EOF
import numpy as np
emb = np.load('app/src/main/assets/kb_embeddings.npy')
print(f"Shape: {emb.shape}")  # Should be (517, 384)
print(f"Dtype: {emb.dtype}")  # Should be float32
EOF

# Copy to assets
cp kb_embeddings.npy app/src/main/assets/
cp sentence_encoder.ms app/src/main/assets/
cp sentence_tokenizer.json app/src/main/assets/
\`\`\`

---

### 3. LLM Model (Local Language Model)

#### Implementation

**Architecture**: Llama 3.2 1B Instruct

- **Quantization**: Q4_K_M (4-bit quantization)
- **Context length**: 128K tokens (limited to 2048 in practice)
- **Vocabulary size**: 128,256
- **Parameters**: ~1B (quantized to ~750MB)

**Inference engine**: llama.cpp (Android JNI bindings)

\`\`\`cpp
// Native inference (simplified)
struct llama_context * ctx = llama_new_context_with_model(model, ctx_params);

// Tokenize
std::vector<llama_token> tokens = llama_tokenize(ctx, prompt, true);

// Generate
for (int i = 0; i < max_tokens; i++) {
    llama_token next = llama_sample_token(ctx, tokens);
    if (next == llama_token_eos(model)) break;
    tokens.push_back(next);
    
    // Stream to Kotlin
    std::string token_str = llama_token_to_piece(ctx, next);
    callback(token_str);
}
\`\`\`

#### Training

**Note**: We use a **pre-trained and pre-quantized** model. No training is performed.

- **Base model**: Meta Llama 3.2 1B Instruct
- **Quantization**: Performed by community (bartowski on HuggingFace)
- **Format**: GGUF (GPT-Generated Unified Format)

For custom fine-tuning (advanced):
\`\`\`bash
# Fine-tune on agricultural data (requires GPU cluster)
python -m llama_recipes.finetuning \\
  --model_name meta-llama/Llama-3.2-1B-Instruct \\
  --dataset agricultural_qa \\
  --batch_size 4 \\
  --num_epochs 3 \\
  --use_peft \\
  --peft_method lora

# Convert to GGUF
python convert-hf-to-gguf.py ./fine_tuned_model

# Quantize
./quantize ./model.gguf ./model_q4.gguf Q4_K_M
\`\`\`

#### Inference

**Prompt construction:**

\`\`\`kotlin
fun buildPrompt(context: String, query: String): String {
    return """<|begin_of_text|><|start_header_id|>system<|end_header_id|>

You are FarmifAI, an expert agricultural assistant.
Respond clearly in Spanish using the provided context.<|eot_id|><|start_header_id|>user<|end_header_id|>

Context: $context

Question: $query<|eot_id|><|start_header_id|>assistant<|end_header_id|>

"""
}
\`\`\`

**Streaming generation:**

\`\`\`kotlin
suspend fun generate(prompt: String, maxTokens: Int = 150): Flow<String> {
    return llama.generate(prompt).map { token ->
        // Filter control tokens
        token.replace("<|eot_id|>", "").trim()
    }.takeWhile { !it.contains("<|end") }
}
\`\`\`

**Inference time:** 2-5 seconds for 150 tokens (~30 tokens/sec on mid-range devices)

#### Deployment

**Step 1: Download model**

\`\`\`bash
# Download from HuggingFace
wget https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf

# Verify integrity
sha256sum Llama-3.2-1B-Instruct-Q4_K_M.gguf
\`\`\`

**Step 2: Deploy to device**

\`\`\`bash
# Option A: Manual push
adb push Llama-3.2-1B-Instruct-Q4_K_M.gguf \\
  /sdcard/Android/data/edu.unicauca.app.agrochat/files/

# Option B: Use deployment script
./tools/push_llama_model_to_device.sh Llama-3.2-1B-Instruct-Q4_K_M.gguf

# Option C: In-app download
# Users can download directly from the app settings
\`\`\`

**Step 3: Verify deployment**

\`\`\`bash
# Check file size
adb shell ls -lh /sdcard/Android/data/edu.unicauca.app.agrochat/files/*.gguf

# Should show ~750MB file

# Test loading
adb logcat -s LlamaService | grep "Model loaded"
\`\`\`

---

### 4. Voice Models (STT/TTS)

#### Implementation - Speech-to-Text (Vosk)

- **Architecture**: Kaldi-based neural network
- **Model**: Lightweight Spanish model (~50MB)
- **Sampling rate**: 16kHz
- **Output format**: JSON with text and confidence

#### Implementation - Text-to-Speech

- **Engine**: Android system TTS
- **Language**: Spanish (es-ES or es-MX)
- **Fallback**: Generic Spanish if regional variant unavailable

#### Inference

\`\`\`kotlin
// STT Setup
val recognizer = Recognizer(voskModel, 16000.0f)
val speechService = SpeechService(recognizer, 16000.0f)

speechService.startListening(object : RecognitionListener {
    override fun onResult(hypothesis: String?) {
        val json = JSONObject(hypothesis)
        val text = json.getString("text")
        onTranscription(text)
    }
})

// TTS Setup
tts = TextToSpeech(context) { status ->
    if (status == TextToSpeech.SUCCESS) {
        tts?.setLanguage(Locale("es", "ES"))
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_id")
    }
}
\`\`\`

#### Deployment

\`\`\`bash
# Vosk model is included in assets
cp -r model-es-small/ app/src/main/assets/

# Git LFS tracking
git lfs track "app/src/main/assets/model-es-small/**"
git add app/src/main/assets/model-es-small/
git commit -m "chore: add Vosk Spanish model"
\`\`\`

---

### Model Update Workflow

**When updating any model:**

1. **Train/convert new model**
2. **Validate performance** (accuracy, inference time, size)
3. **Update version in labels/metadata**
4. **Test on representative devices**
5. **Commit with Git LFS**
6. **Update documentation**
7. **Tag release** with version number

\`\`\`bash
# Example workflow
git checkout -b model/vision-v1.1
python train_model.py
python convert_to_mindspore.py
python validate_model.py
cp new_model.ms app/src/main/assets/plant_disease_model.ms
git add -f app/src/main/assets/plant_disease_model.ms
git commit -m "feat: vision model v1.1 - improved coffee detection"
git tag -a v1.1-vision -m "Vision model v1.1"
git push origin model/vision-v1.1 --tags
\`\`\`

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
