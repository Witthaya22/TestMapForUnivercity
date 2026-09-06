#!/usr/bin/env python3
"""Seed campus_config.json, pois.geojson and paths.geojson from OpenStreetMap.

Why this exists
---------------
The app ships with no coordinates on purpose (see SETUP.md): guessed coordinates put the
blue dot in the wrong place, and coordinates copied out of Google Maps sit on a different
grid from the OSM-derived basemap the app renders (docs/ACCURACY.md, A1).

OpenStreetMap is the one source that does not have that problem, because it is the same
data the basemap is drawn from. So this script pulls the campus outline, its road network
and its named buildings straight from OSM and writes them in the app's own schema.

What comes out is a starting point, not a survey. The OSM campus outline carries
"note=Outline completly estimated." and building footprints are traced from imagery, so
every coordinate here can be off by a few metres. Surveyor Mode in the app is still how a
point is made exact - this script only means the app is usable on day one instead of after
a full field survey.

Usage
-----
    python3 tools/osm_import.py                 # fetch (cached) and write the three files
    python3 tools/osm_import.py --margin 300    # widen the offline download area
    python3 tools/osm_import.py --refresh       # ignore the cache and re-query Overpass
    python3 tools/osm_import.py --dry-run       # print the summary, write nothing

Then always run:  python3 tools/geojson_validate.py
"""

from __future__ import annotations

import argparse
import json
import math
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
ASSETS = REPO_ROOT / "app" / "src" / "main" / "assets"
CONFIG_PATH = ASSETS / "config" / "campus_config.json"
POIS_PATH = ASSETS / "data" / "pois.geojson"
PATHS_PATH = ASSETS / "data" / "paths.geojson"
CACHE_DIR = Path(__file__).resolve().parent / ".osm_cache"

OVERPASS_URL = "https://overpass-api.de/api/interpreter"
USER_AGENT = "kmutnb-prachin-map/1.0 (+https://github.com/Witthaya22/TestMapForUnivercity)"

# way/518190351 = the campus outline, amenity=university, addr 129 / เนินหอม /
# เมืองปราจีนบุรี / 25230, wikidata Q29426484. Matches the official address published at
# https://www.kmutnb.ac.th/contact-us.aspx.
CAMPUS_WAY_ID = 518190351

EARTH_R = 6371008.8  # same constant as GeoUtils.kt and geojson_validate.py

# How far outside the campus outline to keep roads, and to extend the offline download box.
# Deliberately small: the download has to cover the campus, not the whole subdistrict.
DEFAULT_MARGIN_M = 200.0

# Shortest kept piece of road. Below this a fragment carries no routing value.
MIN_SEGMENT_M = 8.0

# ---------------------------------------------------------------------------------------
# highway=* -> PathType.id  (navigation/model/WalkPath.kt)
#
# The campus is crossed by service roads, not by a footpath network - OSM holds 214 service
# ways inside the outline against 41 footways. Routing therefore runs over roads as well as
# footways: "front gate to the IT building" is a walk along a driveable road for most of its
# length. Only genuinely indoor ways are dropped (see is_indoor below).
# ---------------------------------------------------------------------------------------
HIGHWAY_TO_PATH_TYPE = {
    "motorway": "road",
    "trunk": "road",
    "primary": "road",
    "secondary": "road",
    "tertiary": "road",
    "unclassified": "road",
    "residential": "road",
    "living_street": "road",
    "service": "road",
    "track": "road",
    "road": "road",
    "motorway_link": "road",
    "trunk_link": "road",
    "primary_link": "road",
    "secondary_link": "road",
    "tertiary_link": "road",
    "footway": "footway",
    "path": "footway",
    "pedestrian": "footway",
    "cycleway": "footway",
    "bridleway": "footway",
    "steps": "stairs",
    "crossing": "crossing",
}

# Ways that exist inside a building. The app navigates with GPS, which does not work
# indoors at all (docs/ACCURACY.md, "ข้อจำกัดที่แก้ไม่ได้"), so routing through them would
# promise something the hardware cannot deliver.
SKIP_INDOOR_HIGHWAYS = {"corridor", "elevator"}


def is_indoor(tags: dict) -> bool:
    if tags.get("highway") in SKIP_INDOOR_HIGHWAYS:
        return True
    if tags.get("indoor") not in (None, "no"):
        return True
    # `level` on a way means one specific storey of a building.
    return "level" in tags


def path_type_for(tags: dict) -> str | None:
    """PathType.id for an OSM way, or None when it must not enter the routing graph."""
    highway = tags.get("highway")
    if highway is None or is_indoor(tags):
        return None
    # A footway tagged as a crossing costs extra in the router, so classify it first.
    if tags.get("footway") == "crossing" or tags.get("path") == "crossing":
        return "crossing"
    return HIGHWAY_TO_PATH_TYPE.get(highway)


