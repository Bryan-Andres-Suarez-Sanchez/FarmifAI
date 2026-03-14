import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Generar timestamp para nombre único de APK
val buildTimestamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

android {
    namespace = "edu.unicauca.app.agrochat"
    compileSdk = 35

    defaultConfig {
        applicationId = "edu.unicauca.app.agrochat"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                arguments.add("-DANDROID_STL=c++_shared")
            }
        }

        ndk {
            // Only arm64-v8a to reduce APK size (most modern devices)
            // Remove armeabi-v7a (~35MB savings) - can add back if needed for older devices
            abiFilters.addAll(setOf("arm64-v8a"))
        }
    }

    sourceSets {
        named("main") {
            jniLibs.srcDirs("src/main/jniLibs")
            // Si también quieres añadir 'libs':
            // jniLibs.srcDirs("src/main/jniLibs", "libs")
        }
    }
//para sincronizar
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Nombre de APK con timestamp
            applicationIdSuffix = ""
        }
    }
    
    // Nombre único para cada APK generada
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val versionName = variant.versionName ?: "1.0"
            output.outputFileName = "FarmifAI-${variant.buildType.name}-v${versionName}-${buildTimestamp}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // Excluir modelos MindSpore grandes de los assets del APK
            // (la ruta en el APK es "assets/...")
            excludes += "assets/sentence_encoder.ms"
            excludes += "assets/plant_disease_model.ms"
        }
    }

    // Esta sección externalNativeBuild se mantiene tal cual si la usas
    // para construir OTRA librería nativa (ej. msjni) con CMake.
    // No interfiere directamente con la inclusión de tus .so precompilados
    // del tokenizador si estos ya son la capa JNI.
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    ndkVersion = "27.2.12479018"
}


dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(fileTree(layout.projectDirectory.dir("libs")) {
        include("*.aar")
    })
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    
    // Material Icons Extended para más iconos
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    
    // ONNX Runtime para modelos exportados desde nlp_dev
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")
    
    // Vosk - Reconocimiento de voz offline (sin Google)
    implementation("com.alphacephei:vosk-android:0.3.47")
    
    // Similitud de texto (Jaccard/Jaro-Winkler/FuzzyScore) para fallback semántico
    implementation("org.apache.commons:commons-text:1.12.0")
    
    // CameraX para diagnóstico visual de plantas
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
