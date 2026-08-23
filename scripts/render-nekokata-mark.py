"""rasterize the nekokata lockup into launcher splash and in-app marks"""
import base64
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw

CREAM = (242, 239, 230, 255)  # #F2EFE6 app paper
ROOT = Path(__file__).resolve().parents[1]
LOCKUP = ROOT / "www" / "icons" / "brand-lockup.png"

DENSITIES = {
    "mdpi": 1,
    "hdpi": 1.5,
    "xhdpi": 2,
    "xxhdpi": 3,
    "xxxhdpi": 4,
}


def is_cream(rgb, tol=22):
    r, g, b = rgb[:3]
    return r >= 230 and g >= 210 and b >= 195 and (r - b) < 50 and (b - g) < 22


def punch_cream(im):
    im = im.convert("RGBA")
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            p = px[x, y]
            if is_cream(p):
                dist = min(p[0] - 230, p[1] - 210, p[2] - 195)
                alpha = 0 if dist >= 12 else int(255 * (1 - dist / 12))
                px[x, y] = (p[0], p[1], p[2], max(0, min(255, alpha)))
    return im


def ink_bbox(im):
    rgb = im.convert("RGB")
    px = rgb.load()
    w, h = rgb.size
    xs, ys = [], []
    for y in range(h):
        for x in range(w):
            if not is_cream(px[x, y]):
                xs.append(x)
                ys.append(y)
    if not xs:
        return (0, 0, w, h)
    return (min(xs), min(ys), max(xs) + 1, max(ys) + 1)


def split_lockup_blocks(im):
    rgb = im.convert("RGB")
    px = rgb.load()
    w, h = rgb.size
    ys = [y for y in range(h) if any(not is_cream(px[x, y]) for x in range(w))]
    if not ys:
        return [(0, h - 1)]
    blocks, start, prev = [], ys[0], ys[0]
    for y in ys[1:]:
        if y - prev > 10:
            blocks.append((start, prev))
            start = y
        prev = y
    blocks.append((start, prev))
    return blocks


def crop_cat(im, pad_ratio=0.12):
    blocks = split_lockup_blocks(im)
    y0, y1 = blocks[0]
    rgb = im.convert("RGB")
    px = rgb.load()
    w, _ = rgb.size
    xs = []
    for y in range(y0, y1 + 1):
        for x in range(w):
            if not is_cream(px[x, y]):
                xs.append(x)
    x0, x1 = min(xs), max(xs)
    bw, bh = x1 - x0 + 1, y1 - y0 + 1
    side = int(max(bw, bh) * (1 + pad_ratio))
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    left = int(round(cx - side / 2))
    top = int(round(cy - side / 2))
    box = (
        max(0, left),
        max(0, top),
        min(im.width, left + side),
        min(im.height, top + side),
    )
    crop = im.crop(box).convert("RGBA")
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    ox = (side - crop.width) // 2
    oy = (side - crop.height) // 2
    square.paste(crop, (ox, oy))
    return punch_cream(square)


def fit_center(src, size, fill=None, inner_ratio=1.0):
    canvas = Image.new("RGBA", (size, size), fill if fill is not None else (0, 0, 0, 0))
    inner = max(1, int(size * inner_ratio))
    fitted = src.copy()
    fitted.thumbnail((inner, inner), Image.Resampling.LANCZOS)
    x = (size - fitted.width) // 2
    y = (size - fitted.height) // 2
    canvas.paste(fitted, (x, y), fitted)
    return canvas


def circle_mask(size):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).ellipse((0, 0, size - 1, size - 1), fill=255)
    return m


def save_png(im, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path, "PNG")


def main():
    if not LOCKUP.exists():
        raise SystemExit(f"missing {LOCKUP}")

    lockup = Image.open(LOCKUP).convert("RGBA")
    cat = crop_cat(lockup)

    www = ROOT / "www"
    save_png(fit_center(cat, 512, None, 0.92), www / "icons" / "brand-mark.png")
    save_png(fit_center(cat, 192, CREAM, 0.72), www / "icon-192.png")
    save_png(fit_center(cat, 512, CREAM, 0.72), www / "icon-512.png")

    thumb = fit_center(cat, 256, None, 0.92)
    buf = BytesIO()
    thumb.save(buf, "PNG")
    b64 = base64.b64encode(buf.getvalue()).decode("ascii")
    (www / "icons" / "brand.svg").write_text(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">\n'
        "  <title>nekokata</title>\n"
        f'  <image width="256" height="256" href="data:image/png;base64,{b64}"/>\n'
        "</svg>\n",
        encoding="utf-8",
    )

    res = ROOT / "android" / "app" / "src" / "main" / "res"
    leftover = res / "drawable" / "ic_launcher_foreground.png"
    if leftover.exists():
        leftover.unlink()

    for name, scale in DENSITIES.items():
        folder = res / f"mipmap-{name}"
        launcher = fit_center(cat, int(48 * scale), CREAM, 0.78)
        round_icon = launcher.copy()
        round_icon.putalpha(circle_mask(round_icon.size[0]))
        cream_round = Image.new("RGBA", round_icon.size, CREAM)
        cream_round.paste(round_icon, (0, 0), round_icon)
        save_png(launcher.convert("RGB").convert("RGBA"), folder / "ic_launcher.png")
        save_png(cream_round, folder / "ic_launcher_round.png")
        save_png(fit_center(cat, int(108 * scale), None, 72 / 108), folder / "ic_launcher_foreground.png")

    print("wrote nekokata icons from", LOCKUP.name)


if __name__ == "__main__":
    main()
