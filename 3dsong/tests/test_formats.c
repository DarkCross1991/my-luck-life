#include "formats.h"

#include <stdio.h>
#include <string.h>

static int fail(const char *msg)
{
    fprintf(stderr, "FAIL: %s\n", msg);
    return 1;
}

int main(void)
{
    char ext[16];

    if (format_from_path("song.MP3") != FMT_MP3) return fail("mp3");
    if (format_from_path("/Music/a.flac") != FMT_FLAC) return fail("flac");
    if (format_from_path("x.ogg") != FMT_OGG) return fail("ogg");
    if (format_from_path("x.opus") != FMT_OPUS) return fail("opus");
    if (format_from_path("x.wav") != FMT_WAV) return fail("wav");
    if (format_from_path("x.aiff") != FMT_AIFF) return fail("aiff");
    if (format_from_path("x.aac") != FMT_AAC) return fail("aac");
    if (format_from_path("x.m4a") != FMT_M4A) return fail("m4a");
    if (format_from_path("x.txt") != FMT_UNKNOWN) return fail("txt");
    if (!format_is_supported(FMT_MP3)) return fail("supported mp3");
    if (format_is_supported(FMT_UNKNOWN)) return fail("unsupported");
    if (strcmp(format_name(FMT_FLAC), "FLAC") != 0) return fail("name");
    format_get_ext("foo.BAR.Opus", ext, sizeof(ext));
    if (strcmp(ext, "opus") != 0) return fail("ext");
    if (!path_has_audio_ext("a.mp3")) return fail("has ext");
    if (path_has_audio_ext("a.doc")) return fail("doc");
    printf("test_formats ok\n");
    return 0;
}
