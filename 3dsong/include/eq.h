#ifndef THREEDSONG_EQ_H
#define THREEDSONG_EQ_H

#include <stdint.h>

#define EQ_BANDS 3
#define EQ_GAIN_MIN_DB -12.0f
#define EQ_GAIN_MAX_DB  12.0f

typedef struct {
    float b0, b1, b2, a1, a2;
    float z1, z2;
} EqBiquad;

typedef struct {
    float sample_rate;
    float gain_db[EQ_BANDS]; /* bass, mid, treble */
    EqBiquad left[EQ_BANDS];
    EqBiquad right[EQ_BANDS];
} Equalizer;

void eq_init(Equalizer *eq, float sample_rate);
void eq_reset_state(Equalizer *eq);
void eq_set_gain(Equalizer *eq, int band, float gain_db);
void eq_set_gains(Equalizer *eq, const float gain_db[EQ_BANDS]);
void eq_flat(Equalizer *eq);
void eq_rebuild(Equalizer *eq);

/* Interleaved stereo s16. frames = number of stereo sample pairs. */
void eq_process_s16_stereo(Equalizer *eq, int16_t *samples, int frames);

float eq_clamp_gain(float gain_db);
const char *eq_band_name(int band);
float eq_band_hz(int band);

#endif
