# 3DSong 0.5 — Nintendo 3DS XL (Old)

Dual-screen music player for **Old 3DS / 3DS XL**.

## What this version does

- **Top 400×240** — full-screen warm LED spectrum (or playlist picker)
- **Bottom 320×240** — transport, ORD/RPT, EQ, playlist browser
- Playlists = subfolders of `sdmc:/Music/` (folder name only in UI; Cyrillic OK)
- Formats: **WAV, AIFF, MP3, OGG, FLAC, Opus, AAC (ADTS)**
- 3-band EQ: Bass / Mid / Treble (±12 dB); B closes EQ (no BACK button)
- No in-app volume (console slider only)

## Controls

| Input | Action |
| --- | --- |
| ↑↓ | Highlight song / playlist row |
| ←→ | Switch playlist |
| A | Select song (or pause if already current) |
| B | Pause, or close EQ / playlists |
| X | Stop |
| Y | Playlists screen (Y again: focus top ↔ bottom) |
| Touch EQ / ORD / RPT | Equalizer · play order SEQ→S1→S* · repeat R-→R~→R1 |
| L / R | Previous / next track |
| START | Exit |

## Packages

Prebuilt files in `dist/`:

- `3DSong.3dsx` + `3DSong.smdh` → `sdmc:/3ds/3DSong/`
- `3DSong.cia` — FBI install (Title ID `0004000003d50200`)

Needs `sdmc:/3ds/dspfirm.cdc` and music under `sdmc:/Music/<playlist>/`.

Rebuild:

```bash
./3dsong/tools/build-3ds.sh
make -C 3dsong/tests run
```

Web preview:

```bash
python3 -m http.server 8765 --directory 3dsong/preview
```