# ---------------------------------------------------------------------------------------
# OSM tags -> PoiCategory.id  (data/model/Poi.kt). First match wins.
# ---------------------------------------------------------------------------------------
CATEGORY_RULES: list[tuple[str, str, str]] = [
    ("barrier", "gate", "gate"),
    ("entrance", "main", "gate"),
    ("amenity", "dormitory", "dorm"),
    ("building", "dormitory", "dorm"),
    ("building", "apartments", "dorm"),
    ("tourism", "hotel", "dorm"),
    ("amenity", "restaurant", "canteen"),
    ("amenity", "cafe", "canteen"),
    ("amenity", "fast_food", "canteen"),
    ("amenity", "food_court", "canteen"),
    ("amenity", "canteen", "canteen"),
    ("shop", "convenience", "canteen"),
    ("shop", "supermarket", "canteen"),
    ("amenity", "parking", "parking"),
    ("amenity", "library", "service"),
    ("amenity", "clinic", "service"),
    ("amenity", "hospital", "service"),
    ("amenity", "pharmacy", "service"),
    ("amenity", "bank", "service"),
    ("amenity", "atm", "service"),
    ("amenity", "post_office", "service"),
    ("amenity", "police", "service"),
    ("amenity", "place_of_worship", "service"),
    ("amenity", "toilets", "service"),
    ("amenity", "university", "academic"),
    ("amenity", "college", "academic"),
    ("amenity", "school", "academic"),
    ("amenity", "research_institute", "academic"),
    ("building", "university", "academic"),
    ("building", "college", "academic"),
    ("building", "school", "academic"),
]
LEISURE_SPORT = {
    "sports_centre", "sports_hall", "stadium", "pitch", "track",
    "fitness_centre", "swimming_pool", "sports_complex",
}


def category_for(tags: dict) -> str:
    if tags.get("leisure") in LEISURE_SPORT or tags.get("amenity") == "sports_centre":
        return "sport"
    for key, value, category in CATEGORY_RULES:
        if tags.get(key) == value:
            return category
    if tags.get("office") or tags.get("amenity") or tags.get("shop"):
        return "service"
    if tags.get("building"):
        return "academic"
    return "custom"


# Gates are what F4 calls "หน้ามอ 1-5". OSM has exactly one barrier=gate node inside this
# campus and it carries no name, so the gates are found geometrically instead: a gate is
# where a road crosses the campus outline. Numbering follows how many ways meet there, so
# gate 1 is the busiest entrance. The names and the exact positions still have to come from
# a survey, which is why every generated gate carries needsSurvey.
GATE_NAME_TH = "ประตูทางเข้า"

# Crossings closer together than this are one gate: a divided entrance road, or a gate with
# a footway beside it, produces several crossings a few metres apart.
GATE_CLUSTER_RADIUS_M = 45.0

# A barrier=gate node this close to a crossing describes the same gate, and its coordinate is
# better than the crossing point because a mapper placed it deliberately.
GATE_NODE_MATCH_M = 60.0


# Starter text for the arrival sheet (F7), which pops up with a vibration when a walker
# reaches a waypoint. An empty description makes that sheet say "ยังไม่มีรายละเอียด", which
# is a worse first impression than a plain sentence saying what kind of place this is. Every
# one of these is editable in the app, and that is the point: the seed is a placeholder the
# person who actually knows the campus replaces.
CATEGORY_DESCRIPTION_TH = {
    "gate": "ประตูทางเข้า-ออกวิทยาเขต",
    "academic": "อาคารเรียน / สำนักงานคณะ",
    "canteen": "จุดขายอาหารและเครื่องดื่ม",
    "dorm": "ที่พักอาศัย",
    "sport": "สนามกีฬา / อาคารกีฬา",
    "service": "อาคารบริการ",
    "parking": "ที่จอดรถ",
    "custom": "",
}

# Where the generic line is not good enough to be worth shipping.
TAG_DESCRIPTION_TH = {
    ("amenity", "place_of_worship"): "จุดสักการะประจำวิทยาเขต คนมักแวะมาขอพรก่อนเข้าเรียนหรือก่อนสอบ",
    ("amenity", "library"): "ห้องสมุด มีที่นั่งอ่านหนังสือและยืม-คืนหนังสือ",
    ("amenity", "restaurant"): "โรงอาหาร",
    ("shop", "convenience"): "ร้านสะดวกซื้อ",
    ("amenity", "parking"): "ลานจอดรถ",
}


def starter_description(tags: dict, category: str) -> str:
    for (key, value), text in TAG_DESCRIPTION_TH.items():
        if tags.get(key) == value:
            return text
    return CATEGORY_DESCRIPTION_TH.get(category, "")


# Order the POI list is presented in: gates first, then the places people head for.
CATEGORY_ORDER = {
    "gate": 0, "academic": 1, "canteen": 2, "dorm": 3,
    "sport": 4, "service": 5, "parking": 6, "custom": 7,
}


# ---------------------------------------------------------------------------------------
# Geometry. Local equirectangular projection, matching geojson_validate.py so the two tools
# never disagree about a distance.
# ---------------------------------------------------------------------------------------
DEG_M = math.pi * EARTH_R / 180.0


def project(lat: float, lon: float, lat0: float) -> tuple[float, float]:
    return lon * DEG_M * math.cos(math.radians(lat0)), lat * DEG_M


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = math.radians(lat2 - lat1), math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * EARTH_R * math.asin(min(1.0, math.sqrt(a)))


