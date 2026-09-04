#include "player.h"
#include "library.h"
#include "playlists.h"
#include "ui.h"
#include "ui_layout.h"
#include "version.h"
#include "layout.h"

#ifdef __3DS__

#include <3ds.h>
#include <citro2d.h>
#include <citro3d.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define BUF_FRAMES 4096
#define NUM_BUFS   3

static Player g_player;
static Library g_lib;
static Library g_all;
static Library g_pl_preview;
static PlaylistSet g_playlists;
static LightLock g_lock;
static ndspWaveBuf g_wb[NUM_BUFS];
static int16_t *g_abuf;
static volatile int g_run;
static Thread g_th;
static int g_ndsp_ok;
static int g_configured_rate = 44100;

static Library *nav_lib(void)
{
    if (g_player.play_order == ORDER_SHUFFLE_ALL && g_all.count > 0)
        return &g_all;
    return &g_lib;
}

static void refresh_all_lib(void)
{
    library_scan(&g_all);
}

static void ndsp_reconfig(int rate)
{
    int i;
    float mix[12];
    if (rate < 8000) rate = 44100;
    ndspChnReset(0);
    ndspChnSetInterp(0, NDSP_INTERP_LINEAR);
    ndspChnSetRate(0, (float)rate);
    ndspChnSetFormat(0, NDSP_FORMAT_STEREO_PCM16);
    memset(mix, 0, sizeof(mix));
    mix[0] = 1.0f;
    mix[1] = 1.0f;
    ndspChnSetMix(0, mix);
    memset(g_wb, 0, sizeof(g_wb));
    for (i = 0; i < NUM_BUFS; i++) {
        g_wb[i].data_vaddr = g_abuf + i * BUF_FRAMES * 2;
        g_wb[i].nsamples = BUF_FRAMES;
        g_wb[i].status = NDSP_WBUF_DONE;
    }
    g_configured_rate = rate;
}

static void audio_thread(void *arg)
{
    (void)arg;
    while (g_run) {
        int i;
        if (!g_ndsp_ok) {
            svcSleepThread(10000000);
            continue;
        }
        LightLock_Lock(&g_lock);
        if (g_player.decoder.open && g_player.decoder.sample_rate != g_configured_rate) {
            ndsp_reconfig(g_player.decoder.sample_rate);
        }
        if (g_player.ended) {
            if (g_player.repeat == REPEAT_ONE && g_player.track_index >= 0) {
                decoder_seek_frame(&g_player.decoder, 0);
                g_player.ended = 0;
                g_player.state = PLAYER_PLAYING;
            } else if (g_player.repeat == REPEAT_ALL) {
                player_next(&g_player, nav_lib());
            } else {
                g_player.ended = 0;
            }
        }
        LightLock_Unlock(&g_lock);

        for (i = 0; i < NUM_BUFS; i++) {
            if (g_wb[i].status == NDSP_WBUF_DONE || g_wb[i].status == NDSP_WBUF_FREE) {
                LightLock_Lock(&g_lock);
                player_fill_s16_stereo(&g_player, g_wb[i].data_pcm16, BUF_FRAMES);
                LightLock_Unlock(&g_lock);
                DSP_FlushDataCache(g_wb[i].data_pcm16, BUF_FRAMES * 4);
                ndspChnWaveBufAdd(0, &g_wb[i]);
            }
        }
        svcSleepThread(2000000);
    }
}

static void ensure_cursor_visible(Library *lib)
{
    if (lib->cursor < lib->scroll) lib->scroll = lib->cursor;
    if (lib->cursor >= lib->scroll + LIST_ROWS)
        lib->scroll = lib->cursor - LIST_ROWS + 1;
}

