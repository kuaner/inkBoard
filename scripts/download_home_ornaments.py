#!/usr/bin/env python3
"""Download a fixed set of Koboyo animal SVGs into app assets.

Usage (from repo root):
  python3 scripts/download_home_ornaments.py

Source:
  https://koboyo.com/icons/data/groups/v1/object--animal.json
  https://koboyo.com/icons/svg/{slug}.svg

These are bundled as static assets under:
  app/src/main/assets/home_ornaments/
"""

from __future__ import annotations

import json
import time
import urllib.request
from pathlib import Path

HOST = "https://koboyo.com"
CATALOG_URL = f"{HOST}/icons/data/groups/v1/object--animal.json"
REPO_ROOT = Path(__file__).resolve().parents[1]
OUT = REPO_ROOT / "app/src/main/assets/home_ornaments"
TARGET_N = 128


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    print(f"fetch catalog {CATALOG_URL}")
    req = urllib.request.Request(
        CATALOG_URL,
        headers={"Accept": "application/json", "User-Agent": "inkBoard-asset-fetch/1.0"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    entries = data.get("entries") or []
    if not entries:
        raise SystemExit("catalog empty")

    if len(entries) <= TARGET_N:
        chosen = entries
    else:
        step = len(entries) / TARGET_N
        chosen = [entries[int(i * step)] for i in range(TARGET_N)]

    manifest = []
    ok = 0
    fail = []
    for entry in chosen:
        slug = str(entry[0]).strip()
        name = str(entry[1] if len(entry) > 1 else slug).strip() or slug
        safe = "".join(c if c.isalnum() or c in "._-" else "_" for c in slug)
        if not safe:
            continue
        filename = f"{safe}.svg"
        dest = OUT / filename
        url = f"{HOST}/icons/svg/{slug}.svg"
        try:
            svg_req = urllib.request.Request(
                url,
                headers={
                    "Accept": "image/svg+xml,text/plain,*/*",
                    "User-Agent": "inkBoard-asset-fetch/1.0",
                },
            )
            with urllib.request.urlopen(svg_req, timeout=20) as resp:
                body = resp.read().decode("utf-8", errors="replace")
            if not body.lstrip().startswith("<svg"):
                fail.append((slug, "not-svg"))
                continue
            dest.write_text(body, encoding="utf-8")
            manifest.append({"slug": slug, "name": name, "file": filename})
            ok += 1
            if ok % 20 == 0:
                print(f"  downloaded {ok}/{len(chosen)}")
        except Exception as exc:  # noqa: BLE001 - asset script: collect and continue
            fail.append((slug, str(exc)))
        time.sleep(0.05)

    (OUT / "manifest.json").write_text(
        json.dumps(
            {
                "source": "koboyo object/animal",
                "catalog": CATALOG_URL,
                "count": len(manifest),
                "icons": manifest,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    total = sum(p.stat().st_size for p in OUT.glob("*.svg"))
    print(f"done ok={ok} fail={len(fail)} dir={OUT} bytes={total}")
    if fail:
        print("failures:", fail[:8])


if __name__ == "__main__":
    main()
