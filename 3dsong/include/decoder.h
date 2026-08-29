#ifndef THREEDSONG_DECODER_H
#define THREEDSONG_DECODER_H

#include <stdint.h>
#include "formats.h"
#include "wavdec.h"

#ifdef __3DS__
#ifdef HAVE_MPG123
#include <mpg123.h>
#endif
#ifdef HAVE_VORBIS
#include <tremor/ivorbisfile.h>
#endif
#ifdef HAVE_OPUS
#include <opus/opusfile.h>
#endif
#ifdef HAVE_FLAC
#include <FLAC/stream_decoder.h>
#endif
#ifdef HAVE_FAAD
#include <neaacdec.h>
#endif
#endif

typedef struct Decoder Decoder;

struct Decoder {
    AudioFormat format;
    int sample_rate;
    int channels;
    int open;
    uint64_t total_frames; /* 0 if unknown */
    uint64_t position_frames;

    WavDec wav;

#ifdef __3DS__
#ifdef HAVE_MPG123
    mpg123_handle *mh;
#endif
#ifdef HAVE_VORBIS
    OggVorbis_File vf;
    int vf_open;
#endif
#ifdef HAVE_OPUS
    OggOpusFile *opus;
#endif
#ifdef HAVE_FLAC
    FLAC__StreamDecoder *flac;
    int16_t *flac_fifo;
    int flac_fifo_cap;
    int flac_fifo_len;
    int flac_fifo_r;
    int flac_eof;
    int flac_err;
#endif
#ifdef HAVE_FAAD
    NeAACDecHandle aac;
    FILE *aac_fp;
    uint8_t *aac_in;
    size_t aac_in_cap;
    int aac_ready;
#endif
#endif
};

int decoder_open(Decoder *d, const char *path);
void decoder_close(Decoder *d);
/* Read interleaved s16 stereo frames. Returns frames actually written (0 = EOF/error). */
int decoder_read_s16_stereo(Decoder *d, int16_t *out, int frames);
int decoder_seek_frame(Decoder *d, uint64_t frame);
int decoder_can_seek(const Decoder *d);

#endif
