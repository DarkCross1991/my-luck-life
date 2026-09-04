#include "playlists.h"

#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>

#ifdef __3DS__
#define PLAYLIST_ROOT "sdmc:/Music"
#else
#define PLAYLIST_ROOT "music"
#endif

static int is_dir(const char *path)
{
    struct stat st;
    if (stat(path, &st) != 0) return 0;
    return S_ISDIR(st.st_mode);
}

static int count_audio(const char *dir)
{
    DIR *d;
    struct dirent *ent;
    int n = 0;
    d = opendir(dir);
    if (!d) return 0;
    while ((ent = readdir(d)) != NULL) {
        char child[LIBRARY_PATH_MAX];
        if (ent->d_name[0] == '.') continue;
        if (snprintf(child, sizeof(child), "%s/%s", dir, ent->d_name) >= (int)sizeof(child))
            continue;
        if (!is_dir(child) && path_has_audio_ext(child)) n++;
    }
    closedir(d);
    return n;
}

static int name_cmp(const void *a, const void *b)
{
    const PlaylistInfo *pa = (const PlaylistInfo *)a;
    const PlaylistInfo *pb = (const PlaylistInfo *)b;
    return strcasecmp(pa->name, pb->name);
}

void playlists_init(PlaylistSet *ps)
{
    if (!ps) return;
    memset(ps, 0, sizeof(*ps));
    ps->active = -1;
}

void playlists_display_name(const char *path, char *out, size_t n)
{
    const char *slash;
    const char *prefix = PLAYLIST_ROOT;
    size_t plen;
    if (!out || n == 0) return;
    out[0] = 0;
    if (!path || !path[0]) return;
    plen = strlen(prefix);
    if (strncmp(path, prefix, plen) == 0) {
        const char *rest = path + plen;
        if (*rest == '/' || *rest == '\0') {
            if (*rest == '/') rest++;
            if (*rest) {
                strncpy(out, rest, n - 1);
                out[n - 1] = 0;
                return;
            }
            strncpy(out, "Music", n - 1);
            out[n - 1] = 0;
            return;
        }
    }
    slash = strrchr(path, '/');
    strncpy(out, slash ? slash + 1 : path, n - 1);
    out[n - 1] = 0;
}

int playlists_refresh(PlaylistSet *ps)
{
    DIR *d;
    struct dirent *ent;
    char keep[LIBRARY_PATH_MAX];
    int i;

    if (!ps) return -1;
    keep[0] = 0;
    if (ps->active >= 0 && ps->active < ps->count)
        strncpy(keep, ps->items[ps->active].path, sizeof(keep) - 1);

    playlists_init(ps);
    if (!is_dir(PLAYLIST_ROOT)) return 0;

    d = opendir(PLAYLIST_ROOT);
    if (!d) return 0;
    while ((ent = readdir(d)) != NULL && ps->count < PLAYLIST_MAX) {
        char child[LIBRARY_PATH_MAX];
        if (ent->d_name[0] == '.') continue;
        if (snprintf(child, sizeof(child), "%s/%s", PLAYLIST_ROOT, ent->d_name) >= (int)sizeof(child))
            continue;
        if (!is_dir(child)) continue;
        memset(&ps->items[ps->count], 0, sizeof(PlaylistInfo));
        strncpy(ps->items[ps->count].name, ent->d_name, LIBRARY_NAME_MAX - 1);
        strncpy(ps->items[ps->count].path, child, LIBRARY_PATH_MAX - 1);
        ps->items[ps->count].song_count = count_audio(child);
        ps->count++;
    }
    closedir(d);

    if (ps->count > 1)
        qsort(ps->items, (size_t)ps->count, sizeof(PlaylistInfo), name_cmp);

    ps->active = 0;
    for (i = 0; i < ps->count; i++) {
        if (keep[0] && strcmp(ps->items[i].path, keep) == 0) {
            ps->active = i;
            break;
        }
    }
    ps->cursor = ps->active;
    return ps->count;
}

const PlaylistInfo *playlists_at(const PlaylistSet *ps, int index)
{
    if (!ps || index < 0 || index >= ps->count) return NULL;
    return &ps->items[index];
}

int playlists_load(PlaylistSet *ps, Library *lib, int index)
{
    const PlaylistInfo *pl;
    if (!ps || !lib) return -1;
    pl = playlists_at(ps, index);
    if (!pl) return -1;
    if (library_open_dir(lib, pl->path) < 0) return -1;
    ps->active = index;
    ps->cursor = index;
    ps->items[index].song_count = 0;
    {
        int i;
        for (i = 0; i < lib->count; i++) {
            if (lib->tracks[i].kind == ENTRY_FILE) ps->items[index].song_count++;
        }
    }
    return 0;
}

int playlists_switch(PlaylistSet *ps, Library *lib, int dir)
{
    int next;
    if (!ps || !lib || ps->count <= 0) return -1;
    next = (ps->active + dir) % ps->count;
    if (next < 0) next += ps->count;
    return playlists_load(ps, lib, next);
}
