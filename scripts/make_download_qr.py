"""Generates the website's download QR code from website/assets/js/site-config.js and verifies it.

The URL is built from the same values the site uses for its download button, so the two cannot
differ. The QR image is decoded again and must give back exactly that URL; only then is
qrEncodedUrl written into site-config.js (the site shows the QR code only when it matches).

    python -m pip install segno zxing-cpp pillow
    python scripts/make_download_qr.py
"""
import io
import pathlib
import re
import sys

import segno
import zxingcpp
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONFIG = ROOT / "website" / "assets" / "js" / "site-config.js"
OUTPUT = ROOT / "website" / "assets" / "img" / "download-qr.svg"


def config_value(text: str, key: str) -> str:
    match = re.search(rf'\b{key}:\s*"([^"]*)"', text)
    if not match:
        sys.exit(f"{key} not found in {CONFIG}")
    return match.group(1)


def main() -> None:
    text = CONFIG.read_text(encoding="utf-8")
    owner, repo, apk = config_value(text, "owner"), config_value(text, "repo"), config_value(text, "apkFileName")
    if not (owner and repo):
        sys.exit("github.owner and github.repo are empty in site-config.js; set them first.")
    url = f"https://github.com/{owner}/{repo}/releases/latest/download/{apk}"

    qr = segno.make(url, error="m", micro=False)

    # Decode a rendering of the code and compare
    buffer = io.BytesIO()
    qr.save(buffer, kind="png", scale=8, border=4)
    decoded = [r.text for r in zxingcpp.read_barcodes(Image.open(io.BytesIO(buffer.getvalue())))]
    if decoded != [url]:
        sys.exit(f"QR verification failed: decoded {decoded!r}, expected {url!r}")

    qr.save(str(OUTPUT), kind="svg", scale=8, border=2, dark="#0B1430", light="#FFFFFF",
            title=f"Download Baziq Calculator: {url}")

    new_text, count = re.subn(r'qrEncodedUrl:\s*"[^"]*"', f'qrEncodedUrl: "{url}"', text)
    if count != 1:
        sys.exit("qrEncodedUrl not found in site-config.js")
    CONFIG.write_text(new_text, encoding="utf-8", newline="\n")
    print(f"QR code written to {OUTPUT.relative_to(ROOT)} and verified: {url}")


if __name__ == "__main__":
    main()
