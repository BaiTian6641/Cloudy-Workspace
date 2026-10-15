#include "policy.h"
#include <arpa/inet.h>
#include <limits.h>
#include <openssl/crypto.h>
#include <openssl/evp.h>
#include <openssl/x509.h>
#include <time.h>

uint64_t cloudy_phase_deadline(uint64_t now, uint64_t overall, uint64_t budget)
{
    if (now >= overall || budget >= overall - now) return overall;
    return now + budget;
}

void cloudy_wipe(void *data, size_t length)
{
    volatile uint8_t *bytes = data;
    while (length--) *bytes++ = 0;
}

int cloudy_dimensions(int width, int height, size_t *bytes)
{
    if (!bytes || width <= 0 || height <= 0 || width > 4096 || height > 4096 ||
        (uint64_t)width * (uint64_t)height > 8u * 1024u * 1024u) return 0;
    *bytes = (size_t)width * (size_t)height * 4u;
    return 1;
}

int cloudy_numeric_endpoint(const char *address)
{
    uint8_t output[16];
    return address && (inet_pton(AF_INET, address, output) == 1 ||
                       inet_pton(AF_INET6, address, output) == 1);
}

int cloudy_utf8(const uint8_t *bytes, size_t length)
{
    for (size_t i = 0; i < length;) {
        uint32_t value = bytes[i++];
        unsigned int count;
        uint32_t minimum;
        if (value == 0) return 0;
        if (value < 128) continue;
        if (value >= 0xc2 && value <= 0xdf) { count = 1; minimum = 0x80; value &= 0x1f; }
        else if (value >= 0xe0 && value <= 0xef) { count = 2; minimum = 0x800; value &= 0x0f; }
        else if (value >= 0xf0 && value <= 0xf4) { count = 3; minimum = 0x10000; value &= 7; }
        else return 0;
        if (count > length - i) return 0;
        while (count--) {
            if ((bytes[i] & 0xc0) != 0x80) return 0;
            value = (value << 6) | (bytes[i++] & 63);
        }
        if (value < minimum || value > 0x10ffff || (value >= 0xd800 && value <= 0xdfff)) return 0;
    }
    return 1;
}

int cloudy_verify_leaf(const uint8_t *der, size_t length, const uint8_t pin[32])
{
    if (!der || !pin || length == 0 || length > 128u * 1024u || length > LONG_MAX) return 0;
    const unsigned char *cursor = der;
    X509 *certificate = d2i_X509(NULL, &cursor, (long)length);
    if (!certificate) return 0;
    uint8_t fingerprint[32];
    unsigned int size = 0;
    time_t now = time(NULL);
    int before = ASN1_TIME_cmp_time_t(X509_get0_notBefore(certificate), now);
    int after = ASN1_TIME_cmp_time_t(X509_get0_notAfter(certificate), now);
    /* Pin trust is explicit leaf identity, bound to one immutable endpoint/profile.
     * No CA-validity shortcut or error/expiry override is allowed. ASN.1 time
     * errors return -2, distinct from before(-1), equal(0) and after(1). */
    int accepted = cursor == der + length &&
        now != (time_t)-1 && before >= -1 && before <= 0 && after >= 0 && after <= 1 &&
        X509_digest(certificate, EVP_sha256(), fingerprint, &size) == 1 && size == 32 &&
        CRYPTO_memcmp(fingerprint, pin, 32) == 0;
    cloudy_wipe(fingerprint, sizeof(fingerprint));
    X509_free(certificate);
    return accepted;
}
