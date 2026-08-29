#include "ui.h"
#include "colors.h"
#include "layout.h"
#include "version.h"

#ifdef __3DS__

#include <math.h>
#include <stdio.h>
#include <string.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static C2D_TextBuf g_textbuf;

void ui_init(void)
{
    g_textbuf = C2D_TextBufNew(8192);
}

void ui_fini(void)
{
    if (g_textbuf) C2D_TextBufDelete(g_textbuf);
    g_textbuf = NULL;
}

void ui_draw_text(const char *s, float x, float y, float scale, u32 color, u32 flags)
{
    C2D_Text t;
    if (!s || !s[0]) return;
    C2D_TextBufClear(g_textbuf);
    C2D_TextParse(&t, g_textbuf, s);
    C2D_TextOptimize(&t);
    C2D_DrawText(&t, flags | C2D_WithColor, x, y, 0.5f, scale, scale, color);
}

static void rect(float x, float y, float w, float h, u32 c)
{
    C2D_DrawRectSolid(x, y, 0.4f, w, h, c);
}

static void circle(float x, float y, float r, u32 c)
{
    C2D_DrawCircleSolid(x, y, 0.45f, r, c);
}

static void vu_meter(float cx, float cy, float r, float level, const char *tag)
{
    int i;
    float t = level;
    if (t < 0.0f) t = 0.0f;
    if (t > 1.0f) t = 1.0f;

    circle(cx, cy, r + 3.0f, COL_METAL_DK);
    circle(cx, cy, r, COL_VU_FACE);

    for (i = 0; i <= 10; i++) {
        float a = (210.0f + (120.0f * (float)i / 10.0f)) * (float)M_PI / 180.0f;
        float c = cosf(a), s = sinf(a);
        float x0 = cx + c * (r - 4.0f);
        float y0 = cy + s * (r - 4.0f);
        float x1 = cx + c * (r - 11.0f);
        float y1 = cy + s * (r - 11.0f);
        u32 col = (i >= 8) ? COL_VU_RED : COL_VU_SCALE;
        C2D_DrawLine(x0, y0, col, x1, y1, col, 1.0f, 0.55f);
    }

    {
        float a = (210.0f + 120.0f * t) * (float)M_PI / 180.0f;
        float nx = cx + cosf(a) * (r - 14.0f);
        float ny = cy + sinf(a) * (r - 14.0f);
        C2D_DrawLine(cx, cy, COL_NEEDLE, nx, ny, COL_AMBER, 1.6f, 0.62f);
        circle(cx, cy, 3.2f, COL_GOLD);
        circle(cx, cy, 1.4f, COL_METAL_DK);
    }

    ui_draw_text(tag, cx, cy + r - 18.0f, 0.38f, COL_GOLD, C2D_AlignCenter);
    ui_draw_text("VU", cx, cy + 6.0f, 0.32f, COL_TEXT_DIM, C2D_AlignCenter);
}

static void tube(float x, float y, float energy)
{
    float glow = 0.20f + energy * 0.80f;
    u32 glass = RGBA(30, 42, 38, 200);
    u32 fire = RGBA(255, (int)(90 + glow * 80), (int)(20 + glow * 40), (int)(80 + glow * 140));
    u32 core = RGBA(255, 210, 120, (int)(100 + glow * 155));

    circle(x, y - 10.0f, 11.0f + glow * 6.0f, RGBA(255, 120, 30, (int)(30 + glow * 50)));
    C2D_DrawEllipseSolid(x - 10.0f, y - 28.0f, 0.5f, 20.0f, 44.0f, glass);
    C2D_DrawEllipseSolid(x - 6.0f, y - 18.0f, 0.52f, 12.0f, 22.0f, fire);
    circle(x, y - 8.0f, 3.5f + glow * 2.0f, core);
    rect(x - 7.0f, y + 12.0f, 14.0f, 6.0f, COL_METAL_LT);
    rect(x - 5.0f, y + 18.0f, 10.0f, 4.0f, COL_GOLD_DK);
}

static void crt_scope(float x, float y, float w, float h, const VizState *viz)
{
    int i;
    rect(x - 2, y - 2, w + 4, h + 4, COL_METAL_DK);
    rect(x, y, w, h, COL_CRT_BG);
    C2D_DrawLine(x, y + h * 0.5f, RGBA(40, 80, 40, 80), x + w, y + h * 0.5f, RGBA(40, 80, 40, 80), 1.0f, 0.5f);
    for (i = 1; i < VIZ_WAVE_N; i++) {
        float x0 = x + (float)(i - 1) * w / (float)(VIZ_WAVE_N - 1);
        float x1 = x + (float)i * w / (float)(VIZ_WAVE_N - 1);
        float y0 = y + h * 0.5f - viz->wave[i - 1] * h * 0.42f;
        float y1 = y + h * 0.5f - viz->wave[i] * h * 0.42f;
        C2D_DrawLine(x0, y0, COL_CRT_PHOS, x1, y1, COL_AMBER_HI, 1.1f, 0.58f);
    }
}