def point_in_ring(x: float, y: float, ring: list[tuple[float, float]]) -> bool:
    """Ray casting. `ring` is a list of projected (x, y), implicitly closed."""
    inside = False
    count = len(ring)
    for i in range(count):
        x1, y1 = ring[i]
        x2, y2 = ring[(i + 1) % count]
        if (y1 > y) != (y2 > y):
            crossing_x = x1 + (y - y1) * (x2 - x1) / (y2 - y1)
            if x < crossing_x:
                inside = not inside
    return inside


def dist_to_segment(px, py, ax, ay, bx, by) -> float:
    dx, dy = bx - ax, by - ay
    length_sq = dx * dx + dy * dy
    if length_sq == 0.0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length_sq))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


class CampusArea:
    """The campus outline, plus a metric buffer around it."""

    def __init__(self, geometry: list[dict], margin_m: float):
        lats = [p["lat"] for p in geometry]
        lons = [p["lon"] for p in geometry]
        self.lat0 = sum(lats) / len(lats)
        self.min_lat, self.max_lat = min(lats), max(lats)
        self.min_lon, self.max_lon = min(lons), max(lons)
        self.margin_m = margin_m
        self.ring = [project(p["lat"], p["lon"], self.lat0) for p in geometry]

    def contains(self, lat: float, lon: float) -> bool:
        """True inside the outline, or within margin_m of it."""
        x, y = project(lat, lon, self.lat0)
        if point_in_ring(x, y, self.ring):
            return True
        count = len(self.ring)
        for i in range(count):
            ax, ay = self.ring[i]
            bx, by = self.ring[(i + 1) % count]
            if dist_to_segment(x, y, ax, ay, bx, by) <= self.margin_m:
                return True
        return False

    def margin_degrees(self) -> tuple[float, float]:
        return (self.margin_m / DEG_M,
                self.margin_m / (DEG_M * math.cos(math.radians(self.lat0))))

    def query_bbox(self) -> tuple[float, float, float, float]:
        """south, west, north, east widened by the margin - Overpass bbox order."""
        dlat, dlon = self.margin_degrees()
        return (self.min_lat - dlat, self.min_lon - dlon,
                self.max_lat + dlat, self.max_lon + dlon)

    def width_height_m(self) -> tuple[float, float]:
        width = haversine_m(self.lat0, self.min_lon, self.lat0, self.max_lon)
        height = haversine_m(self.min_lat, self.min_lon, self.max_lat, self.min_lon)
        return width, height


# ---------------------------------------------------------------------------------------
# Overpass access
# ---------------------------------------------------------------------------------------
def overpass(query: str, cache_key: str, refresh: bool) -> dict:
    CACHE_DIR.mkdir(exist_ok=True)
    cached = CACHE_DIR / f"{cache_key}.json"
    if cached.exists() and not refresh:
        print(f"  cache hit: {cached.relative_to(REPO_ROOT)}")
        return json.loads(cached.read_text(encoding="utf-8"))

    print(f"  querying Overpass ({cache_key}) ...")
    request = urllib.request.Request(
        OVERPASS_URL,
        data=query.encode("utf-8"),
        headers={"User-Agent": USER_AGENT, "Content-Type": "text/plain; charset=utf-8"},
    )
    payload = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=180) as response:
                payload = response.read().decode("utf-8")
            break
        except (urllib.error.URLError, TimeoutError) as exc:
            if attempt == 2:
                sys.exit(f"Overpass ล้มเหลว: {exc}")
            print(f"  retry {attempt + 1}/2 after {exc}")
            time.sleep(5)
    cached.write_text(payload, encoding="utf-8")
    return json.loads(payload)


def fetch_campus_outline(refresh: bool) -> dict:
    data = overpass(
        f"[out:json][timeout:120];way({CAMPUS_WAY_ID});out geom tags;",
        "campus_outline", refresh,
    )
    ways = [e for e in data["elements"] if e["type"] == "way"]
    if not ways:
        sys.exit(f"ไม่พบ way/{CAMPUS_WAY_ID} ใน OSM - อาจถูกลบหรือเปลี่ยน id")
    return ways[0]


def fetch_highways(area: CampusArea, refresh: bool) -> list[dict]:
    south, west, north, east = area.query_bbox()
    box = f"{south:.6f},{west:.6f},{north:.6f},{east:.6f}"
    query = f'[out:json][timeout:180];way["highway"]({box});out geom tags;'
    return overpass(query, "highways", refresh)["elements"]


def fetch_places(area: CampusArea, refresh: bool) -> list[dict]:
    south, west, north, east = area.query_bbox()
    box = f"{south:.6f},{west:.6f},{north:.6f},{east:.6f}"
    query = f"""[out:json][timeout:180];
(
  nwr["building"]({box});
  nwr["amenity"]({box});
  nwr["leisure"]({box});
  nwr["shop"]({box});
  nwr["office"]({box});
  nwr["tourism"]({box});
  nwr["barrier"="gate"]({box});
);
out center tags;"""
    return overpass(query, "places", refresh)["elements"]


