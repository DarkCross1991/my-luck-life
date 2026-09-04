#ifndef THREEDSONG_PLAYLISTS_H
#define THREEDSONG_PLAYLISTS_H

#include "library.h"

#define PLAYLIST_MAX 64

typedef struct {
    char name[LIBRARY_NAME_MAX];
    char path[LIBRARY_PATH_MAX];
    int song_count;
} PlaylistInfo;

typedef struct {
    PlaylistInfo items[PLAYLIST_MAX];
    int count;
    int active;   /* currently loaded playlist */
    int cursor;   /* highlighted on playlist screen */
} PlaylistSet;

void playlists_init(PlaylistSet *ps);
/* Scan sdmc:/Music (or host music/) for subfolders = playlists. */
int playlists_refresh(PlaylistSet *ps);
int playlists_load(PlaylistSet *ps, Library *lib, int index);
int playlists_switch(PlaylistSet *ps, Library *lib, int dir);
const PlaylistInfo *playlists_at(const PlaylistSet *ps, int index);
/* Leaf folder name for UI (no sdmc:/Music/ prefix). */
void playlists_display_name(const char *path, char *out, size_t n);

#endif
