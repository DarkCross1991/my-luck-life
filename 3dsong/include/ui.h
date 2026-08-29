#ifndef THREEDSONG_UI_H
#define THREEDSONG_UI_H

#include "player.h"
#include "library.h"

#ifdef __3DS__
#include <3ds.h>
#include <citro2d.h>
#endif

void ui_init(void);
void ui_fini(void);
void ui_draw_top(const Player *p);
void ui_draw_bottom(const Player *p, const Library *lib);
void ui_handle_touch(Player *p, Library *lib, int px, int py, int pressed, int held);

#endif