# ---------------------------------------------------------------------------------------
# Conversion
# ---------------------------------------------------------------------------------------
def clip_way(geometry: list[dict], area: CampusArea) -> list[list[dict]]:
    """Split an OSM way into the runs of it that lie inside the campus buffer.

    A segment is kept when either end is inside, so a way that only clips a corner still
    contributes the piece that reaches the boundary. Node coordinates are passed through
    untouched: ways that share an OSM node keep identical coordinates, which is what lets
    RouteGraphBuilder weld them into one connected graph at junctions.
    """
    inside = [area.contains(p["lat"], p["lon"]) for p in geometry]
    runs: list[list[dict]] = []
    current: list[dict] = []
    for i in range(len(geometry) - 1):
        if inside[i] or inside[i + 1]:
            if not current:
                current = [geometry[i]]
            current.append(geometry[i + 1])
        elif current:
            runs.append(current)
            current = []
    if current:
        runs.append(current)
    return runs


def run_length_m(points: list[dict]) -> float:
    return sum(
        haversine_m(points[i]["lat"], points[i]["lon"], points[i + 1]["lat"], points[i + 1]["lon"])
        for i in range(len(points) - 1)
    )


def build_paths(highways: list[dict], area: CampusArea) -> tuple[list[dict], dict]:
    features: list[dict] = []
    stats: dict[str, int] = {}
    dropped_indoor = 0
    for way in highways:
        tags = way.get("tags", {})
        geometry = way.get("geometry")
        if not geometry or len(geometry) < 2:
            continue
        if tags.get("highway") and is_indoor(tags):
            dropped_indoor += 1
            continue
        path_type = path_type_for(tags)
        if path_type is None:
            continue

        oneway = tags.get("oneway") in ("yes", "true", "1", "-1")
        lit = tags.get("lit") not in (None, "no")
        covered = tags.get("covered") not in (None, "no") or tags.get("tunnel") not in (None, "no")
        name = tags.get("name:th") or tags.get("name")

        for index, run in enumerate(clip_way(geometry, area)):
            if len(run) < 2 or run_length_m(run) < MIN_SEGMENT_M:
                continue
            properties = {
                "id": f"osm_w{way['id']}_{index}",
                "type": path_type,
                "oneway": oneway,
                "lit": lit,
                "covered": covered,
                "source": f"osm:way/{way['id']}",
                "osmHighway": tags.get("highway"),
            }
            if name:
                properties["name"] = name
            features.append({
                "type": "Feature",
                "geometry": {
                    "type": "LineString",
                    "coordinates": [[round(p["lon"], 7), round(p["lat"], 7)] for p in run],
                },
                "properties": properties,
            })
            stats[path_type] = stats.get(path_type, 0) + 1
    stats["_dropped_indoor"] = dropped_indoor
    return features, stats


# A way that stops this close to another way is drawn as ending there, not as a dead end -
# OSM contributors routinely leave a service road a few metres short of the road it meets.
# Above this the gap is more likely to be real (a fence, a ditch) than a mapping shortcut.
MAX_STITCH_GAP_M = 12.0


def stitch_network(features: list[dict], lat0: float, max_gap_m: float = MAX_STITCH_GAP_M) -> int:
    """Join way ends that stop just short of another way.

    RouteGraphBuilder welds vertices that are within 1.5 m of each other, which is the right
    tolerance for GPS traces but not for OSM: a footway drawn 6 m short of the road it joins
    stays a separate component, and A* then reports "no route" for a walk anyone can make.

    For every loose end within `max_gap_m` of another line this inserts the projection point
    as a real vertex of that line and adds a short connector between the two, so both sides
    share an identical coordinate and the builder merges them into one junction.

    Returns the number of connectors added. The connectors are synthetic geometry and are
    tagged as such in their properties.
    """
    projected: list[list[tuple[float, float]]] = [
        [project(lat, lon, lat0) for lon, lat in f["geometry"]["coordinates"]]
        for f in features
    ]
    connectors: list[dict] = []
    # Vertices to insert afterwards: feature index -> list of (segment index, x, y, lon, lat)
    insertions: dict[int, list[tuple[int, float, float]]] = {}

    for i, line in enumerate(projected):
        for end_index in (0, len(line) - 1):
            ex, ey = line[end_index]
            best = None  # (distance, target feature, segment index, x, y)
            for j, other in enumerate(projected):
                if j == i:
                    continue
                for k in range(len(other) - 1):
                    ax, ay = other[k]
                    bx, by = other[k + 1]
                    dx, dy = bx - ax, by - ay
                    length_sq = dx * dx + dy * dy
                    if length_sq == 0.0:
                        continue
                    t = max(0.0, min(1.0, ((ex - ax) * dx + (ey - ay) * dy) / length_sq))
                    px, py = ax + t * dx, ay + t * dy
                    distance = math.hypot(ex - px, ey - py)
                    if distance <= max_gap_m and (best is None or distance < best[0]):
                        best = (distance, j, k, px, py)
            if best is None or best[0] <= 0.5:
                continue  # already touching, or as good as
            distance, target, segment, px, py = best
            insertions.setdefault(target, []).append((segment, px, py))
            connectors.append({
                "_from": (i, end_index),
                "_to": (px, py),
                "_len": distance,
            })

    if not connectors:
        return 0

    def unproject(x: float, y: float) -> tuple[float, float]:
        lat = y / DEG_M
        lon = x / (DEG_M * math.cos(math.radians(lat0)))
        return round(lon, 7), round(lat, 7)

    # Insert the new vertices back-to-front so earlier indices stay valid.
    for index, items in insertions.items():
        coordinates = features[index]["geometry"]["coordinates"]
        for segment, px, py in sorted(items, key=lambda item: -item[0]):
            coordinates.insert(segment + 1, list(unproject(px, py)))

    for number, connector in enumerate(connectors, start=1):
        source_index, end_index = connector["_from"]
        start = features[source_index]["geometry"]["coordinates"][
            0 if end_index == 0 else -1
        ]
        end = list(unproject(*connector["_to"]))
        features.append({
            "type": "Feature",
            "geometry": {"type": "LineString", "coordinates": [list(start), end]},
            "properties": {
                "id": f"stitch_{number:03d}",
                "type": "footway",
                "oneway": False,
                "lit": False,
                "covered": False,
                "synthetic": True,
                "source": "osm_import.py:stitch",
                "lengthM": round(connector["_len"], 1),
            },
        })
    return len(connectors)


