#include "decoder.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef __3DS__
#ifdef HAVE_FLAC
typedef struct {
    Decoder *d;
} FlacClient;

static FLAC__StreamDecoderWriteStatus flac_write(const FLAC__StreamDecoder *dec, const FLAC__Frame *frame, const FLAC__int32 *const buffer[], void *client)
{
    Decoder *d = ((FlacClient *)client)->d;
    unsigned i, ch;
    int need;
    (void)dec;
    if (d->flac_err) return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
    ch = frame->header.channels;
    if (ch < 1) return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
    need = d->flac_fifo_len + (int)frame->header.blocksize * 2;
    if (need > d->flac_fifo_cap) {
        int cap = need + 2048;
        int16_t *nbuf = (int16_t *)realloc(d->flac_fifo, (size_t)cap * sizeof(int16_t));
        if (!nbuf) {
            d->flac_err = 1;
            return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
        }
        if (d->flac_fifo_r > 0 && d->flac_fifo_len > 0) {
            memmove(nbuf, nbuf + d->flac_fifo_r, (size_t)d->flac_fifo_len * sizeof(int16_t));
            d->flac_fifo_r = 0;
        }
        d->flac_fifo = nbuf;
        d->flac_fifo_cap = cap;
    }
    if (d->flac_fifo_r > 0 && d->flac_fifo_len + (int)frame->header.blocksize * 2 > d->flac_fifo_cap - d->flac_fifo_r) {
        memmove(d->flac_fifo, d->flac_fifo + d->flac_fifo_r, (size_t)d->flac_fifo_len * sizeof(int16_t));
        d->flac_fifo_r = 0;
    }
    for (i = 0; i < frame->header.blocksize; i++) {
        int32_t L = buffer[0][i];
        int32_t R = (ch > 1) ? buffer[1][i] : L;
        int shift = (int)frame->header.bits_per_sample - 16;
        if (shift > 0) {
            L >>= shift;
            R >>= shift;
        } else if (shift < 0) {
            L <<= -shift;
            R <<= -shift;
        }
        if (L > 32767) L = 32767;
        if (L < -32768) L = -32768;
        if (R > 32767) R = 32767;
        if (R < -32768) R = -32768;
        d->flac_fifo[d->flac_fifo_r + d->flac_fifo_len] = (int16_t)L;
        d->flac_fifo[d->flac_fifo_r + d->flac_fifo_len + 1] = (int16_t)R;
        d->flac_fifo_len += 2;
    }
    return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
}

static void flac_meta(const FLAC__StreamDecoder *dec, const FLAC__StreamMetadata *m, void *client)
{
    Decoder *d = ((FlacClient *)client)->d;
    (void)dec;
    if (m->type == FLAC__METADATA_TYPE_STREAMINFO) {
        d->sample_rate = (int)m->data.stream_info.sample_rate;
        d->channels = (int)m->data.stream_info.channels;
        d->total_frames = m->data.stream_info.total_samples;
    }
}

static void flac_err(const FLAC__StreamDecoder *dec, FLAC__StreamDecoderErrorStatus status, void *client)
{
    Decoder *d = ((FlacClient *)client)->d;
    (void)dec;
    (void)status;
    d->flac_err = 1;
}

static FlacClient g_flac_client;
#endif
#endif

static void decoder_clear(Decoder *d)
{
    memset(d, 0, sizeof(*d));
}

