#define _POSIX_C_SOURCE 200809L
#include <jni.h>
#include <rfb/rfbclient.h>
#include "policy.h"
#include <errno.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>

enum { CREATED, CONNECTING, CONNECTED, STOPPED, FAILED };
enum { OK, CANCELLED, DEADLINE, IO_ERROR, CERTIFICATE_ERROR, CALLBACK_ERROR,
       FRAME_ERROR, INPUT_ERROR, MEMORY_ERROR, THREAD_ERROR, SECURITY_ERROR };
enum { POINTER, KEY, RELEASE };
enum { QUEUE_CAPACITY = 256, HELD_CAPACITY = 64 };

typedef struct { uint64_t sequence; int kind, x, y, flags; uint32_t key; } Input;
typedef struct {
    uint64_t id;
    unsigned int calls;
    pthread_mutex_t mutex;
    pthread_cond_t done_condition;
    pthread_t thread;
    int started, done, joining, joined, socket;
    atomic_int state, error, stop;
    uint64_t deadline, auth_deadline, frames, admitted, completed, successful;
    int width, height, last_x, last_y, buttons;
    uint32_t held[HELD_CAPACITY];
    size_t held_count;
    Input queue[QUEUE_CAPACITY];
    size_t head, length;
    char profile[65], address[64], username[257], password[1025];
    int port, duration;
    uint8_t pin[32];
    jobject sink;
    jmethodID on_state, on_frame, on_input;
    JNIEnv *worker_env;
    rfbClient *client;
    uint8_t *rgba;
    size_t bytes;
    int certificate_checked;
} Session;

static JavaVM *vm;
static pthread_mutex_t registry_mutex = PTHREAD_MUTEX_INITIALIZER;
static Session *registered;
static uint64_t next_id = 1;
static const uint32_t subtypes[] = { rfbVeNCryptX509Plain, 0 };
static char owner_tag;

static uint64_t now_millis(void)
{
    struct timespec value;
    if (clock_gettime(CLOCK_MONOTONIC, &value) != 0) return UINT64_MAX;
    return (uint64_t)value.tv_sec * 1000u + (uint64_t)value.tv_nsec / 1000000u;
}

static void fail(Session *s, int error)
{
    int expected = OK;
    atomic_compare_exchange_strong(&s->error, &expected, error);
    atomic_store(&s->stop, 1);
}

static Session *acquire(jlong id)
{
    pthread_mutex_lock(&registry_mutex);
    Session *s = registered;
    if (!s || id <= 0 || s->id != (uint64_t)id) s = NULL;
    else s->calls++;
    pthread_mutex_unlock(&registry_mutex);
    return s;
}

static void release(Session *s)
{
    pthread_mutex_lock(&registry_mutex);
    s->calls--;
    pthread_mutex_unlock(&registry_mutex);
}

static void callback_result(Session *s)
{
    if ((*s->worker_env)->ExceptionCheck(s->worker_env)) {
        (*s->worker_env)->ExceptionClear(s->worker_env);
        fail(s, CALLBACK_ERROR);
    }
}

static void state_callback(Session *s, int state)
{
    atomic_store(&s->state, state);
    (*s->worker_env)->CallVoidMethod(s->worker_env, s->sink, s->on_state,
                                   (jlong)s->id, state, atomic_load(&s->error));
    callback_result(s);
}

static Session *owner(rfbClient *client)
{
    return rfbClientGetClientData(client, &owner_tag);
}

static rfbBool continuing(rfbClient *client)
{
    Session *s = owner(client);
    if (!s) return FALSE;
    uint64_t now = now_millis();
    if (now >= s->deadline || (s->auth_deadline && now >= s->auth_deadline)) fail(s, DEADLINE);
    return !atomic_load(&s->stop);
}

static rfbBool leaf(rfbClient *client, const uint8_t *der, unsigned int length)
{
    Session *s = owner(client);
    if (!s || !continuing(client) || client->authScheme != rfbVeNCrypt ||
        client->subAuthScheme != rfbVeNCryptX509Plain || !cloudy_verify_leaf(der, length, s->pin)) {
        if (s) fail(s, CERTIFICATE_ERROR);
        return FALSE;
    }
    /* Only this immutable profile/address/port pin can authorize these credentials. */
    s->certificate_checked = 1;
    return TRUE;
}

