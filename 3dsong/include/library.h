#ifndef THREEDSONG_LIBRARY_H
#define THREEDSONG_LIBRARY_H

#include "formats.h"

#define LIBRARY_MAX_TRACKS 256
#define LIBRARY_PATH_MAX   512
#define LIBRARY_NAME_MAX   96

typedef enum {
    ENTRY_PARENT = 0,
    ENTRY_DIR,
    ENTRY_FILE
} EntryKind;

typedef struct {
    char path[LIBRARY_PATH_MAX];
    char name[LIBRARY_NAME_MAX];
    AudioFormat format;
    EntryKind kind;
} Track;

typedef struct {
    Track tracks[LIBRARY_MAX_TRACKS];
    int count;
    int cursor;
    int scroll;
    char cwd[LIBRARY_PATH_MAX];
} Library;

void library_init(Library *lib);
int library_boot(Library *lib);
int library_open_dir(Library *lib, const char *path);
int library_scan(Library *lib);
int library_add_file(Library *lib, const char *path);
int library_activate(Library *lib, int index);
int library_is_file(const Library *lib, int index);
const Track *library_at(const Library *lib, int index);
void library_sort(Library *lib);

#endif
