cmake_minimum_required(VERSION 3.22.1)

project(lvk_native_ai_core LANGUAGES CXX)

add_library(lvk_native_ai_core STATIC
    src/native_ai_engine.cpp
)

target_include_directories(lvk_native_ai_core
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_compile_features(lvk_native_ai_core PUBLIC cxx_std_17)
set_target_properties(lvk_native_ai_core PROPERTIES POSITION_INDEPENDENT_CODE ON)

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
