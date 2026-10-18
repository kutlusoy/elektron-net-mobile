#!/usr/bin/env python3
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main"
FORBIDDEN = [
    r"java\.util\.Random\b",
    r"kotlin\.random",
    r"\bRandom\.Default\b",
    r"\bRandom\(",
    r"Math\.random",
    r"ThreadLocalRandom",
    r"\.shuffled\(",
    r"\.random\(",
]
ALLOWED = {
    "BackupQuiz.kt": {r"java\.util\.Random\b", r"\bRandom\(", r"\.shuffled\("},
}
SECURE = re.compile(r"\bSecureRandom\b")
SECURE_ALLOWED = {"Entropy.kt", "FirstRunScreen.kt"}

errors = []
for path in sorted(ROOT.rglob("*.kt")):
    text = path.read_text()
    allowed = ALLOWED.get(path.name, set())
    for pattern in FORBIDDEN:
        if pattern in allowed:
            continue
        for match in re.finditer(pattern, text):
            if re.search(r"SecureRandom\(", text[max(0, match.start() - 6):match.end()]):
                continue
            line = text.count("\n", 0, match.start()) + 1
            errors.append(f"{path.relative_to(ROOT)}:{line}: forbidden randomness source {pattern}")
    if SECURE.search(text) and path.name not in SECURE_ALLOWED:
        errors.append(f"{path.relative_to(ROOT)}: SecureRandom used outside the entropy module")

if errors:
    print("\n".join(errors))
    sys.exit(1)
print("rng ok")
