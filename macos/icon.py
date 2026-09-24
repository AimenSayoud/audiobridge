"""Generates AudioBridge.icns — the same rising-bars mark as the Android app."""
import os, subprocess, sys
from PIL import Image, ImageDraw

BG = (11, 17, 32, 255)
BARS = [(0.235, 0.62, "#BAE6FD"), (0.355, 0.50, "#7DD3FC"),
        (0.475, 0.34, "#38BDF8"), (0.595, 0.44, "#0EA5E9"), (0.715, 0.62, "#BAE6FD")]

def render(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = size * 0.22
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=BG)
    width = size * 0.075
    bottom = size * 0.74
    for x, top, colour in BARS:
        left = size * x
        d.rounded_rectangle([left, size * top, left + width, bottom],
                            radius=width * 0.42, fill=colour)
    return img

def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "AudioBridge.icns"
    iconset = "AudioBridge.iconset"
    os.makedirs(iconset, exist_ok=True)
    for base in (16, 32, 128, 256, 512):
        render(base).save(f"{iconset}/icon_{base}x{base}.png")
        render(base * 2).save(f"{iconset}/icon_{base}x{base}@2x.png")
    subprocess.run(["iconutil", "-c", "icns", iconset, "-o", out], check=True)
    subprocess.run(["rm", "-rf", iconset], check=True)
    print(f"wrote {out}")

main()