void ui_draw_top(const Player *p)
{
    int i;
    const VizState *v = &p->viz;

    rect(0, 0, TOP_W, TOP_H, COL_WOOD_DK);
    rect(6, 4, TOP_W - 12, TOP_H - 8, COL_WOOD);
    rect(8, 6, TOP_W - 16, 3, COL_WOOD_LT);
    rect(14, 14, TOP_W - 28, TOP_H - 34, COL_METAL);
    rect(16, 16, TOP_W - 32, TOP_H - 38, COL_METAL_LT);
    rect(18, 18, TOP_W - 36, TOP_H - 42, COL_METAL);

    ui_draw_text("3DSong", 28, 22, 0.52f, COL_GOLD, 0);
    ui_draw_text("STEREO INTEGRATED AMPLIFIER", 108, 26, 0.32f, COL_TEXT_DIM, 0);
    ui_draw_text("TYPE " THREEDSONG_VERSION, 310, 22, 0.32f, COL_GOLD_DK, 0);

    /* power jewel */
    circle(372, 30, 6.0f, COL_METAL_DK);
    circle(372, 30, 4.2f, p->state == PLAYER_PLAYING ? COL_AMBER : RGBA(80, 40, 20, 255));
    circle(371, 28.5f, 1.4f, COL_AMBER_HI);

    vu_meter(78, 118, 52, v->vu_l, "L");
    vu_meter(322, 118, 52, v->vu_r, "R");

    for (i = 0; i < 4; i++) {
        tube(148.0f + (float)i * 26.0f, 92.0f, v->tube[i]);
    }
    ui_draw_text("EL34", 174, 118, 0.28f, COL_TEXT_DIM, C2D_AlignCenter);

    crt_scope(138, 132, 124, 46, v);

    rect(18, 196, TOP_W - 36, 22, COL_WOOD_DK);
    ui_draw_text(p->current_title[0] ? p->current_title : "NO SIGNAL", 24, 200, 0.42f, COL_TEXT, 0);
    ui_draw_text(format_name(p->current_format), 300, 202, 0.36f, COL_ACCENT, 0);
}

/* Bottom screen hit-test layout — keep in sync with preview/app.js */
#define HDR_H 18
#define PLAY_X 52
#define PLAY_Y 66
#define PLAY_W 50
#define PLAY_H 36
#define PREV_X 10
#define PREV_Y 70
#define PREV_W 36
#define PREV_H 30
#define NEXT_X 108
#define NEXT_Y 70
#define NEXT_W 36
#define NEXT_H 30
#define STOP_X 150
#define STOP_Y 70
#define STOP_W 28
#define STOP_H 30
#define VOL_X 186
#define VOL_Y 76
#define VOL_W 124
#define VOL_H 18
#define SEEK_X 10
#define SEEK_Y 50
#define SEEK_W 300
#define SEEK_H 12
#define EQ_Y 110
#define EQ_H 48
#define EQ_X0 18
#define EQ_SLOT 70
#define FLAT_X 246
#define FLAT_Y 118
#define FLAT_W 62
#define FLAT_H 32
#define LIST_Y 164
#define LIST_H 72
#define ROW_H 18

static int in_box(int px, int py, int x, int y, int w, int h)
{
    return px >= x && px < x + w && py >= y && py < y + h;
}

static void btn(float x, float y, float w, float h, int hot)
{
    rect(x, y, w, h, hot ? COL_BTN_HI : COL_BTN);
    rect(x, y, w, 1, COL_WOOD_LT);
    rect(x, y + h - 1, w, 1, COL_WOOD_DK);
}

static void draw_icon_prev(float x, float y)
{
    C2D_DrawTriangle(x + 6, y + 8, COL_TEXT, x + 16, y + 4, COL_TEXT, x + 16, y + 12, COL_TEXT, 0.6f);
    C2D_DrawTriangle(x + 16, y + 8, COL_TEXT, x + 26, y + 4, COL_TEXT, x + 26, y + 12, COL_TEXT, 0.6f);
}

static void draw_icon_next(float x, float y)
{
    C2D_DrawTriangle(x + 10, y + 4, COL_TEXT, x + 20, y + 8, COL_TEXT, x + 10, y + 12, COL_TEXT, 0.6f);
    C2D_DrawTriangle(x + 20, y + 4, COL_TEXT, x + 30, y + 8, COL_TEXT, x + 20, y + 12, COL_TEXT, 0.6f);
}

