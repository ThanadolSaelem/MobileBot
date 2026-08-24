cmake_minimum_required(VERSION 3.22.1)
project(llama-mobile LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# ---- llama.cpp options for Android NDK ----
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH   "ARM" CACHE STRING "" FORCE)
    # KleidiAI = ARM optimised kernels shipped in ggml (v0.2.0)
    set(GGML_CPU_KLEIDIAI  ON  CACHE BOOL "" FORCE)
else()
    set(GGML_SYSTEM_ARCH   "x86" CACHE STRING "" FORCE)
    set(GGML_CPU_KLEIDIAI  OFF CACHE BOOL "" FORCE)
endif()
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)     # NDK has no OpenMP
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(llama-mobile SHARED llama-mobile.cpp)

target_compile_definitions(llama-mobile PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>)

target_include_directories(llama-mobile PRIVATE
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include)

find_library(log-lib log)
target_link_libraries(llama-mobile PRIVATE llama llama-common android ${log-lib})
