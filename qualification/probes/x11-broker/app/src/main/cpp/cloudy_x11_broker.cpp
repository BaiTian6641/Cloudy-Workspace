#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <xcb/composite.h>
#include <xcb/damage.h>
#include <xcb/xcb.h>
#include <xcb/xtest.h>

namespace {
constexpr size_t MAX_IMAGE_BYTES = 64U * 1024U * 1024U;

struct Broker {
    xcb_connection_t* connection = nullptr;
    xcb_window_t root = XCB_WINDOW_NONE;
    xcb_pixmap_t pixmap = XCB_PIXMAP_NONE;
    uint16_t pixmapWidth = 0;
    uint16_t pixmapHeight = 0;

    ~Broker() {
        if (pixmap != XCB_PIXMAP_NONE && connection && !xcb_connection_has_error(connection))
            xcb_free_pixmap(connection, pixmap);
        if (connection) xcb_disconnect(connection);
    }
};

std::atomic<jlong> nextHandle{1};
std::mutex handlesMutex;
std::map<jlong, std::shared_ptr<Broker>> handles;
std::mutex demoMutex;
std::thread demoThread;
std::atomic<bool> demoStop{false};

std::shared_ptr<Broker> getBroker(jlong handle) {
    std::lock_guard<std::mutex> lock(handlesMutex);
    auto found = handles.find(handle);
    return found == handles.end() ? nullptr : found->second;
}

bool connectSocket(const std::string& path, int* fdOut) {
    if (path.empty() || path.size() >= sizeof(sockaddr_un::sun_path)) return false;
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return false;
    sockaddr_un address{};
    address.sun_family = AF_UNIX;
    std::memcpy(address.sun_path, path.c_str(), path.size() + 1);
    if (connect(fd, reinterpret_cast<sockaddr*>(&address), sizeof(address)) != 0) {
        close(fd);
        return false;
    }
    *fdOut = fd;
    return true;
}

std::shared_ptr<Broker> openConnection(const std::string& path, const uint8_t cookie[16], bool redirect) {
    int fd = -1;
    if (!connectSocket(path, &fd)) return nullptr;
    char cookieData[16];
    std::memcpy(cookieData, cookie, sizeof(cookieData));
    xcb_auth_info_t auth{};
    auth.name = const_cast<char*>("MIT-MAGIC-COOKIE-1");
    auth.namelen = 18;
    auth.data = cookieData;
    auth.datalen = sizeof(cookieData);
    xcb_connection_t* connection = xcb_connect_to_fd(fd, &auth);
    std::memset(cookieData, 0, sizeof(cookieData));
    if (!connection || xcb_connection_has_error(connection)) {
        if (connection) xcb_disconnect(connection); else close(fd);
        return nullptr;
    }
    auto broker = std::make_shared<Broker>();
    broker->connection = connection;
    const xcb_setup_t* setup = xcb_get_setup(connection);
    xcb_screen_iterator_t screens = xcb_setup_roots_iterator(setup);
    if (!setup || screens.rem == 0) return nullptr;
    broker->root = screens.data->root;

    xcb_composite_query_version_cookie_t versionCookie = xcb_composite_query_version(connection, 0, 2);
    xcb_generic_error_t* error = nullptr;
    xcb_composite_query_version_reply_t* version =
        xcb_composite_query_version_reply(connection, versionCookie, &error);
    bool supported = version && !error;
    free(version);
    free(error);
    if (!supported) return nullptr;

    if (redirect) {
        xcb_void_cookie_t redirectCookie = xcb_composite_redirect_subwindows_checked(
            connection, broker->root, XCB_COMPOSITE_REDIRECT_AUTOMATIC);
        error = xcb_request_check(connection, redirectCookie);
        if (error) {
            free(error);
            return nullptr;
        }
        uint32_t mask = XCB_EVENT_MASK_SUBSTRUCTURE_NOTIFY | XCB_EVENT_MASK_STRUCTURE_NOTIFY;
        xcb_change_window_attributes_checked(connection, broker->root, XCB_CW_EVENT_MASK, &mask);
    }
    xcb_flush(connection);
    return broker;
}

xcb_atom_t internAtom(xcb_connection_t* connection, const char* name) {
    xcb_intern_atom_cookie_t cookie = xcb_intern_atom(connection, 0, std::strlen(name), name);
    xcb_intern_atom_reply_t* reply = xcb_intern_atom_reply(connection, cookie, nullptr);
    xcb_atom_t atom = reply ? reply->atom : XCB_ATOM_NONE;
    free(reply);
    return atom;
}

xcb_window_t transientFor(xcb_connection_t* connection, xcb_window_t window) {
    xcb_atom_t atom = internAtom(connection, "WM_TRANSIENT_FOR");
    if (atom == XCB_ATOM_NONE) return XCB_WINDOW_NONE;
    xcb_get_property_cookie_t cookie = xcb_get_property(connection, 0, window, atom,
        XCB_ATOM_WINDOW, 0, 1);
    xcb_get_property_reply_t* reply = xcb_get_property_reply(connection, cookie, nullptr);
    xcb_window_t parent = XCB_WINDOW_NONE;
    if (reply && xcb_get_property_value_length(reply) >= static_cast<int>(sizeof(xcb_window_t)))
        parent = *reinterpret_cast<xcb_window_t*>(xcb_get_property_value(reply));
    free(reply);
    return parent;
}

bool geometry(const std::shared_ptr<Broker>& broker, xcb_window_t window, int16_t* x, int16_t* y,
              uint16_t* width, uint16_t* height) {
    xcb_get_geometry_cookie_t cookie = xcb_get_geometry(broker->connection, window);
    xcb_get_geometry_reply_t* reply = xcb_get_geometry_reply(broker->connection, cookie, nullptr);
    if (!reply) return false;
    *x = reply->x;
    *y = reply->y;
    *width = reply->width;
    *height = reply->height;
    free(reply);
    return *width > 0 && *height > 0;
}

bool renderPixmap(const std::shared_ptr<Broker>& broker, xcb_window_t window, JNIEnv* env, jobject bitmap) {
    int16_t x = 0, y = 0;
    uint16_t width = 0, height = 0;
    if (!geometry(broker, window, &x, &y, &width, &height)) return false;
    const uint64_t required = static_cast<uint64_t>(width) * height * 4U;
    if (required == 0 || required > MAX_IMAGE_BYTES) return false;

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || info.width != width || info.height != height)
        return false;

