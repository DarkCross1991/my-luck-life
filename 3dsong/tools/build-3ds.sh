#!/usr/bin/env bash
# Build 3DSong.3dsx (and optionally .cia) with the official devkitARM Docker image.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="${THREEDSONG_DKP_IMAGE:-devkitpro/devkitarm}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required (or run make with DEVKITARM set)." >&2
  exit 1
fi

DOCKER=(docker)
if ! docker info >/dev/null 2>&1; then
  if command -v sudo >/dev/null 2>&1 && sudo docker info >/dev/null 2>&1; then
    DOCKER=(sudo docker)
  else
    echo "cannot talk to the docker daemon." >&2
    exit 1
  fi
fi

"${DOCKER[@]}" run --rm -v "$ROOT:/src" -w /src "$IMAGE" bash -lc '
  set -euo pipefail
  export DEVKITPRO=/opt/devkitpro
  export DEVKITARM=$DEVKITPRO/devkitARM
  export PATH=$DEVKITARM/bin:$DEVKITPRO/tools/bin:$PATH
  . /opt/devkitpro/3dsvars.sh
  make -j"$(nproc)"
'

mkdir -p "$ROOT/dist"
cp -f "$ROOT/3DSong.3dsx" "$ROOT/3DSong.smdh" "$ROOT/dist/"
echo "Wrote $ROOT/dist/3DSong.3dsx"

pack_cia() {
  local makerom="" bannertool=""
  if command -v makerom >/dev/null 2>&1; then
    makerom="$(command -v makerom)"
  elif [ -x /tmp/cia-tools/makerom ]; then
    makerom=/tmp/cia-tools/makerom
  fi
  if command -v bannertool >/dev/null 2>&1; then
    bannertool="$(command -v bannertool)"
  elif [ -x /tmp/cia-tools/bannertool-1.2.3-linux/bannertool ]; then
    bannertool=/tmp/cia-tools/bannertool-1.2.3-linux/bannertool
  fi
  if [ -z "$makerom" ] || [ -z "$bannertool" ]; then
    echo "skip CIA (install makerom + bannertool to build 3DSong.cia)"
    return 0
  fi
  mkdir -p "$ROOT/build"
  "$bannertool" makebanner -i "$ROOT/meta/banner.png" -a "$ROOT/meta/audio.wav" -o /tmp/3dsong-banner.bin
  cp -f /tmp/3dsong-banner.bin "$ROOT/build/banner.bin" 2>/dev/null || true
  "$makerom" -f cia -o "$ROOT/dist/3DSong.cia" \
    -rsf "$ROOT/meta/cia.rsf" -target t \
    -elf "$ROOT/3DSong.elf" -icon "$ROOT/3DSong.smdh" \
    -banner /tmp/3dsong-banner.bin -desc app:7
  echo "Wrote $ROOT/dist/3DSong.cia"
}

pack_cia
