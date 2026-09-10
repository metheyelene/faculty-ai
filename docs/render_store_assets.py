#!/usr/bin/env python3
"""Render Play Store graphics from docs/store-templates/*.html via headless Chrome.

Outputs to docs/store/:
  icon-512.png            512x512   store icon
  feature-graphic.png     1024x500  feature graphic
  phone-XX-<name>.png     1080x2340 phone screenshots

Usage: python3 render_store_assets.py
"""
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEMPLATES = ROOT / "docs" / "store-templates"
OUT = ROOT / "docs" / "store"

CHROME_CANDIDATES = [
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
]

JOBS = [
    ("icon.html", "icon-512.png", 512, 512),
    ("feature-graphic.html", "feature-graphic.png", 1024, 500),
    ("screens/01-home.html", "phone-01-home.png", 1080, 2340),
    ("screens/02-timetable.html", "phone-02-timetable.png", 1080, 2340),
    ("screens/03-tasks.html", "phone-03-tasks.png", 1080, 2340),
    ("screens/04-assistant.html", "phone-04-assistant.png", 1080, 2340),
    ("screens/05-attendance.html", "phone-05-attendance.png", 1080, 2340),
]


def chrome_path() -> str:
    for c in CHROME_CANDIDATES:
        if Path(c).exists():
            return c
    print("error: Chrome not found", file=sys.stderr)
    sys.exit(1)


def render(chrome: str, template: Path, out: Path, w: int, h: int) -> bool:
    cmd = [
        chrome,
        "--headless",
        "--disable-gpu",
        "--hide-scrollbars",
        "--force-device-scale-factor=1",
        "--default-background-color=00000000",
        f"--window-size={w},{h}",
        f"--screenshot={out}",
        template.as_uri(),
    ]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    if r.returncode != 0 or not out.exists():
        print(f"  FAIL {template.name}: {r.stderr.strip()[:200]}", file=sys.stderr)
        return False
    return True


def main() -> None:
    chrome = chrome_path()
    OUT.mkdir(parents=True, exist_ok=True)
    ok = 0
    for template, out_name, w, h in JOBS:
        out = OUT / out_name
        if render(chrome, TEMPLATES / template, out, w, h):
            print(f"  ok   {out_name}")
            ok += 1
    print(f"\n{ok}/{len(JOBS)} rendered to {OUT}")
    sys.exit(0 if ok == len(JOBS) else 1)


if __name__ == "__main__":
    main()
