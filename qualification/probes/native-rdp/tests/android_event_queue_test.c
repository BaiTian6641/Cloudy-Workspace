/* Cloudy diagnostic boundary test. Compile the actual patched upstream source.
 * No server, JNI runtime or simulated protocol result is involved. */
#include <pthread.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>

#include "android_event.c"

#define CHECK(condition) do { if (!(condition)) { \
    fprintf(stderr, "check failed at %s:%d: %s\n", __FILE__, __LINE__, #condition); \
    abort(); } } while (0)

static freerdp instance;
static androidContext context;
static unsigned drained;

static void counted_free(void* object)
{
    drained++;
    android_message_free(object);
}

static void fresh_queue(void)
{
    instance.context = &context.common.context;
    CHECK(android_event_queue_init(&instance));
    MessageQueue_Object(context.event_queue->messages)->fnObjectFree = counted_free;
}

static void capacity_wake_and_ownership(void)
{
    fresh_queue();
    CHECK(WaitForSingleObject(android_get_handle(&instance), 0) == WAIT_TIMEOUT);
    for (unsigned i = 0; i < ANDROID_EVENT_QUEUE_LIMIT; i++)
    {
        ANDROID_EVENT_KEY* event = android_event_key_new(0, (UINT16)i);
        CHECK(event != NULL);
        CHECK(android_push_event(&instance, (ANDROID_EVENT*)event));
    }
    CHECK(MessageQueue_Size(context.event_queue->messages) == ANDROID_EVENT_QUEUE_LIMIT);
    ANDROID_EVENT* rejected = android_event_disconnect_new();
    CHECK(rejected != NULL);
    CHECK(!android_push_event(&instance, rejected));
    /* Failure leaves ownership here. ASan catches a queue-side double free. */
    android_event_free(rejected);
    CHECK(WaitForSingleObject(android_get_handle(&instance), 0) == WAIT_OBJECT_0);
    for (unsigned i = 0; i < ANDROID_EVENT_QUEUE_LIMIT; i++)
    {
        wMessage message = { 0 };
        CHECK(MessageQueue_Peek(context.event_queue->messages, &message, TRUE) == 1);
        ANDROID_EVENT_KEY* event = message.wParam;
        CHECK(event->scancode == i);
        android_event_free((ANDROID_EVENT*)event);
    }
    CHECK(WaitForSingleObject(android_get_handle(&instance), 0) == WAIT_TIMEOUT);
    /* Exercise the real consumer path with an actual disconnect event. */
    CHECK(android_push_event(&instance, android_event_disconnect_new()));
    CHECK(!android_check_handle(&instance));
    CHECK(MessageQueue_Size(context.event_queue->messages) == 0);
    CHECK(WaitForSingleObject(android_get_handle(&instance), 0) == WAIT_TIMEOUT);
    for (unsigned i = 0; i < 17; i++)
    {
        ANDROID_EVENT_CLIPBOARD* event = android_event_clipboard_new("owned", 5, "text/plain");
        CHECK(event != NULL);
        CHECK(android_push_event(&instance, (ANDROID_EVENT*)event));
    }
    drained = 0;
    android_event_queue_uninit(&instance);
    CHECK(context.event_queue == NULL);
    CHECK(drained == 17);
    android_event_queue_uninit(&instance);
}

#define PRODUCERS 4
#define EVENTS_PER_PRODUCER 5000

static void* producer(void* opaque)
{
    unsigned id = (unsigned)(size_t)opaque;
    for (unsigned i = 0; i < EVENTS_PER_PRODUCER; i++)
    {
        ANDROID_EVENT_KEY* event = android_event_key_new((int)id, (UINT16)i);
        CHECK(event != NULL);
        while (!android_push_event(&instance, (ANDROID_EVENT*)event))
            sched_yield();
    }
    return NULL;
}

static void concurrent_order_and_wake(void)
{
    pthread_t threads[PRODUCERS];
    unsigned next[PRODUCERS] = { 0 };
    fresh_queue();
    for (unsigned i = 0; i < PRODUCERS; i++)
        CHECK(pthread_create(&threads[i], NULL, producer, (void*)(size_t)i) == 0);
    for (unsigned count = 0; count < PRODUCERS * EVENTS_PER_PRODUCER;)
    {
        CHECK(WaitForSingleObject(android_get_handle(&instance), 10000) == WAIT_OBJECT_0);
        wMessage message = { 0 };
        int result = MessageQueue_Peek(context.event_queue->messages, &message, TRUE);
        CHECK(result >= 0);
        if (result == 0)
            continue;
        ANDROID_EVENT_KEY* event = message.wParam;
        CHECK(event->flags >= 0 && event->flags < PRODUCERS);
        CHECK(event->scancode == next[event->flags]++);
        android_event_free((ANDROID_EVENT*)event);
        count++;
    }
    for (unsigned i = 0; i < PRODUCERS; i++)
    {
        CHECK(pthread_join(threads[i], NULL) == 0);
        CHECK(next[i] == EVENTS_PER_PRODUCER);
    }
    CHECK(MessageQueue_Size(context.event_queue->messages) == 0);
    CHECK(WaitForSingleObject(android_get_handle(&instance), 0) == WAIT_TIMEOUT);
    android_event_queue_uninit(&instance);
}

