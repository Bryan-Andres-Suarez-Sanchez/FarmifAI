# 📸 Plan de Implementación: Diagnóstico Visual de Enfermedades

> **Estado:** 🚀 EN PROGRESO - Código implementado, pendiente modelo y embeddings

Este documento detalla el plan completo para agregar diagnóstico visual de enfermedades de plantas a ARApp, manteniendo la filosofía 100% offline.

---

## ✅ Progreso Actual

| Tarea | Estado | Notas |
|-------|--------|-------|
| Dependencias CameraX | ✅ Completado | camera-core, camera-camera2, camera-lifecycle, camera-view 1.3.1 |
| Permiso de cámara | ✅ Completado | CAMERA permission en AndroidManifest.xml |
| PlantDiseaseClassifier.kt | ✅ Completado | Wrapper MindSpore para clasificación |
| CameraHelper.kt | ✅ Completado | Manejo de CameraX con callbacks |
| plant_disease_labels.json | ✅ Completado | 38 clases con traducciones español |
| UI de cámara | ✅ Completado | Modo CAMERA en MainActivity con preview y captura |
| Entradas KB enfermedades | ✅ Completado | IDs 100-115 con tratamientos |
| **Modelo .ms** | ⏳ Pendiente | Necesita entrenamiento/conversión |
| **Embeddings** | ⏳ Pendiente | Ejecutar generate_mindspore_compatible_embeddings.py |

---

## 🎯 Objetivo

Permitir que los agricultores tomen una foto de una hoja/planta enferma y reciban:
1. **Identificación** de la enfermedad (ej: "Tizón temprano en tomate")
2. **Recomendaciones** de control/tratamiento via RAG + LLM

---

## 📊 Arquitectura Propuesta

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FLUJO DE DIAGNÓSTICO VISUAL                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐                │
│  │   Usuario    │     │   CameraX    │     │  Bitmap      │                │
│  │  📸 Toma     │────▶│   Captura    │────▶│  224x224     │                │
│  │    foto      │     │   imagen     │     │  RGB         │                │
│  └──────────────┘     └──────────────┘     └──────┬───────┘                │
│                                                    │                        │
│                                                    ▼                        │
│                              ┌─────────────────────────────────┐            │
│                              │     MindSpore Lite              │            │
│                              │     plant_disease_model.ms      │            │
│                              │     (~15 MB)                    │            │
│                              │                                 │            │
│                              │     Input: [1, 224, 224, 3]     │            │
│                              │     Output: [1, 38] softmax     │            │
│                              └─────────────────────────────────┘            │
│                                              │                              │
│                                              ▼                              │
│                              ┌─────────────────────────────────┐            │
│                              │  Resultado:                     │            │
│                              │  "Tomato___Early_blight"        │            │
│                              │  Confianza: 94.5%               │            │
│                              └─────────────────────────────────┘            │
│                                              │                              │
│                                              ▼                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Sistema RAG Existente                             │   │
│  │                                                                      │   │
│  │  Query: "Tizón temprano en tomate tratamiento control"              │   │
│  │                    │                                                 │   │
│  │                    ▼                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐    │   │
│  │  │ SemanticSearchHelper.findTopKContexts()                      │    │   │
│  │  │ → Busca en agrochat_knowledge_base.json                      │    │   │
│  │  │ → Encuentra entrada sobre tizón temprano                     │    │   │
│  │  └─────────────────────────────────────────────────────────────┘    │   │
│  │                    │                                                 │   │
│  │                    ▼                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐    │   │
│  │  │ LlamaService.generate() / GroqService.chat()                 │    │   │
│  │  │ → Genera respuesta personalizada                             │    │   │
│  │  └─────────────────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                              │                              │
│                                              ▼                              │
│                              ┌─────────────────────────────────┐            │
│                              │  Respuesta al Usuario:          │            │
│                              │                                 │            │
│                              │  "Tu tomate tiene TIZÓN         │            │
│                              │   TEMPRANO (Alternaria solani)  │            │
│                              │                                 │            │
│                              │   Recomendaciones:              │            │
│                              │   1. Eliminar hojas afectadas   │            │
│                              │   2. Aplicar fungicida cúprico  │            │
│                              │   3. Evitar mojar el follaje"   │            │
│                              └─────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🌿 Dataset y Modelo

### PlantVillage Dataset

**Fuente:** https://www.kaggle.com/datasets/emmarex/plantdisease