int decoder_open(Decoder *d, const char *path)
{
    AudioFormat fmt;
    decoder_clear(d);
    if (!path) return -1;
    fmt = format_from_path(path);
    d->format = fmt;

    if (fmt == FMT_WAV || fmt == FMT_AIFF) {
        if (wavdec_open(&d->wav, path) != 0) return -1;
        d->sample_rate = d->wav.sample_rate;
        d->channels = d->wav.channels;
        d->total_frames = d->wav.total_frames;
        d->open = 1;
        return 0;
    }

#ifdef __3DS__
#ifdef HAVE_MPG123
    if (fmt == FMT_MP3) {
        int err = MPG123_OK;
        long rate = 0;
        int channels = 0, enc = 0;
        static int mpg_inited = 0;
        if (!mpg_inited) {
            if (mpg123_init() != MPG123_OK) return -1;
            mpg_inited = 1;
        }
        d->mh = mpg123_new(NULL, &err);
        if (!d->mh) return -1;
        mpg123_param(d->mh, MPG123_ADD_FLAGS, MPG123_FORCE_STEREO, 0.0);
        if (mpg123_open(d->mh, path) != MPG123_OK) {
            mpg123_delete(d->mh);
            d->mh = NULL;
            return -1;
        }
        mpg123_format_none(d->mh);
        mpg123_format(d->mh, 44100, MPG123_STEREO, MPG123_ENC_SIGNED_16);
        mpg123_format(d->mh, 48000, MPG123_STEREO, MPG123_ENC_SIGNED_16);
        mpg123_format(d->mh, 32000, MPG123_STEREO, MPG123_ENC_SIGNED_16);
        mpg123_format(d->mh, 22050, MPG123_STEREO, MPG123_ENC_SIGNED_16);
        mpg123_format(d->mh, 24000, MPG123_STEREO, MPG123_ENC_SIGNED_16);
        mpg123_format(d->mh, 16000, MPG123_STEREO, MPG123_ENC_SIGNED_16);
        if (mpg123_getformat(d->mh, &rate, &channels, &enc) != MPG123_OK) {
            mpg123_close(d->mh);
            mpg123_delete(d->mh);
            d->mh = NULL;
            return -1;
        }
        d->sample_rate = (int)rate;
        d->channels = channels;
        {
            off_t len = mpg123_length(d->mh);
            d->total_frames = (len > 0) ? (uint64_t)len : 0;
        }
        d->open = 1;
        return 0;
    }
#endif

#ifdef HAVE_VORBIS
    if (fmt == FMT_OGG) {
        FILE *fp;
        vorbis_info *vi;
        fp = fopen(path, "rb");
        if (!fp) return -1;
        if (ov_open(fp, &d->vf, NULL, 0) != 0) {
            fclose(fp);
            return -1;
        }
        d->vf_open = 1;
        vi = ov_info(&d->vf, -1);
        if (!vi) {
            ov_clear(&d->vf);
            d->vf_open = 0;
            return -1;
        }
        d->sample_rate = (int)vi->rate;
        d->channels = vi->channels;
        {
            ogg_int64_t pcm = ov_pcm_total(&d->vf, -1);
            d->total_frames = (pcm > 0) ? (uint64_t)pcm : 0;
        }
        d->open = 1;
        return 0;
    }
#endif

#ifdef HAVE_OPUS
    if (fmt == FMT_OPUS) {
        int err = 0;
        const OpusHead *head;
        d->opus = op_open_file(path, &err);
        if (!d->opus) return -1;
        head = op_head(d->opus, -1);
        d->sample_rate = 48000;
        d->channels = head ? head->channel_count : 2;
        {
            ogg_int64_t pcm = op_pcm_total(d->opus, -1);
            d->total_frames = (pcm > 0) ? (uint64_t)pcm : 0;
        }
        d->open = 1;
        return 0;
    }
#endif

#ifdef HAVE_FLAC
    if (fmt == FMT_FLAC) {
        FLAC__StreamDecoderInitStatus st;
        FILE *fp = fopen(path, "rb");
        if (!fp) return -1;
        d->flac = FLAC__stream_decoder_new();
        if (!d->flac) {
            fclose(fp);
            return -1;
        }
        g_flac_client.d = d;
        st = FLAC__stream_decoder_init_FILE(d->flac, fp, flac_write, flac_meta, flac_err, &g_flac_client);
        if (st != FLAC__STREAM_DECODER_INIT_STATUS_OK) {
            FLAC__stream_decoder_delete(d->flac);
            d->flac = NULL;
            fclose(fp);
            return -1;
        }
        if (!FLAC__stream_decoder_process_until_end_of_metadata(d->flac)) {
            decoder_close(d);
            return -1;
        }
        if (d->sample_rate <= 0) d->sample_rate = 44100;
        d->open = 1;
        return 0;
    }
#endif

#ifdef HAVE_FAAD
    if (fmt == FMT_AAC) {
        unsigned long sr = 0;
        unsigned char ch = 0;
        unsigned char probe[2048];
        size_t n;
        long leftover;
        d->aac_fp = fopen(path, "rb");
        if (!d->aac_fp) return -1;
        n = fread(probe, 1, sizeof(probe), d->aac_fp);
        if (n < 8) {
            fclose(d->aac_fp);
            d->aac_fp = NULL;
            return -1;
        }
        d->aac = NeAACDecOpen();
        if (!d->aac) {
            fclose(d->aac_fp);
            d->aac_fp = NULL;
            return -1;
        }
        leftover = (long)NeAACDecInit(d->aac, probe, (unsigned long)n, &sr, &ch);
        if (leftover < 0 || sr == 0) {
            NeAACDecClose(d->aac);
            d->aac = NULL;
            fclose(d->aac_fp);
            d->aac_fp = NULL;
            return -1;
        }
        fseek(d->aac_fp, leftover, SEEK_SET);
        d->sample_rate = (int)sr;
        d->channels = ch ? (int)ch : 2;
        d->aac_ready = 1;
        d->open = 1;
        return 0;
    }
#endif
#endif /* __3DS__ */

    (void)path;
    return -1;
}

