#include "ui.h"
#include "colors.h"
#include "layout.h"
#include "ui_layout.h"
#include "version.h"

#ifdef __3DS__

#include <math.h>
#include <stdio.h>
#include <string.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static C2D_TextBuf g_textbuf;
static int g_eq_screen;

void ui_init(void)
{
    g_textbuf = C2D_TextBufNew(8192);
    g_eq_screen = 0;
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

static u32 warm_led_color(float t, float bright)
{
    static const struct { float pos; int r, g, b; } stops[] = {
        { 0.00f, 42, 24, 10 },
        { 0.22f, 88, 48, 16 },
        { 0.45f, 168, 108, 32 },
        { 0.62f, 228, 176, 64 },
        { 0.78f, 255, 148, 40 },
        { 1.00f, 168, 44, 18 },
    };
    int i = 0;
    float f;
    int r, g, b;
    if (t < 0.0f) t = 0.0f;
    if (t > 1.0f) t = 1.0f;
    if (bright < 0.0f) bright = 0.0f;
    if (bright > 1.0f) bright = 1.0f;
    while (i < 4 && t > stops[i + 1].pos) i++;
    f = (t - stops[i].pos) / (stops[i + 1].pos - stops[i].pos);
    r = (int)((stops[i].r + (stops[i + 1].r - stops[i].r) * f) * bright);
    g = (int)((stops[i].g + (stops[i + 1].g - stops[i].g) * f) * bright);
    b = (int)((stops[i].b + (stops[i + 1].b - stops[i].b) * f) * bright);
    if (r > 255) r = 255;
    if (g > 255) g = 255;
    if (b > 255) b = 255;
    return RGBA(r, g, b, 255);
}

static float viz_col_energy(const VizState *v, int col, int cols)
{
    float f = (float)col / (float)(cols - 1) * (float)(VIZ_BINS - 1);
    int i = (int)f;
    float frac = f - (float)i;
    float a = v->bins[i];
    float b = (i + 1 < VIZ_BINS) ? v->bins[i + 1] : v->bins[i];
    return a * (1.0f - frac) + b * frac;
}

void ui_draw_top(const Player *p)
{
    const VizState *v = &p->viz;
    const int led_cols = 64;
    const int led_rows = 48;
    const float cell_w = (float)TOP_W / (float)led_cols;
    const float cell_h = (float)TOP_H / (float)led_rows;
    const float mid = (float)led_rows * 0.5f;
    static u32 tick;
    int col, r, half;
    float t, energy, bright;
    int playing = (p->state == PLAYER_PLAYING);

    tick++;

    rect(0, 0, TOP_W, TOP_H, RGBA(6, 4, 2, 255));

    for (col = 0; col <= led_cols; col++) {
        float x = (float)col * cell_w;
        C2D_DrawLine(x, 0, RGBA(20, 12, 6, 80), x, (float)TOP_H, RGBA(20, 12, 6, 80), 1.0f, 0.35f);
    }
    for (r = 0; r <= led_rows; r++) {
        float y = (float)r * cell_h;
        C2D_DrawLine(0, y, RGBA(20, 12, 6, 80), (float)TOP_W, y, RGBA(20, 12, 6, 80), 1.0f, 0.35f);
    }

    for (col = 0; col < led_cols; col++) {
        t = (float)col / (float)(led_cols - 1);
        energy = viz_col_energy(v, col, led_cols);
        if (!playing) {
            energy *= 0.35f;
            energy += 0.06f * sinf((float)col * 0.35f + (float)tick * 0.04f);
        }
        energy = energy * 1.65f + 0.04f;
        if (energy > 1.0f) energy = 1.0f;
        if (energy < 0.0f) energy = 0.0f;
        half = (int)(energy * (mid - 2.0f));
        for (r = 0; r < half; r++) {
            float x = (float)col * cell_w + 0.5f;
            bright = 0.55f + ((float)r / (float)(half > 0 ? half : 1)) * 0.45f;
            rect(x, (mid - 1.0f - (float)r) * cell_h + 0.5f, cell_w - 1.0f, cell_h - 1.0f, warm_led_color(t, bright));
            rect(x, (mid + (float)r) * cell_h + 0.5f, cell_w - 1.0f, cell_h - 1.0f, warm_led_color(t, bright));
        }
    }

    if (playing) {
        float pulse = 0.55f + v->vu_l * 0.45f;
        float cx = TOP_W * 0.5f;
        float cy = TOP_H * 0.5f;
        int i;
        for (i = 0; i < 24; i++) {
            float ang = (float)i * (2.0f * (float)M_PI / 24.0f);
            float wobble = sinf((float)tick * 0.05f + (float)i * 0.7f) * 0.08f;
            float rad = (TOP_W * 0.38f) * (0.18f + (float)(i % 5) * 0.03f + wobble) * pulse;
            float sx = cx + cosf(ang) * rad;
            float sy = cy + sinf(ang) * rad * 0.55f;
            float sr = 2.0f + pulse * 3.0f;
            circle(sx, sy, sr + 2.0f, RGBA(255, 150, 40, (int)(40 + pulse * 50)));
            circle(sx, sy, sr, RGBA(255, 220, 140, (int)(120 + pulse * 100)));
        }
    }
}

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

static void draw_icon_stop(float x, float y, float w, float h)
{
    float s = 12.0f;
    rect(x + (w - s) * 0.5f, y + (h - s) * 0.5f, s, s, COL_TEXT);
}

static void trunc_left(const char *path, char *out, size_t n, int max_chars)
{
    size_t len;
    if (!path || !out || n == 0) return;
    len = strlen(path);
    if (len <= (size_t)max_chars || max_chars <= 3) {
        strncpy(out, path, n - 1);
        out[n - 1] = 0;
        return;
    }
    snprintf(out, n, "...%s", path + (len - (size_t)(max_chars - 3)));
}

static void play_or_activate(Player *p, Library *lib)
{
    if (library_is_file(lib, lib->cursor) &&
        p->decoder.open &&
        p->track_index == lib->cursor) {
        player_toggle(p);
        return;
    }
    player_activate(p, lib, lib->cursor);
}

int ui_eq_screen_open(void)
{
    return g_eq_screen;
}

int ui_handle_back(void)
{
    if (g_eq_screen) {
        g_eq_screen = 0;
        return 1;
    }
    return 0;
}

static void draw_eq_screen(const Player *p)
{
    int i;
    char line[32];

    rect(0, 0, BOT_W, BOT_H, COL_BOT_BG);
    rect(0, 0, BOT_W, 38, COL_WOOD_DK);
    btn(EQ_BACK_X, EQ_BACK_Y, EQ_BACK_W, EQ_BACK_H, 0);
    ui_draw_text("BACK", EQ_BACK_X + EQ_BACK_W * 0.5f, EQ_BACK_Y + 6, 0.38f, COL_TEXT, C2D_AlignCenter);
    ui_draw_text("EQUALIZER", 160, 10, 0.42f, COL_GOLD, C2D_AlignCenter);

    for (i = 0; i < EQ_BANDS; i++) {
        float cx = (float)(EQ_COL0 + i * EQ_COL_GAP);
        float g = (p->eq.gain_db[i] - EQ_GAIN_MIN_DB) / (EQ_GAIN_MAX_DB - EQ_GAIN_MIN_DB);
        float fill = (float)EQ_SLIDER_H;
        float h = g * fill;
        float tx = cx - (float)EQ_SLIDER_W * 0.5f;
        float midy = (float)EQ_SLIDER_Y + fill * 0.5f;

        ui_draw_text(eq_band_name(i), cx, (float)EQ_SLIDER_Y - 14.0f, 0.32f, COL_TEXT_DIM, C2D_AlignCenter);
        rect(tx, (float)EQ_SLIDER_Y, (float)EQ_SLIDER_W, fill, COL_METAL_DK);
        rect(tx, (float)EQ_SLIDER_Y + (fill - h), (float)EQ_SLIDER_W, h, COL_ACCENT);
        C2D_DrawLine(tx - 5.0f, midy, COL_TEXT_DIM, tx + (float)EQ_SLIDER_W + 5.0f, midy, COL_TEXT_DIM, 1.0f, 0.5f);
        rect(tx - 4.0f, (float)EQ_SLIDER_Y + (fill - h) - 4.0f, (float)EQ_SLIDER_W + 8.0f, 10.0f, COL_GOLD);
        snprintf(line, sizeof(line), "%+.0f dB", p->eq.gain_db[i]);
        ui_draw_text(line, cx, (float)EQ_SLIDER_Y + fill + 6.0f, 0.32f, COL_GOLD, C2D_AlignCenter);
    }
}

static void draw_player_screen(const Player *p, const Library *lib)
{
    int i, vis;
    char line[160];
    float prog = player_progress(p);

    rect(0, 0, BOT_W, BOT_H, COL_BOT_BG);

    ui_draw_text(p->current_title[0] ? p->current_title : "Выберите трек в папке",
                 8, 3, 0.40f, COL_TEXT, 0);
    if (p->error) {
        ui_draw_text(p->error_msg, 8, 16, 0.30f, COL_VU_RED, 0);
    } else {
        snprintf(line, sizeof(line), "%s  %s",
                 format_name(p->current_format),
                 p->state == PLAYER_PLAYING ? "PLAY" : (p->state == PLAYER_PAUSED ? "PAUSE" : "STOP"));
        ui_draw_text(line, 8, 16, 0.30f, COL_TEXT_DIM, 0);
    }
    {
        int cur_s = 0, tot_s = 0;
        if (p->decoder.open && p->decoder.sample_rate > 0) {
            cur_s = (int)(p->decoder.position_frames / (uint64_t)p->decoder.sample_rate);
            tot_s = (int)(p->decoder.total_frames / (uint64_t)p->decoder.sample_rate);
        }
        snprintf(line, sizeof(line), "%d:%02d / %d:%02d", cur_s / 60, cur_s % 60, tot_s / 60, tot_s % 60);
        ui_draw_text(line, 230, 16, 0.30f, COL_TEXT_DIM, 0);
    }

    rect(SEEK_X, SEEK_Y + 3, SEEK_W, 6, COL_METAL_DK);
    rect(SEEK_X, SEEK_Y + 3, SEEK_W * prog, 6, COL_ACCENT);
    circle(SEEK_X + SEEK_W * prog, SEEK_Y + 6, 4.0f, COL_GOLD);

    btn(PREV_X, PREV_Y, PREV_W, PREV_H, 0);
    draw_icon_prev(PREV_X, PREV_Y + 7);
    btn(PLAY_X, PLAY_Y, PLAY_W, PLAY_H, p->state == PLAYER_PLAYING);
    draw_icon_play(PLAY_X, PLAY_Y, p->state == PLAYER_PLAYING);
    btn(NEXT_X, NEXT_Y, NEXT_W, NEXT_H, 0);
    draw_icon_next(NEXT_X, NEXT_Y + 7);
    btn(STOP_X, STOP_Y, STOP_W, STOP_H, 0);
    draw_icon_stop(STOP_X, STOP_Y, STOP_W, STOP_H);
    btn(EQBTN_X, EQBTN_Y, EQBTN_W, EQBTN_H, 0);
    ui_draw_text("EQ", EQBTN_X + EQBTN_W * 0.5f, EQBTN_Y + 8, 0.40f, COL_TEXT, C2D_AlignCenter);
    btn(FLAT_X, FLAT_Y, FLAT_W, FLAT_H, 0);
    ui_draw_text("FLAT", FLAT_X + FLAT_W * 0.5f, FLAT_Y + 8, 0.38f, COL_TEXT, C2D_AlignCenter);

    rect(0, FOLDER_Y, BOT_W, FOLDER_H, COL_WOOD_DK);
    trunc_left(lib->cwd[0] ? lib->cwd : "sdmc:/", line, sizeof(line), 44);
    ui_draw_text(line, 6, FOLDER_Y + 2, 0.32f, COL_GOLD, 0);

    vis = LIST_ROWS;
    for (i = 0; i < vis; i++) {
        int idx = lib->scroll + i;
        float y = (float)(LIST_Y + i * ROW_H);
        const Track *t;
        if (idx >= lib->count) break;
        t = &lib->tracks[idx];
        if (idx == lib->cursor) rect(0, y, BOT_W, ROW_H, COL_LIST_SEL);
        if (t->kind == ENTRY_FILE && p->current_path[0] && strcmp(p->current_path, t->path) == 0) {
            circle(8, y + ROW_H * 0.5f, 3.0f, COL_GREEN_LED);
        } else if (t->kind != ENTRY_FILE) {
            rect(4, y + 5, 8, 6, COL_GOLD_DK);
            rect(6, y + 4, 5, 2, COL_GOLD);
        }
        if (t->kind == ENTRY_PARENT) {
            ui_draw_text("..", 16, y + 2, 0.34f, COL_TEXT_DIM, 0);
        } else if (t->kind == ENTRY_DIR) {
            snprintf(line, sizeof(line), "%.26s/", t->name);
            ui_draw_text(line, 16, y + 2, 0.34f, COL_ACCENT, 0);
        } else {
            snprintf(line, sizeof(line), "%.28s", t->name);
            ui_draw_text(line, 16, y + 2, 0.34f, COL_TEXT, 0);
            ui_draw_text(format_name(t->format), 268, y + 2, 0.28f, COL_TEXT_DIM, 0);
        }
    }
    if (lib->count == 0) {
        ui_draw_text("Нет файлов. Положите музыку в sdmc:/Music", 8, LIST_Y + 20, 0.32f, COL_TEXT_DIM, 0);
    } else {
        int any_file = 0;
        for (i = 0; i < lib->count; i++) {
            if (lib->tracks[i].kind == ENTRY_FILE) { any_file = 1; break; }
        }
        if (!any_file && lib->count <= vis) {
            ui_draw_text("Нет песен в этой папке", 8, LIST_Y + lib->count * ROW_H + 6, 0.32f, COL_TEXT_DIM, 0);
        }
    }
}

void ui_draw_bottom(const Player *p, const Library *lib)
{
    if (g_eq_screen) draw_eq_screen(p);
    else draw_player_screen(p, lib);
}

void ui_handle_touch(Player *p, Library *lib, int px, int py, int pressed, int held)
{
    int i;
    if (!held && !pressed) return;

    if (g_eq_screen) {
        if (held) {
            for (i = 0; i < EQ_BANDS; i++) {
                int cx = EQ_COL0 + i * EQ_COL_GAP;
                if (px >= cx - 28 && px < cx + 28 &&
                    py >= EQ_SLIDER_Y - 4 && py < EQ_SLIDER_Y + EQ_SLIDER_H + 4) {
                    float g = 1.0f - ((float)(py - EQ_SLIDER_Y) / (float)EQ_SLIDER_H);
                    if (g < 0) g = 0;
                    if (g > 1) g = 1;
                    eq_set_gain(&p->eq, i, EQ_GAIN_MIN_DB + g * (EQ_GAIN_MAX_DB - EQ_GAIN_MIN_DB));
                    return;
                }
            }
        }
        if (pressed && in_box(px, py, EQ_BACK_X, EQ_BACK_Y, EQ_BACK_W, EQ_BACK_H)) {
            g_eq_screen = 0;
        }
        return;
    }

    if (held && in_box(px, py, SEEK_X, SEEK_Y - 4, SEEK_W, SEEK_H + 8)) {
        float f = (float)(px - SEEK_X) / (float)SEEK_W;
        player_seek_frac(p, f);
        return;
    }

    if (!pressed) return;

    if (in_box(px, py, PLAY_X, PLAY_Y, PLAY_W, PLAY_H)) {
        play_or_activate(p, lib);
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
    if (in_box(px, py, EQBTN_X, EQBTN_Y, EQBTN_W, EQBTN_H)) {
        g_eq_screen = 1;
        return;
    }
    if (in_box(px, py, FLAT_X, FLAT_Y, FLAT_W, FLAT_H)) {
        eq_flat(&p->eq);
        return;
    }
    if (py >= LIST_Y && py < LIST_Y + LIST_H) {
        int row = (py - LIST_Y) / ROW_H;
        int idx = lib->scroll + row;
        if (idx >= 0 && idx < lib->count) {
            lib->cursor = idx;
            player_activate(p, lib, idx);
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
int ui_handle_back(void) { return 0; }
int ui_eq_screen_open(void) { return 0; }
#endif
