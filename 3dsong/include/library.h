#ifndef THREEDSONG_LIBRARY_H
#define THREEDSONG_LIBRARY_H

#include "formats.h"

#define LIBRARY_MAX_TRACKS 256
#define LIBRARY_PATH_MAX   512
#define LIBRARY_NAME_MAX   96

typedef struct {
    char path[LIBRARY_PATH_MAX];
    char name[LIBRARY_NAME_MAX];
    AudioFormat format;
} Track;

typedef struct {
    Track tracks[LIBRARY_MAX_TRACKS];
    int count;
    int cursor;
    int scroll;
} Library;

void library_init(Library *lib);
int library_scan(Library *lib);
int library_add_file(Library *lib, const char *path);
const Track *library_at(const Library *lib, int index);
void library_sort(Library *lib);

#endif
