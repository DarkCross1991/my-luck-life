#include "eq.h"

#include <math.h>
#include <string.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static const float k_band_hz[EQ_BANDS] = {110.0f, 900.0f, 6500.0f};
static const char *k_band_name[EQ_BANDS] = {"BASS", "MID", "TREBLE"};
static const float k_band_q[EQ_BANDS] = {0.70f, 0.85f, 0.70f};

float eq_clamp_gain(float gain_db)
{
    if (gain_db < EQ_GAIN_MIN_DB) return EQ_GAIN_MIN_DB;
    if (gain_db > EQ_GAIN_MAX_DB) return EQ_GAIN_MAX_DB;
    return gain_db;
}

const char *eq_band_name(int band)
{
    if (band < 0 || band >= EQ_BANDS) return "";
    return k_band_name[band];
}

float eq_band_hz(int band)
{
    if (band < 0 || band >= EQ_BANDS) return 0.0f;
    return k_band_hz[band];
}

static void biquad_clear(EqBiquad *b)
{
    b->b0 = 1.0f;
    b->b1 = 0.0f;
    b->b2 = 0.0f;
    b->a1 = 0.0f;
    b->a2 = 0.0f;
    b->z1 = 0.0f;
    b->z2 = 0.0f;
}

/* RBJ audio EQ cookbook: peaking EQ. */
static void biquad_peaking(EqBiquad *b, float fs, float f0, float q, float gain_db)
{
    biquad_clear(b);
    if (fs < 1000.0f) fs = 44100.0f;
    if (f0 < 20.0f) f0 = 20.0f;
    if (f0 > fs * 0.45f) f0 = fs * 0.45f;
    if (q < 0.1f) q = 0.1f;

    if (fabsf(gain_db) < 0.05f) {
        return;
    }

    {
        float A = powf(10.0f, gain_db / 40.0f);
        float w0 = 2.0f * (float)M_PI * f0 / fs;
        float cosw = cosf(w0);
        float sinw = sinf(w0);
        float alpha = sinw / (2.0f * q);
        float b0 = 1.0f + alpha * A;
        float b1 = -2.0f * cosw;
        float b2 = 1.0f - alpha * A;
        float a0 = 1.0f + alpha / A;
        float a1 = -2.0f * cosw;
        float a2 = 1.0f - alpha / A;
        float inv = 1.0f / a0;
        b->b0 = b0 * inv;
        b->b1 = b1 * inv;
        b->b2 = b2 * inv;
        b->a1 = a1 * inv;
        b->a2 = a2 * inv;
        b->z1 = 0.0f;
        b->z2 = 0.0f;
    }
}

static float biquad_tick(EqBiquad *b, float x)
{
    float y = b->b0 * x + b->z1;
    b->z1 = b->b1 * x - b->a1 * y + b->z2;
    b->z2 = b->b2 * x - b->a2 * y;
    return y;
}

void eq_init(Equalizer *eq, float sample_rate)
{
    memset(eq, 0, sizeof(*eq));
    eq->sample_rate = (sample_rate > 0.0f) ? sample_rate : 44100.0f;
    eq_rebuild(eq);
}

void eq_reset_state(Equalizer *eq)
{
    int i;
    for (i = 0; i < EQ_BANDS; i++) {
        eq->left[i].z1 = eq->left[i].z2 = 0.0f;
        eq->right[i].z1 = eq->right[i].z2 = 0.0f;
    }
}

void eq_rebuild(Equalizer *eq)
{
    int i;
    for (i = 0; i < EQ_BANDS; i++) {
        biquad_peaking(&eq->left[i], eq->sample_rate, k_band_hz[i], k_band_q[i], eq->gain_db[i]);
        eq->right[i] = eq->left[i];
        eq->right[i].z1 = eq->right[i].z2 = 0.0f;
    }
}

void eq_set_gain(Equalizer *eq, int band, float gain_db)
{
    if (band < 0 || band >= EQ_BANDS) return;
    eq->gain_db[band] = eq_clamp_gain(gain_db);
    eq_rebuild(eq);
}

void eq_set_gains(Equalizer *eq, const float gain_db[EQ_BANDS])
{
    int i;
    for (i = 0; i < EQ_BANDS; i++) {
        eq->gain_db[i] = eq_clamp_gain(gain_db[i]);
    }
    eq_rebuild(eq);
}

void eq_flat(Equalizer *eq)
{
    eq->gain_db[0] = eq->gain_db[1] = eq->gain_db[2] = 0.0f;
    eq_rebuild(eq);
}

void eq_process_s16_stereo(Equalizer *eq, int16_t *samples, int frames)
{
    int i, b;
    int active[EQ_BANDS];
    int any = 0;

    if (!eq || !samples || frames <= 0) return;

    for (b = 0; b < EQ_BANDS; b++) {
        active[b] = fabsf(eq->gain_db[b]) >= 0.05f;
        any |= active[b];
    }
    if (!any) return;

    for (i = 0; i < frames; i++) {
        float l = samples[i * 2] / 32768.0f;
        float r = samples[i * 2 + 1] / 32768.0f;
        for (b = 0; b < EQ_BANDS; b++) {
            if (!active[b]) continue;
            l = biquad_tick(&eq->left[b], l);
            r = biquad_tick(&eq->right[b], r);
        }
        if (l > 0.98f) l = 0.98f;
        if (l < -0.98f) l = -0.98f;
        if (r > 0.98f) r = 0.98f;
        if (r < -0.98f) r = -0.98f;
        samples[i * 2] = (int16_t)(l * 32767.0f);
        samples[i * 2 + 1] = (int16_t)(r * 32767.0f);
    }
}
