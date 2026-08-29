#include "library.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static void touch(const char *path)
{
    FILE *fp = fopen(path, "wb");
    if (fp) {
        fputs("x", fp);
        fclose(fp);
    }
}

int main(void)
{
    Library lib;
    const Track *t;

    mkdir("tmp_lib", 0755);
    touch("tmp_lib/zeta.mp3");
    touch("tmp_lib/alpha.flac");
    touch("tmp_lib/skip.txt");
    touch("tmp_lib/mid.ogg");

    library_init(&lib);
    if (library_add_file(&lib, "tmp_lib/zeta.mp3") != 1) return 1;
    if (library_add_file(&lib, "tmp_lib/alpha.flac") != 1) return 1;
    if (library_add_file(&lib, "tmp_lib/skip.txt") != -1) return 1;
    if (library_add_file(&lib, "tmp_lib/mid.ogg") != 1) return 1;
    if (library_add_file(&lib, "tmp_lib/alpha.flac") != 0) return 1; /* dup */
    if (lib.count != 3) {
        fprintf(stderr, "FAIL: count %d\n", lib.count);
        return 1;
    }
    library_sort(&lib);
    t = library_at(&lib, 0);
    if (!t || strcmp(t->name, "alpha") != 0) {
        fprintf(stderr, "FAIL: sort %s\n", t ? t->name : "nil");
        return 1;
    }
    if (lib.tracks[0].format != FMT_FLAC) return 1;
    if (lib.tracks[1].format != FMT_OGG) return 1;
    if (lib.tracks[2].format != FMT_MP3) return 1;

    unlink("tmp_lib/zeta.mp3");
    unlink("tmp_lib/alpha.flac");
    unlink("tmp_lib/skip.txt");
    unlink("tmp_lib/mid.ogg");
    rmdir("tmp_lib");
    printf("test_library ok\n");
    return 0;
}
