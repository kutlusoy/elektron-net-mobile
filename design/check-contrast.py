#!/usr/bin/env python3
"""Checks the contrast of every color theme in palettes.json against the legibility rules of the design guideline.

Usage: check-contrast.py [palettes.json]
Exit status is 1 if any pair is below its minimum.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

# (foreground, background, minimum ratio, what it is used for)
DARK_RULES = [
    ("text", "bg", 7.0, "body text"),
    ("text", "surface", 7.0, "body text on cards"),
    ("text", "surface-2", 7.0, "body text on raised cards"),
    ("muted", "bg", 5.5, "secondary text"),
    ("muted", "surface", 5.5, "secondary text on cards"),
    ("muted", "surface-2", 5.5, "secondary text on raised cards"),
    ("accent", "bg", 4.5, "accent as text or icon"),
    ("link", "surface", 4.5, "links"),
    ("on-accent", "accent", 4.5, "label on a filled accent button"),
]
LIGHT_RULES = [
    ("text", "bg", 7.0, "body text"),
    ("text", "surface", 7.0, "body text on cards"),
    ("text", "surface-2", 7.0, "body text on raised cards"),
    ("muted", "bg", 5.0, "secondary text"),
    ("muted", "surface", 5.0, "secondary text on cards"),
    ("muted", "surface-2", 5.0, "secondary text on raised cards"),
    ("link", "surface", 4.5, "links"),
    ("on-accent", "link", 4.5, "label on a filled button"),
]
STATUS_ON = ("bg", "surface", "surface-2")


def luminance(color):
    channels = [int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    r, g, b = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(a, b):
    la, lb = luminance(a), luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "palettes.json")
    with open(path) as f:
        data = json.load(f)
    failures = checked = 0
    for key, palette in data["palettes"].items():
        for mode, rules in (("dark", DARK_RULES), ("light", LIGHT_RULES)):
            tokens = palette[mode]
            pairs = [(fg, bg, minimum, use) for fg, bg, minimum, use in rules]
            for status, color in data["status"][mode].items():
                pairs += [(color, bg, 4.5, f"{status} status text") for bg in STATUS_ON]
            for fg, bg, minimum, use in pairs:
                foreground = tokens[fg] if fg in tokens else fg
                background = tokens[bg] if bg in tokens else bg
                value = ratio(foreground, background)
                checked += 1
                if value < minimum:
                    failures += 1
                    print(f"FAIL {key} {mode}: {use} {foreground} on {background} is {value:.1f}, need {minimum}")
    print(f"{checked - failures} of {checked} contrast checks passed")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
