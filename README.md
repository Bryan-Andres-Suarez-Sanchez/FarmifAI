# ARApp 🌱🤖

Asistente agrícola inteligente para Android con IA completamente offline y capacidades de voz.

## ✨ Características Principales

- **🎤 Reconocimiento de voz 100% offline** - Usa Vosk (sin Google/internet)
- **🔊 Síntesis de voz (TTS)** - Nativo de Android
- **🧠 IA Híbrida**:
  - **Offline**: Búsqueda semántica con MindSpore Lite + LLM local (Llama 3.2)
  - **Online**: Groq LLM para respuestas más fluidas (opcional)
- **📱 UI moderna** - Jetpack Compose con modo voz y chat
- **🌐 RAG (Retrieval Augmented Generation)** - Respuestas basadas en conocimiento agrícola

---

## 📋 Requisitos del Sistema

### Para ejecutar la app:
- Android 7.0+ (API 24)
- ~1.5GB de espacio (incluye modelos de voz y LLM)
- Arquitectura arm64-v8a

### Para desarrollo:
- Android Studio Hedgehog (2023.1.1) o superior
- JDK 17
- NDK 25.1.8937393 (para compilar código nativo)
- CMake 3.22.1
- Python 3.10+ (para regenerar embeddings)

---

## 🚀 Instalación desde Cero

### 1. Clonar el repositorio

```bash
git clone https://github.com/Bryan-Andres-Suarez-Sanchez/AgroChat_Project.git
cd AgroChat_Project
git checkout Integracion-voz
```

### 2. Descargar modelos grandes (no incluidos en git)

Los siguientes archivos son demasiado grandes para git y deben descargarse manualmente:

#### a) Modelo de voz Vosk (español)
```bash
# Descargar modelo de Vosk
cd app/src/main/assets
wget https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip
unzip vosk-model-small-es-0.42.zip
mv vosk-model-small-es-0.42 model-es-small
rm vosk-model-small-es-0.42.zip
cd ../../../..
```

#### b) Modelo LLM offline (Llama 3.2 1B)
```bash
# Crear directorio para el modelo
mkdir -p app/src/main/assets

# Descargar modelo cuantizado (aproximadamente 770MB)
# Opción 1: Desde Hugging Face
wget -O app/src/main/assets/Llama-3.2-1B-Instruct-Q4_K_M.gguf \
  "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
```

#### c) Modelo MindSpore Sentence Encoder (si no está)
El archivo `sentence_encoder.ms` (~224MB) debería estar en el repositorio. Si no:
```bash
# Contactar al equipo para obtener el modelo
# O regenerarlo siguiendo ARCHITECTURE.md
```

### 3. Configurar Android Studio

1. Abrir Android Studio
2. File → Open → Seleccionar carpeta `AgroChat_Project`
3. Esperar a que Gradle sincronice
4. Si hay errores de NDK:
   - File → Settings → Android SDK → SDK Tools
   - Instalar: NDK (Side by side) 25.1.8937393, CMake 3.22.1

### 4. Compilar y ejecutar

#### Desde Android Studio:
1. Conectar dispositivo Android (USB debugging habilitado)
2. Seleccionar dispositivo en la barra de herramientas
3. Click en Run (▶️)

#### Desde terminal:
```bash
# Compilar
./gradlew assembleDebug

# Instalar en dispositivo conectado
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Iniciar la app
adb shell am start -n edu.unicauca.app.agrochat/.MainActivity
```

### 5. Verificar instalación

Al abrir la app debería ver:
- ✅ Mensaje de bienvenida
- ✅ Campo de texto para escribir preguntas
- ✅ Botón de micrófono para voz
- ✅ Indicador "Local" o "Online" arriba

---

## ⚙️ Configuración Opcional

### Habilitar LLM Online (Groq)

