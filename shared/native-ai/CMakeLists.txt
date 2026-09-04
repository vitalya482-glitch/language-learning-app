cmake_minimum_required(VERSION 3.22.1)

project(lvk_native_ai_core LANGUAGES C CXX)

include(FetchContent)

# Keep the embedded llama.cpp build small and deterministic for Android.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG v0.3.0
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(lvk_native_ai_core STATIC
    src/native_ai_engine.cpp
)

target_include_directories(lvk_native_ai_core
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_compile_features(lvk_native_ai_core PUBLIC cxx_std_17)
set_target_properties(lvk_native_ai_core PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_link_libraries(lvk_native_ai_core PUBLIC llama)

if(PROJECT_IS_TOP_LEVEL)
    include(CTest)
    if(BUILD_TESTING)
        add_executable(lvk_native_ai_core_test
            tests/native_ai_engine_test.cpp
        )
        target_link_libraries(lvk_native_ai_core_test PRIVATE lvk_native_ai_core)
        add_test(NAME lvk_native_ai_core_test COMMAND lvk_native_ai_core_test)
    endif()
endif()
