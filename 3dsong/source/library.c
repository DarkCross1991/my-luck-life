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
#define LIBRARY_FS_ROOT "sdmc:/"
#else
static const char *k_roots[] = {
    "music",
    "preview/demo",
    ".",
    NULL
};
#define LIBRARY_FS_ROOT "."
#endif

static void leaf_name(const char *path, char *out, size_t out_len, int keep_ext)
{
    const char *slash = strrchr(path, '/');
    const char *base = slash ? slash + 1 : path;
    char *dot;
    strncpy(out, base, out_len - 1);
    out[out_len - 1] = 0;
    if (keep_ext) return;
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

static int is_dir(const char *path)
{
    struct stat st;
    if (stat(path, &st) != 0) return 0;
    return S_ISDIR(st.st_mode);
}

static void parent_path(const char *path, char *out, size_t n)
{
    char tmp[LIBRARY_PATH_MAX];
    char *slash;
    size_t len;

    strncpy(tmp, path, sizeof(tmp) - 1);
    tmp[sizeof(tmp) - 1] = 0;
    len = strlen(tmp);
    while (len > 1 && tmp[len - 1] == '/') {
        tmp[--len] = 0;
    }
    slash = strrchr(tmp, '/');
    if (!slash) {
        strncpy(out, LIBRARY_FS_ROOT, n - 1);
        out[n - 1] = 0;
        return;
    }
    if (slash == tmp) {
        strncpy(out, "/", n - 1);
        out[n - 1] = 0;
        return;
    }
    *slash = 0;
    if (strcmp(tmp, "sdmc:") == 0) {
        strncpy(out, "sdmc:/", n - 1);
    } else {
        strncpy(out, tmp, n - 1);
    }
    out[n - 1] = 0;
}

static int is_fs_root(const char *path)
{
    return strcmp(path, "sdmc:/") == 0 || strcmp(path, "/") == 0;
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
    leaf_name(path, lib->tracks[lib->count].name, LIBRARY_NAME_MAX, 0);
    lib->tracks[lib->count].format = fmt;
    lib->tracks[lib->count].kind = ENTRY_FILE;
    lib->count++;
    return 1;
}

static int add_dir(Library *lib, const char *path, const char *name, EntryKind kind)
{
    if (!lib || lib->count >= LIBRARY_MAX_TRACKS) return -1;
    if (already_has(lib, path)) return 0;
    memset(&lib->tracks[lib->count], 0, sizeof(Track));
    strncpy(lib->tracks[lib->count].path, path, LIBRARY_PATH_MAX - 1);
    strncpy(lib->tracks[lib->count].name, name, LIBRARY_NAME_MAX - 1);
    lib->tracks[lib->count].format = FMT_UNKNOWN;
    lib->tracks[lib->count].kind = kind;
    lib->count++;
    return 1;
}

static int track_cmp(const void *a, const void *b)
{
    const Track *ta = (const Track *)a;
    const Track *tb = (const Track *)b;
    if (ta->kind != tb->kind) return (int)ta->kind - (int)tb->kind;
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

int library_open_dir(Library *lib, const char *path)
{
    DIR *d;
    struct dirent *ent;
    char norm[LIBRARY_PATH_MAX];

    if (!lib || !path || !path[0]) return -1;
    strncpy(norm, path, sizeof(norm) - 1);
    norm[sizeof(norm) - 1] = 0;

    library_init(lib);
    strncpy(lib->cwd, norm, LIBRARY_PATH_MAX - 1);

    if (!is_fs_root(norm)) {
        char up[LIBRARY_PATH_MAX];
        parent_path(norm, up, sizeof(up));
        add_dir(lib, up, "..", ENTRY_PARENT);
    }

    d = opendir(norm);
    if (!d) return lib->count;

    while ((ent = readdir(d)) != NULL && lib->count < LIBRARY_MAX_TRACKS) {
        char child[LIBRARY_PATH_MAX];
        if (ent->d_name[0] == '.') continue;
        if (snprintf(child, sizeof(child), "%s/%s", norm, ent->d_name) >= (int)sizeof(child)) continue;
        /* sdmc:/ + name should not become sdmc:// */
        if (norm[strlen(norm) - 1] == '/') {
            if (snprintf(child, sizeof(child), "%s%s", norm, ent->d_name) >= (int)sizeof(child)) continue;
        }
        if (is_dir(child)) {
            add_dir(lib, child, ent->d_name, ENTRY_DIR);
        } else if (path_has_audio_ext(child)) {
            library_add_file(lib, child);
        }
    }
    closedir(d);
    library_sort(lib);
    lib->cursor = 0;
    lib->scroll = 0;
    return lib->count;
}

int library_boot(Library *lib)
{
    int i;
    for (i = 0; k_roots[i]; i++) {
        if (is_dir(k_roots[i])) {
            return library_open_dir(lib, k_roots[i]);
        }
    }
    return library_open_dir(lib, LIBRARY_FS_ROOT);
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

int library_activate(Library *lib, int index)
{
    const Track *t = library_at(lib, index);
    char next[LIBRARY_PATH_MAX];
    if (!t) return -1;
    if (t->kind == ENTRY_FILE) return 1;
    strncpy(next, t->path, sizeof(next) - 1);
    next[sizeof(next) - 1] = 0;
    library_open_dir(lib, next);
    return 0;
}

int library_is_file(const Library *lib, int index)
{
    const Track *t = library_at(lib, index);
    return t && t->kind == ENTRY_FILE;
}

const Track *library_at(const Library *lib, int index)
{
    if (!lib || index < 0 || index >= lib->count) return NULL;
    return &lib->tracks[index];
}
