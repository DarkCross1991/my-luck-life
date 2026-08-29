#ifndef THREEDSONG_VIZ_H
#define THREEDSONG_VIZ_H

#include <stdint.h>

#define VIZ_WAVE_N 128
#define VIZ_BINS   64

typedef struct {
    float rms_l;
    float rms_r;
    float peak_l;
    float peak_r;
    float vu_l;
    float vu_r;
    float bins[VIZ_BINS];
    float wave[VIZ_WAVE_N];
    float tube[4];
} VizState;

void viz_init(VizState *v);
void viz_analyze_s16_stereo(VizState *v, const int16_t *samples, int frames);
void viz_idle(VizState *v, float dt);

#endif