static void handle_keys(u32 down, u32 held)
{
    (void)held;

    LightLock_Lock(&g_lock);

    if (down & KEY_Y) {
        if (ui_playlists_open()) ui_toggle_pl_focus();
        else ui_open_playlists(&g_playlists, &g_pl_preview);
    }

    if (down & KEY_X) {
        player_stop(&g_player);
    }

    if (down & KEY_B) {
        if (!ui_handle_back()) {
            if (g_player.state == PLAYER_PLAYING) player_pause(&g_player);
        }
    }

    if (down & KEY_A) {
        if (ui_eq_screen_open()) {
            /* ignore */
        } else if (ui_playlists_open()) {
            if (!ui_pl_focus_bottom()) {
                playlists_load(&g_playlists, &g_lib, g_playlists.cursor);
                ui_sync_pl_preview(&g_playlists, &g_pl_preview);
            } else {
                int i;
                const Track *t = library_at(&g_pl_preview, g_pl_preview.cursor);
                playlists_load(&g_playlists, &g_lib, g_playlists.cursor);
                if (t && t->kind == ENTRY_FILE) {
                    for (i = 0; i < g_lib.count; i++) {
                        if (g_lib.tracks[i].kind == ENTRY_FILE &&
                            strcmp(g_lib.tracks[i].path, t->path) == 0) {
                            player_activate(&g_player, &g_lib, i);
                            ui_handle_back();
                            break;
                        }
                    }
                }
            }
        } else {
            if (library_is_file(&g_lib, g_lib.cursor) &&
                g_player.decoder.open &&
                g_player.track_index == g_lib.cursor) {
                player_toggle(&g_player);
            } else {
                player_activate(&g_player, &g_lib, g_lib.cursor);
            }
        }
    }

    if (ui_playlists_open()) {
        if (!ui_pl_focus_bottom()) {
            if (down & KEY_DUP) {
                if (g_playlists.cursor > 0) g_playlists.cursor--;
                ui_sync_pl_preview(&g_playlists, &g_pl_preview);
            }
            if (down & KEY_DDOWN) {
                if (g_playlists.cursor + 1 < g_playlists.count) g_playlists.cursor++;
                ui_sync_pl_preview(&g_playlists, &g_pl_preview);
            }
        } else {
            if (down & KEY_DUP) {
                int c = g_pl_preview.cursor;
                while (c > 0) {
                    c--;
                    if (library_is_file(&g_pl_preview, c)) {
                        g_pl_preview.cursor = c;
                        if (c < g_pl_preview.scroll) g_pl_preview.scroll = c;
                        break;
                    }
                }
            }
            if (down & KEY_DDOWN) {
                int c = g_pl_preview.cursor;
                while (c + 1 < g_pl_preview.count) {
                    c++;
                    if (library_is_file(&g_pl_preview, c)) {
                        g_pl_preview.cursor = c;
                        if (c >= g_pl_preview.scroll + 9)
                            g_pl_preview.scroll = c - 8;
                        break;
                    }
                }
            }
        }
        LightLock_Unlock(&g_lock);
        return;
    }

    if (ui_eq_screen_open()) {
        LightLock_Unlock(&g_lock);
        return;
    }

    /* Main player */
    if (down & KEY_L) player_prev(&g_player, nav_lib());
    if (down & KEY_R) player_next(&g_player, nav_lib());

    if (down & KEY_DUP) {
        if (g_lib.cursor > 0) g_lib.cursor--;
        ensure_cursor_visible(&g_lib);
    }
    if (down & KEY_DDOWN) {
        if (g_lib.cursor + 1 < g_lib.count) g_lib.cursor++;
        ensure_cursor_visible(&g_lib);
    }
    if (down & KEY_DLEFT) {
        playlists_switch(&g_playlists, &g_lib, -1);
    }
    if (down & KEY_DRIGHT) {
        playlists_switch(&g_playlists, &g_lib, 1);
    }

    LightLock_Unlock(&g_lock);
}