# How far apart consecutive vertices may sit. OSM draws a straight road as two points
# hundreds of metres apart, which is correct geometry but useless as a graph: a route can
# only start, end or deviate at a vertex, so a walker standing halfway along one has no
# nearby node to be attached to. GPS traces from the in-app recorder are already denser
# than this, so densifying only ever affects imported ways.
MAX_VERTEX_SPACING_M = 20.0


def densify(features: list[dict], lat0: float, max_spacing_m: float = MAX_VERTEX_SPACING_M) -> int:
    """Insert intermediate vertices so no segment is longer than `max_spacing_m`.

    Endpoints are never moved and no existing vertex is dropped, so ways that share an OSM
    node still share an identical coordinate and RouteGraphBuilder still welds them.

    Returns the number of vertices added.
    """
    added = 0
    for feature in features:
        coordinates = feature["geometry"]["coordinates"]
        dense: list[list[float]] = [coordinates[0]]
        for (lon1, lat1), (lon2, lat2) in zip(coordinates, coordinates[1:]):
            length = haversine_m(lat1, lon1, lat2, lon2)
            steps = int(math.ceil(length / max_spacing_m))
            for step in range(1, steps):
                t = step / steps
                dense.append([
                    round(lon1 + (lon2 - lon1) * t, 7),
                    round(lat1 + (lat2 - lat1) * t, 7),
                ])
                added += 1
            dense.append([lon2, lat2])
        feature["geometry"]["coordinates"] = dense
    return added


