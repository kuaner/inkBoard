#!/usr/bin/env python3
"""Pre-download Koboyo icon catalogs + SVGs into app assets (offline picker).

Layout:
  app/src/main/assets/koboyo/
    catalogs/{endpointKey}.json   # up to PER_CATEGORY entries
    svg/{slug}.svg                # deduped by slug

Usage (repo root):
  python3 scripts/download_koboyo_icons.py

Matches KoboyoIconRepository.CATEGORIES / PAGE_SIZE=16:
  50 icons ≈ 4 pages per category (5 pages would be 80).
"""

from __future__ import annotations

import concurrent.futures
import json
import time
import urllib.error
import urllib.request
from pathlib import Path

HOST = "https://koboyo.com"
PER_CATEGORY = 50
WORKERS = 16
REPO = Path(__file__).resolve().parents[1]
OUT = REPO / "app/src/main/assets/koboyo"
CATALOGS = OUT / "catalogs"
SVG_DIR = OUT / "svg"

# Mirror of KoboyoIconRepository.CATEGORIES (endpointKey = group--subgroup|group)
CATEGORIES: list[tuple[str, str | None]] = [
    ("face", None),
    *[("mark", s) for s in "icon mark math solid status symbol texture".split()],
    *[
        ("object", s)
        for s in (
            "agriculture ai animal aviation beauty business civic collage commerce "
            "communication compsci concept content craft culture data dev document "
            "education entertainment environment event everyday family fantasy fashion "
            "feature file food gaming hand health history hobby home hospitality "
            "incident industry infographic interface iso legal logistics maritime mark "
            "marketing mascot math media military misc nature place plan playful print "
            "property rail safety science security social sport stationery symbol "
            "sysdesign tech telecom time tool toy travel vehicle workplace workshop"
        ).split()
    ],
    *[
        ("people", s)
        for s in (
            "action business character creative culture education emotion event famous "
            "figure gesture group health home interface misc outdoor person pose present "
            "profession sport tech vehicle workplace"
        ).split()
    ],
    *[("scene", s) for s in "uistate vignette".split()],
    ("solid", None),
]


def endpoint_key(group: str, subgroup: str | None) -> str:
    return f"{group}--{subgroup or group}"


def fetch(url: str, accept: str) -> bytes:
    req = urllib.request.Request(
        url,
        headers={
            "Accept": accept,
            "User-Agent": "inkBoard-asset-fetch/1.0",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        if resp.status not in range(200, 300):
            raise RuntimeError(f"HTTP {resp.status}")
        return resp.read()


def safe_slug(slug: str) -> str:
    return "".join(c if c.isalnum() or c in "._-" else "_" for c in slug)


def load_catalog(group: str, subgroup: str | None) -> list[list]:
    key = endpoint_key(group, subgroup)
    url = f"{HOST}/icons/data/groups/v1/{key}.json"
    data = json.loads(fetch(url, "application/json").decode("utf-8"))
    entries = data.get("entries") or []
    return entries[:PER_CATEGORY]


def download_svg(slug: str) -> tuple[str, bool, str]:
    safe = safe_slug(slug)
    if not safe:
        return slug, False, "empty-slug"
    dest = SVG_DIR / f"{safe}.svg"
    if dest.exists() and dest.stat().st_size > 0:
        return slug, True, "cached"
    url = f"{HOST}/icons/svg/{slug}.svg"
    try:
        body = fetch(url, "image/svg+xml,text/plain,*/*").decode("utf-8", errors="replace")
        if not body.lstrip().startswith("<svg"):
            return slug, False, "not-svg"
        dest.write_text(body, encoding="utf-8")
        return slug, True, "ok"
    except Exception as exc:  # noqa: BLE001
        return slug, False, str(exc)


def main() -> None:
    CATALOGS.mkdir(parents=True, exist_ok=True)
    SVG_DIR.mkdir(parents=True, exist_ok=True)

    print(f"categories={len(CATEGORIES)} per_category={PER_CATEGORY} out={OUT}")
    all_slugs: set[str] = set()
    index: dict[str, list[dict[str, str]]] = {}

    for i, (group, subgroup) in enumerate(CATEGORIES, 1):
        key = endpoint_key(group, subgroup)
        try:
            entries = load_catalog(group, subgroup)
        except Exception as exc:  # noqa: BLE001
            print(f"[{i}/{len(CATEGORIES)}] FAIL catalog {key}: {exc}")
            continue

        slim = []
        for entry in entries:
            slug = str(entry[0]).strip()
            if not slug:
                continue
            name = str(entry[1] if len(entry) > 1 else slug).strip() or slug
            slim.append({"slug": slug, "name": name, "file": f"{safe_slug(slug)}.svg"})
            all_slugs.add(slug)

        (CATALOGS / f"{key}.json").write_text(
            json.dumps(
                {
                    "group": group,
                    "subgroup": subgroup or group,
                    "count": len(slim),
                    "entries": [[e["slug"], e["name"]] for e in slim],
                },
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )
        index[key] = slim
        print(f"[{i}/{len(CATEGORIES)}] catalog {key}: {len(slim)} icons")
        time.sleep(0.03)

    print(f"unique slugs to fetch: {len(all_slugs)}")
    ok = cached = fail = 0
    fails: list[tuple[str, str]] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = [pool.submit(download_svg, slug) for slug in sorted(all_slugs)]
        done = 0
        for fut in concurrent.futures.as_completed(futures):
            slug, success, reason = fut.result()
            done += 1
            if success and reason == "cached":
                cached += 1
            elif success:
                ok += 1
            else:
                fail += 1
                fails.append((slug, reason))
            if done % 100 == 0 or done == len(futures):
                print(f"  svg progress {done}/{len(futures)} ok={ok} cached={cached} fail={fail}")

    # Drop catalog entries whose SVG failed
    missing = {s for s, _ in fails}
    if missing:
        for key, items in list(index.items()):
            kept = [e for e in items if e["slug"] not in missing]
            index[key] = kept
            path = CATALOGS / f"{key}.json"
            path.write_text(
                json.dumps(
                    {
                        "group": key.split("--", 1)[0],
                        "subgroup": key.split("--", 1)[-1],
                        "count": len(kept),
                        "entries": [[e["slug"], e["name"]] for e in kept],
                    },
                    ensure_ascii=False,
                    indent=2,
                ),
                encoding="utf-8",
            )

    summary = {
        "source": HOST,
        "perCategory": PER_CATEGORY,
        "categories": len(index),
        "uniqueSvgs": len(list(SVG_DIR.glob("*.svg"))),
        "downloadOk": ok,
        "downloadCached": cached,
        "downloadFail": fail,
        "catalogs": {k: len(v) for k, v in sorted(index.items())},
    }
    (OUT / "manifest.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    total = sum(p.stat().st_size for p in SVG_DIR.glob("*.svg"))
    print(
        f"done categories={len(index)} svgs={summary['uniqueSvgs']} "
        f"bytes={total} fail={fail}"
    )
    if fails[:10]:
        print("sample fails:", fails[:10])


if __name__ == "__main__":
    main()
