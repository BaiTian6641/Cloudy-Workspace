/* Focused defensive LibVNCClient regression entry, not a replacement decoder.
 * Link the reviewed, sanitizer-instrumented static library with
 * --wrap=ReadFromRFBServer,--wrap=WriteToRFBServer. No sockets are created.
 * Exact source/fix mapping:
 * docs/07-reference/2026-10-15-vnc-patched-source-selection.md
 * Fixtures cover only the three named guards and matching valid controls.
 * Build bundled miniLZO with supported LZO_CFG_NO_UNALIGNED=1 so its deliberate
 * unaligned startup self-check uses portable accesses under full UBSan. This
 * does not disable sanitizers or change the compressed format/decoder guards.
 */
#include <rfb/rfbclient.h>
#include "minilzo.h"
#include <zlib.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define CAPACITY (128u * 1024u)
#define FRAME_MARKER 0xa5

typedef struct {
    unsigned char bytes[CAPACITY];
    size_t length;
    size_t consumed;
    unsigned int updates;
    unsigned int bitmaps;
    unsigned int writes;
    unsigned int callbacks_outside_frame;
    unsigned int exhausted_reads;
} Fixture;

static Fixture fixture;
static unsigned int failures;
static unsigned int cases;
static unsigned int tight_controls;
static unsigned int gradient_controls;
static unsigned int ultrazip_controls;

static void require(int condition, const char *reason)
{
    if (!condition) {
        fprintf(stderr, "Harness invariant failed: %s\n", reason);
        exit(2);
    }
}

/* Public transport APIs are substituted only in this test executable. Decoding,
 * dispatch, compression, memory management and pixel conversion remain upstream. */
rfbBool __wrap_ReadFromRFBServer(rfbClient *client, char *out, unsigned int count)
{
    (void)client;
    if (count > fixture.length - fixture.consumed) {
        fixture.exhausted_reads++;
        return FALSE;
    }
    memcpy(out, fixture.bytes + fixture.consumed, count);
    fixture.consumed += count;
    return TRUE;
}

rfbBool __wrap_WriteToRFBServer(rfbClient *client, const char *data, unsigned int count)
{
    (void)client;
    (void)data;
    (void)count;
    fixture.writes++;
    return TRUE;
}

static void append(const void *data, size_t length)
{
    require(length <= CAPACITY - fixture.length, "packet capacity");
    memcpy(fixture.bytes + fixture.length, data, length);
    fixture.length += length;
}

static void byte(unsigned int value)
{
    unsigned char data = (unsigned char)value;
    append(&data, 1);
}

static void word(unsigned int value)
{
    byte(value >> 8);
    byte(value);
}

static void dword(uint32_t value)
{
    word(value >> 16);
    word(value & 0xffffu);
}

static void header(int x, int y, int width, int height, uint32_t encoding)
{
    memset(&fixture, 0, sizeof(fixture));
    byte(rfbFramebufferUpdate);
    byte(0);
    word(1);
    word((unsigned int)x);
    word((unsigned int)y);
    word((unsigned int)width);
    word((unsigned int)height);
    dword(encoding);
}

static void compact_length(size_t length)
{
    require(length < (1u << 22), "Tight compact length");
    unsigned int first = (unsigned int)length & 127u;
    length >>= 7;
    byte(first | (length ? 128u : 0));
    if (length) {
        unsigned int second = (unsigned int)length & 127u;
        length >>= 7;
        byte(second | (length ? 128u : 0));
        if (length) byte((unsigned int)length);
    }
}

static void got_update(rfbClient *client, int x, int y, int width, int height)
{
    /* UltraZip's outer rectangle encodes count/length rather than geometry. */
    (void)client; (void)x; (void)y; (void)width; (void)height;
    fixture.updates++;
}

static void got_bitmap(rfbClient *client, const uint8_t *pixels,
                       int x, int y, int width, int height)
{
    fixture.bitmaps++;
    if (x < 0 || y < 0 || width < 0 || height < 0 ||
        (int64_t)x + width > client->width || (int64_t)y + height > client->height) {
        fixture.callbacks_outside_frame++;
        return;
    }
    size_t row_bytes = (size_t)width * (client->format.bitsPerPixel / 8u);
    for (int row = 0; row < height; row++) {
        size_t position = ((size_t)(y + row) * (size_t)client->width + (size_t)x)
                        * (client->format.bitsPerPixel / 8u);
        memcpy(client->frameBuffer + position, pixels + (size_t)row * row_bytes, row_bytes);
    }
}

