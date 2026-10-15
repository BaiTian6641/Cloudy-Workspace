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

int main(void)
{
    capacity_wake_and_ownership();
    concurrent_order_and_wake();
    puts("actual patched Android queue: capacity/FIFO/wake/ownership/concurrency passed");
    return 0;
}
