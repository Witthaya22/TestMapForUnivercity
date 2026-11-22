#!/usr/bin/env python3
"""
Download the photos the Khao Yai screens show, from Wikimedia Commons, with their credits.

Every file below was picked by hand for a free licence (CC0, public domain, CC BY or
CC BY-SA). The script asks Commons for a resized copy (WIDTH px wide), stores it under
app/src/main/assets/photos/<name>.jpg and writes photos/credits.json with the author,
licence and page of each one - the app prints that line under the photo, which is what
CC BY and CC BY-SA require.

Run again after editing PHOTOS. Commons throttles bursts, hence the pause between files.

    python3 tools/fetch_photos.py
"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

WIDTH = 960
# Cards crop to a wide box anyway; a tall portrait at full height only grows the APK.
MAX_BOX = (960, 720)
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "photos")
UA = {"User-Agent": "khaoyai-offline-trail-app/1.0 (educational; build-time photo fetch)"}

# asset name -> Commons file title (without the "File:" prefix)
PHOTOS = {
    "hero_khaoyai": "Khao Yai National Park Landscape.JPG",
    # animals
    "elephant": "Wild Asian elephants at Khao Yai NP.JPG",
    "elephant_herd": "Asian elephant herd in Khao Yai National Park.jpg",
    "great_hornbill": "Great Hornbill Buceros bicornis (Linnaeus, 1758) (16353178501).jpg",
    "gibbon": "Hylobates lar at Khao Yai NP.JPG",
    "sambar": "Sambar deer head, Khao Yai, Thailand.jpg",
    "muntjac": "Muntiacus muntjak Khao Yai NP.JPG",
    "macaque": "Macaca leonina mother with baby - Khao Yai.jpg",
    # plants
    "tetrameles": "Tetrameles nudiflora 192589387.jpg",
    "yang_na": "Dipterocarpus alatus.jpg",
    "strangler_fig": "Strangler Fig (Ficus sp.) (7851610862).jpg",
    "tree_fern": "Cyathea sp Daintree National Park Queensland 1986 IMG 0087.jpg",
    "rattan": "Rattan Tree.jpg",
    "wild_orchid": "WildOrchidsThailand flip666.jpg",
    "agarwood": "Aquilaria crassna 1 - Chiang Mai.jpg",
    # trails and places
    "trail_moist_forest": "Khao Yai, Thailand, Tropical moist evergreen forest.jpg",
    "haew_suwat": "Nam Tok Heo Suwat.jpg",
    "haew_suwat_top": "Khao Yai, Thailand, Haew Suwat Waterfall, Top.jpg",
    "nong_phak_chi": "Nong Phak Chi (II).jpg",
    "grassland": "Khao Yai, Thailand, Tropical grasslands.jpg",
    "lake": "Khao Yai, Thailand, Lake in Khao Yai wilderness.jpg",
    "sunset_grassland": "Khao Yai, Thailand, Setting sun over Khao Yai grasslands and trees.jpg",
    "forest_rain": "Khao Yai, Thailand, Khao Yai forest under the rain.jpg",
    "forest_complex": "Khao Yai Forest Complex National Park.jpg",
    "hill_forest": "Khao Yai, Thailand, Hill evergreen forest.jpg",
    "rock_formation": "Khlong Phanom, Limestone rock formations, Surat Thani, Thailand.jpg",
    "black_bear": "หมีควาย.jpg",
}

FREE = re.compile(r"^(CC0|Public domain|CC BY(-SA)? [0-9.]+)", re.I)


def api(params):
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(dict(params, format="json"))
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)


def clean(html):
    return " ".join(re.sub(r"<[^>]+>", " ", html or "").split())


def shrink(path):
    """Fits the photo inside MAX_BOX and re-encodes it, when Pillow is installed."""
    try:
        from PIL import Image
    except ImportError:
        return
    with Image.open(path) as im:
        im = im.convert("RGB")
        im.thumbnail(MAX_BOX)
        im.save(path, "JPEG", quality=80, optimize=True, progressive=True)


def fetch(name, title):
    pages = api({
        "action": "query", "titles": "File:" + title, "prop": "imageinfo",
        "iiprop": "url|extmetadata", "iiurlwidth": WIDTH,
    })["query"]["pages"]
    info = next(iter(pages.values()))["imageinfo"][0]
    meta = info.get("extmetadata", {})
    licence = clean(meta.get("LicenseShortName", {}).get("value"))
    if not FREE.match(licence):
        raise SystemExit(f"{title}: licence '{licence}' is not on the allowed list")
    with urllib.request.urlopen(urllib.request.Request(info["thumburl"], headers=UA), timeout=120) as r:
        data = r.read()
    path = os.path.join(OUT, name + ".jpg")
    with open(path, "wb") as f:
        f.write(data)
    shrink(path)
    return {
        "title": title,
        "author": clean(meta.get("Artist", {}).get("value")) or "ไม่ระบุ",
        "license": licence,
        "licenseUrl": clean(meta.get("LicenseUrl", {}).get("value")),
        "source": info["descriptionurl"],
        "bytes": os.path.getsize(path),
    }


def main():
    os.makedirs(OUT, exist_ok=True)
    credits_path = os.path.join(OUT, "credits.json")
    credits = {}
    if os.path.exists(credits_path):
        credits = json.load(open(credits_path, encoding="utf-8"))
    if sys.argv[1:] == ["--shrink"]:
        for name in PHOTOS:
            path = os.path.join(OUT, name + ".jpg")
            if os.path.exists(path):
                shrink(path)
                credits.get(name, {})["bytes"] = os.path.getsize(path)
        with open(credits_path, "w", encoding="utf-8") as f:
            json.dump(credits, f, ensure_ascii=False, indent=2)
        return
    only = set(sys.argv[1:])
    for name, title in PHOTOS.items():
        if only and name not in only:
            continue
        if name in credits and os.path.exists(os.path.join(OUT, name + ".jpg")) and not only:
            continue
        for attempt in range(4):
            try:
                credits[name] = fetch(name, title)
                print(f"ok   {name:20s} {credits[name]['license']:14s} {credits[name]['bytes'] // 1024} KB")
                break
            except urllib.error.HTTPError as e:
                if e.code != 429 or attempt == 3:
                    raise
                time.sleep(20 * (attempt + 1))
        time.sleep(4)
    credits = {k: credits[k] for k in PHOTOS if k in credits}
    with open(credits_path, "w", encoding="utf-8") as f:
        json.dump(credits, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
