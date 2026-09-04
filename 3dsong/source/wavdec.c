#include "wavdec.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

static uint16_t rd_u16le(const unsigned char *p)
{
    return (uint16_t)(p[0] | (p[1] << 8));
}

static uint32_t rd_u32le(const unsigned char *p)
{
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

static uint16_t rd_u16be(const unsigned char *p)
{
    return (uint16_t)((p[0] << 8) | p[1]);
}

static uint32_t rd_u32be(const unsigned char *p)
{
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | (uint32_t)p[3];
}

static int read_exact(FILE *fp, void *buf, size_t n)
{
    return fread(buf, 1, n, fp) == n;
}

static int16_t sat_s16(int v)
{
    if (v > 32767) return 32767;
    if (v < -32768) return -32768;
    return (int16_t)v;
}

static int parse_wav(WavDec *w)
{
    unsigned char hdr[12];
    unsigned char chunk[8];
    int got_fmt = 0;
    int got_data = 0;
    uint16_t audio_format = 0;

    if (!read_exact(w->fp, hdr, 12)) return -1;
    if (memcmp(hdr, "RIFF", 4) != 0 || memcmp(hdr + 8, "WAVE", 4) != 0) return -1;

    while (read_exact(w->fp, chunk, 8)) {
        uint32_t size = rd_u32le(chunk + 4);
        long after = ftell(w->fp);
        if (after < 0) return -1;

        if (memcmp(chunk, "fmt ", 4) == 0) {
            unsigned char fmt[40];
            uint32_t n = size < sizeof(fmt) ? size : (uint32_t)sizeof(fmt);
            if (!read_exact(w->fp, fmt, n)) return -1;
            audio_format = rd_u16le(fmt);
            w->channels = rd_u16le(fmt + 2);
            w->sample_rate = (int)rd_u32le(fmt + 4);
            w->bits = rd_u16le(fmt + 14);
            w->is_float = (audio_format == 3);
            if (audio_format == 0xFFFE && n >= 24) {
                /* WAVEFORMATEXTENSIBLE: subformat GUID first two bytes */
                audio_format = rd_u16le(fmt + 24);
                w->is_float = (audio_format == 3);
            }
            got_fmt = 1;
        } else if (memcmp(chunk, "data", 4) == 0) {
            w->data_offset = (uint64_t)ftell(w->fp);
            w->data_bytes = size;
            got_data = 1;
            break;
        }

        if (fseek(w->fp, after + (long)((size + 1) & ~1u), SEEK_SET) != 0) return -1;
    }

    if (!got_fmt || !got_data) return -1;
    if (w->channels < 1 || w->channels > 2) return -1;
    if (w->sample_rate < 8000 || w->sample_rate > 192000) return -1;
    if (!(audio_format == 1 || audio_format == 3 || audio_format == 0xFFFE)) return -1;
    if (!(w->bits == 8 || w->bits == 16 || w->bits == 24 || w->bits == 32)) return -1;
    {
        int bpf = w->channels * (w->bits / 8);
        if (bpf <= 0) return -1;
        w->total_frames = w->data_bytes / (uint64_t)bpf;
    }
    if (fseek(w->fp, (long)w->data_offset, SEEK_SET) != 0) return -1;
    w->bytes_read = 0;
    w->is_aiff = 0;
    return 0;
}

/* 80-bit IEEE-754 extended (AIFF sample rate). */
static double read_f80be(const unsigned char *p)
{
    int exp = ((p[0] & 0x7F) << 8) | p[1];
    uint64_t mant = 0;
    int i;
    double sign = (p[0] & 0x80) ? -1.0 : 1.0;
    for (i = 2; i < 10; i++) {
        mant = (mant << 8) | p[i];
    }
    if (exp == 0 && mant == 0) return 0.0;
    return sign * ldexp((double)mant, exp - 16383 - 63);
}

static int parse_aiff(WavDec *w)
{
    unsigned char hdr[12];
    unsigned char chunk[8];
    int got_comm = 0;
    int got_ssnd = 0;
    uint32_t ssnd_offset = 0;

    if (!read_exact(w->fp, hdr, 12)) return -1;
    if (memcmp(hdr, "FORM", 4) != 0) return -1;
    if (memcmp(hdr + 8, "AIFF", 4) != 0 && memcmp(hdr + 8, "AIFC", 4) != 0) return -1;
    if (memcmp(hdr + 8, "AIFC", 4) == 0) {
        /* Only uncompressed PCM AIFC (NONE) is accepted later via COMM. */
    }

    while (read_exact(w->fp, chunk, 8)) {
        uint32_t size = rd_u32be(chunk + 4);
        long after = ftell(w->fp);
        if (after < 0) return -1;

        if (memcmp(chunk, "COMM", 4) == 0) {
            unsigned char comm[26];
            uint32_t n = size < sizeof(comm) ? size : (uint32_t)sizeof(comm);
            if (!read_exact(w->fp, comm, n)) return -1;
            w->channels = rd_u16be(comm);
            w->total_frames = rd_u32be(comm + 2);
            w->bits = rd_u16be(comm + 6);
            w->sample_rate = (int)(read_f80be(comm + 8) + 0.5);
            w->is_float = 0;
            if (memcmp(hdr + 8, "AIFC", 4) == 0 && size >= 22) {
                unsigned char comp[4];
                if (fseek(w->fp, after + 18, SEEK_SET) != 0) return -1;
                if (!read_exact(w->fp, comp, 4)) return -1;
                if (memcmp(comp, "NONE", 4) != 0 && memcmp(comp, "sowt", 4) != 0) return -1;
            }
            got_comm = 1;
        } else if (memcmp(chunk, "SSND", 4) == 0) {
            unsigned char ssnd[8];
            if (!read_exact(w->fp, ssnd, 8)) return -1;
            ssnd_offset = rd_u32be(ssnd);
            w->data_offset = (uint64_t)ftell(w->fp) + ssnd_offset;
            w->data_bytes = size > 8 ? (uint64_t)size - 8 - ssnd_offset : 0;
            got_ssnd = 1;
            break;
        }

        if (fseek(w->fp, after + (long)((size + 1) & ~1u), SEEK_SET) != 0) return -1;
    }

    if (!got_comm || !got_ssnd) return -1;
    if (w->channels < 1 || w->channels > 2) return -1;
    if (w->sample_rate < 8000 || w->sample_rate > 192000) return -1;
    if (!(w->bits == 8 || w->bits == 16 || w->bits == 24 || w->bits == 32)) return -1;
    if (fseek(w->fp, (long)w->data_offset, SEEK_SET) != 0) return -1;
    w->bytes_read = 0;
    w->is_aiff = 1;
    return 0;
}

int wavdec_open(WavDec *w, const char *path)
{
    unsigned char mag[12];
    memset(w, 0, sizeof(*w));
    w->fp = fopen(path, "rb");
    if (!w->fp) return -1;
    if (!read_exact(w->fp, mag, 12)) {
        fclose(w->fp);
        w->fp = NULL;
        return -1;
    }
    rewind(w->fp);
    if (memcmp(mag, "RIFF", 4) == 0) {
        if (parse_wav(w) != 0) {
            fclose(w->fp);
            w->fp = NULL;
            return -1;
        }
        return 0;
    }
    if (memcmp(mag, "FORM", 4) == 0) {
        if (parse_aiff(w) != 0) {
            fclose(w->fp);
            w->fp = NULL;
            return -1;
        }
        return 0;
    }
    fclose(w->fp);
    w->fp = NULL;
    return -1;
}

void wavdec_close(WavDec *w)
{
    if (!w) return;
    if (w->fp) fclose(w->fp);
    memset(w, 0, sizeof(*w));
}

static int bytes_per_frame(const WavDec *w)
{
    return w->channels * (w->bits / 8);
}

uint64_t wavdec_tell_frame(const WavDec *w)
{
    int bpf = bytes_per_frame(w);
    if (bpf <= 0) return 0;
    return w->bytes_read / (uint64_t)bpf;
}

int wavdec_seek_frame(WavDec *w, uint64_t frame)
{
    int bpf;
    uint64_t off;
    if (!w->fp) return -1;
    bpf = bytes_per_frame(w);
    if (bpf <= 0) return -1;
    if (frame > w->total_frames) frame = w->total_frames;
    off = w->data_offset + frame * (uint64_t)bpf;
    if (fseek(w->fp, (long)off, SEEK_SET) != 0) return -1;
    w->bytes_read = frame * (uint64_t)bpf;
    return 0;
}

static int16_t sample_from_bytes(const WavDec *w, const unsigned char *p)
{
    int32_t v;
    if (w->is_float) {
        float f;
        memcpy(&f, p, 4);
        if (!w->is_aiff) {
            /* WAV float is little-endian; memcpy is fine on LE hosts (3DS/PC). */
        }
        if (f > 1.0f) f = 1.0f;
        if (f < -1.0f) f = -1.0f;
        return sat_s16((int)(f * 32767.0f));
    }
    if (w->is_aiff) {
        if (w->bits == 8) {
            v = (int8_t)p[0];
            return sat_s16(v * 256);
        }
        if (w->bits == 16) {
            v = (int16_t)rd_u16be(p);
            return (int16_t)v;
        }
        if (w->bits == 24) {
            v = ((int32_t)(int8_t)p[0] << 16) | ((int32_t)p[1] << 8) | p[2];
            return sat_s16(v >> 8);
        }
        v = (int32_t)rd_u32be(p);
        return sat_s16(v >> 16);
    }
    if (w->bits == 8) {
        v = (int)p[0] - 128;
        return sat_s16(v * 256);
    }
    if (w->bits == 16) {
        return (int16_t)rd_u16le(p);
    }
    if (w->bits == 24) {
        v = ((int32_t)p[0]) | ((int32_t)p[1] << 8) | ((int32_t)(int8_t)p[2] << 16);
        return sat_s16(v >> 8);
    }
    v = (int32_t)rd_u32le(p);
    return sat_s16(v >> 16);
}

int wavdec_read_s16_stereo(WavDec *w, int16_t *out, int frames)
{
    int bpf;
    int i;
    int got = 0;
    unsigned char raw[8];

    if (!w->fp || frames <= 0) return 0;
    bpf = bytes_per_frame(w);
    if (bpf <= 0 || bpf > 8) return 0;

    for (i = 0; i < frames; i++) {
        if (w->bytes_read + (uint64_t)bpf > w->data_bytes) break;
        if (!read_exact(w->fp, raw, (size_t)bpf)) break;
        w->bytes_read += (uint64_t)bpf;
        if (w->channels == 1) {
            int16_t s = sample_from_bytes(w, raw);
            out[i * 2] = s;
            out[i * 2 + 1] = s;
        } else {
            int bps = w->bits / 8;
            out[i * 2] = sample_from_bytes(w, raw);
            out[i * 2 + 1] = sample_from_bytes(w, raw + bps);
        }
        got++;
    }
    return got;
}