static rfbCredential *credential(rfbClient *client, int type)
{
    Session *s = owner(client);
    if (!s || !continuing(client) || client->authScheme != rfbVeNCrypt ||
        client->subAuthScheme != rfbVeNCryptX509Plain) {
        if (s) fail(s, SECURITY_ERROR);
        return NULL;
    }
    rfbCredential *result = calloc(1, sizeof(*result));
    if (!result) { fail(s, MEMORY_ERROR); return NULL; }
    if (type == rfbCredentialTypeX509) return result;
    if (type != rfbCredentialTypeUser || !s->certificate_checked || !client->tlsSession) {
        free(result); fail(s, SECURITY_ERROR); return NULL;
    }
    result->userCredential.username = strdup(s->username);
    result->userCredential.password = strdup(s->password);
    cloudy_wipe(s->password, sizeof(s->password));
    if (!result->userCredential.username || !result->userCredential.password) {
        if (result->userCredential.password) {
            cloudy_wipe(result->userCredential.password, strlen(result->userCredential.password));
            free(result->userCredential.password);
        }
        free(result->userCredential.username); free(result); fail(s, MEMORY_ERROR); return NULL;
    }
    return result;
}

static rfbBool allocate_frame(rfbClient *client)
{
    Session *s = owner(client);
    size_t bytes;
    if (!s || !cloudy_dimensions(client->width, client->height, &bytes)) {
        if (s) fail(s, FRAME_ERROR);
        return FALSE;
    }
    uint8_t *frame = calloc(1, bytes), *rgba = malloc(bytes);
    if (!frame || !rgba) {
        free(frame); free(rgba); fail(s, MEMORY_ERROR); return FALSE;
    }
    free(client->frameBuffer); free(s->rgba);
    client->frameBuffer = frame; s->rgba = rgba; s->bytes = bytes;
    pthread_mutex_lock(&s->mutex);
    s->width = client->width; s->height = client->height;
    pthread_mutex_unlock(&s->mutex);
    return TRUE;
}

static void frame_finished(rfbClient *client)
{
    Session *s = owner(client);
    if (!s || !continuing(client) || !s->certificate_checked) return;
    JNIEnv *env = s->worker_env;
    memcpy(s->rgba, client->frameBuffer, s->bytes);
    for (size_t i = 3; i < s->bytes; i += 4) s->rgba[i] = 255;
    jbyteArray copy = (*env)->NewByteArray(env, (jsize)s->bytes);
    if (!copy) { callback_result(s); fail(s, MEMORY_ERROR); return; }
    (*env)->SetByteArrayRegion(env, copy, 0, (jsize)s->bytes, (const jbyte *)s->rgba);
    if (!(*env)->ExceptionCheck(env))
        (*env)->CallVoidMethod(env, s->sink, s->on_frame, (jlong)s->id, client->width, client->height, copy);
    (*env)->DeleteLocalRef(env, copy);
    callback_result(s);
    pthread_mutex_lock(&s->mutex); s->frames++; pthread_mutex_unlock(&s->mutex);
}

static int release_held(Session *s)
{
    int success = 1;
    if (s->buttons) success = SendPointerEvent(s->client, s->last_x, s->last_y, 0);
    s->buttons = 0;
    for (size_t i = 0; i < s->held_count; i++)
        if (!SendKeyEvent(s->client, s->held[i], FALSE)) success = 0;
    s->held_count = 0;
    return success;
}

static int execute_input(Session *s, Input event)
{
    rfbClient *client = s->client;
    if (event.kind == RELEASE) return release_held(s);
    if (event.kind == POINTER) {
        if (event.x < 0 || event.y < 0 || event.x >= client->width || event.y >= client->height) return 0;
        if (!SendPointerEvent(client, event.x, event.y, event.flags)) return 0;
        s->last_x = event.x; s->last_y = event.y; s->buttons = event.flags;
        return 1;
    }
    size_t index = 0;
    while (index < s->held_count && s->held[index] != event.key) index++;
    if (event.flags && index == s->held_count && s->held_count == HELD_CAPACITY) return 0;
    if (!SendKeyEvent(client, event.key, event.flags)) return 0;
    if (event.flags && index == s->held_count) s->held[s->held_count++] = event.key;
    else if (!event.flags && index < s->held_count) s->held[index] = s->held[--s->held_count];
    return 1;
}

