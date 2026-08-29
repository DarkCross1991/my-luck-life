#ifndef THREEDSONG_PLAYER_H
#define THREEDSONG_PLAYER_H

#include "decoder.h"
#include "eq.h"
#include "library.h"
#include "viz.h"

typedef enum {
    PLAYER_STOPPED = 0,
    PLAYER_PLAYING,
    PLAYER_PAUSED
} PlayerState;

typedef enum {
    REPEAT_OFF = 0,
    REPEAT_ONE,
    REPEAT_ALL
} RepeatMode;

typedef struct {
    PlayerState state;
    RepeatMode repeat;
    int shuffle;
    int volume_pct; /* 0..100 */
    int track_index;
    char current_path[LIBRARY_PATH_MAX];
    char current_title[LIBRARY_NAME_MAX];
    AudioFormat current_format;
    Decoder decoder;
    Equalizer eq;
    VizState viz;
    int ended;
    int error;
    char error_msg[96];
} Player;

void player_init(Player *p);
void player_shutdown(Player *p);
int player_open_index(Player *p, Library *lib, int index);
int player_play(Player *p);
void player_pause(Player *p);
void player_toggle(Player *p);
void player_stop(Player *p);
int player_next(Player *p, Library *lib);
int player_prev(Player *p, Library *lib);
void player_set_volume(Player *p, int pct);
void player_seek_frac(Player *p, float frac);
float player_progress(const Player *p);
int player_fill_s16_stereo(Player *p, int16_t *out, int frames);

#endif
