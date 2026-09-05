# PROMPT สำหรับ Claude Code — แอป Android แผนที่นำทางออฟไลน์ มจพ. ปราจีนบุรี

> **วิธีใช้:** สร้างโฟลเดอร์เปล่า → เปิด terminal → พิมพ์ `claude` → วางข้อความทั้งไฟล์นี้ลงไป
> (หรือวางไฟล์นี้ไว้ในโฟลเดอร์แล้วบอกว่า "อ่าน PROMPT_KMUTNB_PRACHIN_OFFLINE_MAP.md แล้วทำตามทั้งหมด")

---

## 0. คำสั่งหลักถึง Agent

คุณคือ Senior Android Engineer สร้างแอปนำทางในมหาวิทยาลัยที่ **ทำงานได้จริงแบบออฟไลน์ 100%**

**กติกาเหล็ก:**
1. ห้ามใช้บริการที่ต้องจ่ายเงินหรือต้องผูกบัตร — **ห้ามใช้ Google Maps SDK / Places API / Directions API เด็ดขาด**
2. ห้ามใช้ Google Play Services Location (FusedLocationProvider) — ใช้ `android.location.LocationManager` ของ AOSP ตรง ๆ เพื่อให้ทำงานได้แม้เครื่องไม่มี GMS และไม่มีเน็ต
3. ทุกอย่างที่แผนที่ต้องใช้ (tiles, ฟอนต์ glyphs, sprite, style.json) ต้องอยู่ในเครื่องหลังดาวน์โหลดครั้งแรก — **ห้ามมี network call ใด ๆ ตอนใช้งานปกติ**
4. เขียน Kotlin, ไม่มี TODO ค้าง, คอมไพล์ผ่าน, มี unit test ส่วนคำนวณระยะทาง/เส้นทาง
5. ทำงานเป็นเฟส (Milestone) ตามข้อ 9 — จบเฟสไหนให้ `git commit` แล้วรายงานก่อนไปต่อ
6. ก่อนใส่เลขเวอร์ชัน dependency ให้เช็คเวอร์ชันล่าสุดจริงบน Maven Central ก่อน อย่าเดา
7. **ห้าม hardcode พิกัดมั่ว** — พิกัดทั้งหมดอยู่ในไฟล์ config/GeoJSON แยก และมีโหมดให้ผู้ใช้เก็บพิกัดจริงเอง (ดูข้อ 6)

**ภาษาใน UI:** ไทยทั้งหมด
**ภาษาในโค้ด/คอมเมนต์:** อังกฤษ ยกเว้น string resource

---

## 1. โจทย์ย่อ

