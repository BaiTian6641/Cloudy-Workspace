# Actual Android queue boundary test

`android_event_queue_test.c` includes the **actual patched** upstream `android_event.c` and links the maintained WinPR message queue. It checks FIFO/capacity, failed-enqueue caller ownership, pending-allocation drain, actual disconnect-event consumption, event wake/reset, and 20,000 events from four concurrent producers with per-producer order. It uses test-only session/context structs; no protocol result or device compatibility is simulated. It does not test JNI join, auth, decoder or connection behavior.

**Not executed by the author.** Host `pkg-config --modversion winpr3 freerdp3` found neither package. Parent owns patch application, compiler and device execution. Tests must be run against patched source, not the earlier unpatched build source. The queue code change is directly compiled into the executable; accepted existing libraries supply unchanged FreeRDP/WinPR functions.

For an Android arm64 executable, after applying `android-session-ownership.patch` to a separate exact source tree, use the selected NDK and accepted Android prefix. Use a source/stage path without spaces as in the native recipe. The executable itself contains no listener and touches no app-private files; device execution still requires the parent's exclusive device coordination.

```bash
cloudy_android_cpp="$cloudy_patched_src/client/Android/Studio/freeRDPCore/src/main/cpp"
cloudy_clang="$cloudy_ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang"
"$cloudy_clang" -std=c2x -O1 -g -fPIE -pie -Wall -Wextra \
  -I"$cloudy_android_cpp" \
  -I"$cloudy_prefix/include/freerdp3" -I"$cloudy_prefix/include/winpr3" \
  "$cloudy_repo/qualification/probes/native-rdp/tests/android_event_queue_test.c" \
  "$cloudy_android_cpp/android_cliprdr.c" \
  "$cloudy_android_cpp/android_jni_callback.c" \
  "$cloudy_android_cpp/android_jni_utils.c" \
  -L"$cloudy_prefix/lib" -Wl,--no-as-needed \
  -lfreerdp3 -lfreerdp-client3 -lwinpr3 -lssl -lcrypto -lcjson -luriparser \
  -llog -ldl -lm \
  -o "$cloudy_stage/android_event_queue_test"
```

The parent can copy the executable and the seven actual linked dependency libraries into a fresh private directory under `/data/local/tmp`, then execute it with `LD_LIBRARY_PATH` naming that directory, a finite host-side timeout and retained stdout/stderr/exit code. Do not copy the optional OpenSSL legacy provider or an unneeded C++ runtime for this test. Remove only the newly created directory afterward. Passing this test establishes the changed queue boundary on that run; it does not qualify an RDP connection or prove JNI load behavior.

The optional host `CMakeLists.txt` requires matching native Linux `winpr3`/`freerdp3` development packages, a configured exact FreeRDP source/build and JDK JNI headers. It compiles maintained Android clipboard/JNI helper sources to satisfy real consumer references rather than supplying replacement fake implementations. Use Clang with C23 support and sanitizers when available. Host prerequisites are not currently established, and this route has not been compiled.
