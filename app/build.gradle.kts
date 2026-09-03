plugins {
    alias(libs.plugins.android.application)
}

// ---- ONNX Runtime：解压 AAR 里的原生库(jni/**)与头文件(headers/**) ----
// 供 CMake 的 bge.cpp 链接（ONNX Runtime C API），目录传给 externalNativeBuild
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
// 确保 CMake 配置/编译在解压之后（否则头文件找不到）
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
        versionCode = 1
        versionName = "1.0"

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
            // ONNXRUNTIME_DIR 由 CMakeLists 按相对路径定位（app/build/onnxruntime），无需传参
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.material)
    implementation(libs.blurview)
    // 网络（拉资讯 / 直连 DeepSeek）
    implementation(libs.okhttp)
    // AI 回复的基础 Markdown 渲染（标题、列表、粗体、引用、代码、链接）
    implementation("io.noties.markwon:core:4.6.2")
    // 本地数据库（缓存资讯 / 存 embedding 向量）
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    // 本地 embedding（语义检索 v2）；排除同命名空间的 api 模块，避免 manifest 冲突
    implementation(libs.tensorflow.lite) {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
    // 本地语义检索（RAG v2）：bge-small-zh ONNX 向量化（C++ 通过 C API 链接同一份 so）
    implementation(libs.onnxruntime.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
