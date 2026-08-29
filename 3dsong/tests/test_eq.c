#include "eq.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static void sine_stereo(int16_t *buf, int frames, int rate, float hz, float amp)
{
    int i;
    for (i = 0; i < frames; i++) {
        float s = sinf(2.0f * (float)M_PI * hz * (float)i / (float)rate) * amp;
        int16_t v = (int16_t)(s * 32767.0f);
        buf[i * 2] = v;
        buf[i * 2 + 1] = v;
    }
}

static double rms(const int16_t *buf, int frames)
{
    double acc = 0.0;
    int i;
    for (i = 0; i < frames * 2; i++) {
        double x = buf[i] / 32768.0;
        acc += x * x;
    }
    return sqrt(acc / (double)(frames * 2));
}

static int fail(const char *msg)
{
    fprintf(stderr, "FAIL: %s\n", msg);
    return 1;
}

int main(void)
{
    Equalizer eq;
    const int rate = 44100;
    const int frames = 44100;
    int16_t *buf = (int16_t *)malloc((size_t)frames * 2 * sizeof(int16_t));
    double e_flat, e_boost, e_cut, e_other;

    if (!buf) return fail("oom");
    eq_init(&eq, (float)rate);

    if (eq_clamp_gain(99.0f) != EQ_GAIN_MAX_DB) return fail("clamp max");
    if (eq_clamp_gain(-99.0f) != EQ_GAIN_MIN_DB) return fail("clamp min");
    if (eq_band_hz(0) != 110.0f) return fail("bass hz");
    if (eq_band_hz(2) != 6500.0f) return fail("treble hz");

    sine_stereo(buf, frames, rate, 110.0f, 0.25f);
    eq_flat(&eq);
    eq_process_s16_stereo(&eq, buf, frames);
    sine_stereo(buf, frames, rate, 110.0f, 0.25f);
    e_flat = rms(buf, frames);

    sine_stereo(buf, frames, rate, 110.0f, 0.25f);
    eq_set_gain(&eq, 0, 12.0f);
    eq_process_s16_stereo(&eq, buf, frames);
    e_boost = rms(buf, frames);

    sine_stereo(buf, frames, rate, 110.0f, 0.25f);
    eq_set_gain(&eq, 0, -12.0f);
    eq_process_s16_stereo(&eq, buf, frames);
    e_cut = rms(buf, frames);

    if (!(e_boost > e_flat * 1.4)) {
        fprintf(stderr, "bass boost %f vs flat %f\n", e_boost, e_flat);
        return fail("bass boost");
    }
    if (!(e_cut < e_flat * 0.75)) {
        fprintf(stderr, "bass cut %f vs flat %f\n", e_cut, e_flat);
        return fail("bass cut");
    }

    /* Treble boost should not explode a bass sine. */
    sine_stereo(buf, frames, rate, 110.0f, 0.25f);
    eq_flat(&eq);
    eq_set_gain(&eq, 2, 12.0f);
    eq_process_s16_stereo(&eq, buf, frames);
    e_other = rms(buf, frames);
    if (e_other > e_flat * 1.35) {
        fprintf(stderr, "treble leaked into bass %f vs %f\n", e_other, e_flat);
        return fail("band isolation");
    }

    /* Mid boost on 900 Hz. */
    sine_stereo(buf, frames, rate, 900.0f, 0.25f);
    eq_flat(&eq);
    e_flat = rms(buf, frames);
    sine_stereo(buf, frames, rate, 900.0f, 0.25f);
    eq_set_gain(&eq, 1, 12.0f);
    eq_process_s16_stereo(&eq, buf, frames);
    e_boost = rms(buf, frames);
    if (!(e_boost > e_flat * 1.4)) return fail("mid boost");

    free(buf);
    printf("test_eq ok\n");
    return 0;
}
