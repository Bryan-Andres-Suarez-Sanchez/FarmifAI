# FarmifAI (AgroChat Mobile)

![Android](https://img.shields.io/badge/Android-API%2024%2B-3DDC84)
![Offline First](https://img.shields.io/badge/Mode-Offline--First-2E7D32)
![Local LLM](https://img.shields.io/badge/LLM-Qwen%202.5%200.5B%20GGUF-455A64)
![Semantic Retrieval](https://img.shields.io/badge/Retrieval-Dense%20Bi--Encoder-1565C0)
![Status](https://img.shields.io/badge/Status-Research%20Prototype-607D8B)

> Offline-first Android assistant for agriculture that combines local voice, dense semantic retrieval with embeddings, and on-device LLM inference.

![FarmifAI Hero](docs2/docs/assets/Logo.png)

Core technical docs: [docs2/docs/TECHNICAL_DOCUMENTATION.md](docs2/docs/TECHNICAL_DOCUMENTATION.md)

## Demo

- APK (ready to install): [FarmifAI-release-signed-20260415_132722.apk](FarmifAI-release-signed-20260415_132722.apk)
- Architecture visual: [docs2/docs/assets/fig_system_architecture.png](docs2/docs/assets/fig_system_architecture.png)
- RAG pipeline visual: [docs2/docs/assets/fig_rag_pipeline.png](docs2/docs/assets/fig_rag_pipeline.png)
- Product feature visual: [docs2/docs/assets/fig_vision_inference.png](docs2/docs/assets/fig_vision_inference.png)
- Technical paper (EN): [docs2/docs/FarmifAI_Paper_EN.pdf](docs2/docs/FarmifAI_Paper_EN.pdf)
- Technical paper (ES): [docs2/docs/FarmifAI_Paper_ES.pdf](docs2/docs/FarmifAI_Paper_ES.pdf)
- Demo video: pending public upload

## Quick Evaluation Path

1. Install [FarmifAI-release-signed-20260415_132722.apk](FarmifAI-release-signed-20260415_132722.apk) on an Android device.
2. Launch the app with internet enabled for first-time model download (one-time setup).
3. After setup, disable internet (airplane mode or Wi-Fi/mobile data off).
4. Ask three queries:
	 - `que es una arvense`
	 - `que son las buenas practicas agricolas`
	 - `como reducir la roya en cafe`
5. Verify that responses are generated with local assets and no cloud dependency in the core flow.

Question bank for extended testing: [docs2/docs/BANCO_PREGUNTAS_TEST_KB.md](docs2/docs/BANCO_PREGUNTAS_TEST_KB.md)

## Problem

Smallholder farmers often work in areas with weak or intermittent connectivity, where cloud-dependent assistants become unreliable exactly when guidance is needed in the field.

Agronomic questions are context-dependent (crop, disease, nutrition, management stage). A practical assistant must answer quickly, in Spanish, and remain useful even with no internet.

## Solution

FarmifAI is an Android app that integrates voice I/O, plant disease vision, and a retrieval-augmented chat pipeline. The core QA path uses dense semantic retrieval over local embeddings, then routes to local LLM generation when needed.

The retrieval stack uses a bi-encoder style flow: user query and KB questions are encoded independently in the same embedding space, then ranked by cosine similarity.

## Key Features

- Offline chat and voice assistant for agricultural Q&A in Spanish.
- Dense semantic retrieval with 384-d embeddings and top-k context selection.
- Bi-encoder style retrieval and explicit cosine similarity scoring.
- Local LLM inference using llama.cpp-compatible GGUF models (default Qwen 2.5 0.5B Q4_K_M).
- Camera-based plant disease support for common crops.
- Optional online acceleration path (Groq) without removing offline capability.

## System Architecture

![System Architecture](docs2/docs/assets/fig_system_architecture.png)

High-level layers:

- UI layer: Compose chat, voice controls, camera diagnosis UI.
- Domain orchestration: response routing policy (`KB_DIRECT`, `LLM_WITH_KB`, `LLM_GENERAL`).
- Data layer: local KB records, embedding matrix, mapping metadata.
- Inference layer: MindSpore Lite for embeddings/vision and llama.cpp for local generation.
- Optional online layer: Groq service when internet and API key are available.

Additional architecture notes: [docs2/docs/ESTRUCTURA_PROYECTO.md](docs2/docs/ESTRUCTURA_PROYECTO.md)

## AI / Model Details

### Base Model

- Semantic encoder: `paraphrase-multilingual-MiniLM-L12-v2` exported for MindSpore runtime.
- Local generation model (default): `Qwen2.5-0.5B-Instruct-Q4_K_M.gguf`.
- Vision model: MobileNetV2-based plant disease classifier exported to MindSpore.
- Speech: Vosk small Spanish model + Android TTS.

### Quantization / Format

- LLM format: GGUF (`Q4_K_M` default), ~379 MB download.
- Semantic embeddings: `kb_embeddings.npy` float32 matrix, 384 dimensions per vector.
- MindSpore assets: `.ms` model files loaded at runtime.

### On-Device Runtime

- MindSpore JNI bridge for sentence encoder and vision inference.
- llama.cpp Android JNI for local text generation.
- Retrieval defaults in helper: `topK=3`, `minScore=0.4`.

### Knowledge Source

- Primary KB records: `app/src/main/assets/kb_nueva/extract/*.jsonl`
- Embedding matrix: `app/src/main/assets/kb_embeddings.npy`
- Mapping metadata: `app/src/main/assets/kb_embeddings_mapping.json`

### Retrieval Method (Dense + Bi-Encoder + Cosine)

- Dense semantic retrieval with L2-normalized embeddings.
- Bi-encoder style formulation:
	- Encode query once: `q = Enc(query)`
	- Encode KB entries offline: `d_i = Enc(question_i)`
- Ranking by cosine similarity:

$$
\operatorname{score}(q, d_i) = \frac{q \cdot d_i}{\|q\|\,\|d_i\|}
$$

- Top-ranked contexts are merged for RAG prompting.

### Intended Use

- Agricultural guidance for field users in Spanish.
- Fast first-pass assistance for crop management, pests, nutrition, and practices.
- Educational and operational support in low-connectivity settings.

### Out-of-Scope Use

- Not a replacement for certified agronomist diagnosis in high-risk decisions.
- Not a legal, medical, or regulatory authority.
- Not intended for autonomous action without human review in critical cases.

### Known Limitations

- First launch requires internet to download models.
- Local LLM latency can vary significantly by device and generation settings.
- Retrieval quality depends on encoder/tokenizer/embedding alignment.
- KB coverage is bounded by loaded records and curated content.

### Evaluation Results

Detailed reports are available in:

- [docs2/docs/TECHNICAL_DOCUMENTATION.md](docs2/docs/TECHNICAL_DOCUMENTATION.md)
- [docs2/docs/ANALISIS_LATENCIA_RESPUESTA_6MIN.md](docs2/docs/ANALISIS_LATENCIA_RESPUESTA_6MIN.md)

## Offline-First Design

What is offline after setup:

- Voice recognition (Vosk) and TTS.
- Semantic retrieval over local embeddings.
- Local LLM generation path.
- Plant disease local inference.

What may require internet:

- First-time download of required models.
- Optional Groq path for online acceleration.
- Optional feedback synchronization endpoint.

## Evaluation And Benchmarks

### Benchmarks On Device

Reported technical metrics (project documentation and code constants):

| Metric | Value | Source |
|---|---|---|
| Plant disease Top-1 accuracy | 92.3% | docs2/docs/TECHNICAL_DOCUMENTATION.md |
| Plant disease Top-3 accuracy | 98.1% | docs2/docs/TECHNICAL_DOCUMENTATION.md |
| Query encoding latency | 50-100 ms | docs2/docs/TECHNICAL_DOCUMENTATION.md |
| Similarity search latency | 10-30 ms | docs2/docs/TECHNICAL_DOCUMENTATION.md |
| LLM expected model size | ~379 MB | app/src/main/java/edu/unicauca/app/agrochat/llm/LlamaService.kt |
| LLM loading time | 2-4 s | docs2/docs/TECHNICAL_DOCUMENTATION.md |
| LLM memory usage | ~1.2 GB during inference | docs2/docs/TECHNICAL_DOCUMENTATION.md |
| Retrieval Top-1 accuracy | 94.4% | docs2/docs/TECHNICAL_DOCUMENTATION.md |
| Retrieval Top-3 accuracy | 98.1% | docs2/docs/TECHNICAL_DOCUMENTATION.md |

Observed latency case analysis (log-based):

| Stage | Observed Time | Notes | Source |
|---|---|---|---|
| Retrieval + routing | ~1.36 s | Not the main bottleneck | docs2/docs/ANALISIS_LATENCIA_RESPUESTA_6MIN.md |
| LLM generation phase | ~348.14 s | Dominant latency in analyzed case | docs2/docs/ANALISIS_LATENCIA_RESPUESTA_6MIN.md |
| Fallback delivery | <0.05 s | Fast once fallback is selected | docs2/docs/ANALISIS_LATENCIA_RESPUESTA_6MIN.md |

Benchmark limitations:

- Current public metrics combine controlled tests and targeted log-case analysis.
- Hardware-normalized benchmark matrix (same prompt set across multiple devices) is pending publication.

## Privacy, Security And Permissions

Core privacy posture:

- Main inference pipeline runs on-device after setup.
- App requests only functional permissions used by enabled features.
- No continuous internet requirement for the main offline workflow.

Permissions in `AndroidManifest.xml`:

- `android.permission.RECORD_AUDIO`: voice input.
- `android.permission.CAMERA`: visual diagnosis.
- `android.permission.INTERNET`: optional online services and model downloads.
- `android.permission.ACCESS_NETWORK_STATE`: connectivity-aware routing.

Feedback data notes:

- Feedback events are persisted locally in JSONL under app files.
- Remote sync is optional and triggered by feedback actions when configured.

## Installation



1. Install [FarmifAI-release-signed-20260415_132722.apk](FarmifAI-release-signed-20260415_132722.apk).
2. Open the app once with internet to complete model setup.
3. Switch to offline mode and run the quick prompts in the evaluation path.





Requirements:

- Android Studio (recent stable)
- JDK 11+ (project targets Java/Kotlin 11)
- Android SDK / NDK configured in local Android setup

Optional environment/build variables:

- `GROQ_API_KEY`
- `FEEDBACK_LIVE_ENDPOINT`

### For Reproducibility

- Runtime models are intentionally decoupled from the APK/repository footprint.
- MindSpore and Vosk required assets are downloaded on first setup by [app/src/main/java/edu/unicauca/app/agrochat/models/ModelDownloadService.kt](app/src/main/java/edu/unicauca/app/agrochat/models/ModelDownloadService.kt) from `https://media.githubusercontent.com/media/pazussa/models_FarmifAI/main`.
- Default local GGUF model is downloaded by [app/src/main/java/edu/unicauca/app/agrochat/llm/LlamaService.kt](app/src/main/java/edu/unicauca/app/agrochat/llm/LlamaService.kt) from Hugging Face (`Qwen2.5-0.5B-Instruct-Q4_K_M.gguf`).
- After one-time downloads complete, the core QA workflow can be validated offline.

## Build From Source

Build debug APK with Gradle:

```bash
./gradlew :app:assembleDebug
```

Or use helper script:

```bash
./scripts/build_apk.sh debug
```

Install latest debug APK wirelessly (ADB):

```bash
./scripts/install_latest_apk_wireless.sh <device_ip:port> [pair_ip:port] [pair_code]
```

## Repository Structure

```text
AgroChat_Project/
	app/                 # Android app (Compose + JNI + assets)
	docs2/docs/          # Technical docs, paper, architecture figures
	nlp_dev/             # NLP/export scripts and data tooling
	tools/               # Vision training/export utilities
	scripts/             # Build/deploy/install helper scripts
	pc_rag_clone/        # PC replica and llama.cpp vendor clone
	README.md
```

## Limitations

- No public demo video link is bundled yet in the repository.
- Local generation latency can be high on low-end hardware or high token budgets.
- Evaluation metrics are documented, but hardware-standardized benchmark suites are still limited.
- Root-level licensing and citation metadata files are not yet formalized.

## Future Work

- Publish official demo video and reproducible benchmark dashboard.
- Improve low-latency presets per device profile.
- Expand KB domain coverage with stronger grounding checks.
- Add formal CI workflows for mobile regression and performance tests.
- Add root-level `LICENSE`, `CITATION.cff`, `SECURITY.md`, and `CONTRIBUTING.md`.

## License

No root `LICENSE` file is currently present in this repository snapshot. Define project licensing before external redistribution.

## Citation

If you need to cite this work, use the project paper as current reference:

- [docs2/docs/FarmifAI_Paper_EN.pdf](docs2/docs/FarmifAI_Paper_EN.pdf)
- [docs2/docs/FarmifAI_Paper_ES.pdf](docs2/docs/FarmifAI_Paper_ES.pdf)

A `CITATION.cff` file is recommended for future updates.

## Acknowledgements

- MindSpore Lite (on-device inference runtime)
- llama.cpp (local LLM runtime)
- Vosk (offline ASR)
- Android Jetpack Compose ecosystem
