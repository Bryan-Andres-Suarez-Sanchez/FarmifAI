#include <jni.h>
#include <android/log.h>
#include <memory>
#include <cstring>
#include <vector>
#include <string>

// --- NUEVAS CABECERAS PARA LA API C++ DE MINDSPORE LITE ---
#include "include/api/context.h" // Para mindspore::Context, mindspore::CPUDeviceInfo
#include "include/api/model.h"   // Para mindspore::Model, mindspore::ModelType
#include "include/api/status.h"  // Para mindspore::Status, mindspore::kSuccess
#include "include/api/types.h"   // Para mindspore::DataType (aunque MSTensor lo infiere a veces)

// Si haces preprocesamiento de imágenes con LiteCV, necesitarías algo como:
// #include "include/minddata/dataset/include/lite_cv/lite_mat.h"
// #include "include/minddata/dataset/include/lite_cv/image_process.h"


#define MS_PRINT(format, ...) __android_log_print(ANDROID_LOG_INFO, "MSJNI_CPP_API", format, ##__VA_ARGS__)

// ------------------------------------------------------------------
// Entorno nativo (ahora con punteros inteligentes y clases C++)
// ------------------------------------------------------------------
struct MSNativeEnvCpp {
    std::shared_ptr<mindspore::Model>   model   = nullptr;
    std::shared_ptr<mindspore::Context> context = nullptr; // El contexto se adjunta al modelo, pero puede ser útil guardarlo
};

// ------------------------------------------------------------------
// Carga/Construye el modelo desde ByteBuffer (usando API C++)
// ------------------------------------------------------------------
extern "C"
JNIEXPORT jlong JNICALL
Java_edu_unicauca_app_agrochat_MindSporeHelper_loadModel(JNIEnv *env, jobject thiz,
                                                         jobject model_buffer, jint num_thread) {
    if (!model_buffer) {
        MS_PRINT("model_buffer is null");
        return 0;
    }
    jlong buffer_len = env->GetDirectBufferCapacity(model_buffer);
    if (buffer_len <= 0) {
        MS_PRINT("buffer capacity <= 0");
        return 0;
    }

    // Usar GetDirectBufferAddress es más eficiente si el buffer es directo.
    // La documentación de MindSpore a veces muestra CreateLocalModelBuffer, que podría hacer una copia.
    // Para eficiencia, intentemos usar el buffer directamente si es posible.
    // Si MindSpore requiere que los datos estén en un buffer propio, necesitaríamos copiar.
    auto *model_data_ptr = static_cast<char *>(env->GetDirectBufferAddress(model_buffer));
    if (!model_data_ptr) {
        MS_PRINT("GetDirectBufferAddress failed. El ByteBuffer debe ser directo.");
        // Fallback: si no es directo, necesitamos copiarlo.
        // No implementado aquí por brevedad, pero sería similar a tu localBuf original.
        return 0;
    }

    // 1. Crear y configurar el Contexto
    auto context = std::make_shared<mindspore::Context>();
    if (context == nullptr) {
        MS_PRINT("Context creation failed!");
        return 0;
    }
    context->SetThreadNum(num_thread);
    // context->SetThreadAffinity(0); // Configura afinidad si es necesario, 0 podría no ser siempre lo ideal.
    context->SetEnableParallel(false); // O true según tu modelo y necesidades

    auto &device_list = context->MutableDeviceInfo();
    auto cpu_device_info = std::make_shared<mindspore::CPUDeviceInfo>();
    cpu_device_info->SetEnableFP16(false); // O true si tu modelo y dispositivo soportan FP16
    device_list.push_back(cpu_device_info);

    // 2. Crear y construir el Modelo
    auto model = std::make_shared<mindspore::Model>();
    if (model == nullptr) {
        MS_PRINT("Model creation failed!");
        return 0;
    }

    // Construir el modelo. model_data_ptr son los bytes, buffer_len es su tamaño.
    mindspore::Status ret = model->Build(model_data_ptr, static_cast<size_t>(buffer_len), mindspore::ModelType::kMindIR, context);

    if (ret != mindspore::kSuccess) {
        MS_PRINT("Model Build failed! Error code: %d", ret.StatusCode());
        return 0;
    }

    MS_PRINT("Model built successfully using C++ API.");

    auto *env_ptr = new MSNativeEnvCpp();
    env_ptr->model = model;
    env_ptr->context = context; // Guardamos el contexto también
    return reinterpret_cast<jlong>(env_ptr);
}

