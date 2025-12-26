# FarmifAI - Offline Agricultural AI Assistant

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Status](https://img.shields.io/badge/Status-Active-success.svg)](https://github.com/Bryan-Andres-Suarez-Sanchez/AgroChat_Project)

**Complete offline AI assistant for Colombian farmers with voice interaction, plant disease detection, and intelligent question answering.**

Repository: [https://github.com/Bryan-Andres-Suarez-Sanchez/AgroChat_Project](https://github.com/Bryan-Andres-Suarez-Sanchez/AgroChat_Project)

📚 **[Technical Documentation →](TECHNICAL_DOCUMENTATION.md)** - Complete guide for model implementation, training, inference & deployment

---

## Key Features

| Feature | Technology | Specification |
|---------|-----------|---------------|
| **Voice Input/Output** | Vosk + Android TTS | Real-time speech recognition & synthesis (Spanish) |
| **Plant Disease Detection** | MobileNetV2 + MindSpore Lite | 21 diseases across 5 crops (Coffee, Corn, Potato, Pepper, Tomato) |
| **Semantic Q&A** | MiniLM + RAG | 517 agricultural questions with intelligent retrieval |
| **AI Assistant** | Llama 3.2 1B (quantized) | Local LLM with 128K context window |
| **100% Offline** | On-device inference | No internet required after initial setup |
| **Lightweight** | ~70MB APK + ~750MB LLM | Optimized for mid-range Android devices |

### 1. Voice Interaction
- Hands-free operation via speech recognition (Vosk)
- Natural language text-to-speech responses (Android TTS)
- Conversation mode with automatic turn-taking
- Optimized for noisy agricultural environments

### 2. Plant Disease Detection
- Camera-based visual diagnosis (224x224 RGB input)
- 21 disease classes + healthy states
- Inference time: 200-400ms on mid-range devices
- Accuracy: 92.3% (Top-1), 98.1% (Top-3)

**Supported Crops:**
- Coffee: 4 diseases (Cercospora, Leaf miner, Phoma, Rust)
- Corn: 4 diseases (Gray spot, Common rust, Healthy, Northern blight)
- Potato: 3 diseases (Healthy, Late blight, Early blight)
- Pepper: 2 diseases (Bacterial spot, Healthy)
- Tomato: 8 diseases (Bacterial spot, Target spot, Leaf mold, etc.)

### 3. Intelligent Knowledge Base (RAG)
- 517 pre-indexed questions and answers
- Semantic search using 384-dim embeddings
- Cosine similarity matching (threshold: 0.4)
- Top-3 context retrieval for LLM augmentation
- Query encoding: 50-100ms latency

### 4. Local Language Model
- Llama 3.2 1B Instruct (4-bit quantized)
- Generation speed: 25-35 tokens/sec
- Max response: 256 tokens
- Context-aware responses using RAG pipeline
- Streaming output for real-time feedback

---

## System Requirements

### Development Requirements
- **OS**: Linux / macOS / Windows with WSL
- **Android Studio**: Hedgehog 2023.1.1 or newer
- **JDK**: 17+
- **Gradle**: 8.13+
- **NDK**: 26.1.10909125 (for native libs)
- **Git LFS**: For large model files
- **Python**: 3.9+ (for training/embeddings)

### Device Requirements
- **Android**: 8.0 (API 26) or higher
- **RAM**: 3GB minimum (4GB+ recommended)
- **Storage**: 1.5GB free space (app + models)
- **CPU**: ARMv8 (64-bit) preferred
- **Permissions**: Camera, Microphone, Storage

---

## Installation Guide

### Step 1: Clone Repository

```bash
git clone https://github.com/Bryan-Andres-Suarez-Sanchez/AgroChat_Project.git
cd AgroChat_Project

# Initialize Git LFS for large files
git lfs install
git lfs pull
```

### Step 2: Configure Development Environment

**Install Android Studio:**
```bash
# Download from https://developer.android.com/studio
# Install NDK via SDK Manager: Tools → SDK Manager → SDK Tools → NDK

# Verify installation
which ndk-build
```

**Set Local Properties:**
```bash
# Create local.properties with SDK path
cat > local.properties << EOF
sdk.dir=/home/$USER/Android/Sdk
ndk.dir=/home/$USER/Android/Sdk/ndk/26.1.10909125