| Característica | Valor |
|----------------|-------|
| Total imágenes | 54,306 |
| Cultivos | 14 |
| Clases | 38 (sano + enfermedades) |
| Resolución | 256x256 |
| Formato | JPG |

### Clases Disponibles (38 total)

```
┌─────────────────────────────────────────────────────────────────┐
│ CULTIVO          │ ENFERMEDADES                                 │
├─────────────────────────────────────────────────────────────────┤
│ 🍎 Manzana       │ Apple_scab, Black_rot, Cedar_rust, Healthy  │
│ 🫐 Arándano      │ Healthy                                      │
│ 🍒 Cereza        │ Powdery_mildew, Healthy                     │
│ 🌽 Maíz          │ Cercospora_Gray_leaf, Common_rust,          │
│                  │ Northern_Blight, Healthy                     │
│ 🍇 Uva           │ Black_rot, Esca, Leaf_blight, Healthy       │
│ 🍊 Naranja       │ Citrus_greening                             │
│ 🍑 Durazno       │ Bacterial_spot, Healthy                     │
│ 🌶️ Pimiento      │ Bacterial_spot, Healthy                     │
│ 🥔 Papa          │ Early_blight, Late_blight, Healthy          │
│ 🍓 Fresa         │ Leaf_scorch, Healthy                        │
│ 🍅 Tomate        │ Bacterial_spot, Early_blight, Late_blight,  │
│                  │ Leaf_Mold, Septoria_leaf, Spider_mites,     │
│                  │ Target_Spot, Mosaic_virus, Yellow_Curl,     │
│                  │ Healthy                                      │
│ 🌱 Calabaza      │ Powdery_mildew                              │
└─────────────────────────────────────────────────────────────────┘
```

### Modelo Pre-entrenado

**Recomendado:** MobileNetV2 fine-tuned en PlantVillage

| Característica | Valor |
|----------------|-------|
| Arquitectura | MobileNetV2 |
| Precisión | 99.2% (validación) |
| Tamaño original | 14 MB (TensorFlow) |
| Tamaño MindSpore | ~10-15 MB (estimado) |
| Input | [1, 224, 224, 3] float32 |
| Output | [1, 38] softmax |
| Inferencia | 50-100ms (CPU), 10-20ms (NPU) |

**Fuentes del modelo:**
- TensorFlow Hub: https://tfhub.dev/google/cropnet/classifier/cassava_disease_V1/2
- Kaggle: https://www.kaggle.com/code/emmarex/plant-disease-detection-using-keras
- GitHub: https://github.com/imskr/Plant_Disease_Detection

---

## 📁 Estructura de Archivos Nuevos

```
app/src/main/
├── java/edu/unicauca/app/agrochat/
│   ├── vision/                          # 📁 NUEVO
│   │   ├── PlantDiseaseClassifier.kt    # Wrapper MindSpore para clasificación
│   │   └── CameraHelper.kt              # Manejo de CameraX
│   └── MainActivity.kt                  # Agregar modo cámara
├── assets/
│   ├── plant_disease_model.ms           # 📄 NUEVO (~15 MB)
│   ├── plant_disease_labels.json        # 📄 NUEVO (mapeo clases)
│   └── agrochat_knowledge_base.json     # Expandir con enfermedades
└── res/
    └── drawable/
        └── ic_camera.xml                # 📄 NUEVO icono
```

---

## 🔧 Implementación Paso a Paso

### Fase 1: Preparar el Modelo (1 día)

#### 1.1 Descargar modelo pre-entrenado

```bash
# Opción A: Descargar de Kaggle (requiere cuenta)
kaggle datasets download -d emmarex/plantdisease
unzip plantdisease.zip -d plantvillage_data/

# Opción B: Usar modelo pre-entrenado de TensorFlow Hub
pip install tensorflow tensorflow-hub
```

#### 1.2 Convertir a MindSpore Lite

```python
# convert_to_mindspore.py
import tensorflow as tf
import mindspore as ms
from mindspore_lite import Converter

# Cargar modelo TensorFlow
model = tf.keras.models.load_model('plant_disease_model.h5')

# Guardar como SavedModel
model.save('saved_model/')

# Convertir a MindSpore Lite
converter = Converter()
converter.convert(
    fmk_type="TF",
    model_file="saved_model/",
    output_file="plant_disease_model",
    config_file=None
)

print("Modelo convertido: plant_disease_model.ms")
```

