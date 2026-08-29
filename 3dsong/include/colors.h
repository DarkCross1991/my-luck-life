#ifndef THREEDSONG_COLORS_H
#define THREEDSONG_COLORS_H

#ifdef __3DS__
#include <citro2d.h>
#define RGBA(r,g,b,a) C2D_Color32((u32)(r),(u32)(g),(u32)(b),(u32)(a))
#else
#define RGBA(r,g,b,a) (0)
#endif

#define COL_WOOD       RGBA(52, 28, 14, 255)
#define COL_WOOD_DK    RGBA(32, 16, 8, 255)
#define COL_WOOD_LT    RGBA(78, 44, 22, 255)
#define COL_METAL      RGBA(58, 56, 54, 255)
#define COL_METAL_LT   RGBA(92, 88, 82, 255)
#define COL_METAL_DK   RGBA(28, 26, 24, 255)
#define COL_GOLD       RGBA(212, 168, 72, 255)
#define COL_GOLD_DK    RGBA(140, 96, 32, 255)
#define COL_AMBER      RGBA(255, 162, 48, 255)
#define COL_AMBER_HI   RGBA(255, 220, 140, 255)
#define COL_VU_FACE    RGBA(18, 16, 12, 255)
#define COL_VU_SCALE   RGBA(230, 210, 150, 255)
#define COL_VU_RED     RGBA(200, 48, 32, 255)
#define COL_NEEDLE     RGBA(240, 236, 220, 255)
#define COL_TUBE_GLASS RGBA(40, 48, 44, 180)
#define COL_CRT_BG     RGBA(8, 16, 10, 255)
#define COL_CRT_PHOS   RGBA(255, 168, 64, 255)
#define COL_BOT_BG     RGBA(22, 16, 12, 255)
#define COL_BOT_PANEL  RGBA(36, 28, 22, 255)
#define COL_BTN        RGBA(64, 48, 34, 255)
#define COL_BTN_HI     RGBA(110, 78, 42, 255)
#define COL_TEXT       RGBA(236, 220, 180, 255)
#define COL_TEXT_DIM   RGBA(160, 140, 100, 255)
#define COL_ACCENT     RGBA(232, 140, 48, 255)
#define COL_LIST_SEL   RGBA(90, 56, 24, 255)
#define COL_GREEN_LED  RGBA(72, 220, 90, 255)

#endif