static rfbClient *client_new(int bpp, int width, int height)
{
    rfbClient *client = rfbGetClient(8, 3, bpp / 8);
    require(client != NULL, "rfbGetClient");
    client->width = width;
    client->height = height;
    client->format.bitsPerPixel = (uint8_t)bpp;
    client->format.depth = (uint8_t)(bpp == 32 ? 24 : bpp);
    client->format.trueColour = TRUE;
    client->format.bigEndian = FALSE;
    client->format.redMax = bpp == 8 ? 7 : bpp == 16 ? 31 : 255;
    client->format.greenMax = bpp == 8 ? 7 : bpp == 16 ? 63 : 255;
    client->format.blueMax = bpp == 8 ? 3 : bpp == 16 ? 31 : 255;
    client->format.redShift = bpp == 8 ? 0 : bpp == 16 ? 11 : 16;
    client->format.greenShift = bpp == 8 ? 3 : bpp == 16 ? 5 : 8;
    client->format.blueShift = bpp == 8 ? 6 : 0;
    client->si.format = client->format;
    size_t length = (size_t)width * (size_t)height * (unsigned int)(bpp / 8);
    require(length <= 64u * 1024u, "bounded frame allocation");
    client->frameBuffer = malloc(length);
    require(client->frameBuffer != NULL, "frame allocation");
    memset(client->frameBuffer, FRAME_MARKER, length);
    client->GotFrameBufferUpdate = got_update;
    client->GotBitmap = got_bitmap;
    return client;
}

static int pixels_match(rfbClient *client, int x, int y, int width, int height)
{
    size_t pixel_bytes = client->format.bitsPerPixel / 8u;
    for (int row = 0; row < client->height; row++) {
        for (int column = 0; column < client->width; column++) {
            int inside = column >= x && column < x + width && row >= y && row < y + height;
            unsigned char expected = inside ? 0 : FRAME_MARKER;
            size_t offset = ((size_t)row * (size_t)client->width + (size_t)column) * pixel_bytes;
            for (size_t channel = 0; channel < pixel_bytes; channel++)
                if (client->frameBuffer[offset + channel] != expected) return 0;
        }
    }
    return 1;
}

static void finish(const char *name, int bpp, rfbClient *client,
                   int expected_result, unsigned int expected_updates,
                   unsigned int expected_bitmaps, int x, int y, int width, int height)
{
    rfbBool result = HandleRFBServerMessage(client);
    int pixels_ok = pixels_match(client, x, y, width, height);
    int passed = !!result == expected_result && fixture.updates == expected_updates &&
                 fixture.bitmaps == expected_bitmaps && !fixture.callbacks_outside_frame &&
                 !fixture.exhausted_reads && pixels_ok;
    cases++;
    if (!passed) failures++;
    printf("{\"case\":\"%s\",\"bpp\":%d,\"expectedAccepted\":%s,\"accepted\":%s,"
           "\"expectedUpdates\":%u,\"updates\":%u,\"expectedBitmaps\":%u,\"bitmaps\":%u,"
           "\"outOfFrameCallbacks\":%u,\"exhaustedReads\":%u,\"pixelsMatch\":%s,\"passed\":%s}\n",
           name, bpp, expected_result ? "true" : "false", result ? "true" : "false",
           expected_updates, fixture.updates, expected_bitmaps, fixture.bitmaps,
           fixture.callbacks_outside_frame, fixture.exhausted_reads,
           pixels_ok ? "true" : "false", passed ? "true" : "false");
    /* rfbClientCleanup deliberately leaves framebuffer ownership to its caller. */
    unsigned char *frame = client->frameBuffer;
    rfbClientCleanup(client);
    free(frame);
}

