#include "wavdec.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static void wr_u16(FILE *fp, uint16_t v)
{
    unsigned char b[2] = { (unsigned char)v, (unsigned char)(v >> 8) };
    fwrite(b, 1, 2, fp);
}

static void wr_u32(FILE *fp, uint32_t v)
{
    unsigned char b[4] = {
        (unsigned char)v, (unsigned char)(v >> 8),
        (unsigned char)(v >> 16), (unsigned char)(v >> 24)
    };
    fwrite(b, 1, 4, fp);
}

static int write_wav(const char *path, int rate, int frames)
{
    FILE *fp = fopen(path, "wb");
    int i;
    uint32_t data_bytes;
    if (!fp) return -1;
    data_bytes = (uint32_t)frames * 2 * 2;
    fwrite("RIFF", 1, 4, fp);
    wr_u32(fp, 36 + data_bytes);
    fwrite("WAVE", 1, 4, fp);
    fwrite("fmt ", 1, 4, fp);
    wr_u32(fp, 16);
    wr_u16(fp, 1);
    wr_u16(fp, 2);
    wr_u32(fp, (uint32_t)rate);
    wr_u32(fp, (uint32_t)rate * 4);
    wr_u16(fp, 4);
    wr_u16(fp, 16);
    fwrite("data", 1, 4, fp);
    wr_u32(fp, data_bytes);
    for (i = 0; i < frames; i++) {
        float s = sinf(2.0f * (float)M_PI * 440.0f * (float)i / (float)rate);
        int16_t v = (int16_t)(s * 16000.0f);
        wr_u16(fp, (uint16_t)v);
        wr_u16(fp, (uint16_t)(v / 2));
    }
    fclose(fp);
    return 0;
}

int main(void)
{
    const char *path = "tmp_tone.wav";
    WavDec w;
    int16_t buf[256 * 2];
    int n, i, nonzero = 0;
    int frames = 4410;

    if (write_wav(path, 44100, frames) != 0) {
        fprintf(stderr, "FAIL: write wav\n");
        return 1;
    }
    if (wavdec_open(&w, path) != 0) {
        fprintf(stderr, "FAIL: open wav\n");
        return 1;
    }
    if (w.sample_rate != 44100 || w.channels != 2 || w.bits != 16) {
        fprintf(stderr, "FAIL: fmt %d %d %d\n", w.sample_rate, w.channels, w.bits);
        return 1;
    }
    if (w.total_frames != (uint64_t)frames) {
        fprintf(stderr, "FAIL: frames %llu\n", (unsigned long long)w.total_frames);
        return 1;
    }
    n = wavdec_read_s16_stereo(&w, buf, 256);
    if (n != 256) {
        fprintf(stderr, "FAIL: read %d\n", n);
        return 1;
    }
    for (i = 0; i < n * 2; i++) {
        if (buf[i] != 0) nonzero = 1;
    }
    if (!nonzero) {
        fprintf(stderr, "FAIL: silence\n");
        return 1;
    }
    if (wavdec_seek_frame(&w, 0) != 0) {
        fprintf(stderr, "FAIL: seek\n");
        return 1;
    }
    wavdec_close(&w);
    remove(path);
    printf("test_wav ok\n");
    return 0;
}
