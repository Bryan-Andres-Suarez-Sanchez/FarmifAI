cmake_minimum_required(VERSION 3.4.1)
project(MyProjectNative LANGUAGES C CXX)

set(CMAKE_VERBOSE_MAKEFILE on)

set(MINDSPORE_LITE_INSTALL_DIR
        "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/mindspore-lite-2.4.10-android-aarch64")

include_directories(
        ${MINDSPORE_LITE_INSTALL_DIR}
        ${MINDSPORE_LITE_INSTALL_DIR}/runtime
        ${MINDSPORE_LITE_INSTALL_DIR}/runtime/include
        ${MINDSPORE_LITE_INSTALL_DIR}/runtime/include/api
        ${MINDSPORE_LITE_INSTALL_DIR}/runtime/include/dataset
        ${MINDSPORE_LITE_INSTALL_DIR}/runtime/include/dataset/lite_cv
        ${MINDSPORE_LITE_INSTALL_DIR}/runtime/include/schema
        ${MINDSPORE_LITE_INSTALL_DIR}/runtime/include/ir/dtype
        ${MINDSPORE_LITE_INSTALL_DIR}/runtime/third_party
)

set(MINDSPORE_LITE_LIB_DIR "${MINDSPORE_LITE_INSTALL_DIR}/runtime/lib/${ANDROID_ABI}")

add_library(mindspore-lite SHARED IMPORTED)
set_target_properties(mindspore-lite PROPERTIES IMPORTED_LOCATION
        "${MINDSPORE_LITE_LIB_DIR}/libmindspore-lite.so")

add_library(minddata-lite SHARED IMPORTED)
set_target_properties(minddata-lite PROPERTIES IMPORTED_LOCATION
        "${MINDSPORE_LITE_LIB_DIR}/libminddata-lite.so")

add_library(libmindspore-lite-train SHARED IMPORTED)
set_target_properties(libmindspore-lite-train PROPERTIES IMPORTED_LOCATION
        "${MINDSPORE_LITE_LIB_DIR}/libmindspore-lite-train.so")

# --- LIBRERÍA JNI DE MINDSPORE (EXISTENTE) ---
file(GLOB_RECURSE NATIVE_SOURCES
        "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/*.cpp"
)
# La capa JNI de llama.cpp se distribuye como .so precompilado en jniLibs.
# Excluimos este archivo fuente para evitar dependencia de headers externos en build local.
list(FILTER NATIVE_SOURCES EXCLUDE REGEX ".*/llama_android_compat\\.cpp$")
add_library(msjni SHARED ${NATIVE_SOURCES}) # Coincidir con System.loadLibrary("msjni")

# llama.cpp b10516 y puente JNI compilado contra la misma API. Los backends
# CPU oficiales se empaquetan desde src/main/jniLibs y se cargan en runtime.
set(LLAMA_JNI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}")
add_library(llama_prebuilt SHARED IMPORTED)
set_target_properties(llama_prebuilt PROPERTIES
        IMPORTED_LOCATION "${LLAMA_JNI_DIR}/libllama.so")
add_library(ggml_prebuilt SHARED IMPORTED)
set_target_properties(ggml_prebuilt PROPERTIES
        IMPORTED_LOCATION "${LLAMA_JNI_DIR}/libggml.so")
add_library(ggml_base_prebuilt SHARED IMPORTED)
set_target_properties(ggml_base_prebuilt PROPERTIES
        IMPORTED_LOCATION "${LLAMA_JNI_DIR}/libggml-base.so")

add_library(llama-android SHARED
        "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/llama_android_compat.cpp")
target_include_directories(llama-android PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/llama_include")
target_link_libraries(llama-android PRIVATE
        llama_prebuilt
        ggml_prebuilt
        ggml_base_prebuilt
        android
        log)

# +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
# +++                  SECCIÓN AÑADIDA PARA EL TOKENIZADOR                +++
# +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

# 1. Definir tu librería precompilada libhf_tokenizer_android.so
#    Esta es la librería Rust/C que compilaste y está en src/main/jniLibs/
add_library(hf_tokenizer_android_prebuilt SHARED IMPORTED)  # Dale un nombre de target único

set_target_properties(hf_tokenizer_android_prebuilt PROPERTIES
        IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libhf_tokenizer_android.so"
        # ${CMAKE_CURRENT_SOURCE_DIR} aquí es app/src/main/cpp/ (si el CMakeLists.txt está ahí)
        # Entonces, ../jniLibs/${ANDROID_ABI}/ es app/src/main/jniLibs/${ANDROID_ABI}/
)
# --- ENLACE DE LIBRERÍAS (EXISTENTE) ---
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(msjni
        PRIVATE
        mindspore-lite
        minddata-lite
        libmindspore-lite-train
        android
        ${log-lib}
        ${jnigraphics-lib}
)








