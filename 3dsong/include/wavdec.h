#ifndef THREEDSONG_WAVDEC_H
#define THREEDSONG_WAVDEC_H

#include <stdint.h>
#include <stdio.h>

typedef struct {
    FILE *fp;
    int sample_rate;
    int channels;       /* 1 or 2 in file */
    int bits;           /* 8, 16, 24, 32 */
    int is_float;
    int is_aiff;
    uint64_t data_offset;
    uint64_t data_bytes;
    uint64_t bytes_read;
    uint64_t total_frames;
} WavDec;

int wavdec_open(WavDec *w, const char *path);
void wavdec_close(WavDec *w);
int wavdec_read_s16_stereo(WavDec *w, int16_t *out, int frames);
int wavdec_seek_frame(WavDec *w, uint64_t frame);
uint64_t wavdec_tell_frame(const WavDec *w);

#endif
