cmake_minimum_required(VERSION 3.22.1)
project(llama-mobile LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# =========================================================================
# CPU Optimizations for Android ARM64
# =========================================================================
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(LLAMA_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH "ARM" CACHE STRING "" FORCE)
    # KleidiAI: ARM-specific kernels for huge speedup on mobile
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
else()
    set(GGML_SYSTEM_ARCH "x86" CACHE STRING "" FORCE)
    set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
endif()

# =========================================================================
# FetchContent: ดึง llama.cpp
# =========================================================================
include(FetchContent)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp
    GIT_TAG        v0.2.0
    GIT_SHALLOW    TRUE
)

FetchContent_MakeAvailable(llama_cpp)

# =========================================================================
# Build JNI bridge
# =========================================================================
add_library(llama-mobile SHARED llama-mobile.cpp)

target_compile_definitions(llama-mobile PRIVATE
    GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
    BUILD_TARGET="android"
    BUILD_ARCH="${ANDROID_ABI}"
)

find_library(log-lib log)
find_library(android-lib android)

target_include_directories(llama-mobile PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
)

target_link_libraries(llama-mobile PRIVATE
    llama
    ggml
    ${log-lib}
    ${android-lib}
)