แอปแผนที่เดินเท้าในพื้นที่ **มหาวิทยาลัยเทคโนโลยีพระจอมเกล้าพระนครเหนือ วิทยาเขตปราจีนบุรี**
(129 ม.21 ต.เนินหอม อ.เมือง จ.ปราจีนบุรี 25230 — ที่มา: https://www.kmutnb.ac.th/contact-us.aspx)

ผู้ใช้ต้องทำสิ่งเหล่านี้ได้:

| # | ความสามารถ |
|---|---|
| F1 | เปิดแอปครั้งแรก → ดาวน์โหลดแผนที่เฉพาะพื้นที่วิทยาเขต → เด้ง Alert "ใช้แผนที่ออฟไลน์ได้แล้ว" |
| F2 | ครั้งต่อไปเปิดแอปในโหมดเครื่องบิน แผนที่ยังขึ้นครบ ทั้งเส้นถนน ชื่อสถานที่ ตัวหนังสือไทย |
| F3 | เห็นหมุดตำแหน่งตัวเอง (จุดฟ้า) ที่ **ตรงกับแผนที่จริง** พร้อมวงรัศมีความแม่นยำ |
| F4 | เลือกจุดหมายจากรายการ (หน้ามอ 1–5, หอชาย, หอหญิง, อาคารเรียน, โรงอาหาร ฯลฯ) หรือแตะบนแผนที่ |
| F5 | สร้างเส้นทางหลายจุดต่อกัน (waypoint chain) เช่น หน้ามอ 1 → หน้ามอ 3 → หอชาย |
| F6 | บอกระยะทางรวม (ม./กม.), เวลาเดินโดยประมาณ, และ **ระยะถึงจุดถัดไป** ที่อัปเดตสด ๆ |
| F7 | เมื่อถึงจุดใด → เด้ง BottomSheet แสดงรายละเอียดจุดนั้น ("จุดนี้มี…") + สั่นเตือน |
| F8 | รายละเอียดแต่ละจุด **แก้ไข/เพิ่มเองได้** และบันทึกลงเครื่อง (มีข้อมูลตัวอย่างมาให้ก่อน) |
| F9 | ถ้าปักจุดที่ไม่อยู่บนเส้นทางเดิน → เตือน + เสนอย้ายไปจุดใกล้สุดบนเส้นทาง |
| F10 | ออกนอกเส้นทาง → แจ้งเตือน + คำนวณเส้นทางใหม่ |
| F11 | โหมด "สำรวจพิกัด" (Surveyor) สำหรับเจ้าของแอป เก็บพิกัดจริงด้วย GPS แล้ว export GeoJSON |

---

## 2. Tech Stack (ฟรีทั้งหมด)

| ส่วน | ของที่ใช้ | License / ค่าใช้จ่าย | ทำไม |
|---|---|---|---|
| Map engine | **MapLibre Native Android SDK** `org.maplibre.gl:android-sdk` | BSD-2 / ฟรี ไม่ต้องมี API key | fork ของ Mapbox GL ก่อนเปลี่ยน license, render vector tile ลื่น, มี `OfflineManager` ในตัว |
| Vector tiles | **OpenFreeMap** (`https://tiles.openfreemap.org/styles/liberty`) | ฟรี ไม่ต้องสมัคร ไม่มี key | ให้โหลดฟรีไม่จำกัด รองรับ offline pack |
| Tiles (สำรอง) | **MBTiles ที่ gen เองด้วย tilemaker/planetiler** จาก Geofabrik Thailand extract | ODbL / ฟรี | เผื่อ OpenFreeMap ล่มหรือปิดบริการ |
| ตำแหน่ง | `android.location.LocationManager` + `GPS_PROVIDER` | AOSP | ไม่พึ่ง GMS, GPS ทำงานออฟไลน์อยู่แล้ว |
| Local tile server | **NanoHTTPD** `org.nanohttpd:nanohttpd` | BSD | เสิร์ฟ tiles/fonts/sprite จาก assets ให้ MapLibre ผ่าน `http://127.0.0.1:PORT` |
| ฐานข้อมูล | **Room** (SQLite) | Apache-2 | เก็บ POI + note ที่ผู้ใช้แก้เอง |
| Settings/flag | **DataStore Preferences** | Apache-2 | เก็บ flag `offline_map_ready` |
| UI | **Jetpack Compose + Material 3** + `AndroidView` ครอบ MapView | Apache-2 | |
| Routing | **เขียน A* เอง** บนกราฟจาก `paths.geojson` | — | พื้นที่เล็ก ไม่ต้องลาก GraphHopper (ขนาด ~40MB) เข้ามา |
| GeoJSON | `org.maplibre.gl:android-sdk-geojson` (หรือ kotlinx.serialization) | | |

**ลิงก์อ้างอิงที่ต้องอ่านก่อนเขียนโค้ด:**
- MapLibre Android docs: https://maplibre.org/maplibre-native/android/api/
- MapLibre offline guide: https://maplibre.org/maplibre-native/docs/book/android/offline-guide.html
- OpenFreeMap: https://openfreemap.org/
- Android LocationManager: https://developer.android.com/reference/android/location/LocationManager
- Geofabrik Thailand extract: https://download.geofabrik.de/asia/thailand.html
- tilemaker: https://tilemaker.org/ | planetiler: https://github.com/onthegomap/planetiler
- osmium-tool: https://osmcode.org/osmium-tool/
- OpenMapTiles fonts (glyphs pbf): https://github.com/openmaptiles/fonts

---

## 3. พื้นที่แผนที่ (Bounding Box) — ทำแบบนี้ ห้ามเดา

พื้นที่วิทยาเขตปราจีนบุรีเล็กมาก (~1–2 ตร.กม.) ให้เผื่อขอบรอบ ๆ ประมาณ 1 กม.

**ขั้นตอนหา bbox จริง (ให้ agent เขียนไว้ใน SETUP.md ให้ผู้ใช้ทำเอง):**
1. เปิด https://www.openstreetmap.org/
2. ค้น "มหาวิทยาลัยเทคโนโลยีพระจอมเกล้าพระนครเหนือ วิทยาเขตปราจีนบุรี" หรือเลื่อนไปที่ ต.เนินหอม อ.เมืองปราจีนบุรี
3. กด **Export** → อ่านค่า `min lon, min lat, max lon, max lat` ที่แถบซ้าย
4. เอาค่าใส่ `app/src/main/assets/config/campus_config.json`

```json
{
  "campusName": "มจพ. วิทยาเขตปราจีนบุรี",
  "bbox": { "minLon": 0.0, "minLat": 0.0, "maxLon": 0.0, "maxLat": 0.0 },
  "center": { "lon": 0.0, "lat": 0.0 },
  "defaultZoom": 16.5,
  "minZoom": 13,
  "maxZoom": 18,
  "styleUrl": "https://tiles.openfreemap.org/styles/liberty"
}
```

> ⚠️ ให้ agent ใส่ค่า `0.0` เป็น placeholder และเขียนใน README ชัด ๆ ว่า **ต้องกรอกก่อนใช้งาน** พร้อมมี validation ตอน build/runtime ที่ throw error พร้อมข้อความไทยถ้ายังเป็น 0
> ค่าอ้างอิงคร่าว ๆ ของ ต.เนินหอม อยู่แถว 14.10–14.13 N / 101.36–101.40 E — **ใช้เป็นแค่ sanity check ว่ากรอกไม่หลุดจังหวัด ไม่ใช่ค่าจริง**

**maxZoom 18 ก็พอ** (z19 ขึ้นไปทำให้ไฟล์ใหญ่ขึ้นเท่าตัวโดยไม่ได้รายละเอียดเพิ่มใน vector tile) — พื้นที่ ~4 ตร.กม. ที่ z13–18 จะได้ประมาณ **15–40 MB**

---

## 4. กลไกออฟไลน์ (หัวใจของงาน)

### 4.1 Plan A — MapLibre OfflineManager (ทางหลัก)

```kotlin
// Pseudocode - ให้เขียนจริงใน OfflineMapRepository.kt
val definition = OfflineTilePyramidRegionDefinition(
    styleURL   = config.styleUrl,
    bounds     = LatLngBounds.from(maxLat, maxLon, minLat, minLon),
    minZoom    = 13.0,
    maxZoom    = 18.0,
    pixelRatio = context.resources.displayMetrics.density,
    includeIdeographs = false      // ต้องเป็น false เพื่อให้ glyph ไทยถูกดาวน์โหลดมาด้วย
)
OfflineManager.getInstance(context).apply {
    setOfflineMapboxTileCountLimit(50_000)   // default 6000 ไม่พอ
    createOfflineRegion(definition, metadataJson.toByteArray(), callback)
}
```

**สิ่งที่ต้องระวัง (เขียนใน CLAUDE.md ด้วย):**
- `includeIdeographs = false` สำคัญมาก ถ้าเป็น `true` MapLibre จะ render ตัวอักษร CJK/ไทยแบบ local font และ **ไม่ดาวน์โหลด glyph** → พอออฟไลน์แล้วชื่อสถานที่ไทยหาย
- ต้อง observe `OfflineRegionObserver.onStatusChanged` → คำนวณ % จาก `completedResourceCount / requiredResourceCount`
- `onError` และ `mapboxTileCountLimitExceeded` ต้องจับและแสดงข้อความไทยที่เข้าใจได้
- หลังสำเร็จ → `dataStore[OFFLINE_READY] = true` → เด้ง `AlertDialog`:
  > **ดาวน์โหลดแผนที่เสร็จแล้ว**
  > ตอนนี้คุณใช้แผนที่และนำทางในพื้นที่ มจพ. ปราจีนบุรี ได้แบบไม่ต้องต่อเน็ตแล้ว
  > (ขนาดไฟล์ XX MB)
- ต้องมีหน้า **"จัดการแผนที่ออฟไลน์"**: แสดงขนาดจริง, ปุ่มดาวน์โหลดซ้ำ/อัปเดต, ปุ่มลบ, วันที่ดาวน์โหลดล่าสุด

### 4.2 Plan B — MBTiles ฝังในเครื่อง (ทางสำรอง / โหมด full-offline)

ให้ implement ไว้ด้วยเป็น `TileSourceMode.BUNDLED` สลับได้ใน Settings:

1. **สคริปต์เตรียมข้อมูล** `tools/build_tiles.sh`:
```bash
#!/usr/bin/env bash
set -e
BBOX="101.36,14.09,101.40,14.13"   # minLon,minLat,maxLon,maxLat - แก้ให้ตรงของจริง

# 1) ดาวน์โหลด OSM extract ประเทศไทย (~700MB)
wget -c https://download.geofabrik.de/asia/thailand-latest.osm.pbf

# 2) ตัดเฉพาะพื้นที่วิทยาเขต
osmium extract -b "$BBOX" thailand-latest.osm.pbf -o kmutnb-prachin.osm.pbf --overwrite

# 3) แปลงเป็น vector MBTiles (OpenMapTiles schema)
tilemaker --input kmutnb-prachin.osm.pbf \
          --output ../app/src/main/assets/map/kmutnb.mbtiles \
          --process resources/process-openmaptiles.lua \
          --config  resources/config-openmaptiles.json

# 4) ดึงฟอนต์ glyph (ต้องมี Noto Sans ที่มีอักษรไทย)
#    https://github.com/openmaptiles/fonts  -> build แล้ววางที่ assets/map/fonts/
```

2. **Local HTTP server** `LocalTileServer.kt` (NanoHTTPD, bind 127.0.0.1, port หาว่าง):

| Route | ทำอะไร |
|---|---|
| `/style.json` | อ่าน style จาก assets แล้ว **replace URL** ของ `sources.tiles`, `glyphs`, `sprite` ให้ชี้มาที่ `http://127.0.0.1:PORT/...` |
| `/tiles/{z}/{x}/{y}.pbf` | query MBTiles: `SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?` |
| `/fonts/{fontstack}/{range}.pbf` | อ่านจาก assets |
| `/sprite.json`, `/sprite.png`, `/sprite@2x.*` | อ่านจาก assets |

> 🔴 **กับดักที่คนพลาดบ่อยที่สุด 2 อย่าง:**
> 1. **TMS vs XYZ:** MBTiles เก็บ Y แบบ TMS (กลับหัว) แต่ MapLibre ขอแบบ XYZ → ต้องแปลง `tmsY = (1 shl z) - 1 - y` ไม่งั้นแผนที่จะกลับหัวหรือขึ้นผิดที่
> 2. **Gzip:** vector tile ใน MBTiles ถูก gzip ไว้ → ต้องส่ง header `Content-Encoding: gzip` (หรือ gunzip ก่อนส่ง) ไม่งั้นจอขาว

3. Start server ใน `Application.onCreate()` หรือ `MapViewModel.init` แล้วชี้ `map.setStyle(Style.Builder().fromUri("http://127.0.0.1:$port/style.json"))`

4. `AndroidManifest.xml` ต้องมี `android:usesCleartextTraffic="true"` หรือ network security config อนุญาตเฉพาะ `127.0.0.1`

---

## 5. โครงสร้างข้อมูล

### 5.1 `assets/data/pois.geojson` — จุดต่าง ๆ

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "geometry": { "type": "Point", "coordinates": [101.3800, 14.1100] },
      "properties": {
        "id": "gate_1",
        "name": "ประตูหน้ามอ 1",
        "shortName": "หน้ามอ 1",
        "category": "gate",
        "order": 1,
        "description": "ประตูทางเข้าหลัก ติดถนนใหญ่ มีป้อม รปภ. ตลอด 24 ชม.",
        "note": "ตัวอย่าง: ช่วง 17:00-18:00 รถติดมาก ให้เผื่อเวลา",
        "icon": "ic_gate"
      }
    }
  ]
}
```

`category` ที่ต้องมี: `gate` (หน้ามอ 1–5), `dorm` (หอชาย/หอหญิง), `academic`, `canteen`, `sport`, `parking`, `service`, `custom`

> ⚠️ **GeoJSON เป็น `[longitude, latitude]` เสมอ** (กลับกับ `LatLng(lat, lng)` ของ MapLibre)
> ให้เขียน unit test ที่ assert ว่าทุกจุดใน pois.geojson อยู่ใน bbox — ถ้าสลับ lat/lon จะ fail ทันที

### 5.2 `assets/data/paths.geojson` — โครงข่ายทางเดิน

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "geometry": { "type": "LineString", "coordinates": [[101.380,14.110],[101.381,14.111]] },
      "properties": { "id": "path_001", "type": "footway", "name": "ทางเดินหน้าอาคารบริหาร", "oneway": false, "lit": true, "covered": false }
    }
  ]
}
```