static void draw_icon_play(float x, float y, int playing)
{
    if (playing) {
        rect(x + 16, y + 8, 6, 20, COL_TEXT);
        rect(x + 28, y + 8, 6, 20, COL_TEXT);
    } else {
        C2D_DrawTriangle(x + 16, y + 8, COL_AMBER, x + 36, y + 18, COL_AMBER_HI, x + 16, y + 28, COL_AMBER, 0.6f);
    }
}

static void draw_icon_stop(float x, float y)
{
    rect(x + 8, y + 8, 12, 12, COL_TEXT);
}

void ui_draw_bottom(const Player *p, const Library *lib)
{
    int i, vis;
    char line[128];
    float prog = player_progress(p);
    int volw;

    rect(0, 0, BOT_W, BOT_H, COL_BOT_BG);
    rect(0, 0, BOT_W, HDR_H, COL_WOOD_DK);
    ui_draw_text(THREEDSONG_TITLE, 6, 2, 0.42f, COL_GOLD, 0);
    ui_draw_text(p->shuffle ? "SHUF" : "SEQ", 210, 3, 0.32f, p->shuffle ? COL_ACCENT : COL_TEXT_DIM, 0);
    ui_draw_text(p->repeat == REPEAT_ONE ? "R1" : (p->repeat == REPEAT_ALL ? "R*" : "R-"), 258, 3, 0.32f,
                 p->repeat ? COL_ACCENT : COL_TEXT_DIM, 0);
    ui_draw_text("O3DS", 286, 3, 0.32f, COL_TEXT_DIM, 0);

    ui_draw_text(p->current_title[0] ? p->current_title : "Выберите трек в списке", 8, 20, 0.40f, COL_TEXT, 0);
    if (p->error) {
        ui_draw_text(p->error_msg, 8, 34, 0.32f, COL_VU_RED, 0);
    } else {
        snprintf(line, sizeof(line), "%s  %d%%  %s",
                 format_name(p->current_format),
                 p->volume_pct,
                 p->state == PLAYER_PLAYING ? "PLAY" : (p->state == PLAYER_PAUSED ? "PAUSE" : "STOP"));
        ui_draw_text(line, 8, 34, 0.32f, COL_TEXT_DIM, 0);
    }

    rect(SEEK_X, SEEK_Y + 3, SEEK_W, 6, COL_METAL_DK);
    rect(SEEK_X, SEEK_Y + 3, SEEK_W * prog, 6, COL_ACCENT);
    circle(SEEK_X + SEEK_W * prog, SEEK_Y + 6, 4.0f, COL_GOLD);

    {
        int cur_s = 0, tot_s = 0;
        if (p->decoder.open && p->decoder.sample_rate > 0) {
            cur_s = (int)(p->decoder.position_frames / (uint64_t)p->decoder.sample_rate);
            tot_s = (int)(p->decoder.total_frames / (uint64_t)p->decoder.sample_rate);
        }
        snprintf(line, sizeof(line), "%d:%02d / %d:%02d", cur_s / 60, cur_s % 60, tot_s / 60, tot_s % 60);
        ui_draw_text(line, 230, 34, 0.32f, COL_TEXT_DIM, 0);
    }

    btn(PREV_X, PREV_Y, PREV_W, PREV_H, 0);
    draw_icon_prev(PREV_X, PREV_Y + 7);
    btn(PLAY_X, PLAY_Y, PLAY_W, PLAY_H, p->state == PLAYER_PLAYING);
    draw_icon_play(PLAY_X, PLAY_Y, p->state == PLAYER_PLAYING);
    btn(NEXT_X, NEXT_Y, NEXT_W, NEXT_H, 0);
    draw_icon_next(NEXT_X, NEXT_Y + 7);
    btn(STOP_X, STOP_Y, STOP_W, STOP_H, 0);
    draw_icon_stop(STOP_X, STOP_Y + 1);

    ui_draw_text("VOL", VOL_X, VOL_Y - 10, 0.28f, COL_TEXT_DIM, 0);
    rect(VOL_X, VOL_Y + 4, VOL_W, 8, COL_METAL_DK);
    volw = VOL_W * p->volume_pct / 100;
    rect(VOL_X, VOL_Y + 4, (float)volw, 8, COL_GOLD);
    circle(VOL_X + volw, VOL_Y + 8, 5.0f, COL_AMBER);

    /* EQ sliders */
    for (i = 0; i < EQ_BANDS; i++) {
        float x = (float)(EQ_X0 + i * EQ_SLOT);
        float g = (p->eq.gain_db[i] - EQ_GAIN_MIN_DB) / (EQ_GAIN_MAX_DB - EQ_GAIN_MIN_DB);
        float fill = EQ_H - 14.0f;
        float h = g * fill;
        rect(x + 14, (float)EQ_Y, 10, fill, COL_METAL_DK);
        rect(x + 14, (float)EQ_Y + (fill - h), 10, h, COL_ACCENT);
        rect(x + 11, (float)EQ_Y + (fill - h) - 3, 16, 8, COL_GOLD);
        ui_draw_text(eq_band_name(i), x, (float)EQ_Y + fill + 2, 0.28f, COL_TEXT_DIM, 0);
    }
    btn(FLAT_X, FLAT_Y, FLAT_W, FLAT_H, 0);
    ui_draw_text("FLAT", FLAT_X + 12, FLAT_Y + 8, 0.38f, COL_TEXT, 0);

    rect(0, LIST_Y - 2, BOT_W, 1, COL_WOOD_LT);
    vis = LIST_H / ROW_H;
    for (i = 0; i < vis; i++) {
        int idx = lib->scroll + i;
        float y = (float)(LIST_Y + i * ROW_H);
        if (idx >= lib->count) break;
        if (idx == lib->cursor) rect(0, y, BOT_W, ROW_H, COL_LIST_SEL);
        if (idx == p->track_index) {
            circle(8, y + ROW_H * 0.5f, 3.0f, COL_GREEN_LED);
        }
        snprintf(line, sizeof(line), "%s", lib->tracks[idx].name);
        ui_draw_text(line, 16, y + 2, 0.36f, COL_TEXT, 0);
        ui_draw_text(format_name(lib->tracks[idx].format), 268, y + 3, 0.30f, COL_TEXT_DIM, 0);
    }
    if (lib->count == 0) {
        ui_draw_text("Нет файлов. Положите музыку в sdmc:/Music", 8, LIST_Y + 20, 0.34f, COL_TEXT_DIM, 0);
    }
}

