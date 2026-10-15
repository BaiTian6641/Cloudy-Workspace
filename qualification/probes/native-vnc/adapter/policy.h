#ifndef CLOUDY_VNC_POLICY_H
#define CLOUDY_VNC_POLICY_H
#include <stddef.h>
#include <stdint.h>
enum { CLOUDY_AUTH_PHASE_MILLIS = 30000, CLOUDY_AUTH_READ_IDLE_SECONDS = 15,
       CLOUDY_SESSION_READ_IDLE_SECONDS = 2 };
/* A phase never extends the overall monotonic session deadline or wraps. */
uint64_t cloudy_phase_deadline(uint64_t now, uint64_t overall, uint64_t budget);
int cloudy_dimensions(int width, int height, size_t *bytes);
int cloudy_numeric_endpoint(const char *address);
int cloudy_utf8(const uint8_t *bytes, size_t length);
int cloudy_verify_leaf(const uint8_t *der, size_t length, const uint8_t pin[32]);
void cloudy_wipe(void *data, size_t length);
#endif