static void drain(Session *s)
{
    /* One consumer owns every LibVNCClient call, including TLS sends and decode. */
    for (size_t count = 0; count < QUEUE_CAPACITY && !atomic_load(&s->stop); count++) {
        pthread_mutex_lock(&s->mutex);
        if (!s->length) { pthread_mutex_unlock(&s->mutex); break; }
        Input event = s->queue[s->head];
        s->head = (s->head + 1) % QUEUE_CAPACITY; s->length--;
        pthread_mutex_unlock(&s->mutex);
        int success = execute_input(s, event);
        pthread_mutex_lock(&s->mutex);
        s->completed++; if (success) s->successful++;
        pthread_mutex_unlock(&s->mutex);
        (*s->worker_env)->CallVoidMethod(s->worker_env, s->sink, s->on_input,
                                       (jlong)s->id, (jlong)event.sequence, success ? JNI_TRUE : JNI_FALSE);
        callback_result(s);
        if (!success) fail(s, INPUT_ERROR);
    }
}

static void *worker(void *argument)
{
    Session *s = argument;
    JNIEnv *env = NULL;
    if ((*vm)->AttachCurrentThread(vm, &env, NULL) != JNI_OK) {
        fail(s, CALLBACK_ERROR);
        atomic_store(&s->state, FAILED);
        goto done;
    }
    s->worker_env = env;
    state_callback(s, CONNECTING);
    rfbClient *client = rfbGetClient(8, 3, 4);
    s->client = client;
    if (!client) { fail(s, MEMORY_ERROR); goto terminal; }
    rfbClientSetClientData(client, &owner_tag, s);
    if (owner(client) != s) { fail(s, MEMORY_ERROR); goto terminal; }
    client->cloudyVeNCryptSubtypes = subtypes;
    client->cloudyVerifyLeaf = leaf;
    client->cloudyContinue = continuing;
    const uint32_t auth[] = { rfbVeNCrypt, 0 };
    SetClientAuthSchemes(client, auth, 1);
    if (!client->clientAuthSchemes) { fail(s, MEMORY_ERROR); goto terminal; }
    free(client->serverHost); client->serverHost = strdup(s->address);
    if (!client->serverHost) { fail(s, MEMORY_ERROR); goto terminal; }
    client->serverPort = s->port;
    client->connectTimeout = 3; client->readTimeout = CLOUDY_SESSION_READ_IDLE_SECONDS;
    client->GetCredential = credential;
    client->MallocFrameBuffer = allocate_frame;
    client->FinishedFrameBufferUpdate = frame_finished;
    client->canHandleNewFBSize = TRUE;
    client->appData.useRemoteCursor = FALSE;
    client->appData.encodingsString = "tight zrle hextile raw";
    client->format.redShift = 0; client->format.greenShift = 8; client->format.blueShift = 16;
    client->format.redMax = client->format.greenMax = client->format.blueMax = 255;
    client->format.bigEndian = FALSE;
    if (!continuing(client) || !rfbClientConnect(client)) { if (!atomic_load(&s->stop)) fail(s, IO_ERROR); goto terminal; }
    pthread_mutex_lock(&s->mutex);
    s->socket = client->sock;
    if (atomic_load(&s->stop)) shutdown(s->socket, SHUT_RDWR);
    pthread_mutex_unlock(&s->mutex);
    /* Only initialization/authentication admits the existing PAM fingerprint wait
     * before normal password validation. A fixed monotonic phase budget prevents
     * a peer from extending authentication by sending occasional bytes. Socket
     * shutdown and the existing I/O retry checks still cancel this phase. */
    s->auth_deadline = cloudy_phase_deadline(now_millis(), s->deadline, CLOUDY_AUTH_PHASE_MILLIS);
    client->readTimeout = CLOUDY_AUTH_READ_IDLE_SECONDS;
    /* Split connect/initialize APIs never auto-free on failure, unlike rfbInitClient. */
    if (!continuing(client) || !rfbClientInitialise(client) || !continuing(client) || !s->certificate_checked ||
        client->authScheme != rfbVeNCrypt || client->subAuthScheme != rfbVeNCryptX509Plain) {
        if (!atomic_load(&s->stop)) fail(s, SECURITY_ERROR);
        goto terminal;
    }
    /* Restore the ordinary read-idle policy before admitting authenticated work. */
    client->readTimeout = CLOUDY_SESSION_READ_IDLE_SECONDS;
    s->auth_deadline = 0;
    state_callback(s, CONNECTED);
    while (continuing(client)) {
        drain(s);
        if (!continuing(client)) break;
        int ready = WaitForMessage(client, 10000);
        if (ready < 0 || (ready > 0 && !HandleRFBServerMessage(client))) {
            if (!atomic_load(&s->stop)) fail(s, IO_ERROR);
            break;
        }
    }
terminal:
    /* Unpublish under the same lock as cancel's shutdown; cleanup owns close.
     * This prevents cancellation from shutting down a recycled descriptor. */
    pthread_mutex_lock(&s->mutex); s->socket = -1; pthread_mutex_unlock(&s->mutex);
    /* Queue admission is never silently promoted to a send. Cancelled queued
     * operations complete explicitly with written=false before terminal state. */
    for (;;) {
        pthread_mutex_lock(&s->mutex);
        if (!s->length) { pthread_mutex_unlock(&s->mutex); break; }
        Input pending = s->queue[s->head];
        s->head = (s->head + 1) % QUEUE_CAPACITY; s->length--; s->completed++;
        pthread_mutex_unlock(&s->mutex);
        (*env)->CallVoidMethod(env, s->sink, s->on_input, (jlong)s->id, (jlong)pending.sequence, JNI_FALSE);
        callback_result(s);
    }
    if (s->client) {
        uint8_t *frame = s->client->frameBuffer;
        rfbClientCleanup(s->client); s->client = NULL; free(frame);
    }
    free(s->rgba); s->rgba = NULL;
    cloudy_wipe(s->password, sizeof(s->password));
    state_callback(s, atomic_load(&s->error) == CANCELLED || atomic_load(&s->error) == DEADLINE ? STOPPED : FAILED);
    (*vm)->DetachCurrentThread(vm);
done:
    pthread_mutex_lock(&s->mutex); s->done = 1; pthread_cond_broadcast(&s->done_condition); pthread_mutex_unlock(&s->mutex);
    return NULL;
}