def largest_component_only(features: list[dict], lat0: float, merge_m: float = 1.5) -> int:
    """Drop lines that are not reachable from the main network.

    A stub that no route can enter is worse than absent: it makes the app claim a path
    exists where the graph cannot use it, and it fails the connectivity test that guards
    against exactly this. Returns the number of features dropped.
    """
    if not features:
        return 0

    node_of: dict[tuple[int, int], list[int]] = {}
    parent: list[int] = []

    def find(a: int) -> int:
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    def union(a: int, b: int) -> None:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb

    positions: list[tuple[float, float]] = []

    def node_for(lon: float, lat: float) -> int:
        x, y = project(lat, lon, lat0)
        cx, cy = int(x // merge_m), int(y // merge_m)
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for candidate in node_of.get((cx + dx, cy + dy), ()):
                    px, py = positions[candidate]
                    if math.hypot(px - x, py - y) <= merge_m:
                        return candidate
        positions.append((x, y))
        index = len(positions) - 1
        node_of.setdefault((cx, cy), []).append(index)
        parent.append(index)
        return index

    feature_nodes: list[list[int]] = []
    for feature in features:
        ids = [node_for(lon, lat) for lon, lat in feature["geometry"]["coordinates"]]
        feature_nodes.append(ids)
        for a, b in zip(ids, ids[1:]):
            union(a, b)

    sizes: dict[int, int] = {}
    for index in range(len(positions)):
        root = find(index)
        sizes[root] = sizes.get(root, 0) + 1
    main = max(sizes, key=lambda r: sizes[r])

    kept = [f for f, ids in zip(features, feature_nodes) if find(ids[0]) == main]
    dropped = len(features) - len(kept)
    features[:] = kept
    return dropped


def segment_intersection(p1, p2, p3, p4):
    """Intersection of segments p1-p2 and p3-p4 in projected metres, or None."""
    denominator = (p2[0] - p1[0]) * (p4[1] - p3[1]) - (p2[1] - p1[1]) * (p4[0] - p3[0])
    if abs(denominator) < 1e-12:
        return None
    t = ((p3[0] - p1[0]) * (p4[1] - p3[1]) - (p3[1] - p1[1]) * (p4[0] - p3[0])) / denominator
    u = ((p3[0] - p1[0]) * (p2[1] - p1[1]) - (p3[1] - p1[1]) * (p2[0] - p1[0])) / denominator
    if 0.0 <= t <= 1.0 and 0.0 <= u <= 1.0:
        return p1[0] + t * (p2[0] - p1[0]), p1[1] + t * (p2[1] - p1[1])
    return None


def find_gates(highways: list[dict], places: list[dict], area: CampusArea) -> list[dict]:
    """Locate the campus entrances: the points where a road crosses the outline.

    Far more reliable than looking for barrier=gate, which almost nobody tags. Crossings are
    clustered because one entrance usually produces several - the road in, the road out, and
    the footway alongside. Where a mapped gate node sits next to a cluster, its coordinate
    wins, because someone put it there on purpose.

    Each gate is returned as a POI feature. The positions are approximate by construction:
    the outline itself is tagged `note=Outline completly estimated.`, so a gate can only be
    as accurate as the fence line it was derived from. That is what needsSurvey is for, and
    the app can move any POI onto a real GPS reading.
    """
    crossings: list[tuple[tuple[float, float], str]] = []
    for way in highways:
        tags = way.get("tags", {})
        geometry = way.get("geometry")
        if not geometry or len(geometry) < 2 or path_type_for(tags) is None:
            continue
        points = [project(q["lat"], q["lon"], area.lat0) for q in geometry]
        for i in range(len(points) - 1):
            for j in range(len(area.ring)):
                hit = segment_intersection(
                    points[i], points[i + 1],
                    area.ring[j], area.ring[(j + 1) % len(area.ring)],
                )
                if hit is not None:
                    crossings.append((hit, tags.get("highway", "?")))

    clusters: list[dict] = []
    for point, highway in crossings:
        for cluster in clusters:
            if math.hypot(point[0] - cluster["point"][0],
                          point[1] - cluster["point"][1]) <= GATE_CLUSTER_RADIUS_M:
                cluster["ways"].append(highway)
                break
        else:
            clusters.append({"point": point, "ways": [highway], "node": None})

    # A mapped gate node beats a computed crossing.
    for element in places:
        if element.get("tags", {}).get("barrier") != "gate":
            continue
        center = element_center(element)
        if center is None:
            continue
        node_point = project(center[0], center[1], area.lat0)
        for cluster in clusters:
            if math.hypot(node_point[0] - cluster["point"][0],
                          node_point[1] - cluster["point"][1]) <= GATE_NODE_MATCH_M:
                cluster["point"] = node_point
                cluster["node"] = element
                break

    # Busiest entrance first, so "ประตูทางเข้า 1" is the main gate.
    clusters.sort(key=lambda c: (-len(c["ways"]), c["point"][0]))

    features = []
    for number, cluster in enumerate(clusters, start=1):
        lat = cluster["point"][1] / DEG_M
        lon = cluster["point"][0] / (DEG_M * math.cos(math.radians(area.lat0)))
        node = cluster["node"]
        kinds = ", ".join(sorted(set(cluster["ways"])))
        features.append({
            "type": "Feature",
            "geometry": {"type": "Point", "coordinates": [round(lon, 7), round(lat, 7)]},
            "properties": {
                "id": f"gate_{number}",
                "name": f"{GATE_NAME_TH} {number}",
                "category": "gate",
                "order": number,
                "description": f"ทางเข้า-ออกวิทยาเขต ({kinds}) มีถนนตัดผ่านรั้ว {len(cluster['ways'])} เส้น",
                "note": "",
                "isUserCreated": False,
                "source": (f"osm:node/{node['id']}" if node
                           else "osm_import.py:boundary-crossing"),
                "needsSurvey": True,
            },
        })
    return features


def element_center(element: dict) -> tuple[float, float] | None:
    center = element.get("center")
    if center:
        return center["lat"], center["lon"]
    if element.get("lat") is not None:
        return element["lat"], element["lon"]
    return None


def build_pois(
    places: list[dict],
    area: CampusArea,
    campus_way_id: int,
    gate_count: int = 0,
) -> tuple[list[dict], dict]:
    candidates = []
    for element in places:
        if element["type"] == "way" and element["id"] == campus_way_id:
            continue  # the campus outline itself is not a destination
        tags = element.get("tags", {})
        if is_indoor(tags):
            continue  # a storey inside a building cannot be reached by GPS navigation
        if tags.get("barrier") == "gate":
            continue  # gates are located by find_gates, from where roads cross the outline
        name = tags.get("name:th") or tags.get("name")
        if not name:
            continue  # an unnamed footprint is not something a user can pick from a list
        center = element_center(element)
        if center is None or not area.contains(*center):
            continue
        candidates.append((element, tags, name, center))

    # A relation and its outer way often carry the same name and nearly the same centre.
    # Keep one per name, preferring the relation because its centroid covers the whole site.
    by_name: dict[str, tuple] = {}
    for entry in candidates:
        element, tags, name, center = entry
        existing = by_name.get(name)
        if existing is None or (existing[0]["type"] != "relation" and element["type"] == "relation"):
            by_name[name] = entry

    features: list[dict] = []
    stats: dict[str, int] = {}
    ordered = sorted(
        by_name.values(),
        key=lambda e: (CATEGORY_ORDER.get(category_for(e[1]), 9), e[2]),
    )
    for order, (element, tags, name, (lat, lon)) in enumerate(ordered, start=gate_count + 1):
        category = category_for(tags)
        prefix = {"node": "n", "way": "w", "relation": "r"}[element["type"]]
        description_bits = []
        starter = starter_description(tags, category)
        if starter:
            description_bits.append(starter)
        if tags.get("name:en"):
            description_bits.append(tags["name:en"])
        if tags.get("website"):
            description_bits.append(tags["website"])
        properties = {
            "id": f"osm_{prefix}{element['id']}",
            "name": name,
            "category": category,
            "order": order,
            "description": " / ".join(description_bits),
            "note": "",
            "isUserCreated": False,
            "source": f"osm:{element['type']}/{element['id']}",
            "needsSurvey": True,
        }
        short_name = tags.get("short_name") or tags.get("loc_name")
        if short_name:
            properties["shortName"] = short_name
        features.append({
            "type": "Feature",
            "geometry": {"type": "Point", "coordinates": [round(lon, 7), round(lat, 7)]},
            "properties": properties,
        })
        stats[category] = stats.get(category, 0) + 1
    return features, stats


def nearest_path_distance(lat: float, lon: float, paths: list[dict], lat0: float) -> float:
    px, py = project(lat, lon, lat0)
    best = float("inf")
    for feature in paths:
        coordinates = feature["geometry"]["coordinates"]
        for i in range(len(coordinates) - 1):
            ax, ay = project(coordinates[i][1], coordinates[i][0], lat0)
            bx, by = project(coordinates[i + 1][1], coordinates[i + 1][0], lat0)
            distance = dist_to_segment(px, py, ax, ay, bx, by)
            if distance < best:
                best = distance
    return best


def estimate_tiles(bbox: dict, min_zoom: int, max_zoom: int) -> int:
    """Slippy-map tile count for a bbox across a zoom range."""
    total = 0
    for zoom in range(min_zoom, max_zoom + 1):
        n = 2 ** zoom
        x_min = int((bbox["minLon"] + 180.0) / 360.0 * n)
        x_max = int((bbox["maxLon"] + 180.0) / 360.0 * n)

        def y_of(lat: float) -> int:
            rad = math.radians(lat)
            return int((1.0 - math.asinh(math.tan(rad)) / math.pi) / 2.0 * n)

        y_min, y_max = y_of(bbox["maxLat"]), y_of(bbox["minLat"])
        total += (x_max - x_min + 1) * (y_max - y_min + 1)
    return total


def write_collection(path: Path, comment: str, features: list[dict]) -> None:
    document = {
        "type": "FeatureCollection",
        "_comment_th": comment,
        "features": features,
    }
    path.write_text(
        json.dumps(document, ensure_ascii=False, indent=1) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--margin", type=float, default=DEFAULT_MARGIN_M,
                        help="metres to keep outside the campus outline (default 200)")
    parser.add_argument("--refresh", action="store_true", help="re-query Overpass")
    parser.add_argument("--dry-run", action="store_true", help="do not write any file")
    args = parser.parse_args()

    print("1/4 campus outline")
    outline = fetch_campus_outline(args.refresh)
    tags = outline.get("tags", {})
    area = CampusArea(outline["geometry"], args.margin)
    width, height = area.width_height_m()
    print(f"  {tags.get('name', '?')}")
    print(f"  outline {len(outline['geometry'])} nodes, {width:.0f} x {height:.0f} m")
    if tags.get("note"):
        print(f"  OSM note: {tags['note']}")

    print("2/4 highways")
    highways = fetch_highways(area, args.refresh)
    path_features, path_stats = build_paths(highways, area)
    dropped = path_stats.pop("_dropped_indoor", 0)
    print(f"  {len(highways)} ways in the query box -> {len(path_features)} kept")
    for key in sorted(path_stats):
        print(f"    {key:<10} {path_stats[key]}")
    if dropped:
        print(f"    dropped indoor ways: {dropped}")

    stitched = stitch_network(path_features, area.lat0)
    orphaned = largest_component_only(path_features, area.lat0)
    inserted = densify(path_features, area.lat0)
    print(f"    stitched gaps: {stitched}, dropped unreachable: {orphaned}"
          f", vertices inserted: {inserted}")
    print(f"    final: {len(path_features)} lines")

    print("3/4 places")
    places = fetch_places(area, args.refresh)
    gate_features = find_gates(highways, places, area)
    poi_features, poi_stats = build_pois(places, area, CAMPUS_WAY_ID, len(gate_features))
    poi_features = gate_features + poi_features
    poi_stats["gate"] = len(gate_features)
    print(f"  {len(places)} elements in the query box -> {len(poi_features)} POIs")
    for gate in gate_features:
        lon, lat = gate["geometry"]["coordinates"]
        print(f"    {gate['properties']['name']}: {lat:.6f}, {lon:.6f}"
              f"  ({gate['properties']['source']})")
    for key in sorted(poi_stats, key=lambda k: CATEGORY_ORDER.get(k, 9)):
        print(f"    {key:<10} {poi_stats[key]}")

    far = []
    for feature in poi_features:
        lon, lat = feature["geometry"]["coordinates"]
        distance = nearest_path_distance(lat, lon, path_features, area.lat0)
        feature["properties"]["distanceToPathM"] = round(distance, 1)
        if distance > 30.0:
            far.append((feature["properties"]["name"], distance))
    if far:
        print(f"  {len(far)} POI(s) more than 30 m from any road - not routable yet:")
        for name, distance in sorted(far, key=lambda x: -x[1]):
            print(f"    {distance:6.1f} m  {name}")

    print("4/4 config")
    dlat, dlon = area.margin_degrees()
    # The download box is the outline plus the margin, then widened just enough to hold
    # every coordinate that was actually written. Anything the router can reach has to be
    # inside the offline pack, or the map goes blank exactly where someone is walking.
    lons = [area.min_lon - dlon, area.max_lon + dlon]
    lats = [area.min_lat - dlat, area.max_lat + dlat]
    for feature in path_features:
        for lon, lat in feature["geometry"]["coordinates"]:
            lons.append(lon)
            lats.append(lat)
    for feature in poi_features:
        lon, lat = feature["geometry"]["coordinates"]
        lons.append(lon)
        lats.append(lat)
    # Round outwards, never to nearest: rounding a bound inwards by 1e-7 would put a
    # coordinate that is written at 7 decimals just outside the box it is meant to be in.
    bbox = {
        "minLon": math.floor(min(lons) * 1e6) / 1e6,
        "minLat": math.floor(min(lats) * 1e6) / 1e6,
        "maxLon": math.ceil(max(lons) * 1e6) / 1e6,
        "maxLat": math.ceil(max(lats) * 1e6) / 1e6,
    }
    config = {
        "_comment_th": (
            "ค่าพิกัดมาจาก OpenStreetMap way/"
            f"{CAMPUS_WAY_ID} (ODbL) ผ่าน tools/osm_import.py "
            f"เผื่อขอบนอกรั้ว {args.margin:.0f} ม. — bbox นี้คือพื้นที่เดียวที่แอปจะดาวน์โหลดตอนออฟไลน์"
        ),
        "campusName": "มจพ. วิทยาเขตปราจีนบุรี",
        "bbox": bbox,
        "center": {
            "lon": round((area.min_lon + area.max_lon) / 2.0, 6),
            "lat": round((area.min_lat + area.max_lat) / 2.0, 6),
        },
        "defaultZoom": 15.5,
        "minZoom": 13,
        "maxZoom": 18,
        "styleUrl": "https://tiles.openfreemap.org/styles/liberty",
    }
    box_width = haversine_m(config["center"]["lat"], bbox["minLon"],
                            config["center"]["lat"], bbox["maxLon"])
    box_height = haversine_m(bbox["minLat"], bbox["minLon"], bbox["maxLat"], bbox["minLon"])
    tiles = estimate_tiles(bbox, config["minZoom"], config["maxZoom"])
    print(f"  bbox {box_width:.0f} x {box_height:.0f} m = {box_width * box_height / 1e6:.2f} sq.km")
    # Vector tiles in a low-density area run roughly 5-60 KB each; the Thai glyph ranges
    # add a couple of MB on top. The real figure is reported by the app after the download.
    print(f"  ~{tiles} vector tiles at z{config['minZoom']}-{config['maxZoom']}"
          f" (~{tiles * 5 / 1024:.0f}-{tiles * 60 / 1024:.0f} MB, plus ~2 MB of Thai glyphs)")

    if args.dry_run:
        print("\n--dry-run: ไม่ได้เขียนไฟล์")
        return 0

    CONFIG_PATH.write_text(
        json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_collection(
        POIS_PATH,
        "สร้างจาก OpenStreetMap (ODbL) ด้วย tools/osm_import.py — เป็นข้อมูลตั้งต้น "
        "ยังไม่ได้สำรวจด้วย GPS จุดที่มี needsSurvey=true ควรเดินเก็บพิกัดจริงด้วย "
        "Surveyor Mode ทับ (ดู SETUP.md ข้อ 2)",
        poi_features,
    )
    write_collection(
        PATHS_PATH,
        "โครงข่ายเส้นทางในวิทยาเขต (ถนน + ทางเดิน) จาก OpenStreetMap (ODbL) ด้วย "
        "tools/osm_import.py — ไม่รวมทางเดินในอาคารเพราะ GPS ใช้ในร่มไม่ได้ "
        "เส้นทางที่ OSM ยังไม่มีให้บันทึกเพิ่มด้วยโหมดบันทึกเส้นทาง (ดู SETUP.md ข้อ 3)",
        path_features,
    )
    print(f"\nเขียนแล้ว:\n  {CONFIG_PATH.relative_to(REPO_ROOT)}"
          f"\n  {POIS_PATH.relative_to(REPO_ROOT)}"
          f"\n  {PATHS_PATH.relative_to(REPO_ROOT)}")
    print("\nต่อไป: python3 tools/geojson_validate.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
