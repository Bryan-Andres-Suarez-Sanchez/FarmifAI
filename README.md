# FarmifAI — Offline AI for Agriculture

**FarmifAI** is an Android app that provides **agricultural recommendations fully offline**, using **Small Language Models (SLMs).**

Built for rural environments, it enables farmers to **diagnose crops and receive recommendations without internet access**.

---

## Key Features

- Offline AI chat (SLM + RAG)  
- Plant disease detection (on-device vision model)  
- Voice interaction (offline STT + TTS)  
- Local inference (no cloud dependency)  
- Privacy-first (all processing on-device)  

---

## Demo

- Final signed APK (direct download):  
   https://github.com/Bryan-Andres-Suarez-Sanchez/FarmifAI/releases/latest/download/FarmifAI-release-v1.0-20260420_182313-signed.apk  
- Download guide + checksum:  
   [docs/APK_DOWNLOAD.md](docs/APK_DOWNLOAD.md)  

---

## Quick Start (under 5 minutes)

1. Download the final signed APK  
   https://github.com/Bryan-Andres-Suarez-Sanchez/FarmifAI/releases/latest/download/FarmifAI-release-v1.0-20260420_182313-signed.apk  

2. Install on Android device  
   adb install -r FarmifAI-release-v1.0-20260420_182313-signed.apk  

3. Open the app and wait for first-time model setup  

4. Test offline  
   - Enable airplane mode  
   - Open the app  
   - Try one of the questions from the **Sample RAG Questions (Coffee KB)** section below.

---

## Sample RAG Questions (Coffee KB)

These prompts are based on the local knowledge base in `kb nueva/extract/*.jsonl`.

- Que es un sistema de produccion de cafe y cuales son sus componentes principales?
- Que es la fitotecnia y como se aplica al cultivo de cafe?
- Cuantos anos dura la vida comercial del cafeto en condiciones normales?
- A que edad empieza a producir el cafeto y cuando alcanza su maxima productividad?
- Como se define la productividad del cafetal usando cafe pergamino seco?
- De que depende el potencial de produccion del cafetal (genetica, ambiente y manejo)?
- Que decisiones debo definir desde la siembra para planificar un cafetal de largo plazo?
- Como seleccionar semilla de cafe para variedades tradicionales como Caturra, Tipica y Borbon?
- Cual es la diferencia entre arvense y maleza en caficultura?
- Que riesgos trae el control indiscriminado de arvenses y herbicidas?
- Que diferencia hay entre competencia intraespecifica e interespecifica en un cafetal?
- Como influye la densidad de siembra en la productividad del cafe?
- Despues de cuantas cosechas suele bajar la formacion de ramas y nudos productivos?
- Que es la agroforesteria en cafe y cuando puede ayudar o perjudicar la produccion?
- Cuales son los macronutrimentos y micronutrimentos esenciales para el cafeto?
- Que caracteriza a un cafe especial y por que puede tener mejor precio?
- Como disenar sistemas intercalados con cafe para reducir competencia y alelopatia?
- Que son las Buenas Practicas Agricolas (BPA) y cuales son sus objetivos en caficultura?

---

## Problem

Agricultural decision-making in rural areas is limited by:

- Lack of localized technical information  
- High environmental variability  
- Limited or unstable internet connectivity  

Cloud-based AI solutions often fail in-field, where they are most needed.

---

## Solution

FarmifAI integrates:

- Local retrieval (RAG)  
- Local generation (GGUF + llama.cpp)  
- On-device vision (MindSpore Lite)  
- Voice interaction  

All running **fully offline on the device**.

---

## Architecture

![Architecture](docs/images/fig_system_architecture.png)

- UI layer → chat, voice, camera  
- Logic layer → routing and query processing  
- Data layer → local KB + embeddings  
- Inference layer → SLM + vision model  

---

## AI / Model Details

- Hugging Face model: FarmifAI/Qwen3.5-0.8B_FarmifAI2.0
- Runtime: llama.cpp (LLM) + MindSpore Lite (vision & sentence similarity)  
- KB: app/src/main/assets/kb_nueva/extract/*.jsonl  
- Embeddings: (2842, 384) float32  

---

## Limitations

- Not a substitute for professional agronomic advice  
- The model may still produce hallucinations  

---

## Privacy & Permissions

- RECORD_AUDIO → voice input  
- CAMERA → plant diagnosis  
- No internet required for main functionality  

---

## Installation (Developers)

Requirements:
- Android Studio  
- JDK 11  
- Android SDK + NDK  

Build:
- ./gradlew :app:assembleDebug  
- ./gradlew :app:testDebugUnitTest  
- ./scripts/build_apk.sh debug  

---

## Repository Structure

app/ → Android app & inference  
docs/ → Reports & documentation  
tools/ → Vision tools  
scripts/ → Build scripts  

---

## License

Pending  

---

## Acknowledgments
 
- MindSpore Lite  
- llama.cpp  
- Vosk  

AI that works where the internet doesn’t.