void decoder_close(Decoder *d)
{
    if (!d) return;
    if (d->format == FMT_WAV || d->format == FMT_AIFF) {
        wavdec_close(&d->wav);
    }
#ifdef __3DS__
#ifdef HAVE_MPG123
    if (d->mh) {
        mpg123_close(d->mh);
        mpg123_delete(d->mh);
        d->mh = NULL;
    }
#endif
#ifdef HAVE_VORBIS
    if (d->vf_open) {
        ov_clear(&d->vf);
        d->vf_open = 0;
    }
#endif
#ifdef HAVE_OPUS
    if (d->opus) {
        op_free(d->opus);
        d->opus = NULL;
    }
#endif
#ifdef HAVE_FLAC
    if (d->flac) {
        FLAC__stream_decoder_finish(d->flac);
        FLAC__stream_decoder_delete(d->flac);
        d->flac = NULL;
    }
    free(d->flac_fifo);
    d->flac_fifo = NULL;
#endif
#ifdef HAVE_FAAD
    if (d->aac) {
        NeAACDecClose(d->aac);
        d->aac = NULL;
    }
    if (d->aac_fp) {
        fclose(d->aac_fp);
        d->aac_fp = NULL;
    }
    free(d->aac_in);
    d->aac_in = NULL;
#endif
#endif
    decoder_clear(d);
}

static void upmix_or_copy(int16_t *out, const int16_t *in, int frames, int channels)
{
    int i;
    if (channels >= 2) {
        memcpy(out, in, (size_t)frames * 2 * sizeof(int16_t));
        return;
    }
    for (i = 0; i < frames; i++) {
        out[i * 2] = in[i];
        out[i * 2 + 1] = in[i];
    }
}

int decoder_read_s16_stereo(Decoder *d, int16_t *out, int frames)
{
    int got = 0;
    if (!d || !d->open || !out || frames <= 0) return 0;

    if (d->format == FMT_WAV || d->format == FMT_AIFF) {
        got = wavdec_read_s16_stereo(&d->wav, out, frames);
        d->position_frames = wavdec_tell_frame(&d->wav);
        return got;
    }

#ifdef __3DS__
#ifdef HAVE_MPG123
    if (d->mh) {
        size_t done = 0;
        int err;
        unsigned char *dst = (unsigned char *)out;
        size_t want = (size_t)frames * 2 * sizeof(int16_t);
        err = mpg123_read(d->mh, dst, want, &done);
        if (err != MPG123_OK && err != MPG123_DONE && err != MPG123_NEW_FORMAT) {
            return 0;
        }
        got = (int)(done / (2 * sizeof(int16_t)));
        if (d->channels == 1 && got > 0) {
            /* FORCE_STEREO should already expand; leave as-is. */
        }
        d->position_frames += (uint64_t)got;
        return got;
    }
#endif

#ifdef HAVE_VORBIS
    if (d->vf_open) {
        int bitstream = 0;
        int bytes_got = 0;
        int want_bytes = frames * 2 * (int)sizeof(int16_t);
        /* tremor ov_read fills native endian 16-bit PCM, possibly mono. */
        while (bytes_got < want_bytes) {
            long n = ov_read(&d->vf, (char *)out + bytes_got, want_bytes - bytes_got, &bitstream);
            if (n <= 0) break;
            bytes_got += (int)n;
        }
        if (d->channels == 1) {
            int mono_frames = bytes_got / (int)sizeof(int16_t);
            int i;
            for (i = mono_frames - 1; i >= 0; i--) {
                int16_t s = ((int16_t *)out)[i];
                out[i * 2] = s;
                out[i * 2 + 1] = s;
            }
            got = mono_frames;
        } else {
            got = bytes_got / (2 * (int)sizeof(int16_t));
        }
        d->position_frames += (uint64_t)got;
        return got;
    }
#endif

#ifdef HAVE_OPUS
    if (d->opus) {
        int n = op_read_stereo(d->opus, out, frames * 2);
        if (n < 0) return 0;
        got = n;
        d->position_frames += (uint64_t)got;
        return got;
    }
#endif

#ifdef HAVE_FLAC
    if (d->flac) {
        while (d->flac_fifo_len / 2 < frames && !d->flac_eof && !d->flac_err) {
            if (!FLAC__stream_decoder_process_single(d->flac)) break;
            if (FLAC__stream_decoder_get_state(d->flac) == FLAC__STREAM_DECODER_END_OF_STREAM) {
                d->flac_eof = 1;
            }
        }
        got = d->flac_fifo_len / 2;
        if (got > frames) got = frames;
        if (got > 0) {
            memcpy(out, d->flac_fifo + d->flac_fifo_r, (size_t)got * 2 * sizeof(int16_t));
            d->flac_fifo_r += got * 2;
            d->flac_fifo_len -= got * 2;
            if (d->flac_fifo_len == 0) d->flac_fifo_r = 0;
        }
        d->position_frames += (uint64_t)got;
        return got;
    }
#endif

#ifdef HAVE_FAAD
    if (d->aac && d->aac_fp) {
        /* Decode ADTS frames until we fill `frames`. */
        static int16_t fifo[8192 * 2];
        static int fifo_len = 0;
        static int fifo_r = 0;
        int filled = 0;
        while (filled < frames) {
            if (fifo_len / 2 > 0) {
                int take = fifo_len / 2;
                if (take > frames - filled) take = frames - filled;
                memcpy(out + filled * 2, fifo + fifo_r, (size_t)take * 2 * sizeof(int16_t));
                fifo_r += take * 2;
                fifo_len -= take * 2;
                filled += take;
                if (fifo_len == 0) fifo_r = 0;
                continue;
            }
            {
                unsigned char pkt[1024];
                size_t n = fread(pkt, 1, sizeof(pkt), d->aac_fp);
                NeAACDecFrameInfo info;
                void *pcm;
                if (n == 0) break;
                memset(&info, 0, sizeof(info));
                pcm = NeAACDecDecode(d->aac, &info, pkt, (unsigned long)n);
                if (!pcm || info.error) break;
                if (info.bytesconsumed > 0 && info.bytesconsumed < n) {
                    fseek(d->aac_fp, -(long)(n - info.bytesconsumed), SEEK_CUR);
                }
                {
                    unsigned i;
                    unsigned nf = info.samples / (info.channels ? info.channels : 2);
                    int16_t *s = (int16_t *)pcm;
                    fifo_r = 0;
                    fifo_len = 0;
                    for (i = 0; i < nf && fifo_len / 2 < 8192; i++) {
                        if (info.channels >= 2) {
                            fifo[fifo_len++] = s[i * 2];
                            fifo[fifo_len++] = s[i * 2 + 1];
                        } else {
                            fifo[fifo_len++] = s[i];
                            fifo[fifo_len++] = s[i];
                        }
                    }
                }
            }
        }
        d->position_frames += (uint64_t)filled;
        return filled;
    }
#endif
#endif

    (void)upmix_or_copy;
    return 0;
}

