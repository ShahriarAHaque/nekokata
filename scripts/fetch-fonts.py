import re
import urllib.request
from pathlib import Path

fonts_dir = Path(__file__).resolve().parent.parent / "www" / "fonts"
fonts_dir.mkdir(parents=True, exist_ok=True)

url = (
    "https://fonts.googleapis.com/css2?"
    "family=Inter:wght@400;500;600&family=Roboto+Mono:wght@400;500;600&display=swap"
)
req = urllib.request.Request(
    url,
    headers={
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
    },
)
css = urllib.request.urlopen(req, timeout=30).read().decode("utf-8")

saved = []
for block in re.split(r"/\* ", css):
    head = block.split("*/", 1)[0].strip()
    if head != "latin":
        continue
    fam_m = re.search(r"font-family: '([^']+)'", block)
    wt_m = re.search(r"font-weight: (\d+)", block)
    url_m = re.search(r"url\((https://[^)]+\.woff2)\)", block)
    if not (fam_m and wt_m and url_m):
        continue
    fam = fam_m.group(1).lower().replace(" ", "-")
    wt = wt_m.group(1)
    fname = f"{fam}-{wt}.woff2"
    dest = fonts_dir / fname
    data = urllib.request.urlopen(url_m.group(1), timeout=30).read()
    dest.write_bytes(data)
    saved.append(fname)
    print(fname, len(data))

print("saved", saved)
