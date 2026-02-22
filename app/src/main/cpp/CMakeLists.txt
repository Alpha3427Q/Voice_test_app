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

# Ready for upcoming llama.cpp integration:
# add_subdirectory(llama.cpp)
# target_link_libraries(native-lib PRIVATE llama)

find_library(log-lib log)

target_link_libraries(
    native-lib
    PRIVATE
    ${log-lib}
)