#### 1.3 Crear archivo de etiquetas

```json
// plant_disease_labels.json
{
  "labels": [
    {"id": 0, "name": "Apple___Apple_scab", "display": "Sarna del manzano", "crop": "Manzana"},
    {"id": 1, "name": "Apple___Black_rot", "display": "Podredumbre negra", "crop": "Manzana"},
    {"id": 2, "name": "Apple___Cedar_apple_rust", "display": "Roya del manzano", "crop": "Manzana"},
    {"id": 3, "name": "Apple___healthy", "display": "Manzana sana", "crop": "Manzana"},
    {"id": 4, "name": "Corn___Cercospora_leaf_spot", "display": "Mancha foliar Cercospora", "crop": "Maíz"},
    {"id": 5, "name": "Corn___Common_rust", "display": "Roya común del maíz", "crop": "Maíz"},
    {"id": 6, "name": "Corn___Northern_Leaf_Blight", "display": "Tizón norteño del maíz", "crop": "Maíz"},
    {"id": 7, "name": "Corn___healthy", "display": "Maíz sano", "crop": "Maíz"},
    {"id": 8, "name": "Grape___Black_rot", "display": "Podredumbre negra uva", "crop": "Uva"},
    {"id": 9, "name": "Grape___Esca", "display": "Esca de la vid", "crop": "Uva"},
    {"id": 10, "name": "Grape___Leaf_blight", "display": "Tizón foliar uva", "crop": "Uva"},
    {"id": 11, "name": "Grape___healthy", "display": "Uva sana", "crop": "Uva"},
    {"id": 12, "name": "Potato___Early_blight", "display": "Tizón temprano papa", "crop": "Papa"},
    {"id": 13, "name": "Potato___Late_blight", "display": "Tizón tardío papa", "crop": "Papa"},
    {"id": 14, "name": "Potato___healthy", "display": "Papa sana", "crop": "Papa"},
    {"id": 15, "name": "Tomato___Bacterial_spot", "display": "Mancha bacteriana tomate", "crop": "Tomate"},
    {"id": 16, "name": "Tomato___Early_blight", "display": "Tizón temprano tomate", "crop": "Tomate"},
    {"id": 17, "name": "Tomato___Late_blight", "display": "Tizón tardío tomate", "crop": "Tomate"},
    {"id": 18, "name": "Tomato___Leaf_Mold", "display": "Moho foliar tomate", "crop": "Tomate"},
    {"id": 19, "name": "Tomato___Septoria_leaf_spot", "display": "Septoriosis tomate", "crop": "Tomate"},
    {"id": 20, "name": "Tomato___Spider_mites", "display": "Ácaros araña tomate", "crop": "Tomate"},
    {"id": 21, "name": "Tomato___Target_Spot", "display": "Mancha diana tomate", "crop": "Tomate"},
    {"id": 22, "name": "Tomato___Mosaic_virus", "display": "Virus mosaico tomate", "crop": "Tomate"},
    {"id": 23, "name": "Tomato___Yellow_Leaf_Curl", "display": "Rizado amarillo tomate", "crop": "Tomate"},
    {"id": 24, "name": "Tomato___healthy", "display": "Tomate sano", "crop": "Tomate"}
  ],
  "version": "1.0",
  "total_classes": 38
}
```

---

### Fase 2: Código Android (1-2 días)

#### 2.1 PlantDiseaseClassifier.kt

