#!/usr/bin/env python3
"""Validate the campus config and GeoJSON data files before committing.

Catches the mistakes that make the blue dot land in the wrong place:
  * bbox / center still left at the 0.0 placeholder
  * latitude and longitude swapped
  * UTM (EPSG:32647) easting/northing pasted in place of WGS84 degrees
  * POIs outside the downloaded map area
  * POIs too far from any walking path to be routable
  * duplicate ids, unknown categories / path types

Usage:  python3 tools/geojson_validate.py
Exit code 0 = clean, 1 = errors found.
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
ASSETS = REPO_ROOT / "app" / "src" / "main" / "assets"
CONFIG_PATH = ASSETS / "config" / "campus_config.json"
POIS_PATH = ASSETS / "data" / "pois.geojson"
PATHS_PATH = ASSETS / "data" / "paths.geojson"

# Keep in sync with docs/DATA_FORMAT.md and the Kotlin PoiCategory / PathType enums.
VALID_CATEGORIES = {
    "gate", "dorm", "academic", "canteen", "sport", "parking", "service", "custom",
}
VALID_PATH_TYPES = {"footway", "road", "crossing", "stairs"}

# Sanity window for Thailand. Only used to catch swapped lat/lon, not to validate the campus.
THAILAND_LAT = (5.0, 21.0)
THAILAND_LON = (97.0, 106.0)

MAX_POI_DISTANCE_TO_PATH_M = 30.0

errors: list[str] = []
warnings: list[str] = []


def error(msg: str) -> None:
    errors.append(msg)


def warn(msg: str) -> None:
    warnings.append(msg)


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Great-circle distance in metres. Mirrors GeoUtils.haversineMeters in the app."""
    r = 6371008.8  # IUGG mean Earth radius, same constant as the Kotlin side
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(min(1.0, math.sqrt(a)))


def distance_to_segment_m(
    plat: float, plon: float,
    alat: float, alon: float,
    blat: float, blon: float,
) -> float:
    """Perpendicular distance from P to segment AB, via a local equirectangular projection.

    Accurate to well under a metre over the sub-kilometre segments used here.
    """
    lat0 = math.radians((alat + blat) / 2.0)
    mx = 111320.0 * math.cos(lat0)  # metres per degree of longitude at this latitude
    my = 110540.0                   # metres per degree of latitude
    ax, ay = alon * mx, alat * my
    bx, by = blon * mx, blat * my
    px, py = plon * mx, plat * my
    dx, dy = bx - ax, by - ay
    seg_len_sq = dx * dx + dy * dy
    if seg_len_sq == 0.0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / seg_len_sq))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def load_json(path: Path) -> dict | None:
    if not path.exists():
        error(f"ไม่พบไฟล์ {path.relative_to(REPO_ROOT)}")
        return None
    try:
        with path.open(encoding="utf-8") as fh:
            return json.load(fh)
    except json.JSONDecodeError as exc:
        error(f"{path.relative_to(REPO_ROOT)} ไม่ใช่ JSON ที่ถูกต้อง: {exc}")
        return None


def check_coordinate(lon: float, lat: float, where: str) -> bool:
    """Return True if the pair looks like a plausible WGS84 lon/lat inside Thailand."""
    if abs(lon) > 180.0 or abs(lat) > 90.0:
        error(
            f"{where}: พิกัดเกินช่วงองศา (lon={lon}, lat={lat}) "
            f"— น่าจะเป็นพิกัด UTM Zone 47N ต้องแปลงเป็น WGS84 ก่อน (ดู docs/ACCURACY.md ข้อ A6)"
        )
        return False
    if not (THAILAND_LAT[0] <= lat <= THAILAND_LAT[1]) or not (THAILAND_LON[0] <= lon <= THAILAND_LON[1]):
        swapped_ok = (
            THAILAND_LAT[0] <= lon <= THAILAND_LAT[1]
            and THAILAND_LON[0] <= lat <= THAILAND_LON[1]
        )
        hint = " — ดูเหมือนสลับ lat/lon กัน GeoJSON ต้องเป็น [lon, lat]" if swapped_ok else ""
        error(f"{where}: พิกัด (lon={lon}, lat={lat}) อยู่นอกประเทศไทย{hint}")
        return False
    return True