`type`: `footway` | `road` | `crossing` | `stairs`
ให้ routing ถ่วงน้ำหนัก: `stairs` × 1.5, `crossing` × 1.2 (เดินช้ากว่า)

### 5.3 Room schema

```kotlin
@Entity(tableName = "poi")
data class PoiEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: String,
    val description: String,
    val note: String,          // ผู้ใช้แก้ได้
    val isUserCreated: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val gpsAccuracy: Float?    // ถ้ามาจากโหมดสำรวจ
)

@Entity(tableName = "route_history")
data class RouteHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val waypointIds: String,   // csv
    val distanceMeters: Double,
    val durationSeconds: Int,
    val completedAt: Long
)
```

Seed จาก `pois.geojson` ครั้งแรก (`RoomDatabase.Callback.onCreate` + WorkManager หรือ init block)

---

## 6. 🎯 แก้ปัญหา "จุดของเราไม่ตรงกับแผนที่" (ผู้ใช้เคยเจอปัญหานี้ ห้ามพลาด)

Agent ต้อง implement ทุกข้อนี้ และเขียนอธิบายไว้ใน `docs/ACCURACY.md`

### สาเหตุ & วิธีแก้

| # | สาเหตุ | วิธีแก้ที่ต้องทำ |
|---|---|---|
| A1 | **ก็อปพิกัดจาก Google Maps มาแปะบน basemap OSM** — ภาพดาวเทียมของ Google มี offset กับข้อมูล OSM ได้ 3–15 ม. และ OSM ในเขตมหาลัยอาจวาดไม่ครบ | ห้ามก็อปจาก Google เด็ดขาด → ใช้ **โหมดสำรวจ (F11)** เก็บพิกัดจริงด้วย GPS แทน |
| A2 | ใช้ `NETWORK_PROVIDER` หรือ Fused แบบ `PRIORITY_BALANCED` → คลาดเคลื่อน 100–2000 ม. | บังคับ `LocationManager.GPS_PROVIDER` เท่านั้น, ขอ `ACCESS_FINE_LOCATION` |
| A3 | ไม่รอ GPS lock (cold start ไม่มีเน็ต = ไม่มี A-GPS almanac → 30–90 วินาที) | แสดง overlay "กำลังหาสัญญาณดาวเทียม… (ความแม่นยำ XX ม.)" และ **ไม่แสดงหมุดตัวเอง** จนกว่า `accuracy < 20f` |
| A4 | ไม่กรอง fix ห่วย | ทิ้ง fix ที่ `accuracy > 25f` หรือ `elapsedRealtimeNanos` เก่ากว่า 10 วิ หรือกระโดดเร็วเกิน 10 m/s |
| A5 | สลับ lat/lon | unit test + helper `fun Feature.toLatLng()` ที่เดียว ห้ามแปลงมือกระจาย |
| A6 | ใช้พิกัดจากไฟล์รังวัดที่เป็น **UTM Zone 47N (EPSG:32647)** ปนกับ WGS84 | เขียน validator: ถ้าค่า > 180 แสดงว่าเป็น UTM → throw พร้อมข้อความบอกให้แปลงก่อน |
| A7 | Marker anchor ผิด — ปลายหมุดไม่ตรงพิกัด | ตั้ง `iconAnchor = ICON_ANCHOR_BOTTOM` และ `iconOffset` ให้ถูก |
| A8 | ตำแหน่งกระตุก | ใส่ **Kalman filter 1 มิติ** (`GpsKalmanFilter.kt`, Q ≈ 1.0 m/s) หรือ moving average 3 fix |