static void quiet_log(const char *format, ...) { (void)format; }

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *value, void *reserved)
{
    (void)reserved; vm = value;
    /* Upstream logs may contain peer-supplied names/reasons. Only numeric native
     * status/error data crosses this diagnostic ABI; no secret/cert content logs. */
    rfbClientLog = quiet_log; rfbClientErr = quiet_log;
    return JNI_VERSION_1_6;
}

static int copy_string(JNIEnv *env, jstring input, char *output, size_t capacity)
{
    if (!input) return 0;
    jsize length = (*env)->GetStringUTFLength(env, input);
    if (length <= 0 || (size_t)length >= capacity) return 0;
    const char *text = (*env)->GetStringUTFChars(env, input, NULL);
    if (!text) return 0;
    memcpy(output, text, (size_t)length + 1);
    (*env)->ReleaseStringUTFChars(env, input, text);
    return 1;
}

static int copy_secret(JNIEnv *env, jbyteArray input, char *output, size_t capacity)
{
    if (!input) return 0;
    jsize length = (*env)->GetArrayLength(env, input);
    if (length <= 0 || (size_t)length >= capacity) return 0;
    (*env)->GetByteArrayRegion(env, input, 0, length, (jbyte *)output);
    return !(*env)->ExceptionCheck(env) && cloudy_utf8((const uint8_t *)output, (size_t)length);
}

#define JNI_NAME(name) Java_dev_cloudy_qualification_vnc_VncBridge_##name

