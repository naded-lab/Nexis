cmake_minimum_required(VERSION 3.22)
project(nexis_llama)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
    GIT_TAG        master
    GIT_SHALLOW    TRUE
)
# We only need the ggml/llama libraries, not llama.cpp's own CLI tools/tests/server.
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(llama_cpp)

add_library(nexis_llama SHARED nexis_llama.cpp)

target_link_libraries(nexis_llama
    llama
    ggml
    android
    log
)
