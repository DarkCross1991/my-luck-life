#include "player.h"
#include "wavdec.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

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
    data_bytes = (uint32_t)frames * 4;
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
        float s = sinf(2.0f * (float)M_PI * 220.0f * (float)i / (float)rate);
        int16_t v = (int16_t)(s * 20000.0f);
        wr_u16(fp, (uint16_t)v);
        wr_u16(fp, (uint16_t)v);
    }
    fclose(fp);
    return 0;
}

int main(void)
{
    Library lib;
    Player p;
    int16_t buf[1024 * 2];
    int n;
    const char *path = "tmp_player.wav";
    int i, peak = 0;

    if (write_wav(path, 44100, 44100) != 0) return 1;

    library_init(&lib);
    if (library_add_file(&lib, path) != 1) return 1;

    player_init(&p);
    if (player_open_index(&p, &lib, 0) != 0) {
        fprintf(stderr, "FAIL: open %s\n", p.error_msg);
        return 1;
    }
    if (p.decoder.sample_rate != 44100) return 1;
    player_set_volume(&p, 100);
    if (player_play(&p) != 0) return 1;
    n = player_fill_s16_stereo(&p, buf, 1024);
    if (n <= 0) {
        fprintf(stderr, "FAIL: fill %d\n", n);
        return 1;
    }
    for (i = 0; i < n * 2; i++) {
        int a = buf[i] < 0 ? -buf[i] : buf[i];
        if (a > peak) peak = a;
    }
    if (peak < 1000) {
        fprintf(stderr, "FAIL: peak %d\n", peak);
        return 1;
    }
    if (p.viz.vu_l <= 0.0f && p.viz.vu_r <= 0.0f) {
        fprintf(stderr, "FAIL: viz idle\n");
        return 1;
    }
    /* 0.3: software volume is unused — console volume only, app stays at max. */
    player_set_volume(&p, 0);
    n = player_fill_s16_stereo(&p, buf, 1024);
    peak = 0;
    for (i = 0; i < n * 2; i++) {
        int a = buf[i] < 0 ? -buf[i] : buf[i];
        if (a > peak) peak = a;
    }
    if (peak < 1000) {
        fprintf(stderr, "FAIL: software mute still applied (peak %d)\n", peak);
        return 1;
    }
    player_seek_frac(&p, 0.5f);
    if (player_progress(&p) < 0.45f || player_progress(&p) > 0.55f) {
        fprintf(stderr, "FAIL: seek %f\n", player_progress(&p));
        return 1;
    }
    player_shutdown(&p);
    remove(path);
    printf("test_player ok\n");
    return 0;
}