```kotlin
// app/src/main/java/edu/unicauca/app/agrochat/vision/PlantDiseaseClassifier.kt
package edu.unicauca.app.agrochat.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.mindspore.lite.LiteSession
import com.mindspore.lite.MSTensor
import com.mindspore.lite.config.MSConfig
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class DiseaseResult(
    val id: Int,
    val name: String,
    val displayName: String,
    val crop: String,
    val confidence: Float,
    val isHealthy: Boolean
)

class PlantDiseaseClassifier(private val context: Context) {
    
    companion object {
        private const val TAG = "PlantDiseaseClassifier"
        private const val MODEL_FILE = "plant_disease_model.ms"
        private const val LABELS_FILE = "plant_disease_labels.json"
        private const val INPUT_SIZE = 224
        private const val PIXEL_SIZE = 3
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f
    }
    
    private var session: LiteSession? = null
    private var labels: List<LabelInfo> = emptyList()
    private var isInitialized = false
    
    data class LabelInfo(
        val id: Int,
        val name: String,
        val display: String,
        val crop: String
    )
    
    fun initialize(): Boolean {
        return try {
            // Cargar modelo
            val modelBuffer = loadModelFile()
            val config = MSConfig()
            config.init()
            
            session = LiteSession()
            session?.init(config)
            session?.compileGraph(modelBuffer)
            
            // Cargar etiquetas
            labels = loadLabels()
            
            isInitialized = true
            Log.d(TAG, "Clasificador inicializado con ${labels.size} clases")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando clasificador: ${e.message}", e)
            false
        }
    }
    
    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    private fun loadLabels(): List<LabelInfo> {
        val jsonString = context.assets.open(LABELS_FILE).bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        val labelsArray = jsonObject.getJSONArray("labels")
        
        return (0 until labelsArray.length()).map { i ->
            val label = labelsArray.getJSONObject(i)
            LabelInfo(
                id = label.getInt("id"),
                name = label.getString("name"),
                display = label.getString("display"),
                crop = label.getString("crop")
            )
        }
    }
    
    fun classify(bitmap: Bitmap): DiseaseResult? {
        if (!isInitialized) {
            Log.e(TAG, "Clasificador no inicializado")
            return null
        }
        
        return try {
            // Preprocesar imagen
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            val inputBuffer = preprocessImage(resizedBitmap)
            
            // Ejecutar inferencia
            val inputTensor = session?.getInputsByTensorName("input")?.firstOrNull()
            inputTensor?.setData(inputBuffer)
            
            session?.runGraph()
            
            // Obtener resultado
            val outputTensor = session?.getOutputsByTensorName("output")?.firstOrNull()
            val outputData = outputTensor?.floatData ?: return null
            
            // Encontrar clase con mayor probabilidad
            var maxIndex = 0
            var maxProb = outputData[0]
            for (i in 1 until outputData.size) {
                if (outputData[i] > maxProb) {
                    maxProb = outputData[i]
                    maxIndex = i
                }
            }
            
            val labelInfo = labels.getOrNull(maxIndex) ?: return null
            
            DiseaseResult(
                id = labelInfo.id,
                name = labelInfo.name,
                displayName = labelInfo.display,
                crop = labelInfo.crop,
                confidence = maxProb,
                isHealthy = labelInfo.name.contains("healthy", ignoreCase = true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error clasificando imagen: ${e.message}", e)
            null
        }
    }
    
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        
        for (pixelValue in intValues) {
            // Normalizar a [-1, 1]
            byteBuffer.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            byteBuffer.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            byteBuffer.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
        }
        
        return byteBuffer
    }
    
    fun release() {
        session?.free()
        session = null
        isInitialized = false
    }
}
```

#### 2.2 CameraHelper.kt

```kotlin
// app/src/main/java/edu/unicauca/app/agrochat/vision/CameraHelper.kt
package edu.unicauca.app.agrochat.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "CameraHelper"
    }
    
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                onReady()
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando cámara: ${e.message}", e)
            }
            
        }, ContextCompat.getMainExecutor(context))
    }
    
    fun captureImage(onCaptured: (Bitmap?) -> Unit) {
        val imageCapture = imageCapture ?: return
        
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()
                    onCaptured(bitmap)
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Error capturando imagen: ${exception.message}", exception)
                    onCaptured(null)
                }
            }
        )
    }
    
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        
        // Rotar si es necesario
        val rotation = imageProxy.imageInfo.rotationDegrees
        return if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }
    
    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
```

#### 2.3 UI en MainActivity (fragmento a agregar)

