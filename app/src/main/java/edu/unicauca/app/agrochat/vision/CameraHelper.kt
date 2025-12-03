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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Helper para manejar CameraX y la captura de imágenes.
 * 
 * Proporciona una interfaz simplificada para:
 * - Iniciar la cámara con preview
 * - Capturar imágenes como Bitmap
 * - Liberar recursos
 */
class CameraHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "CameraHelper"
    }
    
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var isStarted = false
    
    /**
     * Callback para cuando la cámara está lista
     */
    interface CameraCallback {
        fun onCameraReady()
        fun onCameraError(message: String)
    }
    
    /**
     * Callback para captura de imagen
     */
    interface CaptureCallback {
        fun onImageCaptured(bitmap: Bitmap)
        fun onCaptureError(message: String)
    }
    
    /**
     * Inicia la cámara y conecta el preview.
     * 
     * @param lifecycleOwner El lifecycle owner (Activity/Fragment)
     * @param previewView El view donde se mostrará el preview
     * @param callback Callback para notificar cuando la cámara está lista
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        callback: CameraCallback? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                // Configurar Preview
                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                
                // Configurar ImageCapture
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()
                
                // Usar cámara trasera por defecto
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                // Unbind antes de bind
                cameraProvider?.unbindAll()
                
                // Bind use cases
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                
                isStarted = true
                Log.i(TAG, "✅ Cámara iniciada correctamente")
                callback?.onCameraReady()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando cámara: ${e.message}", e)
                callback?.onCameraError("No se pudo iniciar la cámara: ${e.message}")
            }
            
        }, ContextCompat.getMainExecutor(context))
    }
    
    /**
     * Captura una imagen y la devuelve como Bitmap.
     * 
     * @param callback Callback con el bitmap resultante o error
     */
    fun captureImage(callback: CaptureCallback) {
        val imageCapture = imageCapture
        
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture no inicializado")
            callback.onCaptureError("La cámara no está lista")
            return
        }
        
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val bitmap = imageProxyToBitmap(imageProxy)
                        imageProxy.close()
                        
                        if (bitmap != null) {
                            Log.d(TAG, "Imagen capturada: ${bitmap.width}x${bitmap.height}")
                            callback.onImageCaptured(bitmap)
                        } else {
                            callback.onCaptureError("No se pudo procesar la imagen")
                        }
                    } catch (e: Exception) {
                        imageProxy.close()
                        Log.e(TAG, "Error procesando imagen: ${e.message}", e)
                        callback.onCaptureError("Error procesando imagen: ${e.message}")
                    }
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Error capturando imagen: ${exception.message}", exception)
                    callback.onCaptureError("Error capturando imagen: ${exception.message}")
                }
            }
        )
    }
    
    /**
     * Convierte ImageProxy a Bitmap, aplicando la rotación correcta.
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            
            if (bitmap == null) {
                Log.e(TAG, "BitmapFactory devolvió null")
                return null
            }
            
            // Aplicar rotación si es necesario
            val rotation = imageProxy.imageInfo.rotationDegrees
            if (rotation != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo ImageProxy a Bitmap: ${e.message}", e)
            null
        }
    }
    
    /**
     * Verifica si la cámara está activa
     */
    fun isActive(): Boolean = isStarted && cameraProvider != null
    
    /**
     * Detiene la cámara y libera recursos
     */
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageCapture = null
            isStarted = false
            Log.d(TAG, "Cámara detenida")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo cámara: ${e.message}", e)
        }
    }
    
    /**
     * Libera todos los recursos
     */
    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
        Log.d(TAG, "Recursos liberados")
    }
}
