#!/usr/bin/env python3
"""Converts design/logo/bitcoin-elektron.svg into Android vector drawables.

Usage: gen-logo.py <app/src dir>
Writes ic_launcher_foreground.xml and ic_launcher_monochrome.xml for the mainnet colors under main/res/drawable,
ic_stat_node.xml (notification icon) under main/res/drawable, and a dark grey ic_launcher_foreground.xml under testnet/res/drawable.
"""
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SVG = os.path.join(HERE, "logo", "bitcoin-elektron.svg")
SCALE = 1.765517
OFFSET = 60.027586
SAFE_ZONE_SCALE = 0.62

STOPS = {
    "main": ("#FF4BC2F9", "#FF1A9BF7"),
    "testnet": ("#FF6B7079", "#FF3A3E45"),
}

# Arial Bold "E" at 250 px, as rectangles (x0, y0, x1, y1) relative to the text origin
E_RECTS = [(18, -179, 58, 0), (18, -179, 155, -143), (18, -110, 140, -74), (18, -36, 155, 0)]
TEXT_ORIGIN = (655.895, 795.106)
TEXT_MATRIX = (0.549359, 0.137915, -0.137915, 0.549359, 96.767249, -148.065097)


def outer(x, y):
    return x * SCALE + OFFSET, y * SCALE + OFFSET


def fmt(value):
    text = f"{value:.2f}".rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


def transform_path(data):
    tokens = re.findall(r"[MLCZ]|-?\d+\.?\d*(?:e-?\d+)?", data)
    out = []
    i = 0
    command = None
    while i < len(tokens):
        token = tokens[i]
        if token in "MLCZ":
            command = token
            out.append(token)
            i += 1
            if command == "Z":
                continue
        count = {"M": 2, "L": 2, "C": 6}[command]
        numbers = [float(t) for t in tokens[i:i + count]]
        i += count
        pairs = []
        for j in range(0, count, 2):
            x, y = outer(numbers[j], numbers[j + 1])
            pairs.append(f"{fmt(x)},{fmt(y)}")
        out.append(" ".join(pairs))
    return " ".join(out).replace(" Z", "Z").replace("Z ", "Z")


def e_path():
    a, b, c, d, e, f = TEXT_MATRIX
    ox, oy = TEXT_ORIGIN
    parts = []
    for x0, y0, x1, y1 in E_RECTS:
        corners = []
        for lx, ly in ((x0, y0), (x1, y0), (x1, y1), (x0, y1)):
            px, py = ox + lx, oy + ly
            ix, iy = a * px + c * py + e, b * px + d * py + f
            ox2, oy2 = outer(ix, iy)
            corners.append(f"{fmt(ox2)},{fmt(oy2)}")
        parts.append("M" + " L".join(corners) + "Z")
    return " ".join(parts)


def read_paths():
    svg = open(SVG).read()
    paths = re.findall(r'<path d="([^"]+)"', svg)
    if len(paths) != 3:
        sys.exit(f"expected 3 paths, found {len(paths)}")
    return [transform_path(p) for p in paths]


def gradient(cx, cy, radius, colors):
    gx, gy = outer(cx, cy)
    return (
        '        <aapt:attr name="android:fillColor">\n'
        f'            <gradient android:type="radial" android:centerX="{fmt(gx)}" android:centerY="{fmt(gy)}" android:gradientRadius="{fmt(radius * SCALE)}">\n'
        f'                <item android:offset="0" android:color="{colors[0]}" />\n'
        f'                <item android:offset="1" android:color="{colors[1]}" />\n'
        "            </gradient>\n"
        "        </aapt:attr>\n"
    )


def circle_path():
    cx, cy = outer(255, 255)
    r = 200 * SCALE
    return (
        f"M{fmt(cx - r)},{fmt(cy)} a{fmt(r)},{fmt(r)} 0 1,0 {fmt(2 * r)},0 a{fmt(r)},{fmt(r)} 0 1,0 {fmt(-2 * r)},0Z"
    )


def foreground(variant):
    small1, small2, coin = read_paths()
    colors = STOPS[variant]
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="1024"\n'
        '    android:viewportHeight="1024">\n'
        f'    <group android:pivotX="512" android:pivotY="512" android:scaleX="{SAFE_ZONE_SCALE}" android:scaleY="{SAFE_ZONE_SCALE}">\n'
        f'    <path android:fillColor="#FFFFFFFF" android:pathData="{circle_path()}" />\n'
        f'    <path android:pathData="{small1}">\n'
        + gradient(277.4905, 196.4412, 34.3969, colors)
        + "    </path>\n"
        f'    <path android:pathData="{small2}">\n'
        + gradient(261.9153, 284.5671, 39.8381, colors)
        + "    </path>\n"
        f'    <path android:pathData="{coin}">\n'
        + gradient(256.0276, 256.0027, 255.9878, colors)
        + "    </path>\n"
        f'    <path android:fillColor="#FFFFFFFF" android:pathData="{e_path()}" />\n'
        "    </group>\n"
        "</vector>\n"
    )


def monochrome():
    coin = read_paths()[2]
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="1024"\n'
        '    android:viewportHeight="1024">\n'
        f'    <group android:pivotX="512" android:pivotY="512" android:scaleX="{SAFE_ZONE_SCALE}" android:scaleY="{SAFE_ZONE_SCALE}">\n'
        f'    <path android:fillColor="#FF000000" android:pathData="{coin}" />\n'
        "    </group>\n"
        "</vector>\n"
    )


def status_icon():
    coin = read_paths()[2]
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="1024"\n'
        '    android:viewportHeight="1024">\n'
        f'    <path android:fillColor="#FFFFFFFF" android:pathData="{coin}" />\n'
        "</vector>\n"
    )


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    root = sys.argv[1]
    write(os.path.join(root, "main", "res", "drawable", "ic_launcher_foreground.xml"), foreground("main"))
    write(os.path.join(root, "main", "res", "drawable", "ic_launcher_monochrome.xml"), monochrome())
    write(os.path.join(root, "main", "res", "drawable", "ic_stat_node.xml"), status_icon())
    write(os.path.join(root, "testnet", "res", "drawable", "ic_launcher_foreground.xml"), foreground("testnet"))


if __name__ == "__main__":
    main()
