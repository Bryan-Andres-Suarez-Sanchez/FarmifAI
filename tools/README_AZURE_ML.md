# Azure ML Training - Cultivos Colombianos 🇨🇴

## Ejecutar entrenamiento en segundo plano

Este script permite entrenar el modelo en Azure ML de forma que **continúe aunque cierres VS Code**.

## Requisitos

1. **Cuenta de Azure** con créditos (estudiantes tienen $100 gratis)
2. **Azure ML Workspace** creado
3. **Python 3.8+** con Azure SDK

## Instalación

```bash
pip install azure-ai-ml azure-identity
```

## Configuración

1. Edita `.env.azure` con tus credenciales:
```bash
AZURE_SUBSCRIPTION_ID=xxxx-xxxx-xxxx
AZURE_RESOURCE_GROUP=mi-resource-group
AZURE_WORKSPACE_NAME=mi-workspace
```

2. O usa variables de entorno:
```bash
export AZURE_SUBSCRIPTION_ID="xxx"
export AZURE_RESOURCE_GROUP="xxx"
export AZURE_WORKSPACE_NAME="xxx"
```

## Uso

### 1. Crear script de entrenamiento
```bash
python azure_ml_train.py --create-script
```

### 2. Configurar Azure ML (crear compute GPU)
```bash
python azure_ml_train.py --setup
```

### 3. Enviar entrenamiento a la nube
```bash
python azure_ml_train.py --submit
```
Después de esto puedes **cerrar VS Code**. El entrenamiento continúa en Azure.

### 4. Ver estado del entrenamiento
```bash
python azure_ml_train.py --status
```

### 5. Descargar resultados cuando termine
```bash
python azure_ml_train.py --download
```

## Costos estimados

| GPU | VM Size | Tiempo | Costo |
|-----|---------|--------|-------|
| V100 | NC6s_v3 | ~45 min | ~$2.30 |
| A100 | NC24ads_A100 | ~18 min | ~$1.10 |

## Ver en Azure Portal

Después de enviar el job, puedes verlo en:
- https://ml.azure.com
- Selecciona tu workspace → Jobs → tu experimento

## Archivos generados

Después del entrenamiento, en `./azure_outputs/`:
- `colombia_crop_disease.tflite` - Modelo para Android
- `colombia_crop_labels.json` - Labels en español
- `colombia_crop_disease.keras` - Modelo Keras completo

## Convertir a MindSpore (después de descargar)

```bash
./converter_lite --fmk=TFLITE \
    --modelFile=azure_outputs/colombia_crop_disease.tflite \
    --outputFile=plant_disease_model

cp plant_disease_model.ms ../app/src/main/assets/
cp azure_outputs/colombia_crop_labels.json ../app/src/main/assets/plant_disease_labels.json
```