### 6.1 โหมดสำรวจพิกัด (Surveyor Mode) — สำคัญที่สุด

เข้าจาก Settings → "โหมดสำรวจพิกัด (สำหรับผู้ดูแล)"

Flow:
1. เดินไปยืนที่จุดจริง (เช่น ประตูหน้ามอ 1)
2. กด **"เริ่มเก็บพิกัด"**
3. แอปเก็บ fix ต่อเนื่อง 30 ครั้ง (~30 วิ) โดยรับเฉพาะ `accuracy < 15f`
4. แสดง live: จำนวน fix ที่เก็บได้ / accuracy เฉลี่ย / ค่ากระจาย
5. คำนวณผลลัพธ์ด้วย **median ของ lat และ lon แยกกัน** (ทนต่อ outlier กว่า mean)
6. ให้กรอก ชื่อ, หมวดหมู่, คำอธิบาย
7. บันทึกลง Room + **ปุ่ม Export** → เขียน `pois_surveyed.geojson` ลง `Documents/` (ผ่าน MediaStore / SAF)
8. เอาไฟล์นั้นไปแทน `assets/data/pois.geojson` แล้ว rebuild → พิกัดตรงเป๊ะเพราะมาจาก GPS ตัวเดียวกับที่ใช้นำทาง

**เพิ่มโหมด "บันทึกเส้นทาง" (Track Recording)** ในหน้าเดียวกัน: กดเริ่ม → เดินตามทาง → แอปเก็บจุดทุก 3 ม. → หยุด → ทำ Douglas-Peucker simplify (epsilon 2 ม.) → export เป็น LineString ลง `paths_surveyed.geojson`
→ วิธีนี้ได้ `paths.geojson` ที่ตรงกับความจริงในมอ 100% โดยไม่ต้องพึ่ง OSM ที่อาจไม่มีข้อมูล