# --------------------------------------------------------------------------------------
# 1. campus_config.json
# --------------------------------------------------------------------------------------
def validate_config() -> dict | None:
    config = load_json(CONFIG_PATH)
    if config is None:
        return None

    try:
        bbox = config["bbox"]
        center = config["center"]
        min_lon, min_lat = float(bbox["minLon"]), float(bbox["minLat"])
        max_lon, max_lat = float(bbox["maxLon"]), float(bbox["maxLat"])
        c_lon, c_lat = float(center["lon"]), float(center["lat"])
        min_zoom, max_zoom = int(config["minZoom"]), int(config["maxZoom"])
        style_url = str(config["styleUrl"])
    except (KeyError, TypeError, ValueError) as exc:
        error(f"campus_config.json: โครงสร้างไม่ถูกต้อง ({exc})")
        return None

    if any(v == 0.0 for v in (min_lon, min_lat, max_lon, max_lat, c_lon, c_lat)):
        error(
            "campus_config.json: bbox/center ยังเป็น 0.0 อยู่ "
            "— ต้องกรอกพิกัดจริงก่อนใช้งาน (ดู SETUP.md ข้อ 1)"
        )
        return None

    ok = check_coordinate(min_lon, min_lat, "campus_config.json bbox(min)")
    ok &= check_coordinate(max_lon, max_lat, "campus_config.json bbox(max)")
    ok &= check_coordinate(c_lon, c_lat, "campus_config.json center")
    if not ok:
        return None

    if min_lon >= max_lon:
        error(f"campus_config.json: minLon ({min_lon}) ต้องน้อยกว่า maxLon ({max_lon})")
    if min_lat >= max_lat:
        error(f"campus_config.json: minLat ({min_lat}) ต้องน้อยกว่า maxLat ({max_lat})")
    if not (min_lon <= c_lon <= max_lon and min_lat <= c_lat <= max_lat):
        error("campus_config.json: center อยู่นอก bbox")
    if min_zoom >= max_zoom:
        error(f"campus_config.json: minZoom ({min_zoom}) ต้องน้อยกว่า maxZoom ({max_zoom})")
    if max_zoom > 18:
        error(f"campus_config.json: maxZoom ({max_zoom}) เกิน 18 — ไฟล์จะใหญ่ขึ้นมากโดยไม่ได้รายละเอียดเพิ่ม")
    if "api_key" in style_url.lower() or "access_token" in style_url.lower():
        error("campus_config.json: styleUrl มี API key — โปรเจกต์นี้ต้องใช้ style ที่ไม่ต้องมี key")

    width_km = haversine_m(min_lat, min_lon, min_lat, max_lon) / 1000.0
    height_km = haversine_m(min_lat, min_lon, max_lat, min_lon) / 1000.0
    area = width_km * height_km
    print(f"  พื้นที่ bbox: {width_km:.2f} x {height_km:.2f} กม. = {area:.2f} ตร.กม.")
    if area > 25.0:
        warn(f"bbox กว้าง {area:.1f} ตร.กม. ซึ่งใหญ่กว่าที่ควร แผนที่ออฟไลน์จะกินพื้นที่มาก")
    if area < 0.25:
        warn(f"bbox เล็กแค่ {area:.2f} ตร.กม. อาจไม่ครอบคลุมทั้งวิทยาเขต")

    return config


# --------------------------------------------------------------------------------------
# 2. pois.geojson
# --------------------------------------------------------------------------------------
def validate_pois(config: dict | None) -> list[tuple[str, float, float]]:
    data = load_json(POIS_PATH)
    if data is None:
        return []
    if data.get("type") != "FeatureCollection":
        error("pois.geojson: type ต้องเป็น FeatureCollection")
        return []

    features = data.get("features") or []
    if not features:
        warn("pois.geojson: ยังไม่มีจุดเลย — ใช้ Surveyor Mode เก็บพิกัดก่อน (ดู SETUP.md ข้อ 2)")
        return []

    bbox = config["bbox"] if config else None
    seen_ids: set[str] = set()
    result: list[tuple[str, float, float]] = []

    for i, feature in enumerate(features):
        label = f"pois.geojson feature[{i}]"
        props = feature.get("properties") or {}
        poi_id = props.get("id")
        if not poi_id:
            error(f"{label}: ไม่มี properties.id")
            continue
        label = f"pois.geojson '{poi_id}'"
        if poi_id in seen_ids:
            error(f"{label}: id ซ้ำกับจุดอื่น")
        seen_ids.add(poi_id)

        if not props.get("name"):
            error(f"{label}: ไม่มี properties.name")
        category = props.get("category")
        if category not in VALID_CATEGORIES:
            error(f"{label}: category '{category}' ไม่รองรับ (ต้องเป็นหนึ่งใน {sorted(VALID_CATEGORIES)})")

        geometry = feature.get("geometry") or {}
        if geometry.get("type") != "Point":
            error(f"{label}: geometry ต้องเป็น Point")
            continue
        coords = geometry.get("coordinates") or []
        if len(coords) < 2:
            error(f"{label}: coordinates ต้องมีอย่างน้อย 2 ค่า [lon, lat]")
            continue

        lon, lat = float(coords[0]), float(coords[1])
        if not check_coordinate(lon, lat, label):
            continue

        if bbox and not (
            bbox["minLon"] <= lon <= bbox["maxLon"] and bbox["minLat"] <= lat <= bbox["maxLat"]
        ):
            error(f"{label}: อยู่นอก bbox ของ campus_config.json — แผนที่ออฟไลน์จะไม่ครอบคลุมจุดนี้")

        result.append((str(poi_id), lat, lon))

    print(f"  pois.geojson: {len(result)} จุด")
    return result