```kotlin
// Agregar a MainActivity.kt

// Estado para modo cámara
private var showCamera by mutableStateOf(false)
private var capturedBitmap by mutableStateOf<Bitmap?>(null)
private var diseaseResult by mutableStateOf<DiseaseResult?>(null)
private var plantDiseaseClassifier: PlantDiseaseClassifier? = null

// En onCreate o init
private fun initializeVision() {
    plantDiseaseClassifier = PlantDiseaseClassifier(this)
    lifecycleScope.launch(Dispatchers.IO) {
        val success = plantDiseaseClassifier?.initialize() ?: false
        Log.d(TAG, "Clasificador de plantas inicializado: $success")
    }
}

// Composable para el botón de cámara
@Composable
fun CameraButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = AgroColors.Accent,
        modifier = Modifier.padding(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = "Diagnosticar planta",
            tint = Color.White
        )
    }
}

// Composable para la pantalla de cámara
@Composable
fun CameraScreen(
    onCapture: (Bitmap) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraHelper = remember { CameraHelper(context) }
    
    DisposableEffect(Unit) {
        cameraHelper.startCamera(lifecycleOwner, previewView) {}
        onDispose { cameraHelper.stopCamera() }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        
        // Botón cerrar
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
        }
        
        // Botón capturar
        FloatingActionButton(
            onClick = {
                cameraHelper.captureImage { bitmap ->
                    bitmap?.let { onCapture(it) }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            containerColor = AgroColors.Primary
        ) {
            Icon(Icons.Default.Camera, "Tomar foto", tint = Color.White)
        }
        
        // Guía visual
        Text(
            text = "📸 Enfoca la hoja afectada",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

// Función para procesar diagnóstico
private fun diagnosePlant(bitmap: Bitmap) {
    lifecycleScope.launch(Dispatchers.IO) {
        val result = plantDiseaseClassifier?.classify(bitmap)
        
        withContext(Dispatchers.Main) {
            diseaseResult = result
            
            if (result != null) {
                // Crear query para RAG
                val query = if (result.isHealthy) {
                    "Cuidados preventivos para ${result.crop} sano"
                } else {
                    "${result.displayName} tratamiento control ${result.crop}"
                }
                
                // Usar RAG existente
                handleUserQuery(query, prefixMessage = "📸 Diagnóstico: ${result.displayName}\n\n")
            }
        }
    }
}
```

---

### Fase 3: Expandir Base de Conocimiento (2-3 horas)

Agregar estas entradas a `agrochat_knowledge_base.json`:

