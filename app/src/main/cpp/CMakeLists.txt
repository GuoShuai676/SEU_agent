# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("seu_agent")

# 使用 C++17 标准（json_parser / native-lib 里用到的现代特性都支持）
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        native-lib.cpp
        json_parser.cpp
        bge.cpp)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        android
        log)

# ---- ONNX Runtime（C API：bge.cpp 用）----
# 目录由 app/build.gradle.kts 解压 onnxruntime-android AAR 得到：
#   <dir>/jni/<abi>/libonnxruntime.so   原生库
#   <dir>/headers/*.h                   C/C++ API 头文件
# 解压目标是 app/build/onnxruntime，相对本文件（app/src/main/cpp）固定为 ../../build/onnxruntime
set(ONNXRUNTIME_DIR "" CACHE PATH "onnxruntime extract dir")
if(NOT ONNXRUNTIME_DIR)
    # 解压目标是 app/build/onnxruntime；cpp 目录 = app/src/main/cpp，上三级到 app
    get_filename_component(ONNXRUNTIME_DIR
            "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/onnxruntime" ABSOLUTE)
endif()
if(EXISTS "${ONNXRUNTIME_DIR}")
    add_library(onnxruntime SHARED IMPORTED)
    set_target_properties(onnxruntime PROPERTIES
            IMPORTED_LOCATION "${ONNXRUNTIME_DIR}/jni/${ANDROID_ABI}/libonnxruntime.so")
    target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE "${ONNXRUNTIME_DIR}/headers")
    target_link_libraries(${CMAKE_PROJECT_NAME} onnxruntime)
    message(STATUS "ONNXRUNTIME_DIR=${ONNXRUNTIME_DIR} ABI=${ANDROID_ABI}")
else()
    message(WARNING "ONNXRUNTIME_DIR 不存在：${ONNXRUNTIME_DIR}，bge.cpp 将无法编译")
endif()
