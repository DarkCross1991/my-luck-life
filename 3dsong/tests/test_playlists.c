#include "playlists.h"

#include <stdio.h>
#include <string.h>
#include <sys/stat.h>

static int ensure_dir(const char *path)
{
    struct stat st;
    if (stat(path, &st) == 0 && S_ISDIR(st.st_mode)) return 0;
#ifdef _WIN32
    return mkdir(path);
#else
    return mkdir(path, 0755);
#endif
}

int main(void)
{
    PlaylistSet ps;
    Library lib;
    char name[96];

    ensure_dir("music");
    ensure_dir("music/Demo");
    ensure_dir("music/Музыка_в_дорогу");

    playlists_init(&ps);
    if (playlists_refresh(&ps) < 2) {
        fprintf(stderr, "FAIL: expected >=2 playlists, got %d\n", ps.count);
        return 1;
    }
    playlists_display_name("music/Музыка_в_дорогу", name, sizeof(name));
    if (strcmp(name, "Музыка_в_дорогу") != 0) {
        fprintf(stderr, "FAIL: display name '%s'\n", name);
        return 1;
    }
    if (playlists_load(&ps, &lib, 0) != 0) {
        fprintf(stderr, "FAIL: load playlist\n");
        return 1;
    }
    if (playlists_switch(&ps, &lib, 1) != 0) {
        fprintf(stderr, "FAIL: switch playlist\n");
        return 1;
    }
    printf("test_playlists ok (%d playlists)\n", ps.count);
    return 0;
}