### 6.2 หน้า Debug (เปิดจาก Settings, กดโลโก้ 7 ครั้ง)

แสดงสด ๆ: provider, lat, lon (ทศนิยม 7 ตำแหน่ง), accuracy, จำนวนดาวเทียมที่เห็น/ที่ใช้ (`GnssStatus.Callback`), speed, bearing, เวลาตั้งแต่ fix ล่าสุด, ระยะห่างจากเส้นทางใกล้สุด
→ ใช้ debug ตอนออกภาคสนามว่า "เพี้ยนเพราะ GPS หรือเพราะข้อมูลแผนที่"

---

## 7. Logic การนำทาง

### 7.1 สร้างกราฟ (`RouteGraphBuilder.kt`)
- อ่าน `paths.geojson` → ทุกจุดพิกัดใน LineString = node, ช่วงระหว่างจุด = edge
- Merge node ที่ห่างกัน < 1.5 ม. เข้าด้วยกัน (จุดตัดของทาง)
- Snap POI ทุกจุดลงบน edge ที่ใกล้ที่สุด → สร้าง virtual node บนเส้น
- น้ำหนัก edge = `haversine(a, b) × typeMultiplier`

### 7.2 หาเส้นทาง (`AStarRouter.kt`)
- A* กับ heuristic = ระยะ haversine ตรง
- รองรับ waypoint หลายจุด: หาเส้นทางทีละช่วงแล้วต่อกัน
- ถ้าหาไม่เจอ → "ไม่พบเส้นทางเดินไปยังจุดนี้ ลองเลือกจุดอื่นหรือเพิ่มเส้นทางในโหมดสำรวจ"

### 7.3 สูตรคำนวณ (`GeoUtils.kt`) — เขียนเอง + มี test
```kotlin
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double
fun distanceToSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Double  // ระยะตั้งฉากถึงเส้น
fun nearestPointOnSegment(p: LatLng, a: LatLng, b: LatLng): LatLng    // ใช้ตอน snap
fun bearingDegrees(from: LatLng, to: LatLng): Double
fun simplifyDouglasPeucker(points: List<LatLng>, epsilonMeters: Double): List<LatLng>
```
> ระยะสั้น ๆ (< 5 กม.) ใช้ equirectangular approximation ได้ เร็วกว่า haversine มาก แต่ให้ใช้ haversine ในการแสดงผลระยะจริง

**Unit test ที่ต้องมี:**
- ระยะกรุงเทพ–เชียงใหม่ ≈ 585 กม. (±1%)
- ระยะ 2 จุดห่าง 100 ม. คำนวณได้ 100 ± 0.5 ม.
- `distanceToSegment` ของจุดที่อยู่บนเส้นพอดี = 0
- ทุก POI ใน pois.geojson อยู่ใน bbox
- ทุก POI อยู่ห่างจาก paths.geojson ไม่เกิน 30 ม.

### 7.4 การแสดงผลระหว่างเดิน
Panel ล่างจอ (ตลอดเวลาที่นำทาง):
```
┌──────────────────────────────────────┐
│  ➜ จุดถัดไป: หน้ามอ 3                 │
│     อีก 240 ม. (~3 นาที)              │
│  ─────────────────────────────────   │
│  รวมทั้งหมด 1.2 กม. · เหลือ 850 ม.     │
│  ████████░░░░░░░░░  42%              │
└──────────────────────────────────────┘
```
- ระยะ < 1000 ม. → แสดงเป็น "XXX ม." (ปัดสิบ), ≥ 1000 → "X.X กม."
- เวลา = ระยะ ÷ 1.3 m/s (ความเร็วเดินเฉลี่ย) ปัดขึ้นเป็นนาที
- อัปเดตทุกครั้งที่ได้ location fix ใหม่ (ไม่เกิน 1 ครั้ง/วินาที)
- **ระยะเหลือ** = ระยะจากตำแหน่งปัจจุบัน snap ลงเส้นทาง แล้ววัดไปตามเส้นจนสุด (ไม่ใช่เส้นตรง)

