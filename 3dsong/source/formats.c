#include "formats.h"

#include <ctype.h>
#include <string.h>

static void str_tolower_copy(const char *in, char *out, size_t out_len)
{
    size_t i = 0;
    if (out_len == 0) return;
    while (in[i] && i + 1 < out_len) {
        out[i] = (char)tolower((unsigned char)in[i]);
        i++;
    }
    out[i] = 0;
}

void format_get_ext(const char *path, char *out, size_t out_len)
{
    const char *dot;
    const char *slash;
    const char *base;

    out[0] = 0;
    if (!path || out_len == 0) return;

    slash = strrchr(path, '/');
#ifdef _WIN32
    {
        const char *bslash = strrchr(path, '\\');
        if (bslash && (!slash || bslash > slash)) slash = bslash;
    }
#endif
    base = slash ? slash + 1 : path;
    dot = strrchr(base, '.');
    if (!dot || dot == base || !dot[1]) return;
    str_tolower_copy(dot + 1, out, out_len);
}

AudioFormat format_from_ext(const char *ext)
{
    if (!ext || !ext[0]) return FMT_UNKNOWN;
    if (strcmp(ext, "wav") == 0 || strcmp(ext, "wave") == 0) return FMT_WAV;
    if (strcmp(ext, "aif") == 0 || strcmp(ext, "aiff") == 0) return FMT_AIFF;
    if (strcmp(ext, "mp3") == 0 || strcmp(ext, "mp2") == 0) return FMT_MP3;
    if (strcmp(ext, "ogg") == 0 || strcmp(ext, "oga") == 0) return FMT_OGG;
    if (strcmp(ext, "flac") == 0) return FMT_FLAC;
    if (strcmp(ext, "opus") == 0) return FMT_OPUS;
    if (strcmp(ext, "aac") == 0) return FMT_AAC;
    if (strcmp(ext, "m4a") == 0 || strcmp(ext, "mp4") == 0 || strcmp(ext, "m4b") == 0) return FMT_M4A;
    return FMT_UNKNOWN;
}

AudioFormat format_from_path(const char *path)
{
    char ext[16];
    format_get_ext(path, ext, sizeof(ext));
    return format_from_ext(ext);
}

const char *format_name(AudioFormat fmt)
{
    switch (fmt) {
    case FMT_WAV:  return "WAV";
    case FMT_AIFF: return "AIFF";
    case FMT_MP3:  return "MP3";
    case FMT_OGG:  return "OGG";
    case FMT_FLAC: return "FLAC";
    case FMT_OPUS: return "OPUS";
    case FMT_AAC:  return "AAC";
    case FMT_M4A:  return "M4A";
    default:       return "FILE";
    }
}

const char *format_ext_label(AudioFormat fmt)
{
    return format_name(fmt);
}

int format_is_supported(AudioFormat fmt)
{
    switch (fmt) {
    case FMT_WAV:
    case FMT_AIFF:
    case FMT_MP3:
    case FMT_OGG:
    case FMT_FLAC:
    case FMT_OPUS:
    case FMT_AAC:
    case FMT_M4A:
        return 1;
    default:
        return 0;
    }
}

int path_has_audio_ext(const char *path)
{
    return format_is_supported(format_from_path(path));
}