    if (broker->pixmap == XCB_PIXMAP_NONE || broker->pixmapWidth != width || broker->pixmapHeight != height) {
        if (broker->pixmap != XCB_PIXMAP_NONE) xcb_free_pixmap(broker->connection, broker->pixmap);
        broker->pixmap = xcb_generate_id(broker->connection);
        xcb_void_cookie_t nameCookie = xcb_composite_name_window_pixmap_checked(
            broker->connection, window, broker->pixmap);
        xcb_generic_error_t* error = xcb_request_check(broker->connection, nameCookie);
        if (error) {
            free(error);
            broker->pixmap = XCB_PIXMAP_NONE;
            return false;
        }
        broker->pixmapWidth = width;
        broker->pixmapHeight = height;
    }

    xcb_get_image_cookie_t imageCookie = xcb_get_image(broker->connection, XCB_IMAGE_FORMAT_Z_PIXMAP,
        broker->pixmap, 0, 0, width, height, UINT32_MAX);
    xcb_get_image_reply_t* reply = xcb_get_image_reply(broker->connection, imageCookie, nullptr);
    if (!reply) return false;
    int dataLength = xcb_get_image_data_length(reply);
    const uint8_t* source = xcb_get_image_data(reply);
    if (dataLength < 0 || static_cast<uint64_t>(dataLength) < required || !source) {
        free(reply);
        return false;
    }