```json
{
  "id": 100,
  "category": "enfermedad",
  "questions": ["Tizón temprano en tomate", "Alternaria en tomate", "Manchas concéntricas tomate", "Early blight tomate"],
  "answer": "**Tizón temprano (Alternaria solani)** 🍂\n\n**Síntomas:**\n• Manchas marrones con anillos concéntricos\n• Empiezan en hojas viejas (abajo)\n• Pueden afectar tallos y frutos\n\n**Control:**\n• Eliminar hojas afectadas inmediatamente\n• Fungicida cúprico cada 7-10 días\n• Mancozeb o Clorotalonil\n• Rotación de cultivos 2-3 años\n\n**Prevención:**\n• Evitar mojar follaje\n• Tutoreo para ventilación\n• Mulching para evitar salpicaduras",
  "keywords": ["tizon", "temprano", "alternaria", "manchas", "concentricas", "tomate", "early", "blight"]
},
{
  "id": 101,
  "category": "enfermedad",
  "questions": ["Tizón tardío en papa", "Phytophthora papa", "Gota de la papa", "Late blight papa"],
  "answer": "**Tizón tardío (Phytophthora infestans)** 🥔\n\n**Síntomas:**\n• Manchas acuosas oscuras\n• Micelio blanco bajo hojas (humedad)\n• Avance rápido en clima frío-húmedo\n• Olor característico\n\n**Control urgente:**\n• Fungicida sistémico (Metalaxil + Mancozeb)\n• Aplicar cada 5-7 días si hay lluvia\n• Destruir plantas muy afectadas\n\n**Prevención:**\n• Variedades resistentes\n• Evitar riego por aspersión\n• Aporque alto",
  "keywords": ["tizon", "tardio", "phytophthora", "gota", "papa", "late", "blight"]
},
{
  "id": 102,
  "category": "enfermedad",
  "questions": ["Roya del maíz", "Puccinia maíz", "Manchas naranjas maíz", "Rust maíz"],
  "answer": "**Roya común del maíz (Puccinia sorghi)** 🌽\n\n**Síntomas:**\n• Pústulas naranjas en hojas\n• Polvo naranja al tocar\n• Hojas se secan prematuramente\n\n**Control:**\n• Fungicidas triazoles (Tebuconazol)\n• Aplicar al inicio de síntomas\n• Variedades resistentes\n\n**Prevención:**\n• Eliminación de rastrojos\n• Siembra en época adecuada\n• Evitar siembras tardías",
  "keywords": ["roya", "maiz", "puccinia", "naranja", "rust", "comun"]
},
{
  "id": 103,
  "category": "enfermedad",
  "questions": ["Mancha bacteriana tomate", "Xanthomonas tomate", "Bacterial spot tomate"],
  "answer": "**Mancha bacteriana (Xanthomonas)** 🍅\n\n**Síntomas:**\n• Manchas acuosas pequeñas\n• Bordes amarillos\n• Centro se oscurece y seca\n• Puede afectar frutos\n\n**Control:**\n• NO hay cura efectiva\n• Cobre + Mancozeb preventivo\n• Eliminar plantas muy afectadas\n• Evitar trabajo con plantas mojadas\n\n**Prevención:**\n• Semilla certificada\n• Desinfectar herramientas\n• Rotación 2-3 años",
  "keywords": ["bacteria", "xanthomonas", "mancha", "tomate", "bacterial", "spot"]
},
{
  "id": 104,
  "category": "enfermedad",
  "questions": ["Virus mosaico tomate", "TMV tomate", "Hojas arrugadas tomate", "Mosaic virus tomate"],
  "answer": "**Virus del mosaico del tomate (TMV)** 🍅\n\n**Síntomas:**\n• Mosaico verde claro/oscuro en hojas\n• Hojas arrugadas o deformes\n• Crecimiento reducido\n• Frutos pequeños o deformes\n\n**Control:**\n• NO hay cura\n• Eliminar plantas infectadas\n• NO compostar material enfermo\n\n**Prevención crítica:**\n• Desinfectar manos con leche 1:4\n• No fumar cerca (tabaco porta virus)\n• Variedades resistentes (buscar código 'T')",
  "keywords": ["virus", "mosaico", "tmv", "arrugadas", "mosaic", "tomate"]
},
{
  "id": 105,
  "category": "enfermedad",
  "questions": ["Septoriosis tomate", "Septoria lycopersici", "Manchas pequeñas tomate"],
  "answer": "**Septoriosis (Septoria lycopersici)** 🍅\n\n**Síntomas:**\n• Manchas pequeñas circulares\n• Centro gris con puntos negros\n• Comienza en hojas inferiores\n• Defoliación severa\n\n**Control:**\n• Fungicidas protectores (Mancozeb)\n• Eliminar hojas afectadas\n• Mejorar ventilación\n\n**Prevención:**\n• Mulching plástico o paja\n• Riego por goteo\n• Rotación de cultivos",
  "keywords": ["septoria", "septoriosis", "manchas", "pequenas", "tomate"]
},
{
  "id": 106,
  "category": "diagnostico",
  "questions": ["Mi planta está sana", "Planta sana", "No tiene enfermedad", "Healthy plant"],
  "answer": "**¡Tu planta se ve saludable!** 🌱✅\n\n**Mantén estas prácticas:**\n\n✓ Riego adecuado (mañanas)\n✓ Fertilización balanceada\n✓ Buena ventilación\n✓ Revisar semanalmente\n\n**Prevención de enfermedades:**\n• Rotación de cultivos\n• Eliminar malezas\n• Herramientas limpias\n• Variedades resistentes\n\n¡Sigue así! 🎉",
  "keywords": ["sana", "saludable", "healthy", "bien", "normal"]
}
```

---

### Fase 4: Dependencias y Permisos

#### 4.1 Agregar a app/build.gradle.kts

```kotlin
dependencies {
    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
}
```

#### 4.2 Agregar a AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

---

## 📅 Cronograma de Implementación

| Día | Tarea | Tiempo |
|-----|-------|--------|
| **1** | Descargar dataset PlantVillage | 1 hora |
| **1** | Entrenar/descargar modelo MobileNetV2 | 2-4 horas |
| **1** | Convertir modelo a MindSpore Lite | 2-4 horas |
| **2** | Implementar PlantDiseaseClassifier.kt | 3 horas |
| **2** | Implementar CameraHelper.kt | 2 horas |
| **2** | Integrar UI de cámara en MainActivity | 3 horas |
| **3** | Expandir knowledge base (6+ enfermedades) | 2 horas |
| **3** | Regenerar embeddings | 30 min |
| **3** | Testing y ajustes | 3 horas |
| **Total** | | **~2-3 días** |

---

## ✅ Checklist de Verificación

- [ ] Modelo `plant_disease_model.ms` en assets (~15 MB)
- [ ] Archivo `plant_disease_labels.json` en assets
- [ ] `PlantDiseaseClassifier.kt` creado y probado
- [ ] `CameraHelper.kt` creado y probado
- [ ] UI de cámara integrada en MainActivity
- [ ] Permisos de cámara configurados
- [ ] Dependencias CameraX agregadas
- [ ] 6+ enfermedades agregadas a knowledge base
- [ ] Embeddings regenerados
- [ ] Pruebas en dispositivo real