// ------------------------------------------------------------------
// Inferencia con array de floats (usando API C++)
// ------------------------------------------------------------------
extern "C"
JNIEXPORT jfloatArray JNICALL
Java_edu_unicauca_app_agrochat_MindSporeHelper_runNetFloat(JNIEnv *env, jobject thiz,
                                                           jlong handle, jfloatArray jinput) {
    auto *p_env = reinterpret_cast<MSNativeEnvCpp *>(handle);
    if (!p_env || !p_env->model) {
        MS_PRINT("Invalid native handle or model.");
        return nullptr;
    }
    std::shared_ptr<mindspore::Model> model = p_env->model;

    // 1. Obtener Tensores de Entrada del Modelo
    std::vector<mindspore::MSTensor> inputs = model->GetInputs();
    if (inputs.empty()) {
        MS_PRINT("Failed to get model inputs.");
        return nullptr;
    }
    // Asumimos que el modelo tiene una sola entrada para este ejemplo
    mindspore::MSTensor &in_tensor = inputs.front();

    // 2. Verificar dimensiones y copiar datos de jinput al Tensor de Entrada
    jsize input_len = env->GetArrayLength(jinput);
    if (static_cast<size_t>(input_len) != in_tensor.ElementNum()) {
        MS_PRINT("Input size mismatch: Java array has %d elements, model expects %zu.",
                 (int)input_len, in_tensor.ElementNum());
        return nullptr;
    }

    // Obtener puntero a los datos del tensor de entrada de MindSpore
    // El tipo de dato debería ser kNumberTypeFloat32 si coincide con FloatArray
    if (in_tensor.DataType() != mindspore::DataType::kNumberTypeFloat32) {
        MS_PRINT("Model input tensor data type is not Float32. Actual: %d", static_cast<int>(in_tensor.DataType()));
        // Podrías necesitar convertir los datos o asegurar que el modelo espera floats
    }

    void *in_tensor_data = in_tensor.MutableData(); // Obtiene un puntero a los datos del tensor
    if (!in_tensor_data) {
        MS_PRINT("Failed to get mutable data from input tensor.");
        return nullptr;
    }

    jfloat *src_jinput_data = env->GetFloatArrayElements(jinput, nullptr);
    if (!src_jinput_data) {
        MS_PRINT("Failed to get float array elements.");
        return nullptr;
    }
    memcpy(in_tensor_data, src_jinput_data, input_len * sizeof(float));
    env->ReleaseFloatArrayElements(jinput, src_jinput_data, JNI_ABORT); // JNI_ABORT si no modificaste src_jinput_data

    // 3. Ejecutar Inferencia
    std::vector<mindspore::MSTensor> outputs;
    mindspore::Status status = model->Predict(inputs, &outputs); // Pasamos los inputs (que ahora tienen los datos)

    if (status != mindspore::kSuccess || outputs.empty()) {
        MS_PRINT("Model Predict failed or no outputs. Error: %d", status.StatusCode());
        return nullptr;
    }

    // 4. Procesar Tensores de Salida
    // Asumimos una sola salida para este ejemplo
    const mindspore::MSTensor &out_tensor = outputs.front();
    if (out_tensor.DataType() != mindspore::DataType::kNumberTypeFloat32) {
        MS_PRINT("Model output tensor data type is not Float32. Actual: %d", static_cast<int>(out_tensor.DataType()));
        return nullptr; // O manejar conversión
    }

    const float *out_data_ptr = static_cast<const float *>(out_tensor.Data().get()); // Usar Data().get() para const
    if (!out_data_ptr) {
        MS_PRINT("Failed to get data from output tensor.");
        return nullptr;
    }
    size_t out_elements = out_tensor.ElementNum();

    jfloatArray joutput_array = env->NewFloatArray(static_cast<jsize>(out_elements));
    if (!joutput_array) {
        MS_PRINT("Failed to create new jfloatArray.");
        return nullptr;
    }
    env->SetFloatArrayRegion(joutput_array, 0, static_cast<jsize>(out_elements), out_data_ptr);

    return joutput_array;
}

