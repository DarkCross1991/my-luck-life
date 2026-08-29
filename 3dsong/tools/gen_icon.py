from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


def _font(size: int):
    for name in ("DejaVuSans-Bold.ttf", "DejaVuSans.ttf", "FreeSansBold.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def icon(path: Path) -> None:
    im = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, 47, 47), 8, fill=(36, 20, 10, 255))
    d.rounded_rectangle((3, 3, 44, 44), 6, fill=(56, 54, 50, 255))
    d.ellipse((5, 10, 23, 28), fill=(16, 14, 10, 255), outline=(212, 168, 72, 255))
    d.ellipse((25, 10, 43, 28), fill=(16, 14, 10, 255), outline=(212, 168, 72, 255))
    d.line((14, 19, 8, 12), fill=(255, 180, 60, 255), width=1)
    d.line((34, 19, 40, 12), fill=(255, 180, 60, 255), width=1)
    d.ellipse((12, 30, 16, 42), fill=(255, 120, 40, 255))
    d.ellipse((22, 30, 26, 42), fill=(255, 160, 50, 255))
    d.ellipse((32, 30, 36, 42), fill=(255, 120, 40, 255))
    d.text((6, 2), "3D", font=_font(10), fill=(232, 196, 96, 255))
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)


def banner(path: Path) -> None:
    im = Image.new("RGB", (256, 128), (28, 16, 8))
    d = ImageDraw.Draw(im)
    d.rectangle((8, 8, 247, 119), fill=(48, 30, 16))
    d.rectangle((16, 16, 239, 96), fill=(58, 56, 54))
    d.ellipse((28, 28, 96, 90), fill=(18, 16, 12), outline=(212, 168, 72))
    d.ellipse((160, 28, 228, 90), fill=(18, 16, 12), outline=(212, 168, 72))
    d.line((62, 59, 40, 36), fill=(255, 200, 80), width=2)
    d.line((194, 59, 216, 36), fill=(255, 200, 80), width=2)
    for i, x in enumerate((118, 128, 138, 148)):
        d.ellipse((x, 36, x + 10, 70), fill=(255, 110 + i * 20, 30))
    d.text((20, 100), "3DSong  0.02", font=_font(16), fill=(232, 196, 96))
    d.text((168, 104), "O3DS XL", font=_font(11), fill=(180, 150, 90))
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    icon(root / "meta" / "icon.png")
    banner(root / "meta" / "banner.png")
    print("wrote meta/icon.png and meta/banner.png")
