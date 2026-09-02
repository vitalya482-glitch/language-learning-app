cmake_minimum_required(VERSION 3.22.1)

project(language_learning_ai_jni LANGUAGES CXX)

set(LVK_REPOSITORY_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../..")

add_subdirectory(
    "${LVK_REPOSITORY_ROOT}/shared/native-ai"
    "${CMAKE_CURRENT_BINARY_DIR}/native-ai-core"
)

add_library(language_learning_ai_jni SHARED
    jni_bridge.cpp
)

target_compile_features(language_learning_ai_jni PRIVATE cxx_std_17)
target_link_libraries(language_learning_ai_jni PRIVATE lvk_native_ai_core)
