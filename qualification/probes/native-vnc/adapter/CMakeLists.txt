cmake_minimum_required(VERSION 3.10)
project(CloudyVncAdapter LANGUAGES C)
if(NOT ANDROID OR NOT ANDROID_ABI STREQUAL "arm64-v8a")
  message(FATAL_ERROR "This entry requires the selected Android arm64 toolchain")
endif()
add_library(cloudy-vnc-jni SHARED adapter.c policy.c)
target_include_directories(cloudy-vnc-jni PRIVATE
  "${VNC_SOURCE}/include" "${VNC_BUILD}/include" "${SSL_PREFIX}/include")
target_compile_options(cloudy-vnc-jni PRIVATE -Wall -Wextra -Wpedantic -Werror)
set_target_properties(cloudy-vnc-jni PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED ON)
target_link_libraries(cloudy-vnc-jni PRIVATE
  "${VNC_BUILD}/libvncclient.a" "${JPEG_ARCHIVE}" "${SSL_PREFIX}/lib/libssl.so" "${SSL_PREFIX}/lib/libcrypto.so" z)
target_link_options(cloudy-vnc-jni PRIVATE -Wl,-z,max-page-size=16384 -Wl,--no-undefined -Wl,--exclude-libs,ALL)
