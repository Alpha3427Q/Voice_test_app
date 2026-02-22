cmake_minimum_required(VERSION 3.22.1)
project(alice_native LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# This option is provided from Gradle with -DGGML_VULKAN=ON.
option(GGML_VULKAN "Enable Vulkan backend for ggml/llama.cpp" ON)

add_library(
    native-lib
    SHARED
    native-lib.cpp
)

# If llama.cpp source is present in this project, link it directly.
if (EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt)
    add_subdirectory(llama.cpp)
    target_link_libraries(native-lib PRIVATE llama)
    target_compile_definitions(native-lib PRIVATE ALICE_LLAMA_CPP_LINKED=1)
endif()

find_library(log-lib log)

target_link_libraries(
    native-lib
    PRIVATE
    ${log-lib}
)