JNIEXPORT jlong JNICALL JNI_NAME(create)(JNIEnv *env, jclass type, jstring profile,
    jstring address, jint port, jbyteArray pin, jbyteArray username, jbyteArray password,
    jint duration, jobject sink)
{
    (void)type;
    Session *s = calloc(1, sizeof(*s));
    if (!s) return 0;
    atomic_init(&s->state, CREATED); atomic_init(&s->error, OK); atomic_init(&s->stop, 0);
    s->socket = -1;
    int valid = sink && pin && (*env)->GetArrayLength(env, pin) == 32 && port > 0 && port <= 65535 &&
        duration >= 10 && duration <= 600 && copy_string(env, profile, s->profile, sizeof(s->profile)) &&
        copy_string(env, address, s->address, sizeof(s->address)) && cloudy_numeric_endpoint(s->address) &&
        copy_secret(env, username, s->username, sizeof(s->username)) &&
        copy_secret(env, password, s->password, sizeof(s->password));
    for (size_t i = 0; valid && s->profile[i]; i++) {
        unsigned char c = (unsigned char)s->profile[i];
        if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9') && c != '-' && c != '_') valid = 0;
    }
    if (!valid) { cloudy_wipe(s, sizeof(*s)); free(s); return 0; }
    (*env)->GetByteArrayRegion(env, pin, 0, 32, (jbyte *)s->pin);
    jclass sink_class = (*env)->GetObjectClass(env, sink);
    if (sink_class) {
        s->on_state = (*env)->GetMethodID(env, sink_class, "onState", "(JII)V");
        if (s->on_state) s->on_frame = (*env)->GetMethodID(env, sink_class, "onFrame", "(JII[B)V");
        if (s->on_frame) s->on_input = (*env)->GetMethodID(env, sink_class, "onInputResult", "(JJZ)V");
        (*env)->DeleteLocalRef(env, sink_class);
    }
    if (!(*env)->ExceptionCheck(env) && s->on_input) s->sink = (*env)->NewGlobalRef(env, sink);
    if (!s->sink) { cloudy_wipe(s, sizeof(*s)); free(s); return 0; }
    pthread_condattr_t attributes;
    int mutex_ok = pthread_mutex_init(&s->mutex, NULL) == 0;
    int attr_ok = pthread_condattr_init(&attributes) == 0;
    int cond_ok = attr_ok && pthread_condattr_setclock(&attributes, CLOCK_MONOTONIC) == 0 &&
                  pthread_cond_init(&s->done_condition, &attributes) == 0;
    if (attr_ok) pthread_condattr_destroy(&attributes);
    if (!mutex_ok || !cond_ok) {
        if (mutex_ok) pthread_mutex_destroy(&s->mutex);
        if (cond_ok) pthread_cond_destroy(&s->done_condition);
        (*env)->DeleteGlobalRef(env, s->sink); cloudy_wipe(s, sizeof(*s)); free(s); return 0;
    }
    s->port = port; s->duration = duration;
    pthread_mutex_lock(&registry_mutex);
    if (registered || next_id > INT64_MAX) {
        pthread_mutex_unlock(&registry_mutex);
        pthread_cond_destroy(&s->done_condition); pthread_mutex_destroy(&s->mutex);
        (*env)->DeleteGlobalRef(env, s->sink); cloudy_wipe(s, sizeof(*s)); free(s); return 0;
    }
    s->id = next_id++; registered = s;
    pthread_mutex_unlock(&registry_mutex);
    return (jlong)s->id;
}

