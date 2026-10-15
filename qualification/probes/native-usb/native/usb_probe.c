#include <jni.h>
#include <libusb.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

/* The Java executor owns this struct and keeps the borrowed Android FD open. */
struct probe { libusb_context *context; libusb_device_handle *handle; };
static void fail(JNIEnv *env, const char *operation, int code) {
    char message[160];
    (void)snprintf(message, sizeof(message), "%s: %s (%d)", operation, libusb_error_name(code), code);
    jclass type = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (type != NULL) (void)(*env)->ThrowNew(env, type, message);
}
JNIEXPORT jstring JNICALL Java_dev_cloudy_qualification_usb_NativeUsb_version(JNIEnv *env, jobject self);
JNIEXPORT jlong JNICALL Java_dev_cloudy_qualification_usb_NativeUsb_open(JNIEnv *env, jobject self, jint fd);
JNIEXPORT jstring JNICALL Java_dev_cloudy_qualification_usb_NativeUsb_describe(JNIEnv *env, jobject self, jlong pointer);
JNIEXPORT void JNICALL Java_dev_cloudy_qualification_usb_NativeUsb_close(JNIEnv *env, jobject self, jlong pointer);

JNIEXPORT jstring JNICALL Java_dev_cloudy_qualification_usb_NativeUsb_version(JNIEnv *env, jobject self) {
    (void)self;
    const struct libusb_version *v = libusb_get_version();
    char message[120];
    struct libusb_init_option option = { .option = LIBUSB_OPTION_NO_DEVICE_DISCOVERY };
    libusb_context *context = NULL;
    int result = libusb_init_context(&context, &option, 1);
    if (result != LIBUSB_SUCCESS) { fail(env, "no-discovery init", result); return NULL; }
    libusb_exit(context);
    (void)snprintf(message, sizeof(message), "libusb %u.%u.%u; no-discovery init/exit passed", v->major, v->minor, v->micro);
    return (*env)->NewStringUTF(env, message);
}
JNIEXPORT jlong JNICALL Java_dev_cloudy_qualification_usb_NativeUsb_open(JNIEnv *env, jobject self, jint fd) {
    (void)self;
    if (fd < 0) { fail(env, "invalid FD", LIBUSB_ERROR_INVALID_PARAM); return 0; }
    struct probe *probe = calloc(1, sizeof(*probe));
    if (probe == NULL) { fail(env, "allocate", LIBUSB_ERROR_NO_MEM); return 0; }
    struct libusb_init_option option = { .option = LIBUSB_OPTION_NO_DEVICE_DISCOVERY };
    int result = libusb_init_context(&probe->context, &option, 1);
    if (result == LIBUSB_SUCCESS) result = libusb_wrap_sys_device(probe->context, (intptr_t)fd, &probe->handle);
    if (result != LIBUSB_SUCCESS) {
        if (probe->context != NULL) libusb_exit(probe->context);
        free(probe); fail(env, "native wrap", result); return 0;
    }
    return (jlong)(intptr_t)probe;
}
JNIEXPORT jstring JNICALL Java_dev_cloudy_qualification_usb_NativeUsb_describe(JNIEnv *env, jobject self, jlong pointer) {
    (void)self;
    struct probe *probe = (struct probe *)(intptr_t)pointer;
    if (probe == NULL) { fail(env, "missing handle", LIBUSB_ERROR_INVALID_PARAM); return NULL; }
    struct libusb_device_descriptor descriptor;
    int result = libusb_get_device_descriptor(libusb_get_device(probe->handle), &descriptor);
    if (result != LIBUSB_SUCCESS) { fail(env, "device descriptor", result); return NULL; }
    char text[220];
    (void)snprintf(text, sizeof(text), "Native descriptor: VID %04x PID %04x; class %02x; configurations %u; bcdUSB %04x. Borrowed FD remains open until Close; no interface claimed.", descriptor.idVendor, descriptor.idProduct, descriptor.bDeviceClass, descriptor.bNumConfigurations, descriptor.bcdUSB);
    return (*env)->NewStringUTF(env, text);
}
JNIEXPORT void JNICALL Java_dev_cloudy_qualification_usb_NativeUsb_close(JNIEnv *env, jobject self, jlong pointer) {
    (void)env; (void)self;
    struct probe *probe = (struct probe *)(intptr_t)pointer;
    if (probe == NULL) return;
    libusb_close(probe->handle);
    libusb_exit(probe->context);
    free(probe);
}