/* Exercise the actual fifth-patch constructor/admission/queue/free path.
 * There is no fake server or replacement encoder: inspecting copied queue
 * payloads is a boundary assertion, not evidence of protocol delivery. */
static void relative_validation_and_ownership(void)
{
    CHECK(!android_relative_input_supported(NULL));
    CHECK(!android_send_relative_event(NULL, PTR_FLAGS_MOVE, 1, -1));
    fresh_queue();
    InterlockedExchange(&context.relativeInputEnabled, 0);
    CHECK(!android_relative_input_supported(&instance));
    CHECK(!android_send_relative_event(&instance, PTR_FLAGS_MOVE, 1, -1));
    CHECK(MessageQueue_Size(context.event_queue->messages) == 0);
    const int invalid[][3] = {
        { PTR_FLAGS_MOVE, -32769, 0 }, { PTR_FLAGS_MOVE, 32768, 0 },
        { PTR_FLAGS_MOVE, 0, -32769 }, { PTR_FLAGS_MOVE, 0, 32768 },
        { -1, 0, 0 }, { 0x10000 | PTR_FLAGS_MOVE, 0, 0 },
        { PTR_FLAGS_WHEEL, 0, 0 }, { PTR_FLAGS_HWHEEL, 0, 0 },
        { PTR_FLAGS_MOVE | PTR_FLAGS_WHEEL, 0, 0 },
        { PTR_FLAGS_DOWN, 0, 0 }, { PTR_FLAGS_MOVE | PTR_FLAGS_DOWN, 0, 0 },
        { 0, 0, 0 }
    };
    InterlockedExchange(&context.relativeInputEnabled, 1);
    CHECK(android_relative_input_supported(&instance));
    for (size_t i = 0; i < sizeof(invalid) / sizeof(invalid[0]); i++)
    {
        CHECK(android_event_relative_cursor_new(invalid[i][0], invalid[i][1], invalid[i][2]) == NULL);
        CHECK(!android_send_relative_event(&instance, invalid[i][0], invalid[i][1], invalid[i][2]));
    }
    const int valid[][3] = {
        { PTR_FLAGS_MOVE, -32768, 32767 }, { PTR_FLAGS_MOVE, 32767, -32768 },
        { PTR_FLAGS_BUTTON1 | PTR_FLAGS_DOWN, 0, 0 }, { PTR_FLAGS_BUTTON1, 0, 0 },
        { PTR_FLAGS_BUTTON2 | PTR_FLAGS_DOWN, 0, 0 }, { PTR_FLAGS_BUTTON2, 0, 0 },
        { PTR_FLAGS_BUTTON3 | PTR_FLAGS_DOWN, 0, 0 }, { PTR_FLAGS_BUTTON3, 0, 0 }
    };
    for (size_t i = 0; i < sizeof(valid) / sizeof(valid[0]); i++)
        CHECK(android_send_relative_event(&instance, valid[i][0], valid[i][1], valid[i][2]));
    for (size_t i = 0; i < sizeof(valid) / sizeof(valid[0]); i++)
    {
        wMessage message = { 0 };
        CHECK(MessageQueue_Peek(context.event_queue->messages, &message, TRUE) == 1);
        ANDROID_EVENT_RELATIVE_CURSOR* event = message.wParam;
        CHECK(event->type == EVENT_TYPE_RELATIVE_CURSOR);
        CHECK(event->flags == valid[i][0]);
        CHECK(event->dx == valid[i][1] && event->dy == valid[i][2]);
        android_event_free((ANDROID_EVENT*)event);
    }
    for (unsigned i = 0; i < ANDROID_EVENT_QUEUE_LIMIT; i++)
        CHECK(android_send_relative_event(&instance, PTR_FLAGS_MOVE, -1, 1));
    CHECK(!android_send_relative_event(&instance, PTR_FLAGS_BUTTON1, 0, 0));
    drained = 0;
    android_event_queue_uninit(&instance);
    CHECK(drained == ANDROID_EVENT_QUEUE_LIMIT);
    fresh_queue();
    CHECK(android_send_relative_event(&instance, PTR_FLAGS_MOVE, -1, 1));
    /* Cancellation revokes capability admission and the real consumer frees a
     * pending relative event without reaching any protocol/context function. */
    InterlockedExchange(&context.relativeInputEnabled, -1);
    CHECK(!android_relative_input_supported(&instance));
    CHECK(!android_send_relative_event(&instance, PTR_FLAGS_MOVE, 1, 1));
    CHECK(!android_check_handle(&instance));
    CHECK(MessageQueue_Size(context.event_queue->messages) == 0);
    android_event_queue_uninit(&instance);
}

int main(void)
{
    capacity_wake_and_ownership();
    concurrent_order_and_wake();
    relative_validation_and_ownership();
    puts("actual patched Android queue: capacity/FIFO/wake/ownership/concurrency/relative validation passed");
    return 0;
}
