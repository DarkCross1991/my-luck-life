from __future__ import annotations

import shutil
from pathlib import Path

# Active HOME icon / CIA banner come from curated photo crops.
# Other options live in meta/icon_candidates/ for later.
ACTIVE = "walkman"


def install(root: Path, name: str = ACTIVE) -> None:
    cand = root / "meta" / "icon_candidates"
    icon_src = cand / f"{name}_icon48.png"
    banner_src = cand / f"{name}_banner256x128.png"
    if not icon_src.is_file() or not banner_src.is_file():
        raise SystemExit(f"missing candidates for {name!r} under {cand}")
    shutil.copyfile(icon_src, root / "meta" / "icon.png")
    shutil.copyfile(banner_src, root / "meta" / "banner.png")
    print(f"installed {name} -> meta/icon.png (48x48), meta/banner.png (256x128)")


if __name__ == "__main__":
    install(Path(__file__).resolve().parents[1])
