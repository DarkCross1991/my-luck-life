#include "library.h"

#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>

#ifdef __3DS__
static const char *k_roots[] = {
    "sdmc:/Music",
    "sdmc:/3ds/3DSong",
    "sdmc:/3DSong",
    "sdmc:/3dsong",
    NULL
};
#else
static const char *k_roots[] = {
    "music",
    "preview/demo",
    ".",
    NULL
};
#endif

static void leaf_name(const char *path, char *out, size_t out_len)
{
    const char *slash = strrchr(path, '/');
    const char *base = slash ? slash + 1 : path;
    char *dot;
    strncpy(out, base, out_len - 1);
    out[out_len - 1] = 0;
    dot = strrchr(out, '.');
    if (dot && dot != out) *dot = 0;
}

static int already_has(const Library *lib, const char *path)
{
    int i;
    for (i = 0; i < lib->count; i++) {
        if (strcmp(lib->tracks[i].path, path) == 0) return 1;
    }
    return 0;
}

int library_add_file(Library *lib, const char *path)
{
    AudioFormat fmt;
    if (!lib || !path || lib->count >= LIBRARY_MAX_TRACKS) return -1;
    fmt = format_from_path(path);
    if (!format_is_supported(fmt)) return -1;
    if (already_has(lib, path)) return 0;
    memset(&lib->tracks[lib->count], 0, sizeof(Track));
    strncpy(lib->tracks[lib->count].path, path, LIBRARY_PATH_MAX - 1);
    leaf_name(path, lib->tracks[lib->count].name, LIBRARY_NAME_MAX);
    lib->tracks[lib->count].format = fmt;
    lib->count++;
    return 1;
}

static int track_cmp(const void *a, const void *b)
{
    const Track *ta = (const Track *)a;
    const Track *tb = (const Track *)b;
    return strcasecmp(ta->name, tb->name);
}

void library_sort(Library *lib)
{
    if (!lib || lib->count < 2) return;
    qsort(lib->tracks, (size_t)lib->count, sizeof(Track), track_cmp);
}

void library_init(Library *lib)
{
    memset(lib, 0, sizeof(*lib));
}

static int is_dir(const char *path)
{
    struct stat st;
    if (stat(path, &st) != 0) return 0;
    return S_ISDIR(st.st_mode);
}

static void scan_dir(Library *lib, const char *dir, int depth)
{
    DIR *d;
    struct dirent *ent;
    if (depth > 4 || lib->count >= LIBRARY_MAX_TRACKS) return;
    d = opendir(dir);
    if (!d) return;
    while ((ent = readdir(d)) != NULL && lib->count < LIBRARY_MAX_TRACKS) {
        char child[LIBRARY_PATH_MAX];
        if (ent->d_name[0] == '.') continue;
        if (snprintf(child, sizeof(child), "%s/%s", dir, ent->d_name) >= (int)sizeof(child)) continue;
        if (is_dir(child)) {
            scan_dir(lib, child, depth + 1);
        } else if (path_has_audio_ext(child)) {
            library_add_file(lib, child);
        }
    }
    closedir(d);
}

int library_scan(Library *lib)
{
    int i;
    library_init(lib);
    for (i = 0; k_roots[i]; i++) {
        scan_dir(lib, k_roots[i], 0);
    }
    library_sort(lib);
    return lib->count;
}

const Track *library_at(const Library *lib, int index)
{
    if (!lib || index < 0 || index >= lib->count) return NULL;
    return &lib->tracks[index];
}
