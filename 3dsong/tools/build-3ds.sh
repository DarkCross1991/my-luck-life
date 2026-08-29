#!/usr/bin/env bash
# Build 3DSong.3dsx (and optionally .cia) with the official devkitARM Docker image.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="${THREEDSONG_DKP_IMAGE:-devkitpro/devkitarm}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required (or run make with DEVKITARM set)." >&2
  exit 1
fi

docker run --rm -v "$ROOT:/src" -w /src "$IMAGE" bash -lc '
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