void ui_handle_touch(Player *p, Library *lib, int px, int py, int pressed, int held)
{
    int i;
    if (!held && !pressed) return;

    if (held && in_box(px, py, SEEK_X, SEEK_Y - 4, SEEK_W, SEEK_H + 8)) {
        float f = (float)(px - SEEK_X) / (float)SEEK_W;
        player_seek_frac(p, f);
        return;
    }
    if (held && in_box(px, py, VOL_X, VOL_Y - 4, VOL_W, VOL_H + 10)) {
        int pct = (px - VOL_X) * 100 / VOL_W;
        player_set_volume(p, pct);
        return;
    }
    if (held && py >= EQ_Y && py < EQ_Y + EQ_H - 10) {
        for (i = 0; i < EQ_BANDS; i++) {
            int x = EQ_X0 + i * EQ_SLOT;
            if (px >= x && px < x + 42) {
                float fill = (float)(EQ_H - 14);
                float g = 1.0f - ((float)(py - EQ_Y) / fill);
                if (g < 0) g = 0;
                if (g > 1) g = 1;
                eq_set_gain(&p->eq, i, EQ_GAIN_MIN_DB + g * (EQ_GAIN_MAX_DB - EQ_GAIN_MIN_DB));
                return;
            }
        }
    }

    if (!pressed) return;

    if (in_box(px, py, PLAY_X, PLAY_Y, PLAY_W, PLAY_H)) {
        if (!p->decoder.open && lib->count > 0) {
            int idx = lib->cursor >= 0 ? lib->cursor : 0;
            if (player_open_index(p, lib, idx) == 0) player_play(p);
        } else {
            player_toggle(p);
        }
        return;
    }
    if (in_box(px, py, PREV_X, PREV_Y, PREV_W, PREV_H)) {
        player_prev(p, lib);
        return;
    }
    if (in_box(px, py, NEXT_X, NEXT_Y, NEXT_W, NEXT_H)) {
        player_next(p, lib);
        return;
    }
    if (in_box(px, py, STOP_X, STOP_Y, STOP_W, STOP_H)) {
        player_stop(p);
        return;
    }
    if (in_box(px, py, FLAT_X, FLAT_Y, FLAT_W, FLAT_H)) {
        eq_flat(&p->eq);
        return;
    }
    if (px >= 200 && py < HDR_H) {
        if (px < 250) p->shuffle = !p->shuffle;
        else p->repeat = (RepeatMode)(((int)p->repeat + 1) % 3);
        return;
    }
    if (py >= LIST_Y) {
        int row = (py - LIST_Y) / ROW_H;
        int idx = lib->scroll + row;
        if (idx >= 0 && idx < lib->count) {
            lib->cursor = idx;
            if (player_open_index(p, lib, idx) == 0) player_play(p);
        }
    }
}

#else
void ui_init(void) {}
void ui_fini(void) {}
void ui_draw_top(const Player *p) { (void)p; }
void ui_draw_bottom(const Player *p, const Library *lib) { (void)p; (void)lib; }
void ui_handle_touch(Player *p, Library *lib, int px, int py, int pressed, int held)
{
    (void)p; (void)lib; (void)px; (void)py; (void)pressed; (void)held;
}
#endif
