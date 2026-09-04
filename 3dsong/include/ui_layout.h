#ifndef THREEDSONG_UI_LAYOUT_H
#define THREEDSONG_UI_LAYOUT_H

/* Bottom 320x240 — 3DSong 0.5 mockup. Keep preview/3dsong.js in sync.
 *
 * Marquee (выбранная песня или плейлист, если название не помещается):
 *   1. Пауза 5 с — показывается начало названия.
 *   2. Прокрутка влево 10 px/с, пока конец названия не окажется у правого края.
 *   3. Пауза 5 с.
 *   4. Снова с начала (цикл).
 * Применяется к строке текущего трека, имени плейлиста и длинным пунктам списков.
 */
#define MARQUEE_WAIT_MS   5000
#define MARQUEE_SPEED_PX  10   /* pixels per second, scroll left */

#define SEEK_X 8
#define SEEK_Y 28
#define SEEK_W 304
#define SEEK_H 12

#define PREV_X 6
#define PREV_Y 48
#define PREV_W 36
#define PREV_H 30

#define PLAY_X 46
#define PLAY_Y 46
#define PLAY_W 48
#define PLAY_H 34

#define NEXT_X 98
#define NEXT_Y 48
#define NEXT_W 36
#define NEXT_H 30

#define STOP_X 138
#define STOP_Y 48
#define STOP_W 36
#define STOP_H 30

#define EQBTN_X 180
#define EQBTN_Y 48
#define EQBTN_W 36
#define EQBTN_H 30

#define ORDBTN_X 222
#define ORDBTN_Y 48
#define ORDBTN_W 36
#define ORDBTN_H 30

#define RPTBTN_X 264
#define RPTBTN_Y 48
#define RPTBTN_W 36
#define RPTBTN_H 30

#define FOLDER_Y 86
#define FOLDER_H 16

#define LIST_Y 104
#define LIST_H 136
#define ROW_H  17
#define LIST_ROWS (LIST_H / ROW_H)

/* EQ overlay — no BACK button in 0.5 */
#define EQ_SLIDER_Y 62
#define EQ_SLIDER_H 148
#define EQ_SLIDER_W 18
#define EQ_COL0 46
#define EQ_COL_GAP 90

/* Playlist picker (top 400x240 / bottom 320x240), 20px margin.
 * Each playlist = subfolder of sdmc:/Music/; UI shows folder name only (Cyrillic/Latin). */
#define PL_MARGIN 20
#define PL_TOP_LIST_X 20
#define PL_TOP_LIST_Y 20
#define PL_TOP_LIST_W 348
#define PL_TOP_LIST_H 200
#define PL_SCROLL_X 368
#define PL_SCROLL_W 10
#define PL_SCROLL_THUMB_H 20
#define PL_BOT_LIST_W 280
#define PL_BOT_LIST_H 200

#endif
