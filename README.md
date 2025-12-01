# AgroChat 🌱

Asistente agrícola inteligente para Android con capacidades de voz offline y LLM online.

## Características

- **🎤 Reconocimiento de voz offline** - Usa Vosk (sin Google/internet)
- **🔊 Síntesis de voz** - TTS nativo de Android
- **🧠 IA Híbrida**:
  - **Offline**: Búsqueda semántica con MindSpore Lite + ONNX
  - **Online**: Groq LLM para respuestas más fluidas (opcional, gratis)
- **📱 UI moderna** - Jetpack Compose con modo voz y chat

## Requisitos

- Android 7.0+ (API 24)
- ~100MB espacio (incluye modelo de voz español)

## Tecnologías

| Componente | Tecnología |
|------------|------------|
| UI | Jetpack Compose |
| IA Offline | MindSpore Lite 2.6.0 |
| Embeddings | ONNX Runtime |
| STT Offline | Vosk (vosk-model-small-es) |
| TTS | Android TextToSpeech |
| LLM Online | Groq API (llama-3.3-70b) |

## Estructura

```
app/
├── src/main/
│   ├── java/.../agrochat/
│   │   ├── MainActivity.kt      # UI principal
│   │   ├── llm/GroqService.kt   # Integración LLM
│   │   ├── mindspore/           # Búsqueda semántica
│   │   └── voice/VoiceHelper.kt # STT/TTS
│   └── assets/
│       ├── model-es-small/      # Modelo Vosk español
│       ├── sentence_encoder.ms  # Encoder MindSpore
│       └── kb_embeddings.npy    # Embeddings KB
nlp_dev/
└── data/datasets/               # Base de conocimiento
```

## Uso del LLM (opcional)

1. Obtén API key gratis en [console.groq.com](https://console.groq.com)
2. En la app, toca el indicador "Local" (arriba)
3. Ingresa tu API key
4. ¡Listo! Las respuestas serán más naturales cuando haya internet

## Compilar

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Licencia

MIT
