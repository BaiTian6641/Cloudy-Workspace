cmake_minimum_required(VERSION 3.20)
project(cloudy_android_event_boundary_test C)
set(CMAKE_C_STANDARD 23)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(FREERDP_SOURCE_DIR "" CACHE PATH "Exact source with android-session-ownership.patch applied")
set(FREERDP_HOST_BUILD_DIR "" CACHE PATH "Matching configured native Linux FreeRDP build")
if(NOT EXISTS "${FREERDP_SOURCE_DIR}/client/Android/Studio/freeRDPCore/src/main/cpp/android_event.c")
  message(FATAL_ERROR "FREERDP_SOURCE_DIR must name the patched source")
endif()
find_package(PkgConfig REQUIRED)
pkg_check_modules(WINPR REQUIRED IMPORTED_TARGET winpr3)
pkg_check_modules(FREERDP REQUIRED IMPORTED_TARGET freerdp3)
find_package(JNI REQUIRED)
find_package(Threads REQUIRED)
add_executable(android_event_queue_test android_event_queue_test.c)
target_include_directories(android_event_queue_test PRIVATE
  "${FREERDP_SOURCE_DIR}/client/Android/Studio/freeRDPCore/src/main/cpp"
  "${FREERDP_HOST_BUILD_DIR}/include"
  "${FREERDP_SOURCE_DIR}/include"
  ${JNI_INCLUDE_DIRS})
target_link_libraries(android_event_queue_test PRIVATE PkgConfig::WINPR PkgConfig::FREERDP Threads::Threads)
# android_event.c references this Android channel function in its real consumer.
# Link its maintained implementation, not a replacement test implementation.
target_sources(android_event_queue_test PRIVATE
  "${FREERDP_SOURCE_DIR}/client/Android/Studio/freeRDPCore/src/main/cpp/android_cliprdr.c"
  "${FREERDP_SOURCE_DIR}/client/Android/Studio/freeRDPCore/src/main/cpp/android_jni_callback.c"
  "${FREERDP_SOURCE_DIR}/client/Android/Studio/freeRDPCore/src/main/cpp/android_jni_utils.c")
target_compile_options(android_event_queue_test PRIVATE -Wall -Wextra)
enable_testing()
add_test(NAME android_event_queue_boundary COMMAND android_event_queue_test)
set_tests_properties(android_event_queue_boundary PROPERTIES TIMEOUT 60)
