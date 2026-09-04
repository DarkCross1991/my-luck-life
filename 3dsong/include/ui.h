#ifndef THREEDSONG_UI_H
#define THREEDSONG_UI_H

#include "player.h"
#include "library.h"
#include "playlists.h"

#ifdef __3DS__
#include <3ds.h>
#include <citro2d.h>
#endif

void ui_init(void);
void ui_fini(void);
void ui_draw_top(const Player *p, const PlaylistSet *pls);
void ui_draw_bottom(const Player *p, const Library *lib, const PlaylistSet *pls,
                    const Library *pl_preview);
void ui_handle_touch(Player *p, Library *lib, PlaylistSet *pls, Library *pl_preview,
                     int px, int py, int pressed, int held);
/* 1 if an overlay was closed (EQ / playlists). */
int ui_handle_back(void);
int ui_eq_screen_open(void);
int ui_playlists_open(void);
void ui_open_eq(void);
void ui_open_playlists(PlaylistSet *pls, Library *pl_preview);
void ui_toggle_pl_focus(void);
int ui_pl_focus_bottom(void);
/* Refresh bottom preview for current playlist cursor. */
void ui_sync_pl_preview(const PlaylistSet *pls, Library *pl_preview);

#endif
