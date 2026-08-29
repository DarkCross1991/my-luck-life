#ifndef THREEDSONG_FORMATS_H
#define THREEDSONG_FORMATS_H

#include <stddef.h>

typedef enum {
    FMT_UNKNOWN = 0,
    FMT_WAV,
    FMT_AIFF,
    FMT_MP3,
    FMT_OGG,
    FMT_FLAC,
    FMT_OPUS,
    FMT_AAC,
    FMT_M4A
} AudioFormat;

AudioFormat format_from_path(const char *path);
AudioFormat format_from_ext(const char *ext);
const char *format_name(AudioFormat fmt);
const char *format_ext_label(AudioFormat fmt);
int format_is_supported(AudioFormat fmt);
int path_has_audio_ext(const char *path);

/* Lowercase copy of the extension without the dot. Returns empty string if none. */
void format_get_ext(const char *path, char *out, size_t out_len);

#endif