int decoder_can_seek(const Decoder *d)
{
    if (!d || !d->open) return 0;
    if (d->format == FMT_WAV || d->format == FMT_AIFF) return 1;
#ifdef __3DS__
#ifdef HAVE_MPG123
    if (d->mh) return 1;
#endif
#ifdef HAVE_VORBIS
    if (d->vf_open) return 1;
#endif
#ifdef HAVE_OPUS
    if (d->opus) return 1;
#endif
#ifdef HAVE_FLAC
    if (d->flac) return 1;
#endif
#endif
    return 0;
}

int decoder_seek_frame(Decoder *d, uint64_t frame)
{
    if (!d || !d->open) return -1;
    if (d->format == FMT_WAV || d->format == FMT_AIFF) {
        if (wavdec_seek_frame(&d->wav, frame) != 0) return -1;
        d->position_frames = wavdec_tell_frame(&d->wav);
        return 0;
    }
#ifdef __3DS__
#ifdef HAVE_MPG123
    if (d->mh) {
        off_t pos = mpg123_seek(d->mh, (off_t)frame, SEEK_SET);
        if (pos < 0) return -1;
        d->position_frames = (uint64_t)pos;
        return 0;
    }
#endif
#ifdef HAVE_VORBIS
    if (d->vf_open) {
        if (ov_pcm_seek(&d->vf, (ogg_int64_t)frame) != 0) return -1;
        d->position_frames = frame;
        return 0;
    }
#endif
#ifdef HAVE_OPUS
    if (d->opus) {
        if (op_pcm_seek(d->opus, (ogg_int64_t)frame) != 0) return -1;
        d->position_frames = frame;
        return 0;
    }
#endif
#ifdef HAVE_FLAC
    if (d->flac) {
        if (!FLAC__stream_decoder_seek_absolute(d->flac, frame)) return -1;
        d->flac_fifo_len = 0;
        d->flac_fifo_r = 0;
        d->flac_eof = 0;
        d->position_frames = frame;
        return 0;
    }
#endif
#endif
    return -1;
}
