cmake_minimum_required(VERSION 3.22.1)
project(wd2_qwen LANGUAGES C CXX)

include(FetchContent)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set_property(GLOBAL PROPERTY JOB_POOLS wd2_native_compile=6)
set(CMAKE_JOB_POOL_COMPILE wd2_native_compile)
# Debug APKs exercise the production inference kernels at realistic speeds.
add_compile_options($<$<CONFIG:Debug>:-O2>)

set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_HTTPLIB OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMIT "707cbafcaa3a88d893147bcc107e76b925a867da" CACHE STRING "" FORCE)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
if(ANDROID_ABI STREQUAL "x86_64")
    # QwenRuntime checks these optional x86 instructions before loading the library.
    set(GGML_SSE42 ON CACHE BOOL "" FORCE)
    set(GGML_AVX ON CACHE BOOL "" FORCE)
    set(GGML_AVX2 ON CACHE BOOL "" FORCE)
    set(GGML_FMA ON CACHE BOOL "" FORCE)
    set(GGML_F16C ON CACHE BOOL "" FORCE)
endif()

set(llama_archive "https://codeload.github.com/ggml-org/llama.cpp/tar.gz/${LLAMA_BUILD_COMMIT}")
if(WD2_LLAMA_SOURCE_ARCHIVE)
    file(TO_CMAKE_PATH "${WD2_LLAMA_SOURCE_ARCHIVE}" llama_archive)
endif()
FetchContent_Declare(llama_cpp
    URL "${llama_archive}"
    URL_HASH SHA256=777b763cc439c6b88183f73e935b0bef85aea9cb637ef5fcfb918c82eddd099b
    TLS_VERIFY TRUE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(wd2_qwen SHARED qwen_jni.cpp)
target_compile_features(wd2_qwen PRIVATE cxx_std_17)
target_link_libraries(wd2_qwen PRIVATE common log)
target_link_options(wd2_qwen PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--exclude-libs,ALL")
