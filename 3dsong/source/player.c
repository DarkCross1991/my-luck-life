#include "player.h"

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#ifdef __3DS__
#include <3ds.h>
#endif

void player_init(Player *p)
{
    memset(p, 0, sizeof(*p));
    p->volume_pct = 80;
    p->track_index = -1;
    p->repeat = REPEAT_OFF;
    eq_init(&p->eq, 44100.0f);
    viz_init(&p->viz);
}

void player_shutdown(Player *p)
{
    player_stop(p);
    decoder_close(&p->decoder);
}

static void apply_volume(int16_t *samples, int frames, int pct)
{
    int i;
    int g;
    if (pct >= 100) return;
    if (pct <= 0) {
        memset(samples, 0, (size_t)frames * 2 * sizeof(int16_t));
        return;
    }
    g = pct * 256 / 100;
    for (i = 0; i < frames * 2; i++) {
        samples[i] = (int16_t)((samples[i] * g) / 256);
    }
}

int player_open_index(Player *p, Library *lib, int index)
{
    const Track *t;
    if (!p || !lib) return -1;
    t = library_at(lib, index);
    if (!t) return -1;
    decoder_close(&p->decoder);
    p->error = 0;
    p->error_msg[0] = 0;
    p->ended = 0;
    if (decoder_open(&p->decoder, t->path) != 0) {
        p->error = 1;
        snprintf(p->error_msg, sizeof(p->error_msg), "Cannot decode %s", format_name(t->format));
        p->state = PLAYER_STOPPED;
        strncpy(p->current_path, t->path, sizeof(p->current_path) - 1);
        strncpy(p->current_title, t->name, sizeof(p->current_title) - 1);
        p->current_format = t->format;
        p->track_index = index;
        lib->cursor = index;
        return -1;
    }
    eq_init(&p->eq, (float)p->decoder.sample_rate);
    viz_init(&p->viz);
    strncpy(p->current_path, t->path, sizeof(p->current_path) - 1);
    strncpy(p->current_title, t->name, sizeof(p->current_title) - 1);
    p->current_format = t->format;
    p->track_index = index;
    lib->cursor = index;
    p->state = PLAYER_STOPPED;
    return 0;
}

int player_play(Player *p)
{
    if (!p) return -1;
    if (!p->decoder.open) return -1;
    p->state = PLAYER_PLAYING;
    p->ended = 0;
    return 0;
}

void player_pause(Player *p)
{
    if (!p) return;
    if (p->state == PLAYER_PLAYING) p->state = PLAYER_PAUSED;
}

void player_toggle(Player *p)
{
    if (!p) return;
    if (p->state == PLAYER_PLAYING) p->state = PLAYER_PAUSED;
    else if (p->decoder.open) p->state = PLAYER_PLAYING;
}

void player_stop(Player *p)
{
    if (!p) return;
    p->state = PLAYER_STOPPED;
    if (p->decoder.open) {
        decoder_seek_frame(&p->decoder, 0);
    }
    viz_init(&p->viz);
}

static int pick_next(Player *p, Library *lib, int dir)
{
    int n;
    if (!lib || lib->count <= 0) return -1;
    n = lib->count;
    if (p->shuffle && n > 1) {
        int next = p->track_index;
        int guard = 0;
        while (next == p->track_index && guard++ < 16) {
            next = rand() % n;
        }
        return next;
    }
    {
        int idx = p->track_index + dir;
        if (idx < 0) idx = (p->repeat == REPEAT_ALL) ? n - 1 : 0;
        if (idx >= n) idx = (p->repeat == REPEAT_ALL) ? 0 : n - 1;
        return idx;
    }
}

int player_next(Player *p, Library *lib)
{
    int idx = pick_next(p, lib, 1);
    if (idx < 0) return -1;
    if (player_open_index(p, lib, idx) != 0) return -1;
    return player_play(p);
}

int player_prev(Player *p, Library *lib)
{
    int idx;
    if (player_progress(p) > 0.04f) {
        player_seek_frac(p, 0.0f);
        return 0;
    }
    idx = pick_next(p, lib, -1);
    if (idx < 0) return -1;
    if (player_open_index(p, lib, idx) != 0) return -1;
    return player_play(p);
}

void player_set_volume(Player *p, int pct)
{
    if (!p) return;
    if (pct < 0) pct = 0;
    if (pct > 100) pct = 100;
    p->volume_pct = pct;
}

void player_seek_frac(Player *p, float frac)
{
    uint64_t frame;
    if (!p || !p->decoder.open || p->decoder.total_frames == 0) return;
    if (frac < 0.0f) frac = 0.0f;
    if (frac > 1.0f) frac = 1.0f;
    frame = (uint64_t)(frac * (double)p->decoder.total_frames);
    decoder_seek_frame(&p->decoder, frame);
    p->ended = 0;
}

float player_progress(const Player *p)
{
    if (!p || !p->decoder.open || p->decoder.total_frames == 0) return 0.0f;
    {
        double f = (double)p->decoder.position_frames / (double)p->decoder.total_frames;
        if (f < 0.0) f = 0.0;
        if (f > 1.0) f = 1.0;
        return (float)f;
    }
}

int player_fill_s16_stereo(Player *p, int16_t *out, int frames)
{
    int got;
    if (!p || !out || frames <= 0) return 0;
    if (p->state != PLAYER_PLAYING || !p->decoder.open) {
        memset(out, 0, (size_t)frames * 2 * sizeof(int16_t));
        viz_idle(&p->viz, 1.0f / 60.0f);
        return 0;
    }
    got = decoder_read_s16_stereo(&p->decoder, out, frames);
    if (got <= 0) {
        memset(out, 0, (size_t)frames * 2 * sizeof(int16_t));
        p->ended = 1;
        p->state = PLAYER_STOPPED;
        viz_idle(&p->viz, 1.0f / 60.0f);
        return 0;
    }
    if (got < frames) {
        memset(out + got * 2, 0, (size_t)(frames - got) * 2 * sizeof(int16_t));
    }
    eq_process_s16_stereo(&p->eq, out, got);
    apply_volume(out, got, p->volume_pct);
    viz_analyze_s16_stereo(&p->viz, out, got);
    return got;
}
