plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

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
            abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a"))
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
        // resources { // Comenta o elimina esta sección si el modelo está en assets y se usa
        //     excludes += "/assets/gpt2_model.ms"
        // }
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
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}