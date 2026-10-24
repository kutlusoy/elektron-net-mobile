#!/usr/bin/env python3
"""Checks that English is the default language and that the shipped locales are consistent.

Rules:
  - app/shipped-locales.txt lists the shipped locales, one per line, and starts with en
  - app/src/main/res/xml/locales_config.xml lists exactly those locales
  - every shipped locale other than en has a values-<qualifier> directory with all string keys of values/strings.xml
  - no values-<qualifier> directory exists for a locale that is not shipped
Usage: check-locales.py [repo root]
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
APP = os.path.join(ROOT, "app")
RES = os.path.join(APP, "src", "main", "res")
errors = []


def qualifier(tag):
    parts = tag.split("-")
    if len(parts) == 1:
        return parts[0]
    if len(parts) == 2 and len(parts[1]) == 4:
        return f"b+{parts[0]}+{parts[1]}"
    return f"{parts[0]}-r{parts[1]}"


def keys(path):
    root = ET.parse(path).getroot()
    found = set()
    for node in root:
        if node.tag in ("string", "plurals", "string-array") and node.get("translatable") != "false":
            found.add(node.get("name"))
    return found


with open(os.path.join(APP, "shipped-locales.txt")) as f:
    shipped = [line.strip() for line in f if line.strip() and not line.startswith("#")]
if not shipped or shipped[0] != "en":
    errors.append("shipped-locales.txt must start with en (English is the default language)")

config = ET.parse(os.path.join(RES, "xml", "locales_config.xml")).getroot()
configured = [node.get("{http://schemas.android.com/apk/res/android}name") for node in config]
if configured != shipped:
    errors.append(f"locales_config.xml {configured} differs from shipped-locales.txt {shipped}")

base = keys(os.path.join(RES, "values", "strings.xml"))
wanted = {qualifier(tag): tag for tag in shipped if tag != "en"}
present = {name[len("values-"):] for name in os.listdir(RES) if name.startswith("values-") and re.match(r"values-(b\+|[a-z]{2,3}(-r[A-Z]{2})?$)", name)}
for q, tag in wanted.items():
    path = os.path.join(RES, f"values-{q}", "strings.xml")
    if not os.path.exists(path):
        errors.append(f"{tag}: {path} missing")
        continue
    missing = base - keys(path)
    if missing:
        errors.append(f"{tag}: missing keys {sorted(missing)}")
for q in sorted(present - set(wanted)):
    errors.append(f"values-{q} exists but the locale is not shipped")

stray = re.compile(r"%(?!%|\d\$[sd]|[sd])")
for name in sorted(os.listdir(RES)):
    path = os.path.join(RES, name, "strings.xml")
    if name.startswith("values") and os.path.isfile(path):
        for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', open(path, encoding="utf-8").read(), re.S):
            if stray.search(m.group(2).replace("%%", "")):
                errors.append(f"{name}/{m.group(1)}: a literal percent sign must be written %%")
            if "%%" in m.group(2) and not re.search(r"%(\d\$)?[sd]", m.group(2)):
                errors.append(f"{name}/{m.group(1)}: %% without a placeholder is shown as two percent signs when the string is read without arguments")

for message in errors:
    print("FAIL", message)
print("locales ok" if not errors else f"{len(errors)} problems")
sys.exit(1 if errors else 0)