static void tight_case(int bpp, int gradient, int width, int extra_rows)
{
    int reject_width = gradient && width > TIGHT_GRADIENT_MAX_WIDTH;
    int row_bytes = width * (bpp == 32 ? 3 : bpp / 8);
    size_t raw_size = (size_t)row_bytes * (size_t)(1 + extra_rows);
    unsigned char *raw = calloc(raw_size, 1);
    uLongf capacity = compressBound((uLong)raw_size);
    unsigned char *compressed = malloc(capacity);
    require(raw && compressed, "Tight fixture allocation");
    require(compress2(compressed, &capacity, raw, (uLong)raw_size, Z_BEST_SPEED) == Z_OK,
            "real zlib compression");
    header(0, 0, width, 1, rfbEncodingTight);
    /* Reset stream zero, then select explicit gradient filter when requested. */
    byte(gradient ? 0x41 : 0x01);
    if (gradient) byte(rfbTightFilterGradient);
    compact_length(capacity);
    append(compressed, capacity);
    rfbClient *client = client_new(bpp, width, 1);
    int accept = !reject_width && !extra_rows;
    const char *name = gradient ? (reject_width ? "gradient-width-2049-rejected" : "gradient-width-2048-valid")
                                : (extra_rows ? "tight-excess-decompressed-rows-rejected" : "tight-valid-row");
    if (accept) {
        if (gradient) gradient_controls++; else tight_controls++;
    }
    finish(name, bpp, client, accept, accept ? 1u : 0u, 0, 0, 0, accept ? width : 0, accept ? 1 : 0);
    free(raw);
    free(compressed);
}

static void subrectangle(unsigned char *out, unsigned int width, unsigned int height)
{
    /* One raw subrectangle at (0,0), network byte order. */
    memset(out, 0, 12);
    out[4] = (unsigned char)(width >> 8); out[5] = (unsigned char)width;
    out[6] = (unsigned char)(height >> 8); out[7] = (unsigned char)height;
}

static void ultrazip_case(int bpp, int variant)
{
    unsigned char raw[28] = {0};
    size_t size = 12u + 4u * (unsigned int)(bpp / 8);
    unsigned int count = 1;
    subrectangle(raw, 2, 2);
    const char *name = "ultrazip-valid-subrectangle";
    if (variant == 1) { size = 11; name = "ultrazip-truncated-header-rejected"; }
    if (variant == 2) { size--; name = "ultrazip-truncated-raw-rejected"; }
    if (variant == 3) { count = 2; name = "ultrazip-excess-count-rejected"; }
    if (variant == 4) {
        subrectangle(raw, 65535, 65535);
        size = 12;
        name = "ultrazip-large-raw-product-rejected";
    }
    unsigned char compressed[128] = {0};
    lzo_uint compressed_size = sizeof(compressed);
    void *work = malloc(LZO1X_1_MEM_COMPRESS);
    require(work != NULL, "LZO work allocation");
    require(lzo1x_1_compress(raw, (lzo_uint)size, compressed, &compressed_size, work) == LZO_E_OK,
            "real bundled miniLZO compression");
    /* UltraZip's x=count, y=uncompressed byte count, w=high size word, h=0. */
    header((int)count, (int)size, 0, 0, rfbEncodingUltraZip);
    dword((uint32_t)compressed_size);
    append(compressed, compressed_size);
    rfbClient *client = client_new(bpp, 4, 4);
    unsigned int decoded = variant == 0 || variant == 3 ? 1u : 0u;
    if (variant == 0) ultrazip_controls++;
    finish(name, bpp, client, variant == 0, variant == 0 ? 1u : 0u,
           decoded, 0, 0, decoded ? 2 : 0, decoded ? 2 : 0);
    free(work);
}

int main(void)
{
    require(setvbuf(stdout, NULL, _IOLBF, 0) == 0, "line-buffered evidence");
    require(lzo_init() == LZO_E_OK, "miniLZO initialization");
    for (int bpp = 8; bpp <= 32; bpp *= 2) {
        tight_case(bpp, 0, 16, 0);
        tight_case(bpp, 0, 16, 1);
        tight_case(bpp, 1, 2048, 0);
        tight_case(bpp, 1, 2049, 0);
        for (int variant = 0; variant <= 4; variant++) ultrazip_case(bpp, variant);
    }
    printf("{\"summary\":true,\"cases\":%u,\"failures\":%u,\"tightValidControls\":%u,"
           "\"gradientValidControls\":%u,\"ultrazipValidControls\":%u}\n",
           cases, failures, tight_controls, gradient_controls, ultrazip_controls);
    return failures ? 1 : 0;
}
