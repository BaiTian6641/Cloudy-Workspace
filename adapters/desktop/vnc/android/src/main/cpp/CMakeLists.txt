cmake_minimum_required(VERSION 3.22)
project(CloudyDesktopVnc LANGUAGES C)
if(NOT ANDROID OR NOT ANDROID_ABI STREQUAL "arm64-v8a" OR NOT ANDROID_PLATFORM STREQUAL "android-35")
  message(FATAL_ERROR "Selected Android35 arm64 toolchain required")
endif()
# All generated sources/imports are verified by prepare_desktop_vnc.py before compilation.
add_library(cloudy-vnc-jni SHARED "${INPUT_ROOT}/adapter.c" "${INPUT_ROOT}/policy.c")
target_include_directories(cloudy-vnc-jni PRIVATE "${INPUT_ROOT}" "${INPUT_ROOT}/include" "${INPUT_ROOT}/ssl/include")
target_compile_options(cloudy-vnc-jni PRIVATE -Wall -Wextra -Wpedantic -Werror)
set_target_properties(cloudy-vnc-jni PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED ON)
target_link_libraries(cloudy-vnc-jni PRIVATE "${INPUT_ROOT}/libvncclient.a" "${INPUT_ROOT}/libjpeg.a"
  "${INPUT_ROOT}/ssl/lib/libssl.so" "${INPUT_ROOT}/ssl/lib/libcrypto.so" z)
target_link_options(cloudy-vnc-jni PRIVATE -Wl,-z,max-page-size=16384 -Wl,--no-undefined -Wl,--exclude-libs,ALL)
