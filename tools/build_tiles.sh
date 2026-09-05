#!/usr/bin/env bash
#
# build_tiles.sh - generate the bundled MBTiles pack for TileSourceMode.BUNDLED.
#
# Only needed if you want the app to work without EVER touching the network, or as a
# fallback in case OpenFreeMap goes away. The default TileSourceMode.OFFLINE_PACK mode
# does not need this script at all.
#
# Requirements (Linux / macOS / WSL):
#   wget, osmium-tool        -> apt install wget osmium-tool
#   tilemaker                -> https://tilemaker.org/
#
# Run from the repository root:  bash tools/build_tiles.sh

set -euo pipefail

# ---------------------------------------------------------------------------
# EDIT THIS to match the bbox you put in assets/config/campus_config.json.
# Order is: minLon,minLat,maxLon,maxLat   (longitude first - same order as OSM Export)
# ---------------------------------------------------------------------------
BBOX="${BBOX:-}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG_JSON="$REPO_ROOT/app/src/main/assets/config/campus_config.json"
ASSETS_MAP_DIR="$REPO_ROOT/app/src/main/assets/map"
WORK_DIR="$REPO_ROOT/tools/.tilebuild"

THAILAND_PBF="$WORK_DIR/thailand-latest.osm.pbf"
CAMPUS_PBF="$WORK_DIR/kmutnb-prachin.osm.pbf"
OUT_MBTILES="$ASSETS_MAP_DIR/kmutnb.mbtiles"

die() { echo "ERROR: $*" >&2; exit 1; }

# --- Derive the bbox from campus_config.json unless it was passed in -------------------
if [[ -z "$BBOX" ]]; then
  [[ -f "$CONFIG_JSON" ]] || die "ไม่พบ $CONFIG_JSON"
  command -v python3 >/dev/null || die "ต้องมี python3 เพื่ออ่าน bbox จาก campus_config.json (หรือส่ง BBOX=... มาเอง)"
  BBOX="$(python3 - "$CONFIG_JSON" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as fh:
    box = json.load(fh)["bbox"]
vals = [box["minLon"], box["minLat"], box["maxLon"], box["maxLat"]]
if any(v == 0.0 for v in vals):
    sys.exit("bbox ใน campus_config.json ยังเป็น 0.0 - กรุณากรอกก่อน (ดู SETUP.md ข้อ 1)")
print(",".join(str(v) for v in vals))
PY
)" || die "อ่าน bbox ไม่สำเร็จ"
fi
echo "==> bbox: $BBOX"

for tool in wget osmium tilemaker; do
  command -v "$tool" >/dev/null || die "ไม่พบคำสั่ง '$tool' - ดูวิธีติดตั้งใน SETUP.md ข้อ 4"
done

mkdir -p "$WORK_DIR" "$ASSETS_MAP_DIR"

# --- 1) Thailand extract from Geofabrik (~700 MB, resumable, downloaded once) ----------
echo "==> [1/4] ดาวน์โหลด OSM extract ประเทศไทย (ข้ามถ้ามีอยู่แล้ว)"
wget -c -O "$THAILAND_PBF" https://download.geofabrik.de/asia/thailand-latest.osm.pbf

# --- 2) Cut out just the campus -------------------------------------------------------
echo "==> [2/4] ตัดเฉพาะพื้นที่วิทยาเขต"
osmium extract --bbox "$BBOX" "$THAILAND_PBF" --output "$CAMPUS_PBF" --overwrite

# --- 3) OSM -> vector MBTiles (OpenMapTiles schema, matches the Liberty style) ---------
echo "==> [3/4] แปลงเป็น vector MBTiles"
TILEMAKER_RESOURCES="${TILEMAKER_RESOURCES:-/usr/share/tilemaker/resources}"
[[ -d "$TILEMAKER_RESOURCES" ]] || die "ไม่พบ tilemaker resources ที่ $TILEMAKER_RESOURCES - ตั้ง TILEMAKER_RESOURCES=... ให้ชี้ไปที่โฟลเดอร์ resources ของ tilemaker"

tilemaker \
  --input  "$CAMPUS_PBF" \
  --output "$OUT_MBTILES" \
  --process "$TILEMAKER_RESOURCES/process-openmaptiles.lua" \
  --config  "$TILEMAKER_RESOURCES/config-openmaptiles.json"

echo "==> สร้าง $OUT_MBTILES แล้ว ($(du -h "$OUT_MBTILES" | cut -f1))"

# --- 4) Fonts + sprite: manual step ---------------------------------------------------
echo "==> [4/4] ตรวจฟอนต์และ sprite"
MISSING=0
if [[ ! -d "$ASSETS_MAP_DIR/fonts" ]]; then
  MISSING=1
  cat <<'EOF'

  ยังไม่มี glyph ฟอนต์ ต้องทำเองครั้งเดียว:

    1) โคลน https://github.com/openmaptiles/fonts
    2) npm install && node ./generate.js
    3) ก็อปโฟลเดอร์ฟอนต์ที่ "มีอักษรไทย" มาวางที่ app/src/main/assets/map/fonts/
       ต้องมีอย่างน้อย fontstack ที่ style เรียกใช้ เช่น "Noto Sans Regular"
       ตรวจชื่อ fontstack ได้จากฟิลด์ "text-font" ใน style.json

  ถ้าไม่มีฟอนต์ ตัวหนังสือบนแผนที่จะหายหมดในโหมด BUNDLED
EOF
fi
if [[ ! -f "$ASSETS_MAP_DIR/sprite.json" ]]; then
  MISSING=1
  echo "  ยังไม่มี sprite.json / sprite.png - ดาวน์โหลดจาก style ที่ใช้แล้ววางที่ $ASSETS_MAP_DIR/"
fi
if [[ ! -f "$ASSETS_MAP_DIR/style.json" ]]; then
  MISSING=1
  echo "  ยังไม่มี style.json - บันทึกจาก https://tiles.openfreemap.org/styles/liberty มาวางที่ $ASSETS_MAP_DIR/"
  echo "  (LocalTileServer จะ rewrite URL ข้างในให้ชี้มาที่ 127.0.0.1 เองตอน runtime)"
fi

if [[ "$MISSING" -eq 0 ]]; then
  echo "==> เสร็จเรียบร้อย เปิดใช้ได้ที่ ตั้งค่า -> แหล่งแผนที่ -> ใช้แผนที่ที่ฝังมากับแอป"
else
  echo "==> MBTiles เสร็จแล้ว แต่ยังขาดไฟล์ข้างบน โหมด BUNDLED จะยังใช้ไม่ได้จนกว่าจะครบ"
fi
