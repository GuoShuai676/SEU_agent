plugins {
    alias(libs.plugins.android.application)
}

val onnxRuntimeDir = layout.buildDirectory.dir("onnxruntime")
val extractOnnxRuntime = tasks.register<Copy>("extractOnnxRuntime") {
    val coords = "com.microsoft.onnxruntime:onnxruntime-android:${libs.versions.onnxruntime.get()}"
    val aar = configurations.detachedConfiguration(dependencies.create(coords)).singleFile
    from(zipTree(aar)) {
        include("jni/**", "headers/**")
    }
    into(onnxRuntimeDir)
}
tasks.named("preBuild") {
    dependsOn(extractOnnxRuntime)
}
tasks.matching { it.name.startsWith("configureCMake") }.configureEach {
    dependsOn(extractOnnxRuntime)
}

android {
    namespace = "com.example.seu_agent"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.seu_agent"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.material)
    implementation(libs.blurview)

    implementation(libs.okhttp)
    implementation("io.noties.markwon:core:4.6.2")
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.tensorflow.lite) {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
    implementation(libs.onnxruntime.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