# --------------------------------------------------------------------------------------
# 3. paths.geojson
# --------------------------------------------------------------------------------------
def validate_paths(config: dict | None) -> list[list[tuple[float, float]]]:
    data = load_json(PATHS_PATH)
    if data is None:
        return []
    if data.get("type") != "FeatureCollection":
        error("paths.geojson: type ต้องเป็น FeatureCollection")
        return []

    features = data.get("features") or []
    if not features:
        warn("paths.geojson: ยังไม่มีเส้นทางเลย — ใช้ Track Recording บันทึกก่อน (ดู SETUP.md ข้อ 3)")
        return []

    bbox = config["bbox"] if config else None
    seen_ids: set[str] = set()
    lines: list[list[tuple[float, float]]] = []
    total_m = 0.0

    for i, feature in enumerate(features):
        label = f"paths.geojson feature[{i}]"
        props = feature.get("properties") or {}
        path_id = props.get("id")
        if not path_id:
            error(f"{label}: ไม่มี properties.id")
            continue
        label = f"paths.geojson '{path_id}'"
        if path_id in seen_ids:
            error(f"{label}: id ซ้ำกับเส้นอื่น")
        seen_ids.add(path_id)

        path_type = props.get("type")
        if path_type not in VALID_PATH_TYPES:
            error(f"{label}: type '{path_type}' ไม่รองรับ (ต้องเป็นหนึ่งใน {sorted(VALID_PATH_TYPES)})")

        geometry = feature.get("geometry") or {}
        if geometry.get("type") != "LineString":
            error(f"{label}: geometry ต้องเป็น LineString")
            continue
        coords = geometry.get("coordinates") or []
        if len(coords) < 2:
            error(f"{label}: LineString ต้องมีอย่างน้อย 2 จุด")
            continue

        pts: list[tuple[float, float]] = []
        for j, pair in enumerate(coords):
            lon, lat = float(pair[0]), float(pair[1])
            if not check_coordinate(lon, lat, f"{label} จุดที่ {j}"):
                pts = []
                break
            if bbox and not (
                bbox["minLon"] <= lon <= bbox["maxLon"] and bbox["minLat"] <= lat <= bbox["maxLat"]
            ):
                warn(f"{label} จุดที่ {j}: อยู่นอก bbox")
            pts.append((lat, lon))
        if len(pts) < 2:
            continue

        for a, b in zip(pts, pts[1:]):
            total_m += haversine_m(a[0], a[1], b[0], b[1])
        lines.append(pts)

    print(f"  paths.geojson: {len(lines)} เส้น รวมความยาว {total_m / 1000.0:.2f} กม.")
    return lines


# --------------------------------------------------------------------------------------
# 4. cross-check: every POI must be reachable from the path network
# --------------------------------------------------------------------------------------
def validate_poi_reachability(
    pois: list[tuple[str, float, float]],
    lines: list[list[tuple[float, float]]],
) -> None:
    if not pois or not lines:
        return
    worst = 0.0
    for poi_id, lat, lon in pois:
        nearest = min(
            distance_to_segment_m(lat, lon, a[0], a[1], b[0], b[1])
            for line in lines
            for a, b in zip(line, line[1:])
        )
        worst = max(worst, nearest)
        if nearest > MAX_POI_DISTANCE_TO_PATH_M:
            error(
                f"pois.geojson '{poi_id}': ห่างจากเส้นทางเดินที่ใกล้ที่สุด {nearest:.1f} ม. "
                f"(เกิน {MAX_POI_DISTANCE_TO_PATH_M:.0f} ม.) — นำทางไปจุดนี้ไม่ได้ "
                f"ต้องบันทึกเส้นทางเพิ่มหรือย้ายจุดให้ใกล้ทางเดิน"
            )
    print(f"  ระยะจาก POI ถึงทางเดินที่ไกลที่สุด: {worst:.1f} ม.")


def main() -> int:
    print("ตรวจไฟล์ข้อมูลพิกัด…")
    config = validate_config()
    pois = validate_pois(config)
    lines = validate_paths(config)
    validate_poi_reachability(pois, lines)

    print()
    for w in warnings:
        print(f"  [เตือน] {w}")
    for e in errors:
        print(f"  [ผิดพลาด] {e}")

    print()
    if errors:
        print(f"❌ พบข้อผิดพลาด {len(errors)} รายการ — แก้ก่อน commit")
        return 1
    if warnings:
        print(f"✅ ผ่าน (มีคำเตือน {len(warnings)} รายการ)")
    else:
        print("✅ ผ่านทั้งหมด")
    return 0


if __name__ == "__main__":
    sys.exit(main())