// ------------------------------------------------------------------
// Inferencia con array de ints (ej: tokens para SLM, usando API C++)
// ------------------------------------------------------------------
extern "C"
JNIEXPORT jfloatArray JNICALL
Java_edu_unicauca_app_agrochat_MindSporeHelper_runNetIds(JNIEnv *env, jobject thiz,
                                                         jlong handle, jintArray jids) {
    auto *p_env = reinterpret_cast<MSNativeEnvCpp *>(handle);
    if (!p_env || !p_env->model) {
        MS_PRINT("Invalid native handle or model.");
        return nullptr;
    }
    std::shared_ptr<mindspore::Model> model = p_env->model;

    // 1. Obtener Tensores de Entrada del Modelo
    std::vector<mindspore::MSTensor> inputs = model->GetInputs();
    if (inputs.empty()) {
        MS_PRINT("Failed to get model inputs.");
        return nullptr;
    }
    mindspore::MSTensor &in_tensor = inputs.front();

    // 2. Verificar dimensiones y copiar datos de jids al Tensor de Entrada
    jsize input_len = env->GetArrayLength(jids);
    if (static_cast<size_t>(input_len) != in_tensor.ElementNum()) {
        MS_PRINT("Input ID size mismatch: Java array has %d elements, model expects %zu.",
                 (int)input_len, in_tensor.ElementNum());
        return nullptr;
    }

    // El tipo de dato del tensor de entrada del modelo DEBE ser kNumberTypeInt32 para IDs de token
    if (in_tensor.DataType() != mindspore::DataType::kNumberTypeInt32) {
        MS_PRINT("Model input tensor data type is not Int32 as expected for token IDs. Actual: %d", static_cast<int>(in_tensor.DataType()));
        // Esto es un error crítico para modelos SLM que esperan IDs int32
        return nullptr;
    }

    void *in_tensor_data = in_tensor.MutableData();
    if (!in_tensor_data) {
        MS_PRINT("Failed to get mutable data from input tensor.");
        return nullptr;
    }

    jint *src_jids_data = env->GetIntArrayElements(jids, nullptr);
    if (!src_jids_data) {
        MS_PRINT("Failed to get int array elements.");
        return nullptr;
    }
    memcpy(in_tensor_data, src_jids_data, input_len * sizeof(jint)); // jint es usualmente int32_t
    env->ReleaseIntArrayElements(jids, src_jids_data, JNI_ABORT);

    // 3. Ejecutar Inferencia
    std::vector<mindspore::MSTensor> outputs;
    mindspore::Status status = model->Predict(inputs, &outputs);

    if (status != mindspore::kSuccess || outputs.empty()) {
        MS_PRINT("Model Predict failed or no outputs. Error: %d", status.StatusCode());
        return nullptr;
    }

    // 4. Procesar Tensores de Salida (usualmente logits flotantes para SLM)
    const mindspore::MSTensor &out_tensor = outputs.front();
    if (out_tensor.DataType() != mindspore::DataType::kNumberTypeFloat32) {
        MS_PRINT("Model output tensor data type is not Float32 as expected for SLM logits. Actual: %d", static_cast<int>(out_tensor.DataType()));
        return nullptr;
    }

    const float *out_data_ptr = static_cast<const float *>(out_tensor.Data().get());
    if (!out_data_ptr) {
        MS_PRINT("Failed to get data from output tensor.");
        return nullptr;
    }
    size_t out_elements = out_tensor.ElementNum();

    jfloatArray joutput_array = env->NewFloatArray(static_cast<jsize>(out_elements));
    if (!joutput_array) {
        MS_PRINT("Failed to create new jfloatArray for output.");
        return nullptr;
    }
    env->SetFloatArrayRegion(joutput_array, 0, static_cast<jsize>(out_elements), out_data_ptr);

    return joutput_array;
}

// ------------------------------------------------------------------
// Unload (usando API C++)
// ------------------------------------------------------------------
extern "C"
JNIEXPORT jboolean JNICALL
Java_edu_unicauca_app_agrochat_MindSporeHelper_unloadModel(JNIEnv *env, jobject thiz, jlong handle) {
    auto *p_env = reinterpret_cast<MSNativeEnvCpp *>(handle);
    if (!p_env) {
        MS_PRINT("Invalid native handle for unload.");
        return JNI_FALSE;
    }

    // Los std::shared_ptr se encargarán de liberar la memoria del modelo y contexto
    // cuando salgan de alcance o se reseteen, si p_env->model y p_env->context
    // son los únicos dueños. Al hacer delete p_env, se destruyen los shared_ptr.
    delete p_env;
    MS_PRINT("Native environment (C++ API) unloaded.");
    return JNI_TRUE;
}