---

## 🧪 Testing

### Imágenes de prueba

Descargar imágenes de prueba de PlantVillage:
- https://www.kaggle.com/datasets/emmarex/plantdisease (muestra)

### Comandos de log

```bash
# Ver clasificación
adb logcat -s PlantDiseaseClassifier

# Ver flujo completo
adb logcat -s PlantDiseaseClassifier SemanticSearchHelper LlamaService
```

### Casos de prueba

| Imagen | Resultado Esperado |
|--------|-------------------|
| Hoja tomate con manchas concéntricas | Tizón temprano (>90%) |
| Hoja papa oscura acuosa | Tizón tardío (>85%) |
| Hoja maíz con pústulas naranjas | Roya común (>90%) |
| Hoja tomate verde sana | Healthy (>95%) |

---

## 🚀 Próximos Pasos Sugeridos

1. **Agregar más cultivos colombianos:**
   - Café (roya, broca)
   - Plátano (sigatoka)
   - Yuca (mosaico)

2. **Mejorar precisión:**
   - Fine-tuning con imágenes locales
   - Recolectar fotos de agricultores

3. **Funciones adicionales:**
   - Historial de diagnósticos
   - Geolocalización de enfermedades
   - Modo sin cámara (galería)

---

## 📚 Referencias

- [PlantVillage Dataset](https://www.kaggle.com/datasets/emmarex/plantdisease)
- [MobileNetV2 Paper](https://arxiv.org/abs/1801.04381)
- [MindSpore Lite Docs](https://www.mindspore.cn/lite/docs/en/master/index.html)
- [CameraX Guide](https://developer.android.com/training/camerax)
- [Plant Disease Detection Research](https://arxiv.org/abs/1604.03169)

---

## 🔧 Pasos Finales para Completar

### 1. Regenerar Embeddings (obligatorio)

```bash
cd /home/user/HuaweiProject/AgroChat_Project
pip install torch transformers sentence-transformers
python generate_mindspore_compatible_embeddings.py

# Copiar el archivo generado
cp app/src/main/assets/kb_embeddings_pooler.npy app/src/main/assets/kb_embeddings.npy
```

### 2. Obtener Modelo PlantVillage (necesario para diagnóstico visual)

**Opción A: Usar script automatizado**
```bash
cd /home/user/HuaweiProject/AgroChat_Project
pip install torch torchvision onnx
python tools/prepare_plant_disease_model.py --from-pretrained
```

**Opción B: Descargar modelo pre-convertido**
- Buscar en HuggingFace: `linkanjarad/mobilenet_v2_1.0_224-plant-disease`
- O entrenar con el dataset PlantVillage de Kaggle

**Opción C: Conversión manual**
1. Descargar modelo .h5 o .pt de PlantVillage
2. Exportar a ONNX con `torch.onnx.export()`
3. Convertir a MindSpore con `converter_lite`

### 3. Compilar y probar

```bash
# Compilar APK
./gradlew assembleDebug

# Instalar en dispositivo
adb install app/build/outputs/apk/debug/app-debug.apk

# Ver logs de diagnóstico
adb logcat -s PlantDiseaseClassifier CameraHelper MainActivity
```

### 4. Verificar funcionalidad

1. Abrir la app
2. El botón de cámara (📸) debería aparecer si el modelo está cargado
3. Tomar foto de una hoja
4. Verificar clasificación y recomendaciones

---

## 📂 Archivos Creados en Esta Implementación

```
app/src/main/
├── java/edu/unicauca/app/agrochat/
│   ├── vision/
│   │   ├── PlantDiseaseClassifier.kt  ✅ NUEVO
│   │   └── CameraHelper.kt            ✅ NUEVO
│   └── MainActivity.kt                 ✅ MODIFICADO (modo CAMERA)
├── assets/
│   ├── plant_disease_labels.json       ✅ NUEVO (38 clases)
│   ├── agrochat_knowledge_base.json    ✅ MODIFICADO (+16 enfermedades)
│   └── plant_disease_model.ms          ⏳ PENDIENTE (~15 MB)
└── AndroidManifest.xml                  ✅ MODIFICADO (permiso cámara)

tools/
└── prepare_plant_disease_model.py       ✅ NUEVO (script conversión)
```