### 7.5 ตรวจถึงจุดหมาย (F7)
- ถ้า `haversine(current, nextWaypoint) < 20 ม.` ติดกัน 2 fix → ถือว่าถึง
- Action: สั่น 500ms + เสียง + เปิด `ModalBottomSheet` แสดง ชื่อ / คำอธิบาย / note / ปุ่ม "แก้ไขรายละเอียด" / ปุ่ม "ไปจุดถัดไป"
- ถ้าแอปอยู่ background → ส่ง Notification (ต้องมี foreground service สำหรับ location, `FOREGROUND_SERVICE_LOCATION`)

### 7.6 ตรวจการปักจุดผิดทาง (F9)
เมื่อผู้ใช้แตะบนแผนที่เพื่อเพิ่มจุด:
```
d = ระยะถึงเส้นทางเดินที่ใกล้ที่สุด
if (d <= 15 m)  -> รับได้ ปักเลย
if (15 < d <= 40) -> Dialog:
     "จุดที่เลือกอยู่ห่างจากทางเดิน {d} เมตร
      กรุณาปักจุดบนเส้นทางเดินเพื่อให้นำทางได้ถูกต้อง"
     [ ย้ายไปจุดใกล้สุดบนทาง ]  [ ยืนยันตำแหน่งนี้ ]  [ ยกเลิก ]
if (d > 40)  -> Dialog บังคับ:
     "จุดนี้ไม่อยู่บนเส้นทางเดินในมหาวิทยาลัย ไม่สามารถใช้นำทางได้
      กรุณาเลือกจุดใหม่บนถนนหรือทางเดิน"
     [ ตกลง ]   (ไม่มีตัวเลือกยืนยัน)
```
พร้อมวาดเส้นประสีส้มจากจุดที่แตะ → จุดที่แนะนำบนทาง ให้เห็นภาพ

### 7.7 ออกนอกเส้นทาง (F10)
- ถ้า `distanceToRoutePolyline > 25 ม.` ต่อเนื่อง 10 วินาที → Snackbar "คุณออกนอกเส้นทาง" + ปุ่ม "คำนวณใหม่"
- Auto-recalculate ถ้าเปิดใน Settings

---

## 8. โครงสร้างโปรเจกต์

```
kmutnb-prachin-map/
├── CLAUDE.md                       ← ไฟล์บริบทสำหรับ AI agent (ดูข้อ 10)
├── README.md
├── SETUP.md                        ← ขั้นตอนหา bbox + สำรวจพิกัด + build tiles
├── docs/
│   ├── ACCURACY.md                 ← อธิบายวิธีทำให้พิกัดตรง
│   ├── OFFLINE.md                  ← อธิบายกลไกออฟไลน์ทั้ง 2 แบบ
│   └── DATA_FORMAT.md
├── tools/
│   ├── build_tiles.sh
│   └── geojson_validate.py         ← เช็ค lat/lon, bbox, POI ห่างทางเกินไปไหม
├── gradle/libs.versions.toml
└── app/src/main/
    ├── AndroidManifest.xml
    ├── assets/
    │   ├── config/campus_config.json
    │   ├── data/pois.geojson
    │   ├── data/paths.geojson
    │   └── map/                     (โหมด BUNDLED: mbtiles, fonts/, sprite, style.json)
    └── java/th/ac/kmutnb/prachin/map/
        ├── MainActivity.kt
        ├── MapApplication.kt
        ├── core/
        │   ├── geo/GeoUtils.kt
        │   ├── geo/GpsKalmanFilter.kt
        │   └── ext/…
        ├── data/
        │   ├── local/  (Room: AppDatabase, PoiDao, entities)
        │   ├── prefs/  (DataStore)
        │   ├── geojson/ (GeoJsonLoader, CampusConfigLoader)
        │   └── repository/ (PoiRepository, OfflineMapRepository, LocationRepository)
        ├── map/
        │   ├── LocalTileServer.kt
        │   ├── MapStyleProvider.kt
        │   └── MapLayerManager.kt   (route line, POI symbols, user location)
        ├── location/
        │   ├── GpsLocationSource.kt (LocationManager + filter + Kalman)
        │   └── NavigationService.kt (foreground service)
        ├── navigation/
        │   ├── RouteGraphBuilder.kt
        │   ├── AStarRouter.kt
        │   ├── NavigationEngine.kt  (progress, arrival, off-route)
        │   └── model/
        └── ui/
            ├── theme/
            ├── onboarding/  (permission + download screen)
            ├── map/         (MapScreen, MapViewModel)
            ├── poi/         (PoiListScreen, PoiDetailSheet, PoiEditScreen)
            ├── survey/      (SurveyorScreen, TrackRecordScreen)
            ├── settings/    (SettingsScreen, OfflineMapManagerScreen)
            └── debug/       (GpsDebugScreen)
```

---

## 9. Milestones (ทำตามลำดับ commit ทีละอัน)

