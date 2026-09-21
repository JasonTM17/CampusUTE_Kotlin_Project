#!/usr/bin/env python3
"""Emoji scanner for design-guard gate 2 (reads main source, prints violations)."""
import pathlib
import sys

BANNED = [
    (0x1F000, 0x1FFFF),  # pictographs, emoji
    (0x2600, 0x27BF),    # misc symbols/dingbats (incl. ✓ U+2713, ✨ U+2728)
    (0x2B00, 0x2BFF),    # arrows and symbols block
    (0x25CF, 0x25CF),    # ● black circle
]

def main() -> int:
    hits = []
    root = pathlib.Path("apps/android/app/src/main/java")
    for path in sorted(root.rglob("*.kt")):
        for lineno, line in enumerate(
            path.read_text(encoding="utf-8").splitlines(), 1
        ):
            for ch in line:
                cp = ord(ch)
                if any(lo <= cp <= hi for lo, hi in BANNED):
                    hits.append(f"{path}:{lineno}: U+{cp:04X} {ch!r}")
                    break
    for hit in hits:
        print(hit)
    return 0

if __name__ == "__main__":
    sys.exit(main())
