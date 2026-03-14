# FarmifAI - Technical Documentation

**Complete Technical Guide for Model Implementation, Training, Inference & Deployment**

Repository: [https://github.com/Bryan-Andres-Suarez-Sanchez/AgroChat_Project](https://github.com/Bryan-Andres-Suarez-Sanchez/AgroChat_Project)

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Vision Model - Plant Disease Classification](#1-vision-model---plant-disease-classification)
3. [NLP Model - Semantic Search & RAG](#2-nlp-model---semantic-search--rag)
4. [LLM Model - Local Language Model](#3-llm-model---local-language-model)
5. [Voice Models - STT/TTS](#4-voice-models---stttts)
6. [Model Update Workflow](#model-update-workflow)
7. [Performance Optimization](#performance-optimization)
8. [Troubleshooting](#troubleshooting)

---

## Architecture Overview

### System Architecture

```
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
```

### Training Architecture

```
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
```

---

## 1. Vision Model - Plant Disease Classification

### 1.1 Implementation

The vision model uses **MobileNetV2** as the backbone architecture:

**Specifications:**
- **Base Model**: MobileNetV2 pre-trained on ImageNet
- **Input**: 224x224x3 RGB images
- **Output**: 21-class softmax probabilities
- **Framework**: TensorFlow/Keras (training) → MindSpore Lite (inference)
- **Model Size**: ~14MB (quantized)
- **Inference Time**: 200-400ms (Snapdragon 6xx)

**Architecture Modifications:**

```python
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.layers import Dense, GlobalAveragePooling2D, Dropout, BatchNormalization
from tensorflow.keras.models import Model
from tensorflow.keras.regularizers import l2

# Base model with frozen initial layers
base_model = MobileNetV2(
    input_shape=(224, 224, 3), 
    include_top=False, 
    weights='imagenet'
)

# Custom classification head
x = GlobalAveragePooling2D()(base_model.output)
x = BatchNormalization()(x)
x = Dropout(0.4)(x)
x = Dense(512, activation='relu', kernel_regularizer=l2(0.01))(x)
x = Dropout(0.4)(x)
output = Dense(NUM_CLASSES, activation='softmax')(x)

model = Model(inputs=base_model.input, outputs=output)
```

**Supported Classes (21 total):**

| Crop | Classes | Disease Names |
|------|---------|---------------|
| Coffee (Café) | 4 | Cercospora, Leaf miner, Phoma, Rust |
| Corn (Maíz) | 4 | Gray spot, Common rust, Healthy, Northern blight |
| Potato (Papa) | 3 | Healthy, Late blight, Early blight |
| Pepper (Pimiento) | 2 | Bacterial spot, Healthy |
| Tomato (Tomate) | 8 | Bacterial spot, Target spot, Leaf mold, Healthy, Septoria, Late blight, Early blight, Mosaic virus |

### 1.2 Training

**Two-Phase Training Approach:**

#### Phase 1: Feature Extractor Training (25 epochs)

Trains only the classification head while freezing the base model.

```python
# Freeze base model layers
for layer in base_model.layers:
    layer.trainable = False

# Configure optimizer and loss
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
    fill_mode='reflect',
    validation_split=0.15
)

# Training
history = model.fit(
    train_generator,
    epochs=25,
    validation_data=val_generator,
    callbacks=[
        EarlyStopping(patience=5, restore_best_weights=True),
        ReduceLROnPlateau(factor=0.5, patience=3)
    ]
)
```

#### Phase 2: Fine-Tuning (35 epochs)

Unfreezes all layers and fine-tunes with lower learning rate.

```python
# Unfreeze all layers
for layer in base_model.layers:
    layer.trainable = True

# Lower learning rate for fine-tuning
optimizer = Adam(learning_rate=1e-5)
model.compile(
    optimizer=optimizer,
    loss=CategoricalCrossentropy(label_smoothing=0.1),
    metrics=['accuracy', 'top_3_accuracy']
)

# Continue training
history_fine = model.fit(
    train_generator,
    epochs=35,
    validation_data=val_generator,
    callbacks=[
        EarlyStopping(patience=7, restore_best_weights=True),
        ModelCheckpoint('best_model.h5', save_best_only=True)
    ]
)
```

**Training Configuration:**

```python
CONFIG = {
    'IMG_SIZE': 224,
    'BATCH_SIZE': 64,  # GPU / 32 CPU
    'EPOCHS_PHASE1': 25,
    'EPOCHS_PHASE2': 35,
    'LEARNING_RATE_1': 1e-3,
    'LEARNING_RATE_2': 1e-5,
    'LABEL_SMOOTHING': 0.1,
    'DROPOUT_RATE': 0.4,
    'L2_REG': 0.01,
}
```

**Dataset Preparation:**

```bash
# Training script (Google Colab or Azure ML)
cd tools/training_script/
python train_colombia.py

# Expected structure:
# colombia_crops_dataset/
#   Cafe___Cercospora/
#   Cafe___Minador/
#   ...
#   Tomate___Virus_mosaico/

# Output: SavedModel format
# Location: ./outputs/colombia_v1.0/
```

### 1.3 Inference

**On-Device Inference Pipeline:**

#### Step 1: Image Preprocessing

```kotlin
fun preprocessImage(bitmap: Bitmap): ByteBuffer {
    // Scale to model input size
    val scaled = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
    
    // Allocate buffer (224 * 224 * 3 * 4 bytes for float32)
    val buffer = ByteBuffer.allocateDirect(224 * 224 * 3 * 4)
    buffer.order(ByteOrder.nativeOrder())
    
    // Normalize pixels to [-1, 1]
    for (y in 0 until 224) {
        for (x in 0 until 224) {
            val pixel = scaled.getPixel(x, y)
            
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)
            
            // Apply normalization
            buffer.putFloat((r - 127.5f) / 127.5f)
            buffer.putFloat((g - 127.5f) / 127.5f)
            buffer.putFloat((b - 127.5f) / 127.5f)
        }
    }
    
    return buffer
}
```

#### Step 2: MindSpore Lite Inference

```kotlin
class PlantDiseaseClassifier(private val context: Context) {
    
    private var modelHandle: Long = 0L
    private var labels: List<LabelInfo> = emptyList()
    
    fun initialize(): Boolean {
        // Load model
        val modelPath = getModelPath(context, "plant_disease_model.ms")
        modelHandle = MindSporeHelper.loadModel(context, modelPath)
        
        // Load labels
        labels = loadLabelsFromJson("plant_disease_labels.json")
        
        return modelHandle != 0L && labels.isNotEmpty()
    }
    
    fun classify(bitmap: Bitmap): DiseaseResult? {
        // Preprocess
        val inputBuffer = preprocessImage(bitmap)
        
        // Run inference
        val outputBuffer = MindSporeHelper.runInference(modelHandle, inputBuffer)
        
        // Post-process
        val probabilities = softmax(outputBuffer)
        val maxIdx = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
        
        // Confidence threshold
        if (probabilities[maxIdx] < MIN_CONFIDENCE) return null
        
        val label = labels[maxIdx]
        return DiseaseResult(
            id = label.id,
            name = label.name,
            displayName = label.display,
            crop = label.crop,
            confidence = probabilities[maxIdx],
            isHealthy = label.name.contains("Healthy", ignoreCase = true)
        )
    }
    
    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp((it - maxLogit).toDouble()).toFloat() }
        val sumExp = exps.sum()
        return exps.map { it / sumExp }.toFloatArray()
    }
}
```

**Performance Metrics:**
- Inference time: 200-400ms (mid-range device)
- Memory usage: ~50MB during inference
- Accuracy: 92.3% on test set
- Top-3 accuracy: 98.1%

### 1.4 Deployment

#### Step 1: Model Conversion (TensorFlow → MindSpore)

```bash
# Export SavedModel
python tools/export_trained_model.py \
  --model_path outputs/colombia_v1.0/best_model.h5 \
  --output_dir outputs/exported/

# Convert to MindSpore Lite format
python -m mindspore_lite.converter \
  --fmk=TF \
  --modelFile=outputs/exported/saved_model.pb \
  --outputFile=plant_disease_model \
  --inputShape=1,224,224,3

# Verify conversion
python verify_mindspore_model.py plant_disease_model.ms
```

#### Step 2: Deploy to App Assets

```bash
# Copy model to assets
cp plant_disease_model.ms app/src/main/assets/

# Update labels file
cat > app/src/main/assets/plant_disease_labels.json << EOF
{
  "version": "colombia_v1.0",
  "date": "$(date +%Y-%m-%d)",
  "num_classes": 21,
  "input_size": 224,
  "labels": [...]
}
EOF

# Track with Git LFS (large files)
git lfs track "*.ms"
git add .gitattributes
git add app/src/main/assets/plant_disease_model.ms
git commit -m "feat: deploy vision model v1.0"
```

#### Step 3: Validation

```bash
# Build and test
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/FarmifAI-debug-*.apk

# Monitor logs
adb logcat -s PlantDiseaseClassifier | grep "classify"

# Expected output:
# PlantDiseaseClassifier: Model loaded successfully
# PlantDiseaseClassifier: Classified as Tomate___Tizon_temprano (confidence: 0.94)
```

---

## 2. NLP Model - Semantic Search & RAG

### 2.1 Implementation

**Sentence Encoder Architecture:**

- **Model**: paraphrase-multilingual-MiniLM-L12-v2
- **Type**: Transformer-based encoder (BERT family)
- **Input**: Text sequences (max 128 tokens)
- **Output**: 384-dimensional dense embeddings
- **Pooling Strategy**: Mean pooling over token embeddings
- **Normalization**: L2 norm

```python
from transformers import AutoTokenizer, AutoModel
import torch
import torch.nn.functional as F

MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"

def load_encoder():
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModel.from_pretrained(MODEL_NAME)
    model.eval()
    return tokenizer, model
```

**RAG System Flow:**

```
User Query --> Tokenize --> Encode --> Cosine Similarity --> Top-K Contexts --> LLM
                                              ↓
                                    KB Embeddings (517 x 384)
```

### 2.2 Training (Embedding Generation)

The sentence encoder is **pre-trained** - we use it to generate embeddings for the knowledge base.

#### Offline Embedding Generation

```python
def encode_with_mean_pooling(tokenizer, model, texts, batch_size=32):
    """
    Generate embeddings using mean pooling (sentence-transformers standard).
    """
    all_embeddings = []
    
    for i in range(0, len(texts), batch_size):
        batch_texts = texts[i:i + batch_size]
        
        # Tokenize
        inputs = tokenizer(
            batch_texts,
            padding="max_length",
            truncation=True,
            max_length=128,
            return_tensors="pt"
        )
        
        with torch.no_grad():
            outputs = model(**inputs)
            
            # Mean pooling over valid tokens
            attention_mask = inputs['attention_mask']
            token_embeddings = outputs.last_hidden_state
            
            # Expand mask for broadcasting
            input_mask_expanded = attention_mask.unsqueeze(-1).expand(
                token_embeddings.size()
            ).float()
            
            # Compute mean
            sum_embeddings = torch.sum(token_embeddings * input_mask_expanded, 1)
            sum_mask = torch.clamp(input_mask_expanded.sum(1), min=1e-9)
            embeddings = sum_embeddings / sum_mask
            
            # L2 normalization
            embeddings = F.normalize(embeddings, p=2, dim=1)
            
            all_embeddings.append(embeddings.cpu().numpy())
        
        print(f"Processed: {min(i + batch_size, len(texts))}/{len(texts)}")
    
    return np.vstack(all_embeddings)
```

**Generation Script:**

```python
# generate_mindspore_compatible_embeddings.py

def main():
    # Load knowledge base
    with open('app/src/main/assets/agrochat_knowledge_base.json', 'r', encoding='utf-8') as f:
        kb = json.load(f)
    
    # Extract all questions
    questions = []
    entry_ids = []
    for entry in kb['entries']:
        for question in entry['questions']:
            questions.append(question)
            entry_ids.append(entry['id'])
    
    print(f"Total questions: {len(questions)}")
    
    # Load model
    tokenizer, model = load_encoder()
    
    # Generate embeddings
    embeddings = encode_with_mean_pooling(tokenizer, model, questions)
    
    # Save as NumPy
    np.save('app/src/main/assets/kb_embeddings.npy', embeddings)
    
    print(f"Saved embeddings: {embeddings.shape}")
    print(f"Dtype: {embeddings.dtype}")  # float32
    
    # Verify
    assert embeddings.shape == (len(questions), 384)
    assert np.allclose(np.linalg.norm(embeddings, axis=1), 1.0, atol=1e-5)

if __name__ == "__main__":
    main()
```

**Run Script:**

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install torch transformers sentence-transformers numpy

python generate_mindspore_compatible_embeddings.py

# Output: kb_embeddings.npy (517 x 384 float32)
```

### 2.3 Inference

**Runtime Semantic Search:**

#### Step 1: Tokenization

```kotlin
class SemanticSearchHelper(private val context: Context) {
    
    private var tokenizer: UniversalNativeTokenizer? = null
    private var modelHandle: Long = 0L
    private var kbEmbeddings: Array<FloatArray>? = null
    private var kbQuestions: List<String>? = null
    private var kbEntries: Map<Int, KnowledgeEntry>? = null
    
    fun initialize(): Boolean {
        // Load tokenizer
        tokenizer = UniversalNativeTokenizer(context, "sentence_tokenizer.json")
        
        // Load MindSpore model
        modelHandle = MindSporeHelper.loadModel(context, "sentence_encoder.ms")
        
        // Load pre-computed embeddings
        kbEmbeddings = loadEmbeddings("kb_embeddings.npy")
        
        // Load knowledge base
        kbEntries = loadKnowledgeBase("agrochat_knowledge_base.json")
        
        return modelHandle != 0L && kbEmbeddings != null
    }
}
```

#### Step 2: Query Encoding

```kotlin
fun computeEmbedding(text: String): FloatArray {
    // Tokenize query
    val inputIds = tokenizer?.encode(text, maxLength = 128) ?: return FloatArray(384)
    val attentionMask = IntArray(128) { if (it < inputIds.size) 1 else 0 }
    
    // Pad to max length
    val paddedIds = inputIds + IntArray(128 - inputIds.size) { 0 }
    
    // Run inference with MindSpore
    val embedding = MindSporeHelper.predictSentenceEncoder(
        modelHandle, 
        paddedIds, 
        attentionMask
    )
    
    // Verify normalization
    val norm = sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
    require(abs(norm - 1.0f) < 0.01f) { "Embedding not normalized: $norm" }
    
    return embedding
}
```

#### Step 3: Similarity Search

```kotlin
fun findTopKContexts(
    query: String, 
    topK: Int = 3, 
    minScore: Float = 0.4f
): ContextResult {
    // Encode query
    val queryEmbedding = computeEmbedding(query)
    
    // Compute similarities
    val results = kbEmbeddings!!.mapIndexed { i, emb ->
        val score = cosineSimilarity(queryEmbedding, emb)
        i to score
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

fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    // Since vectors are L2-normalized, dot product = cosine similarity
    var dot = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
    }
    return dot
}
```

**Performance:**
- Query encoding: 50-100ms
- Similarity search: 10-30ms
- Total: 60-130ms per query
- Accuracy: 94.4% Top-1, 98.1% Top-3

### 2.4 Deployment

#### Step 1: Export Encoder to MindSpore

```python
# Option 1: Use pre-converted model (recommended)
# Download from project repository

# Option 2: Convert manually
import mindspore as ms
from mindspore import export

# Load PyTorch model weights into MindSpore architecture
# (requires custom conversion script)
ms_model = convert_pytorch_to_mindspore(pytorch_model)

# Export
export(ms_model, inputs, file_name="sentence_encoder", file_format="MINDIR")
```

#### Step 2: Package Assets

```bash
# Generate embeddings
python generate_mindspore_compatible_embeddings.py

# Verify dimensions and normalization
python - <<EOF
import numpy as np

emb = np.load('app/src/main/assets/kb_embeddings.npy')
print(f"Shape: {emb.shape}")  # (517, 384)
print(f"Dtype: {emb.dtype}")  # float32

norms = np.linalg.norm(emb, axis=1)
print(f"Norm range: [{norms.min():.4f}, {norms.max():.4f}]")  # ~1.0
EOF

# Copy to assets
cp kb_embeddings.npy app/src/main/assets/
cp sentence_encoder.ms app/src/main/assets/
cp sentence_tokenizer.json app/src/main/assets/

# Commit
git add app/src/main/assets/kb_embeddings.npy
git commit -m "chore: update KB embeddings (517 questions)"
```

---

## 3. LLM Model - Local Language Model

### 3.1 Implementation

**Llama 3.2 1B Instruct Specifications:**

- **Architecture**: Llama 3.2 (Meta AI)
- **Parameters**: ~1 billion
- **Quantization**: Q4_K_M (4-bit with k-quants)
- **File Size**: ~750MB (from ~2GB unquantized)
- **Context Length**: 128K tokens (limited to 2048 in practice for performance)
- **Vocabulary**: 128,256 tokens
- **Inference Engine**: llama.cpp (C++ with Android JNI)

**Architecture Overview:**

```
Input Text --> Tokenize --> Embedding --> Transformer Layers --> LM Head --> Logits --> Sample
                                             (32 layers)
```

### 3.2 Training

**Note**: We use a **pre-trained and pre-quantized** model. No custom training is performed for standard usage.

**Source Model:**
- **Base**: Meta Llama 3.2 1B Instruct
- **Pre-training**: 15 trillion tokens (multilingual, code, math)
- **Instruction Tuning**: Supervised fine-tuning + RLHF
- **Quantization**: Community-provided (bartowski on HuggingFace)

**Custom Fine-Tuning (Advanced/Optional):**

If you want to fine-tune on agricultural domain data:

```bash
# Requirements: GPU with 24GB+ VRAM, PyTorch

# 1. Prepare dataset
cat > agricultural_qa.jsonl << EOF
{"instruction": "¿Cómo controlar la roya del café?", "output": "..."}
{"instruction": "¿Cuándo plantar maíz?", "output": "..."}
EOF

# 2. Fine-tune with LoRA (Parameter-Efficient)
python -m llama_recipes.finetuning \
  --model_name meta-llama/Llama-3.2-1B-Instruct \
  --dataset agricultural_qa.jsonl \
  --batch_size 4 \
  --gradient_accumulation_steps 4 \
  --num_epochs 3 \
  --learning_rate 2e-5 \
  --use_peft \
  --peft_method lora \
  --lora_r 16 \
  --lora_alpha 32

# 3. Merge LoRA adapters
python merge_lora.py \
  --base_model meta-llama/Llama-3.2-1B-Instruct \
  --lora_weights ./output/adapter_model \
  --output_dir ./fine_tuned_model

# 4. Convert to GGUF
python convert-hf-to-gguf.py ./fine_tuned_model

# 5. Quantize
./quantize ./model.gguf ./model_q4.gguf Q4_K_M
```

### 3.3 Inference

**Prompt Engineering:**

Llama 3.2 uses a specific chat template:

```kotlin
fun buildPrompt(context: String, userQuery: String): String {
    val systemPrompt = """You are FarmifAI, an expert agricultural assistant for Colombian farmers.
Respond clearly and concisely in Spanish.
Use the information from the provided context.
If the context doesn't contain the answer, say so honestly."""
    
    return """<|begin_of_text|><|start_header_id|>system<|end_header_id|>

$systemPrompt<|eot_id|><|start_header_id|>user<|end_header_id|>

Context: $context

Question: $userQuery<|eot_id|><|start_header_id|>assistant<|end_header_id|>

"""
}
```

**Streaming Generation:**

```kotlin
class LlamaService private constructor() {
    
    private val llama: LLamaAndroid = LLamaAndroid.instance()
    
    suspend fun generate(
        prompt: String, 
        maxTokens: Int = 150,
        temperature: Float = 0.7f,
        topP: Float = 0.9f
    ): Flow<String> = flow {
        // Load model if not loaded
        if (!llama.isLoaded()) {
            val modelPath = getModelPath(context)
            llama.load(modelPath)
        }
        
        // Configure generation parameters
        llama.setTemperature(temperature)
        llama.setTopP(topP)
        llama.setMaxTokens(maxTokens)
        
        // Generate with streaming
        llama.generate(prompt).collect { token ->
            // Filter end tokens
            if (!token.contains("<|eot_id|>") && !token.contains("<|end")) {
                emit(token)
            }
        }
    }
}
```

**Usage in RAG Pipeline:**

```kotlin
suspend fun generateAnswer(userQuery: String): Flow<String> {
    // 1. Semantic search
    val contexts = semanticSearch.findTopKContexts(userQuery, topK = 3)
    
    // 2. Build prompt
    val prompt = llamaService.buildPrompt(contexts.combinedContext, userQuery)
    
    // 3. Generate response
    return llamaService.generate(prompt, maxTokens = 150)
}
```

**Performance:**
- Loading time: 2-4 seconds
- Generation speed: 25-35 tokens/sec (mid-range)
- Memory usage: ~1.2GB during inference
- Latency: 2-5 seconds for typical responses

### 3.4 Deployment

#### Step 1: Download Model

```bash
# From HuggingFace
wget https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf

# Verify SHA256
sha256sum Llama-3.2-1B-Instruct-Q4_K_M.gguf
# Expected: <hash from HF model card>

# Check size
ls -lh Llama-3.2-1B-Instruct-Q4_K_M.gguf
# Should be ~750MB
```

#### Step 2: Deploy to Device

**Option A: Manual ADB Push**

```bash
# Create app directory (may fail if app not installed yet)
adb shell mkdir -p /sdcard/Android/data/edu.unicauca.app.agrochat/files/

# Push model
adb push Llama-3.2-1B-Instruct-Q4_K_M.gguf \
  /sdcard/Android/data/edu.unicauca.app.agrochat/files/

# Verify
adb shell ls -lh /sdcard/Android/data/edu.unicauca.app.agrochat/files/*.gguf
```

**Option B: Using Deployment Script**

```bash
./tools/push_llama_model_to_device.sh Llama-3.2-1B-Instruct-Q4_K_M.gguf

# Script content:
#!/bin/bash
MODEL_PATH=$1
DEVICE_DIR="/sdcard/Android/data/edu.unicauca.app.agrochat/files/"

adb shell mkdir -p "$DEVICE_DIR"
adb push "$MODEL_PATH" "$DEVICE_DIR"
echo "Model deployed successfully"
```

**Option C: In-App Download**

The app includes auto-download functionality:

```kotlin
// In LlamaService.kt
suspend fun downloadModel(context: Context): Result<File> {
    val url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
    val destFile = File(context.getExternalFilesDir(null), "Llama-3.2-1B-Instruct-Q4_K_M.gguf")
    
    // Download with progress callback
    downloadFile(url, destFile) { progress, downloadedMB, totalMB ->
        onDownloadProgress?.invoke(progress, downloadedMB, totalMB)
    }
    
    return Result.success(destFile)
}
```

#### Step 3: Verification

```bash
# Start app and check logs
adb shell am start -n edu.unicauca.app.agrochat/.MainActivity
adb logcat -s LlamaService llama-android

# Expected logs:
# LlamaService: Model path: /sdcard/.../Llama-3.2-1B-Instruct-Q4_K_M.gguf
# LlamaService: Model size: 750MB
# llama-android: Loading model...
# llama-android: Model loaded successfully (2.3s)
# LlamaService: LLM ready for inference
```

---

## 4. Voice Models - STT/TTS

### 4.1 Speech-to-Text (Vosk)

**Implementation:**

- **Architecture**: Kaldi-based neural network (TDNN + LSTM)
- **Model**: Spanish lightweight model
- **Size**: ~50MB
- **Sampling Rate**: 16kHz mono
- **Format**: Raw audio (PCM)
- **Output**: JSON with text + confidence

**Integration:**

```kotlin
class VoiceHelper(private val context: Context) {
    
    private var voskModel: Model? = null
    private var speechService: SpeechService? = null
    
    fun initializeVosk() {
        // Unpack model from assets
        val modelDir = File(context.cacheDir, "model-es-small")
        if (!modelDir.exists()) {
            unpackAssets("model-es-small", modelDir)
        }
        
        // Load Vosk model
        voskModel = Model(modelDir.absolutePath)
        
        Log.d(TAG, "Vosk model loaded from: ${modelDir.absolutePath}")
    }
    
    fun startListening() {
        val recognizer = Recognizer(voskModel, 16000.0f)
        recognizer.setMaxAlternatives(1)
        
        speechService = SpeechService(recognizer, 16000.0f)
        speechService?.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                hypothesis?.let {
                    val json = JSONObject(it)
                    val partial = json.optString("partial", "")
                    if (partial.isNotBlank()) {
                        onPartialResult?.invoke(partial)
                    }
                }
            }
            
            override fun onResult(hypothesis: String?) {
                hypothesis?.let {
                    val json = JSONObject(it)
                    val text = json.getString("text")
                    val confidence = json.optDouble("confidence", 0.0)
                    
                    if (text.isNotBlank()) {
                        onResult?.invoke(text)
                    }
                }
            }
            
            override fun onError(exception: Exception?) {
                onError?.invoke(exception?.message ?: "Unknown error")
            }
        })
    }
    
    fun stopListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
    }
}
```

**Performance:**
- Latency: Real-time (< 100ms behind speech)
- Accuracy: 85-92% (clear Spanish)
- Memory: ~100MB during recognition

### 4.2 Text-to-Speech (Android TTS)

**Implementation:**

```kotlin
private fun initializeTts() {
    tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            // Try Spanish (Spain) first
            var result = tts?.setLanguage(Locale("es", "ES"))
            
            // Fallback to Spanish (Mexico)
            if (result == TextToSpeech.LANG_MISSING_DATA || 
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = tts?.setLanguage(Locale("es", "MX"))
            }
            
            // Fallback to generic Spanish
            if (result == TextToSpeech.LANG_MISSING_DATA || 
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("es"))
            }
            
            // Configure voice parameters
            tts?.setSpeechRate(0.9f)  // Slightly slower for clarity
            tts?.setPitch(1.0f)
            
            isTtsReady = true
            Log.d(TAG, "TTS initialized successfully")
        }
    }
    
    // Set listener for conversation mode
    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            onSpeakingStateChanged?.invoke(true)
        }
        
        override fun onDone(utteranceId: String?) {
            onSpeakingStateChanged?.invoke(false)
            
            // Auto-listen after speaking (conversation mode)
            if (conversationMode) {
                Handler(Looper.getMainLooper()).postDelayed({
                    startListening()
                }, 500)
            }
        }
        
        override fun onError(utteranceId: String?) {
            onSpeakingStateChanged?.invoke(false)
        }
    })
}

fun speak(text: String) {
    if (!isTtsReady) {
        Log.w(TAG, "TTS not ready")
        return
    }
    
    val params = Bundle().apply {
        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "farmifai_utterance")
    }
    
    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "farmifai_utterance")
}
```

### 4.3 Deployment

```bash
# Vosk model is included in assets with Git LFS
cd app/src/main/assets/

# Track model directory
git lfs track "model-es-small/**"

# Add model
git add model-es-small/
git add .gitattributes
git commit -m "chore: add Vosk Spanish STT model"

# Push (Git LFS handles large files)
git push origin main
```

---

## Model Update Workflow

### Standard Update Process

```bash
# 1. Create feature branch
git checkout -b model/vision-v1.1

# 2. Train/convert new model
python train_model.py --config configs/vision_v1.1.yaml
python convert_to_mindspore.py --input outputs/model.h5 --output new_model.ms

# 3. Validate model
python validate_model.py new_model.ms
# Check: accuracy, inference time, file size

# 4. Update version metadata
nano app/src/main/assets/plant_disease_labels.json
# Update "version": "colombia_v1.1" and "date"

# 5. Replace model in assets
cp new_model.ms app/src/main/assets/plant_disease_model.ms

# 6. Test on device
./gradlew installDebug
adb shell am start -n edu.unicauca.app.agrochat/.MainActivity
# Verify in logs and manual testing

# 7. Commit with Git LFS
git add -f app/src/main/assets/plant_disease_model.ms
git add app/src/main/assets/plant_disease_labels.json
git commit -m "feat: vision model v1.1 - improved coffee rust detection"

# 8. Tag release
git tag -a v1.1-vision -m "Vision model v1.1

- Improved coffee rust detection (+5% accuracy)
- Reduced false positives for healthy leaves
- Inference time: 220ms avg (was 250ms)"

# 9. Push
git push origin model/vision-v1.1 --tags

# 10. Create PR and merge after review
```

---

## Performance Optimization

### On-Device Optimization

**1. Model Quantization**

```python
# Post-training quantization for TensorFlow models
converter = tf.lite.TFLiteConverter.from_saved_model('model/')
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]
tflite_model = converter.convert()

# For MindSpore: use built-in quantization
# --quantizeType=WeightQuant for weight quantization only
```

**2. Batch Processing**

```kotlin
// Process multiple images in batch (if memory allows)
fun classifyBatch(bitmaps: List<Bitmap>): List<DiseaseResult> {
    val batchBuffer = prepareBatchInput(bitmaps)
    val outputs = MindSporeHelper.runBatchInference(modelHandle, batchBuffer)
    return outputs.mapIndexed { i, output -> parseResult(output, i) }
}
```

**3. Caching**

```kotlin
// Cache model handle to avoid repeated loading
companion object {
    @Volatile
    private var instance: PlantDiseaseClassifier? = null
    
    fun getInstance(context: Context): PlantDiseaseClassifier {
        return instance ?: synchronized(this) {
            instance ?: PlantDiseaseClassifier(context).also { 
                it.initialize()
                instance = it 
            }
        }
    }
}
```

### Memory Management

```kotlin
// Release resources when not needed
override fun onPause() {
    super.onPause()
    voiceHelper.stopListening()
    // Keep models loaded for faster resume
}

override fun onDestroy() {
    super.onDestroy()
    plantDiseaseClassifier.release()
    semanticSearchHelper.release()
    llamaService.unload()
    voiceHelper.release()
}
```

---

## Troubleshooting

### Model Loading Issues

**Vision Model Not Loading:**

```bash
# Check if model exists
adb shell ls -l /data/data/edu.unicauca.app.agrochat/files/plant_disease_model.ms

# Verify file integrity
adb pull /data/data/edu.unicauca.app.agrochat/files/plant_disease_model.ms
sha256sum plant_disease_model.ms

# Check logs
adb logcat -s PlantDiseaseClassifier MindSpore | grep -i error
```

**LLM Model Not Found:**

```bash
# List files in external storage
adb shell ls -lh /sdcard/Android/data/edu.unicauca.app.agrochat/files/

# Check available space
adb shell df -h /sdcard

# Re-push model if needed
adb push Llama-3.2-1B-Instruct-Q4_K_M.gguf \
  /sdcard/Android/data/edu.unicauca.app.agrochat/files/
```

### Performance Issues

**Slow Inference:**

```kotlin
// Enable logging to measure bottlenecks
val startTime = System.currentTimeMillis()
val result = classifier.classify(bitmap)
val inferenceTime = System.currentTimeMillis() - startTime
Log.d(TAG, "Inference took: ${inferenceTime}ms")

// Expected times:
// Vision: 200-400ms
// Semantic Search: 60-130ms
// LLM generation: 2-5 sec for 150 tokens
```

**High Memory Usage:**

```bash
# Monitor memory
adb shell dumpsys meminfo edu.unicauca.app.agrochat

# Expected:
# TOTAL RAM: ~200-300MB (idle)
# During LLM inference: ~1.2GB peak
```

### Model Accuracy Issues

**Poor Classification Results:**

1. Check image quality: must be clear, well-lit, focused on leaf
2. Verify crop is supported (21 classes only)
3. Check confidence threshold (default 3%)
4. Review preprocessing (normalization must be [-1, 1])

**Poor Semantic Search:**

1. Verify embeddings are normalized (L2 norm = 1.0)
2. Check question phrasing matches KB style
3. Lower similarity threshold if no results (try 0.3)
4. Regenerate embeddings if KB was updated

---

## Conclusion

This technical documentation covers the complete implementation, training, inference, and deployment workflows for all models in FarmifAI. For installation instructions and user-facing documentation, see the main [README.md](README.md).

**Key Takeaways:**

- Vision model: MobileNetV2 with 2-phase training
- NLP model: Pre-trained MiniLM with mean pooling
- LLM: Quantized Llama 3.2 with llama.cpp
- All inference runs offline on-device
- Models deployed via MindSpore Lite or native C++

For questions or contributions, please open an issue on GitHub.

---

*Last updated: December 26, 2025*