| M | งาน | เสร็จเมื่อ |
|---|---|---|
| **M0** | init project (AGP 8.x, Kotlin 2.x, minSdk 24, targetSdk 35), เขียน `CLAUDE.md` + `README.md` + `SETUP.md` | `./gradlew assembleDebug` ผ่าน |
| **M1** | `GeoUtils` + `RouteGraphBuilder` + `AStarRouter` + unit test ครบ | `./gradlew test` เขียว |
| **M2** | โหลด config/geojson จาก assets + Room + seed + validator (ล้มเหลวพร้อมข้อความไทยถ้าพิกัดผิด) | test ผ่าน |
| **M3** | หน้า onboarding: ขอ permission → ดาวน์โหลด offline region → progress % → Alert สำเร็จ | เปิดครั้งแรกดาวน์โหลดได้จริง |
| **M4** | MapScreen แสดงแผนที่ + POI markers + ทดสอบโหมดเครื่องบินแล้วแผนที่ยังขึ้นครบ (รวมตัวหนังสือไทย) | ✅ ทดสอบ airplane mode |
| **M5** | GPS: `GpsLocationSource` + Kalman + accuracy gate + หมุดตัวเอง + วงรัศมี + หน้า Debug GPS | หมุดตรงตำแหน่งจริง |
| **M6** | เลือกจุดหมาย (list + แตะแผนที่) + validation ผิดทาง (F9) + วาดเส้นทาง | เห็นเส้นทางบนแผนที่ |
| **M7** | NavigationEngine: ระยะรวม/ระยะถึงจุดถัดไป/ETA/progress + ถึงจุดแล้วเด้ง BottomSheet + สั่น + off-route | นำทางได้ครบ loop |
| **M8** | แก้ไข/เพิ่มรายละเอียดจุดเอง (Room) + import/export GeoJSON | บันทึกแล้วปิดเปิดแอปข้อมูลยังอยู่ |
| **M9** | Surveyor mode + Track recording + export | export ไฟล์ออกมาได้ |
| **M10** | Plan B (MBTiles + LocalTileServer) + สลับโหมดใน Settings + `tools/build_tiles.sh` | ปิดเน็ตแต่แรกก็ใช้ได้ |
| **M11** | ขัดเกลา: dark mode, ประหยัดแบต (ปรับ interval ตามความเร็ว), ProGuard, ไอคอนแอป, release build | APK ติดตั้งใช้จริงได้ |

---

## 10. ไฟล์ `CLAUDE.md` ที่ต้องสร้างในโปรเจกต์

ให้ agent สร้างไฟล์นี้ **เป็นสิ่งแรก** เนื้อหาตามนี้ (ปรับให้ตรงกับที่ทำจริง):

```markdown
# CLAUDE.md — บริบทโปรเจกต์สำหรับ AI Agent

## โปรเจกต์นี้คืออะไร
แอป Android (Kotlin) แผนที่นำทางเดินเท้าภายใน มจพ. วิทยาเขตปราจีนบุรี
ทำงาน **ออฟไลน์ 100%** หลังดาวน์โหลดแผนที่ครั้งแรก

## กฎที่ห้ามละเมิด
1. ห้ามเพิ่ม dependency ที่เสียเงินหรือต้องมี API key (โดยเฉพาะ Google Maps SDK / Places / Directions)
2. ห้ามใช้ `com.google.android.gms:play-services-location` — ใช้ `android.location.LocationManager` เท่านั้น
3. ห้ามมี network call ตอน runtime ปกติ — ยกเว้นตอนดาวน์โหลด offline region ครั้งแรก
4. พิกัดใน GeoJSON = `[lon, lat]` / ใน MapLibre `LatLng(lat, lon)` — อย่าสลับ
5. `includeIdeographs` ของ OfflineTilePyramidRegionDefinition ต้องเป็น `false` (ไม่งั้นตัวหนังสือไทยหายตอนออฟไลน์)
6. MBTiles ใช้ TMS Y-axis → ต้องแปลง `tmsY = (1 shl z) - 1 - y`
7. vector tile ใน MBTiles ถูก gzip → ต้องตั้ง `Content-Encoding: gzip`

## Stack
- MapLibre Native Android SDK (BSD) — render + OfflineManager
- OpenFreeMap style `liberty` (ฟรี ไม่ต้องมี key) / สำรอง: MBTiles ที่ gen เอง
- LocationManager GPS_PROVIDER
- Room + DataStore
- Jetpack Compose + Material 3
- A* routing เขียนเอง (ไม่ใช้ GraphHopper)
- NanoHTTPD สำหรับโหมด BUNDLED tiles

## ไฟล์สำคัญ
| ไฟล์ | หน้าที่ |
|---|---|
| `assets/config/campus_config.json` | bbox / center / zoom / styleUrl — **ต้องกรอกก่อนใช้** |
| `assets/data/pois.geojson` | จุดต่าง ๆ ในมอ |
| `assets/data/paths.geojson` | โครงข่ายทางเดิน (ใช้ทำกราฟ routing + ตรวจปักจุดผิดทาง) |
| `core/geo/GeoUtils.kt` | สูตรระยะทางทั้งหมด แก้ที่นี่ที่เดียว |
| `navigation/AStarRouter.kt` | หาเส้นทาง |
| `data/repository/OfflineMapRepository.kt` | ดาวน์โหลด/จัดการแผนที่ออฟไลน์ |
| `map/LocalTileServer.kt` | เสิร์ฟ MBTiles โหมด BUNDLED |

## คำสั่งที่ใช้บ่อย
```bash
./gradlew assembleDebug          # build
./gradlew test                   # unit test
./gradlew installDebug           # ติดตั้งลงเครื่อง
python3 tools/geojson_validate.py  # ตรวจไฟล์พิกัดก่อน commit
bash tools/build_tiles.sh        # สร้าง MBTiles ใหม่
```

## เรื่องพิกัดไม่ตรง (อ่าน docs/ACCURACY.md ก่อนแก้)
ห้ามก็อปพิกัดจาก Google Maps มาใส่ — ใช้ Surveyor Mode ในแอปเก็บพิกัดจริงจาก GPS
แล้ว export ทับ `pois.geojson` เท่านั้น

## Definition of Done ของทุกฟีเจอร์
- คอมไพล์ผ่าน + `./gradlew test` เขียว
- ทดสอบในโหมดเครื่องบินแล้วยังทำงาน
- String ทุกตัวอยู่ใน `strings.xml` เป็นภาษาไทย
- ไม่มี `TODO` / `FIXME` ค้าง
```

