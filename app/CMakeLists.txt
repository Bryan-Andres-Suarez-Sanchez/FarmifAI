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

# Aquí se añade ${ANDROID_ABI} para que soporte arm64-v8a y armeabi-v7a
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

# --- AQUÍ defines tu librería JNI ---
file(GLOB_RECURSE NATIVE_SOURCES
        "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/*.cpp"
)

add_library(msjni SHARED ${NATIVE_SOURCES})  # Coincidir con System.loadLibrary("msjni")

# --- Y recién después enlazas ---
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