    void* destination = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &destination) != ANDROID_BITMAP_RESULT_SUCCESS) {
        free(reply);
        return false;
    }
    auto* out = static_cast<uint8_t*>(destination);
    for (uint32_t row = 0; row < height; ++row) {
        const uint32_t* inRow = reinterpret_cast<const uint32_t*>(source + static_cast<uint64_t>(row) * width * 4U);
        uint8_t* outRow = out + static_cast<uint64_t>(row) * info.stride;
        for (uint32_t column = 0; column < width; ++column) {
            uint32_t pixel = inRow[column];
            outRow[column * 4U] = (pixel >> 16U) & 0xffU;
            outRow[column * 4U + 1U] = (pixel >> 8U) & 0xffU;
            outRow[column * 4U + 2U] = pixel & 0xffU;
            outRow[column * 4U + 3U] = 0xffU;
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    free(reply);
    return true;
}

bool copyCookie(JNIEnv* env, jbyteArray cookie, uint8_t output[16]) {
    if (!cookie || env->GetArrayLength(cookie) != 16) return false;
    env->GetByteArrayRegion(cookie, 0, 16, reinterpret_cast<jbyte*>(output));
    return !env->ExceptionCheck();
}

std::shared_ptr<Broker> openFromJava(JNIEnv* env, jstring socketPath, jbyteArray cookie, bool redirect) {
    const char* path = env->GetStringUTFChars(socketPath, nullptr);
    if (!path) return nullptr;
    uint8_t cookieBytes[16];
    bool valid = copyCookie(env, cookie, cookieBytes);
    auto broker = valid ? openConnection(path, cookieBytes, redirect) : nullptr;
    std::memset(cookieBytes, 0, sizeof(cookieBytes));
    env->ReleaseStringUTFChars(socketPath, path);
    return broker;
}

jlong registerBroker(const std::shared_ptr<Broker>& broker) {
    if (!broker) return 0;
    jlong handle = nextHandle.fetch_add(1);
    {
        std::lock_guard<std::mutex> lock(handlesMutex);
        handles.emplace(handle, broker);
    }
    return handle;
}

void closeBroker(jlong handle) {
    std::shared_ptr<Broker> broker;
    {
        std::lock_guard<std::mutex> lock(handlesMutex);
        auto found = handles.find(handle);
        if (found != handles.end()) {
            broker = found->second;
            handles.erase(found);
        }
    }
}

struct DemoClient {
    std::shared_ptr<Broker> broker;
    xcb_window_t window = XCB_WINDOW_NONE;
    xcb_gcontext_t gc = XCB_NONE;
    uint32_t color = 0;
    bool active = true;
};

void drawDemo(DemoClient& client);

void processDemoEvents(DemoClient& client) {
    if (!client.active) return;
    xcb_generic_event_t* event = nullptr;
    while ((event = xcb_poll_for_event(client.broker->connection)) != nullptr) {
        uint8_t type = event->response_type & 0x7f;
        if (type == XCB_EXPOSE) {
            drawDemo(client);
        } else if (type == XCB_BUTTON_PRESS || type == XCB_KEY_PRESS) {
            client.color ^= type == XCB_BUTTON_PRESS ? 0x00f0f0f0U : 0x0000ff00U;
            drawDemo(client);
        } else if (type == XCB_CLIENT_MESSAGE) {
            xcb_atom_t deleteWindow = internAtom(client.broker->connection, "WM_DELETE_WINDOW");
            auto* message = reinterpret_cast<xcb_client_message_event_t*>(event);
            if (message->data.data32[0] == deleteWindow) {
                xcb_unmap_window(client.broker->connection, client.window);
                client.active = false;
            }
        }
        free(event);
    }
    xcb_flush(client.broker->connection);
}

void drawDemo(DemoClient& client) {
    xcb_rectangle_t rectangle{0, 0, 0, 0};
    int16_t x = 0, y = 0;
    uint16_t width = 0, height = 0;
    if (!geometry(client.broker, client.window, &x, &y, &width, &height)) return;
    rectangle.width = width;
    rectangle.height = height;
    xcb_change_gc(client.broker->connection, client.gc, XCB_GC_FOREGROUND, &client.color);
    xcb_poly_fill_rectangle(client.broker->connection, client.window, client.gc, 1, &rectangle);
    xcb_flush(client.broker->connection);
}

DemoClient createDemoClient(const std::string& path, const uint8_t cookie[16], int16_t x, int16_t y,
                            uint16_t width, uint16_t height, uint32_t color, const char* title,
                            xcb_window_t transientParent = XCB_WINDOW_NONE) {
    DemoClient client;
    client.broker = openConnection(path, cookie, false);
    if (!client.broker) return client;
    client.window = xcb_generate_id(client.broker->connection);
    uint32_t values[2] = {0x00ffffffU, XCB_EVENT_MASK_EXPOSURE | XCB_EVENT_MASK_KEY_PRESS |
        XCB_EVENT_MASK_BUTTON_PRESS | XCB_EVENT_MASK_BUTTON_RELEASE | XCB_EVENT_MASK_POINTER_MOTION};
    xcb_create_window(client.broker->connection, XCB_COPY_FROM_PARENT, client.window, client.broker->root,
        x, y, width, height, 0, XCB_WINDOW_CLASS_INPUT_OUTPUT, XCB_COPY_FROM_PARENT,
        XCB_CW_BACK_PIXEL | XCB_CW_EVENT_MASK, values);
    xcb_change_property(client.broker->connection, XCB_PROP_MODE_REPLACE, client.window,
        XCB_ATOM_WM_NAME, XCB_ATOM_STRING, 8, std::strlen(title), title);
    xcb_atom_t protocols = internAtom(client.broker->connection, "WM_PROTOCOLS");
    xcb_atom_t deleteWindow = internAtom(client.broker->connection, "WM_DELETE_WINDOW");
    xcb_change_property(client.broker->connection, XCB_PROP_MODE_REPLACE, client.window, protocols,
        XCB_ATOM_ATOM, 32, 1, &deleteWindow);
    if (transientParent != XCB_WINDOW_NONE) {
        xcb_atom_t transientAtom = internAtom(client.broker->connection, "WM_TRANSIENT_FOR");
        xcb_change_property(client.broker->connection, XCB_PROP_MODE_REPLACE, client.window,
            transientAtom, XCB_ATOM_WINDOW, 32, 1, &transientParent);
    }
    client.gc = xcb_generate_id(client.broker->connection);
    xcb_create_gc(client.broker->connection, client.gc, client.window, 0, nullptr);
    client.color = color;
    xcb_map_window(client.broker->connection, client.window);
    drawDemo(client);
    return client;
}

void runDemo(std::string path, std::array<uint8_t, 16> cookie) {
    DemoClient first = createDemoClient(path, cookie.data(), 40, 40, 480, 360, 0x004568c0, "Cloudy XCB demo A");
    DemoClient second = createDemoClient(path, cookie.data(), 700, 40, 480, 360, 0x007d4a8c, "Cloudy XCB demo B");
    DemoClient dialog = createDemoClient(path, cookie.data(), 150, 150, 280, 180, 0x00b25f3c,
        "Owned demo dialog", first.window);
    while (!demoStop.load()) {
        processDemoEvents(first);
        processDemoEvents(second);
        processDemoEvents(dialog);
        if (!first.active && !second.active && !dialog.active) break;
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    cookie.fill(0);
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_dev_cloudy_qualification_x11broker_NativeBroker_open(
    JNIEnv* env, jclass, jstring socketPath, jbyteArray cookie, jboolean redirect) {
    return registerBroker(openFromJava(env, socketPath, cookie, redirect));
}

JNIEXPORT void JNICALL Java_dev_cloudy_qualification_x11broker_NativeBroker_close(
    JNIEnv*, jclass, jlong handle) {
    closeBroker(handle);
}

JNIEXPORT jintArray JNICALL Java_dev_cloudy_qualification_x11broker_NativeBroker_listWindows(
    JNIEnv* env, jclass, jlong handle) {
    auto broker = getBroker(handle);
    if (!broker) return nullptr;
    xcb_query_tree_cookie_t cookie = xcb_query_tree(broker->connection, broker->root);
    xcb_query_tree_reply_t* tree = xcb_query_tree_reply(broker->connection, cookie, nullptr);
    if (!tree) return nullptr;
    int count = xcb_query_tree_children_length(tree);
    xcb_window_t* children = xcb_query_tree_children(tree);
    std::vector<jint> records;
    for (int index = 0; index < count; ++index) {
        xcb_window_t window = children[index];
        xcb_get_window_attributes_cookie_t attributesCookie = xcb_get_window_attributes(broker->connection, window);
        xcb_get_window_attributes_reply_t* attributes =
            xcb_get_window_attributes_reply(broker->connection, attributesCookie, nullptr);
        if (!attributes) continue;
        bool viewable = attributes->map_state == XCB_MAP_STATE_VIEWABLE;
        bool overrideRedirect = attributes->override_redirect;
        free(attributes);
        int16_t x = 0, y = 0;
        uint16_t width = 0, height = 0;
        if (!geometry(broker, window, &x, &y, &width, &height)) continue;
        if (!viewable || width == 0 || height == 0) continue;
        xcb_window_t parent = transientFor(broker->connection, window);
        jint record[8] = {static_cast<jint>(window), static_cast<jint>(parent), overrideRedirect ? 1 : 0,
            x, y, width, height, 0};
        records.insert(records.end(), record, record + 8);
    }
    free(tree);
    jintArray result = env->NewIntArray(records.size());
    if (result && !records.empty()) env->SetIntArrayRegion(result, 0, records.size(), records.data());
    return result;
}

JNIEXPORT jintArray JNICALL Java_dev_cloudy_qualification_x11broker_NativeBroker_geometry(
    JNIEnv* env, jclass, jlong handle, jint window) {
    auto broker = getBroker(handle);
    if (!broker) return nullptr;
    int16_t x = 0, y = 0;
    uint16_t width = 0, height = 0;
    if (!geometry(broker, static_cast<xcb_window_t>(window), &x, &y, &width, &height)) return nullptr;
    jint values[4] = {x, y, width, height};
    jintArray result = env->NewIntArray(4);
    if (result) env->SetIntArrayRegion(result, 0, 4, values);
    return result;
}

JNIEXPORT jboolean JNICALL Java_dev_cloudy_qualification_x11broker_NativeBroker_render(
    JNIEnv* env, jclass, jlong handle, jint window, jobject bitmap) {
    auto broker = getBroker(handle);
    return broker && renderPixmap(broker, static_cast<xcb_window_t>(window), env, bitmap) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_dev_cloudy_qualification_x11broker_NativeBroker_viewable(
    JNIEnv*, jclass, jlong handle, jint window) {
    auto broker = getBroker(handle);
    if (!broker) return JNI_FALSE;
    xcb_get_window_attributes_cookie_t cookie = xcb_get_window_attributes(
        broker->connection, static_cast<xcb_window_t>(window));
    xcb_get_window_attributes_reply_t* attributes =
        xcb_get_window_attributes_reply(broker->connection, cookie, nullptr);
    bool visible = attributes && attributes->map_state == XCB_MAP_STATE_VIEWABLE;
    free(attributes);
    return visible ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_dev_cloudy_qualification_x11broker_NativeBroker_requestDelete(
    JNIEnv*, jclass, jlong handle, jint window) {
    auto broker = getBroker(handle);
    if (!broker) return JNI_FALSE;
    xcb_atom_t protocols = internAtom(broker->connection, "WM_PROTOCOLS");
    xcb_atom_t deleteWindow = internAtom(broker->connection, "WM_DELETE_WINDOW");
    if (protocols == XCB_ATOM_NONE || deleteWindow == XCB_ATOM_NONE) return JNI_FALSE;
    xcb_client_message_event_t event{};
    event.response_type = XCB_CLIENT_MESSAGE;
    event.format = 32;
    event.window = static_cast<xcb_window_t>(window);
    event.type = protocols;
    event.data.data32[0] = deleteWindow;
    event.data.data32[1] = XCB_CURRENT_TIME;
    xcb_send_event(broker->connection, 0, event.window, XCB_EVENT_MASK_NO_EVENT,
        reinterpret_cast<const char*>(&event));
    xcb_flush(broker->connection);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_dev_cloudy_qualification_x11broker_NativeBroker_focusWindow(
    JNIEnv*, jclass, jlong handle, jint window) {
    auto broker = getBroker(handle);
    if (!broker) return JNI_FALSE;
    xcb_set_input_focus(broker->connection, XCB_INPUT_FOCUS_POINTER_ROOT,
        static_cast<xcb_window_t>(window), XCB_CURRENT_TIME);
    uint32_t stack = XCB_STACK_MODE_ABOVE;
    xcb_configure_window(broker->connection, static_cast<xcb_window_t>(window),
        XCB_CONFIG_WINDOW_STACK_MODE, &stack);
    xcb_flush(broker->connection);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_dev_cloudy_qualification_x11broker_NativeBroker_pointer(
    JNIEnv*, jclass, jlong handle, jint window, jint localX, jint localY, jint button, jboolean down) {
    auto broker = getBroker(handle);
    if (!broker) return JNI_FALSE;
    int16_t x = 0, y = 0;
    uint16_t width = 0, height = 0;
    xcb_window_t target = static_cast<xcb_window_t>(window);
    if (!geometry(broker, target, &x, &y, &width, &height)) return JNI_FALSE;
    int16_t rootX = std::clamp<int>(x + localX, 0, INT16_MAX);
    int16_t rootY = std::clamp<int>(y + localY, 0, INT16_MAX);
    xcb_void_cookie_t motion = xcb_test_fake_input_checked(broker->connection, XCB_MOTION_NOTIFY,
        0, XCB_CURRENT_TIME, broker->root, rootX, rootY, 0);
    xcb_generic_error_t* error = xcb_request_check(broker->connection, motion);
    if (!error && button > 0) {
        xcb_void_cookie_t click = xcb_test_fake_input_checked(broker->connection,
            down ? XCB_BUTTON_PRESS : XCB_BUTTON_RELEASE, button, XCB_CURRENT_TIME,
            broker->root, rootX, rootY, 0);
        error = xcb_request_check(broker->connection, click);
    }
    if (error) {
        __android_log_print(ANDROID_LOG_WARN, "CloudyX11Broker", "XTEST pointer error %u", error->error_code);
        free(error);
        return JNI_FALSE;
    }
    xcb_flush(broker->connection);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_dev_cloudy_qualification_x11broker_NativeBroker_startDemo(
    JNIEnv* env, jclass, jstring socketPath, jbyteArray cookie) {
    std::lock_guard<std::mutex> lock(demoMutex);
    if (demoThread.joinable()) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(socketPath, nullptr);
    if (!path) return JNI_FALSE;
    std::array<uint8_t, 16> cookieBytes{};
    bool valid = copyCookie(env, cookie, cookieBytes.data());
    std::string ownedPath(path);
    env->ReleaseStringUTFChars(socketPath, path);
    if (!valid) return JNI_FALSE;
    demoStop.store(false);
    demoThread = std::thread(runDemo, ownedPath, cookieBytes);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_dev_cloudy_qualification_x11broker_NativeBroker_stopDemo(
    JNIEnv*, jclass) {
    demoStop.store(true);
    std::lock_guard<std::mutex> lock(demoMutex);
    if (demoThread.joinable()) demoThread.join();
}

} // extern "C"