---

## 11. Permissions & Manifest

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />              <!-- ใช้แค่ตอนโหลดครั้งแรก -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />    <!-- API 33+ -->
<uses-permission android:name="android.permission.VIBRATE" />
<uses-feature android:name="android.hardware.location.gps" android:required="true" />
```

- ขอ permission ตอน onboarding พร้อมคำอธิบายไทยว่าทำไมต้องใช้
- ถ้าผู้ใช้ปฏิเสธ → หน้าอธิบาย + ปุ่มไป App Settings
- Android 12+ ต้องเช็คว่าได้ *precise* ไม่ใช่ *approximate* (`ACCESS_FINE_LOCATION` granted จริง) → ถ้าได้แค่ approximate ให้เตือนว่านำทางจะไม่แม่น

---

## 12. Checklist ทดสอบก่อนส่งงาน

- [ ] ติดตั้งใหม่ เปิดครั้งแรก มีเน็ต → ดาวน์โหลดแผนที่สำเร็จ + เด้ง Alert
- [ ] ปิดแอป → **เปิดโหมดเครื่องบิน** → เปิดแอป → แผนที่ขึ้นครบ ชื่อสถานที่ภาษาไทยขึ้น
- [ ] ยืนที่จุดจริงในมอ → หมุดตัวเองตรงตำแหน่ง คลาดเคลื่อน < 10 ม.
- [ ] เลือก หน้ามอ 1 → หอชาย → มีเส้นทาง + ระยะทาง + ETA
- [ ] เดินจริงตามเส้นทาง → ระยะถึงจุดถัดไปลดลงต่อเนื่อง ไม่กระโดด
- [ ] ถึงจุด → เด้ง BottomSheet + สั่น
- [ ] แก้รายละเอียดจุด → ปิดเปิดแอป → ข้อมูลยังอยู่
- [ ] แตะกลางสนามฟุตบอลเพื่อปักจุด → ขึ้นเตือนว่าไม่อยู่บนทางเดิน
- [ ] เดินออกนอกทาง 30 ม. → ขึ้นเตือนออกนอกเส้นทาง
- [ ] Surveyor mode เก็บพิกัด 30 fix → export ไฟล์ออกมาได้ เปิดใน geojson.io แล้วตรงตำแหน่ง
- [ ] ใช้งานต่อเนื่อง 30 นาที แบตลดไม่เกิน ~15%

---

## 13. สิ่งที่ต้องรายงานกลับตอนจบ

1. ตารางสรุป "ใช้อะไร ทำอะไร" (library → หน้าที่ → license)
2. วิธีรัน/build ทีละขั้น
3. สิ่งที่ผู้ใช้ต้องทำเองก่อนใช้จริง (กรอก bbox, สำรวจพิกัด, วาดเส้นทาง)
4. ข้อจำกัดที่รู้ตัว (เช่น GPS cold start ช้าตอนไม่มีเน็ต, ในอาคารสัญญาณหลุด)
5. ขนาด APK และขนาดแผนที่ออฟไลน์จริง

---

## 14. แหล่งอ้างอิงทั้งหมด

- MapLibre Native Android — https://maplibre.org/maplibre-native/android/api/
- MapLibre offline guide — https://maplibre.org/maplibre-native/docs/book/android/offline-guide.html
- OpenFreeMap (vector tiles ฟรี ไม่ต้องมี key) — https://openfreemap.org/
- OpenStreetMap (ข้อมูลแผนที่, ODbL) — https://www.openstreetmap.org/
- Geofabrik Thailand OSM extract — https://download.geofabrik.de/asia/thailand.html
- osmium-tool (ตัด bbox) — https://osmcode.org/osmium-tool/
- tilemaker (OSM → MBTiles) — https://tilemaker.org/
- planetiler (ทางเลือก, เร็วกว่า) — https://github.com/onthegomap/planetiler
- OpenMapTiles fonts (glyph pbf รองรับไทย) — https://github.com/openmaptiles/fonts
- MBTiles spec (เรื่อง TMS Y-axis + gzip) — https://github.com/mapbox/mbtiles-spec
- NanoHTTPD — https://github.com/NanoHttpd/nanohttpd
- Android LocationManager — https://developer.android.com/reference/android/location/LocationManager
- Android GnssStatus (นับดาวเทียม) — https://developer.android.com/reference/android/location/GnssStatus
- Room — https://developer.android.com/training/data-storage/room
- ที่ตั้ง มจพ. ปราจีนบุรี — https://www.kmutnb.ac.th/contact-us.aspx

---

**เริ่มจาก M0 ได้เลย ทำเสร็จแต่ละ Milestone แล้วรายงานก่อนไปต่อ**
