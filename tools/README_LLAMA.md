# Llama (offline) en AgroChat

AgroChat usa `llama.cpp` vía JNI (`libllama-android.so`) y **carga un modelo GGUF desde el almacenamiento del teléfono**.

Por diseño, el archivo `.gguf` **no se incluye en el repositorio** (tamaño/licencia). Debes copiarlo al dispositivo.

## Dónde poner el modelo

La app busca un `.gguf` en:

- `/sdcard/Android/data/edu.unicauca.app.agrochat/files/`

Regla:
- Si existe `llama-3.2-1b-q4.gguf`, lo usa.
- Si no, usa **cualquier** `.gguf` en esa carpeta (elige el más grande).

## Copiar el modelo con ADB

1) Conecta el teléfono y autoriza depuración USB.
2) Ejecuta:

```bash
cd /home/user/HuaweiProject/AgroChat_Project
./tools/push_llama_model_to_device.sh /ruta/al/modelo.gguf
```

3) Abre la app. En **Configuración** verás el estado del modelo y la ruta.

## Ver logs

```bash
adb logcat -s MainActivity LlamaService LLamaAndroid
```