JNIEXPORT jboolean JNICALL JNI_NAME(start)(JNIEnv *env, jclass type, jlong id)
{
    (void)env; (void)type;
    Session *s = acquire(id); if (!s) return JNI_FALSE;
    pthread_mutex_lock(&s->mutex);
    int success = !s->started && !atomic_load(&s->stop);
    if (success) {
        s->deadline = now_millis() + (uint64_t)s->duration * 1000u;
        success = pthread_create(&s->thread, NULL, worker, s) == 0;
        s->started = success;
        if (!success) { fail(s, THREAD_ERROR); atomic_store(&s->state, FAILED); }
    }
    pthread_mutex_unlock(&s->mutex); release(s);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL JNI_NAME(cancel)(JNIEnv *env, jclass type, jlong id)
{
    (void)env; (void)type;
    Session *s = acquire(id); if (!s) return JNI_FALSE;
    fail(s, CANCELLED);
    pthread_mutex_lock(&s->mutex);
    if (s->socket >= 0) shutdown(s->socket, SHUT_RDWR);
    pthread_mutex_unlock(&s->mutex); release(s); return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL JNI_NAME(join)(JNIEnv *env, jclass type, jlong id, jint timeout)
{
    (void)env; (void)type;
    if (timeout < 0 || timeout > 5000) return JNI_FALSE;
    Session *s = acquire(id); if (!s) return JNI_FALSE;
    pthread_mutex_lock(&s->mutex);
    int success = !s->started || s->joined;
    if (!success && !s->joining && !pthread_equal(pthread_self(), s->thread)) {
        uint64_t deadline = now_millis() + (uint64_t)timeout;
        struct timespec absolute = { (time_t)(deadline / 1000u), (long)(deadline % 1000u) * 1000000L };
        while (!s->done && timeout > 0) {
            int result = pthread_cond_timedwait(&s->done_condition, &s->mutex, &absolute);
            if (result != 0) break;
        }
        if (s->done) {
            s->joining = 1;
            pthread_mutex_unlock(&s->mutex);
            success = pthread_join(s->thread, NULL) == 0;
            pthread_mutex_lock(&s->mutex);
            s->joining = 0; s->joined = success;
        }
    }
    pthread_mutex_unlock(&s->mutex); release(s); return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_NAME(free)(JNIEnv *env, jclass type, jlong id)
{
    (void)type;
    pthread_mutex_lock(&registry_mutex);
    Session *s = registered;
    if (!s || id <= 0 || s->id != (uint64_t)id) { pthread_mutex_unlock(&registry_mutex); return; }
    pthread_mutex_lock(&s->mutex);
    int safe_to_free = s->calls == 0 && (!s->started || s->joined);
    pthread_mutex_unlock(&s->mutex);
    if (!safe_to_free) {
        pthread_mutex_unlock(&registry_mutex);
        jclass error = (*env)->FindClass(env, "java/lang/IllegalStateException");
        if (error) { (*env)->ThrowNew(env, error, "VNC owner must join before free"); (*env)->DeleteLocalRef(env, error); }
        return;
    }
    registered = NULL;
    pthread_mutex_unlock(&registry_mutex);
    (*env)->DeleteGlobalRef(env, s->sink);
    pthread_cond_destroy(&s->done_condition); pthread_mutex_destroy(&s->mutex);
    cloudy_wipe(s, sizeof(*s)); free(s);
}

static jlong enqueue(jlong id, Input event)
{
    Session *s = acquire(id); if (!s) return 0;
    pthread_mutex_lock(&s->mutex);
    uint64_t sequence = 0;
    if (atomic_load(&s->state) == CONNECTED && !atomic_load(&s->stop) && s->length < QUEUE_CAPACITY &&
        s->admitted < INT64_MAX && (event.kind != POINTER ||
        (event.x >= 0 && event.y >= 0 && event.x < s->width && event.y < s->height && event.flags >= 0 && event.flags <= 255))) {
        event.sequence = sequence = ++s->admitted;
        s->queue[(s->head + s->length++) % QUEUE_CAPACITY] = event;
    }
    pthread_mutex_unlock(&s->mutex); release(s); return (jlong)sequence;
}

JNIEXPORT jlong JNICALL JNI_NAME(pointer)(JNIEnv *env, jclass type, jlong id, jint x, jint y, jint buttons)
{ (void)env; (void)type; return enqueue(id, (Input){ .kind = POINTER, .x = x, .y = y, .flags = buttons }); }

JNIEXPORT jlong JNICALL JNI_NAME(key)(JNIEnv *env, jclass type, jlong id, jint key, jboolean down)
{
    (void)env; (void)type;
    if (key <= 0 || key > 0x0110ffff) return 0;
    return enqueue(id, (Input){ .kind = KEY, .key = (uint32_t)key, .flags = down ? 1 : 0 });
}

JNIEXPORT jlong JNICALL JNI_NAME(releaseInputs)(JNIEnv *env, jclass type, jlong id)
{ (void)env; (void)type; return enqueue(id, (Input){ .kind = RELEASE }); }

JNIEXPORT jlongArray JNICALL JNI_NAME(status)(JNIEnv *env, jclass type, jlong id)
{
    (void)type;
    Session *s = acquire(id); if (!s) return NULL;
    pthread_mutex_lock(&s->mutex);
    jlong values[] = { atomic_load(&s->state), atomic_load(&s->error), (jlong)s->frames,
        (jlong)s->admitted, (jlong)s->completed, (jlong)s->successful, s->width, s->height };
    pthread_mutex_unlock(&s->mutex);
    jlongArray result = (*env)->NewLongArray(env, 8);
    if (result) (*env)->SetLongArrayRegion(env, result, 0, 8, values);
    release(s); return result;
}
