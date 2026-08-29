# 3DSong 0.02 — Nintendo 3DS XL (Old)

Dual-screen music player: **tube-amp visualization on the top screen**, **all controls on the bottom touch screen**.

## What this version does

- Plays popular formats from the SD card: **WAV, AIFF, MP3, OGG/Vorbis, FLAC, Opus, AAC (ADTS)**
- Simple 3-band equalizer: Bass / Mid / Treble (±12 dB)
- Top 400×240: analog VU meters, EL34 glow, CRT oscilloscope
- Bottom 320×240: transport, volume, EQ, playlist (stylus / face buttons)
- Tuned for **Old 3DS / 3DS XL** clocks (decode on a background thread, IIR EQ, no heavy FFT)

Put files in `sdmc:/Music` or `sdmc:/3ds/3DSong`.

## Controls (Old 3DS XL)

| Input | Action |
| --- | --- |
| Touch / A | Play, pause, pick a track |
| B | Stop |
| L / R | Previous / next |
| D-Pad | Playlist + seek |
| Circle Pad Y | Volume |
| Y | Shuffle |
| X | Repeat off → one → all |
| SELECT | EQ flat |
| START | Exit to Homebrew Launcher |

Headphones keep playing with the lid closed (APT).

## Готовый пакет 0.02

В `dist/` уже лежат собранные файлы:

- `3DSong.3dsx` + `3DSong.smdh` — Homebrew Launcher (`sdmc:/3ds/3DSong/`)
- `3DSong.cia` — установка через FBI

На консоли нужен дамп DSP (`sdmc:/3ds/dspfirm.cdc`) и музыка в `sdmc:/Music`.

Пересобрать в Docker (официальный `devkitpro/devkitarm`):

```bash
./tools/build-3ds.sh
```

## Build `.3dsx` (devkitPro)

```bash
sudo dkp-pacman -S 3ds-dev 3ds-mpg123 3ds-libvorbisidec 3ds-libogg \
  3ds-opusfile 3ds-libopus 3ds-libflac
export DEVKITPRO=/opt/devkitpro
export DEVKITARM=$DEVKITPRO/devkitARM
cd 3dsong && make
```

Copy `3DSong.3dsx` (and `.smdh`) to `sdmc:/3ds/3DSong/`.

NDSP needs dumped DSP firmware (`sdmc:/3ds/dspfirm.cdc`). Dump it once with a DSP dumper homebrew.

M4A/MP4 is listed in the library but not demuxed yet on hardware (use AAC/MP3/FLAC). The web preview plays M4A through the browser.

## Web preview (this machine)

```bash
make -C 3dsong/tests        # portable EQ / WAV / library tests
python3 -m http.server 8765 --directory 3dsong/preview
```

Open `http://127.0.0.1:8765/` — same 400×240 + 320×240 layout, Web Audio EQ, file drop.

## DSP / Old 3DS notes

- Target is **O3DS**: ARM11 @ 268 MHz, 64 MB app mode, no extra New 3DS cores
- High-bitrate FLAC is heavier than MP3/OGG; 44.1 kHz stereo is the sweet spot
- 3D slider is unused (2D viz, saves fillrate)