int main(int argc, char **argv)
{
    C3D_RenderTarget *top;
    C3D_RenderTarget *bot;
    touchPosition touch;
    u32 kDown, kHeld, kUp;
    int touch_was = 0;
    (void)argc;
    (void)argv;

    romfsInit();
    gfxInitDefault();
    gfxSet3D(false);
    C3D_Init(C3D_DEFAULT_CMDBUF_SIZE);
    C2D_Init(C2D_DEFAULT_MAX_OBJECTS);
    C2D_Prepare();
    top = C2D_CreateScreenTarget(GFX_TOP, GFX_LEFT);
    bot = C2D_CreateScreenTarget(GFX_BOTTOM, GFX_LEFT);

    LightLock_Init(&g_lock);
    player_init(&g_player);
    library_init(&g_lib);
    library_init(&g_all);
    library_init(&g_pl_preview);
    playlists_init(&g_playlists);
    playlists_refresh(&g_playlists);
    if (g_playlists.count > 0)
        playlists_load(&g_playlists, &g_lib, 0);
    else
        library_boot(&g_lib);
    refresh_all_lib();
    ui_init();

    g_ndsp_ok = R_SUCCEEDED(ndspInit());
    if (!g_ndsp_ok) {
        g_player.error = 1;
        snprintf(g_player.error_msg, sizeof(g_player.error_msg),
                 "Нет DSP firmware (dspfirm.cdc)");
    }
    if (g_ndsp_ok) {
        ndspSetOutputMode(NDSP_OUTPUT_STEREO);
        g_abuf = (int16_t *)linearAlloc(BUF_FRAMES * NUM_BUFS * 2 * sizeof(int16_t));
        if (g_abuf) {
            memset(g_abuf, 0, BUF_FRAMES * NUM_BUFS * 2 * sizeof(int16_t));
            ndsp_reconfig(44100);
        } else {
            g_ndsp_ok = 0;
        }
    }

    g_run = 1;
    g_th = threadCreate(audio_thread, NULL, 64 * 1024, 0x18, -1, false);

    while (aptMainLoop()) {
        hidScanInput();
        kDown = hidKeysDown();
        kHeld = hidKeysHeld();
        kUp = hidKeysUp();
        if (kDown & KEY_START) break;

        handle_keys(kDown, kHeld);

        /* Touch: ORD cycle refreshes all-lib when entering S* */
        hidTouchRead(&touch);
        if (kHeld & KEY_TOUCH) {
            PlayOrder before = g_player.play_order;
            LightLock_Lock(&g_lock);
            ui_handle_touch(&g_player, &g_lib, &g_playlists, &g_pl_preview,
                            touch.px, touch.py, (kDown & KEY_TOUCH) ? 1 : 0, 1);
            if (g_player.play_order == ORDER_SHUFFLE_ALL && before != ORDER_SHUFFLE_ALL)
                refresh_all_lib();
            LightLock_Unlock(&g_lock);
            touch_was = 1;
        } else if (touch_was && (kUp & KEY_TOUCH)) {
            touch_was = 0;
        }

        C3D_FrameBegin(C3D_FRAME_SYNCDRAW);
        LightLock_Lock(&g_lock);
        C2D_TargetClear(top, C2D_Color32(20, 10, 6, 255));
        C2D_SceneBegin(top);
        ui_draw_top(&g_player, &g_playlists);
        C2D_TargetClear(bot, C2D_Color32(20, 10, 6, 255));
        C2D_SceneBegin(bot);
        ui_draw_bottom(&g_player, &g_lib, &g_playlists, &g_pl_preview);
        LightLock_Unlock(&g_lock);
        C3D_FrameEnd(0);
    }

    g_run = 0;
    if (g_th) {
        threadJoin(g_th, U64_MAX);
        threadFree(g_th);
    }
    player_shutdown(&g_player);
    ui_fini();
    if (g_ndsp_ok) {
        ndspChnWaveBufClear(0);
        ndspExit();
    }
    if (g_abuf) linearFree(g_abuf);
    C2D_Fini();
    C3D_Fini();
    gfxExit();
    romfsExit();
    return 0;
}

#endif
