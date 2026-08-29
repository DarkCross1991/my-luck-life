# 3DSong 0.4 — Nintendo 3DS XL (Old)

Dual-screen music player for **Old 3DS / 3DS XL**.

## What this version does

- **Top 400×240** — full-screen warm LED spectrum (amber / gold / orange)
- **Bottom 320×240** — transport, folder browser, separate EQ screen
- No in-app volume (console slider only; app plays at full level)
- Formats: **WAV, AIFF, MP3, OGG, FLAC, Opus, AAC (ADTS)**
- 3-band EQ: Bass / Mid / Treble (±12 dB)
- Music roots: `sdmc:/Music`, `sdmc:/3ds/3DSong`

## Controls

| Input | Action |
| --- | --- |
| Touch / A | Play, pause, open folder, pick a track |
| EQ | Equalizer screen |
| BACK / B | Close EQ, or stop |
| FLAT / SELECT | Reset EQ |
| L / R | Previous / next file in folder |
| D-Pad | Folder list + seek |
| Y | Shuffle |
| X | Repeat off → one → all |
| START | Exit |

## Packages

Prebuilt files in `dist/`:

- `3DSong.3dsx` + `3DSong.smdh` → `sdmc:/3ds/3DSong/`
- `3DSong.cia` — FBI install (Title ID `0004000003d50200`)

Needs `sdmc:/3ds/dspfirm.cdc` and music on SD.

Rebuild:

```bash
./3dsong/tools/build-3ds.sh
make -C 3dsong/tests run
```

Web preview:

```bash
python3 -m http.server 8765 --directory 3dsong/preview
```
