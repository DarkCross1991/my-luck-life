#include "viz.h"

#include <math.h>
#include <string.h>

void viz_init(VizState *v)
{
    memset(v, 0, sizeof(*v));
}

static float env_follow(float prev, float target, float attack, float release)
{
    float coeff = (target > prev) ? attack : release;
    return prev + (target - prev) * coeff;
}

void viz_analyze_s16_stereo(VizState *v, const int16_t *samples, int frames)
{
    double acc_l = 0.0, acc_r = 0.0;
    float peak_l = 0.0f, peak_r = 0.0f;
    int i, b;
    float band_acc[VIZ_BINS];
    int band_n[VIZ_BINS];
    int step;

    if (!v || !samples || frames <= 0) return;
    memset(band_acc, 0, sizeof(band_acc));
    memset(band_n, 0, sizeof(band_n));

    for (i = 0; i < frames; i++) {
        float l = samples[i * 2] / 32768.0f;
        float r = samples[i * 2 + 1] / 32768.0f;
        float al = fabsf(l);
        float ar = fabsf(r);
        acc_l += (double)(l * l);
        acc_r += (double)(r * r);
        if (al > peak_l) peak_l = al;
        if (ar > peak_r) peak_r = ar;
        {
            float m = 0.5f * (al + ar);
            int bin = (int)((float)i / (float)frames * (float)VIZ_BINS);
            if (bin >= VIZ_BINS) bin = VIZ_BINS - 1;
            band_acc[bin] += m;
            band_n[bin]++;
        }
    }

    v->rms_l = (float)sqrt(acc_l / (double)frames);
    v->rms_r = (float)sqrt(acc_r / (double)frames);
    v->peak_l = peak_l;
    v->peak_r = peak_r;
    v->vu_l = env_follow(v->vu_l, v->rms_l * 2.4f, 0.35f, 0.08f);
    v->vu_r = env_follow(v->vu_r, v->rms_r * 2.4f, 0.35f, 0.08f);
    if (v->vu_l > 1.0f) v->vu_l = 1.0f;
    if (v->vu_r > 1.0f) v->vu_r = 1.0f;

    for (b = 0; b < VIZ_BINS; b++) {
        float e = band_n[b] ? (band_acc[b] / (float)band_n[b]) : 0.0f;
        v->bins[b] = env_follow(v->bins[b], e * 2.2f, 0.45f, 0.12f);
        if (v->bins[b] > 1.0f) v->bins[b] = 1.0f;
    }

    step = frames / VIZ_WAVE_N;
    if (step < 1) step = 1;
    for (i = 0; i < VIZ_WAVE_N; i++) {
        int idx = i * step;
        if (idx >= frames) idx = frames - 1;
        v->wave[i] = samples[idx * 2] / 32768.0f;
    }

    v->tube[0] = env_follow(v->tube[0], v->rms_l * 1.8f + v->bins[1] * 0.4f, 0.25f, 0.06f);
    v->tube[1] = env_follow(v->tube[1], (v->rms_l + v->rms_r) + v->bins[4] * 0.5f, 0.25f, 0.06f);
    v->tube[2] = env_follow(v->tube[2], v->rms_r * 1.8f + v->bins[8] * 0.4f, 0.25f, 0.06f);
    v->tube[3] = env_follow(v->tube[3], (v->peak_l + v->peak_r) * 0.7f + v->bins[12] * 0.5f, 0.3f, 0.07f);
    for (i = 0; i < 4; i++) {
        if (v->tube[i] > 1.0f) v->tube[i] = 1.0f;
        if (v->tube[i] < 0.12f) v->tube[i] = 0.12f; /* filament always warm */
    }
}

void viz_idle(VizState *v, float dt)
{
    int i;
    float k;
    if (!v) return;
    if (dt < 0.0f) dt = 0.0f;
    if (dt > 0.1f) dt = 0.1f;
    k = 1.0f - dt * 2.5f;
    if (k < 0.0f) k = 0.0f;
    v->rms_l *= k;
    v->rms_r *= k;
    v->peak_l *= k;
    v->peak_r *= k;
    v->vu_l *= k;
    v->vu_r *= k;
    for (i = 0; i < VIZ_BINS; i++) v->bins[i] *= k;
    for (i = 0; i < VIZ_WAVE_N; i++) v->wave[i] *= k;
    for (i = 0; i < 4; i++) {
        v->tube[i] = 0.12f + (v->tube[i] - 0.12f) * k;
    }
}