1. Obtener API key gratis en [console.groq.com](https://console.groq.com)
2. En la app, tocar el indicador "Local" (arriba a la derecha)
3. Ingresar tu API key
4. El indicador cambiará a "Online" cuando haya internet

---

## 📁 Estructura del Proyecto

```
AgroChat_Project/
├── app/
│   ├── src/main/
│   │   ├── java/edu/unicauca/app/agrochat/
│   │   │   ├── MainActivity.kt           # UI principal (Compose)
│   │   │   ├── llm/
│   │   │   │   ├── GroqService.kt         # Cliente API Groq (online)
│   │   │   │   └── LlamaService.kt        # LLM local con llama.cpp
│   │   │   ├── mindspore/
│   │   │   │   ├── MindSporeHelper.kt     # Wrapper JNI para MindSpore
│   │   │   │   └── SemanticSearchHelper.kt # RAG y búsqueda semántica
│   │   │   └── voice/
│   │   │       └── VoiceHelper.kt         # STT (Vosk) y TTS
│   │   ├── cpp/
│   │   │   └── MindSporeNetnative.cpp     # Código nativo MindSpore
│   │   ├── jniLibs/arm64-v8a/
│   │   │   ├── libllama-android.so        # Biblioteca llama.cpp
│   │   │   └── libmindspore-lite*.so      # Bibliotecas MindSpore
│   │   └── assets/
│   │       ├── agrochat_knowledge_base.json  # Base de conocimiento
│   │       ├── kb_embeddings.npy             # Embeddings pre-calculados
│   │       ├── sentence_encoder.ms           # Modelo MindSpore (224MB)
│   │       ├── sentence_tokenizer.json       # Tokenizer
│   │       ├── model-es-small/               # Modelo Vosk STT
│   │       └── Llama-3.2-1B-Instruct-Q4_K_M.gguf  # LLM (770MB)
│   └── build.gradle.kts
├── nlp_dev/                               # Herramientas de desarrollo NLP
│   ├── scripts/
│   │   ├── export_sentence_encoder.py     # Exportar modelo a MindSpore
│   │   └── generate_embeddings.py         # Generar embeddings
│   └── data/datasets/
│       └── agricultura_completo.json      # Dataset de entrenamiento
├── ARCHITECTURE.md                        # Documentación técnica detallada
└── README.md                              # Este archivo
```

---

## 🧪 Pruebas

### Probar búsqueda semántica
```bash
# Ver logs de búsqueda
adb logcat -s SemanticSearchHelper | grep "Búsqueda"
```

### Probar LLM
```bash
# Ver logs de Llama
adb logcat -s llama-android LlamaService
```

### Preguntas de prueba
- "¿Cómo cultivar tomates?"
- "plagas en papa"
- "fertilizante para maíz"
- "pH del suelo"

---

## 🔧 Solución de Problemas

### Error: "No se encontró el modelo de voz"
- Verificar que exista `app/src/main/assets/model-es-small/`
- El directorio debe contener archivos como `am/final.mdl`

### Error: "LLM no responde"
- Verificar que exista el archivo `.gguf` en assets
- Revisar logs: `adb logcat -s LlamaService`

### Error de compilación NDK
```bash
# Instalar NDK específico
sdkmanager "ndk;25.1.8937393" "cmake;3.22.1"
```

### La app se cierra al iniciar
- Verificar espacio disponible (necesita ~1.5GB)
- Revisar logs: `adb logcat | grep -E "FATAL|Exception"`

---

## 📚 Documentación Adicional

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Arquitectura técnica detallada
- **[nlp_dev/README.md](nlp_dev/README.md)** - Desarrollo del sistema NLP

---

## 🤝 Contribuir

1. Fork el repositorio
2. Crear rama feature (`git checkout -b feature/nueva-funcionalidad`)
3. Commit cambios (`git commit -am 'Agregar nueva funcionalidad'`)
4. Push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Crear Pull Request

---

## 📄 Licencia

MIT License - Ver [LICENSE](LICENSE) para más detalles.

---

## 👥 Equipo

Proyecto desarrollado por estudiantes de la Universidad del Cauca para el Hackathon Huawei 2024.
