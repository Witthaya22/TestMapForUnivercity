# DATA_FORMAT.md — รูปแบบไฟล์ข้อมูล

ทุกไฟล์เป็น **GeoJSON ตาม RFC 7946** encoding **UTF-8 ไม่มี BOM**

> ⚠️ พิกัดใน GeoJSON เรียง **`[longitude, latitude]`** เสมอ (lon ก่อน lat)
> ที่ปราจีนบุรีจึงหน้าตาแบบ `[101.38, 14.11]` ไม่ใช่ `[14.11, 101.38]`

---

## 1. `assets/config/campus_config.json`

ไม่ใช่ GeoJSON เป็น config ธรรมดา

```json
{
  "campusName": "มจพ. วิทยาเขตปราจีนบุรี",
  "bbox": { "minLon": 101.338353, "minLat": 14.151579, "maxLon": 101.367897, "maxLat": 14.170261 },
  "center": { "lon": 101.352962, "lat": 14.161024 },
  "defaultZoom": 15.5,
  "minZoom": 13,
  "maxZoom": 18,
  "styleUrl": "https://tiles.openfreemap.org/styles/liberty"
}
```

ไฟล์นี้เขียนโดย `tools/osm_import.py` ค่าที่เห็นคือรั้ววิทยาเขตจาก OSM `way/518190351`
เผื่อขอบ 200 ม. แล้วขยายให้ครอบทุกพิกัดใน `pois.geojson` และ `paths.geojson`

| ฟิลด์ | ชนิด | ความหมาย |
|---|---|---|
| `campusName` | String | ชื่อที่โชว์บน UI |
| `bbox` | Object | ขอบเขตที่จะดาวน์โหลดแผนที่ออฟไลน์ |
| `center` | Object | จุดที่แผนที่เปิดมาครั้งแรก |
| `defaultZoom` | Double | zoom เริ่มต้น (15.5 ≈ เห็นทั้งมอพอดี — มอกว้าง 2.75 กม.) |
| `minZoom` / `maxZoom` | Int | ช่วง zoom ที่ดาวน์โหลด — **อย่าเกิน 18** |
| `styleUrl` | String | style ของ MapLibre (ต้องไม่ต้องใช้ key) |

### กฎ validation (แอป throw ถ้าไม่ผ่าน)
- ทุกค่าใน `bbox` และ `center` ต้องไม่เป็น `0.0` → ข้อความ *"ยังไม่ได้ตั้งค่า bbox"*
- `minLon < maxLon` และ `minLat < maxLat`
- `lon ∈ [-180, 180]`, `lat ∈ [-90, 90]` → ถ้าเกินแปลว่าใส่ UTM มา (ดู `docs/ACCURACY.md` A6)
- `center` ต้องอยู่ใน `bbox`
- `minZoom < maxZoom` และ `maxZoom <= 18`

---

## 2. `assets/data/pois.geojson` — จุดต่าง ๆ

`FeatureCollection` ของ `Point` เท่านั้น

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
        "note": "ช่วง 17:00-18:00 รถติดมาก ให้เผื่อเวลา",
        "icon": "ic_gate",
        "source": "osm:way/518190351",
        "needsSurvey": true
      }
    }
  ]
}
```

| property | ชนิด | บังคับ | ความหมาย |
|---|---|---|---|
| `id` | String | ✅ | ไม่ซ้ำกันทั้งไฟล์ ใช้เป็น primary key ใน Room |
| `name` | String | ✅ | ชื่อเต็มภาษาไทย |
| `shortName` | String | — | ชื่อย่อสำหรับ label บนแผนที่ (ถ้าไม่มีใช้ `name`) |
| `category` | String | ✅ | ดูตารางข้างล่าง |
| `order` | Int | — | ลำดับที่แสดงในรายการ (เช่น หน้ามอ 1–5) |
| `description` | String | — | รายละเอียดที่โชว์ใน BottomSheet ตอนถึงจุด |
| `note` | String | — | โน้ตที่ผู้ใช้แก้เองได้ในแอป |
| `icon` | String | — | ชื่อ drawable ถ้าไม่ระบุใช้ไอคอนตาม `category` |
| `source` | String | — | ที่มา เช่น `osm:way/518190351` เขียนโดย `tools/osm_import.py` |
| `needsSurvey` | Boolean | — | `true` = ยังไม่ได้เดินวัดด้วย GPS ควรเก็บทับด้วย Surveyor Mode |
| `gpsAccuracy` | Float | — | ความแม่นตอนสำรวจ (เมตร) มีเฉพาะจุดที่มาจาก Surveyor Mode |
| `distanceToPathM` | Float | — | ระยะถึงเส้นทางที่ใกล้สุดตอน import ไว้ดูเฉย ๆ แอปไม่ได้ใช้ |

property ที่แอปไม่รู้จักจะถูกละเลยตอนอ่าน ใส่เพิ่มได้โดยไม่พัง

### `category` ที่รองรับ
| ค่า | ความหมาย |
|---|---|
| `gate` | ประตูทางเข้า (หน้ามอ 1–5) |
| `dorm` | หอพัก (หอชาย / หอหญิง) |
| `academic` | อาคารเรียน / ห้องแล็บ |
| `canteen` | โรงอาหาร / ร้านค้า |
| `sport` | สนามกีฬา / โรงยิม |
| `parking` | ที่จอดรถ |
| `service` | สำนักงาน / ห้องสมุด / พยาบาล / ธนาคาร |
| `custom` | จุดที่ผู้ใช้เพิ่มเอง |

ค่าอื่นนอกจากนี้จะถูก map เป็น `custom` พร้อม log warning

---

## 3. `assets/data/paths.geojson` — โครงข่ายเส้นทางในวิทยาเขต

`FeatureCollection` ของ `LineString` เท่านั้น

ไฟล์นี้คือ **โครงข่ายเส้นทางจริงที่เดินตามด้วย GPS ได้ = ถนนในมอ + ทางเดิน**
ไม่ใช่ทางเท้าอย่างเดียว เพราะเส้นทางส่วนใหญ่ในมอเป็นถนน (ตอนนี้ถนน 95 เส้น ทางเดิน 51 เส้น)
เส้นทางแบบ "จากประตูมอไปหน้าอาคารคณะ" จึงเดินไปตามถนนเกือบตลอดทาง

**ไม่รวมทางเดินภายในอาคาร** — `tools/osm_import.py` ตัด `highway=corridor`,
`highway=elevator` และ way ที่มี tag `indoor` หรือ `level` ทิ้งทั้งหมด เพราะ GPS
ใช้ในร่มไม่ได้ ปลายทางของทุกเส้นทางคือหน้าอาคาร ไม่ใช่ห้องในอาคาร

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "LineString",
        "coordinates": [[101.3800, 14.1100], [101.3810, 14.1110]]
      },
      "properties": {
        "id": "path_001",
        "type": "road",
        "name": "ถนนหน้าอาคารบริหาร",
        "oneway": false,
        "lit": true,
        "covered": false,
        "source": "osm:way/518187191",
        "osmHighway": "service"
      }
    }
  ]
}
```

| property | ชนิด | บังคับ | ความหมาย |
|---|---|---|---|
| `id` | String | ✅ | ไม่ซ้ำกันทั้งไฟล์ |
| `type` | String | ✅ | `footway` \| `road` \| `crossing` \| `stairs` |
| `name` | String | — | ชื่อเส้นทาง (ใช้ตอน debug) |
| `oneway` | Boolean | — | ถ้า `true` เดินได้ทางเดียวตามลำดับพิกัด (ค่าเริ่มต้น `false`) |
| `lit` | Boolean | — | มีไฟส่องสว่างตอนกลางคืน |
| `covered` | Boolean | — | มีหลังคา |
| `source` | String | — | `osm:way/<id>` หรือ `osm_import.py:stitch` สำหรับเส้นเชื่อมที่สร้างเอง |
| `osmHighway` | String | — | ค่า `highway=*` เดิมจาก OSM ไว้ debug |
| `synthetic` | Boolean | — | `true` = เส้นเชื่อมที่สคริปต์สร้างขึ้น ไม่มีอยู่ใน OSM |

### `type` มาจาก `highway=*` ของ OSM อย่างไร
| OSM `highway` | `type` ในไฟล์นี้ |
|---|---|
| `service` `residential` `unclassified` `tertiary` `secondary` `primary` `track` `living_street` | `road` |
| `footway` `path` `pedestrian` `cycleway` | `footway` |
| `steps` | `stairs` |
| way ที่มี `footway=crossing` | `crossing` |
| `corridor` `elevator` หรือ way ที่มี `indoor` / `level` | **ตัดทิ้ง** |

### น้ำหนักที่ใช้ใน routing
เวลาเดินจริงต่างกันตามประเภททาง จึงคูณ multiplier เข้ากับระยะทาง:

| `type` | multiplier | เหตุผล |
|---|---|---|
| `footway` | 1.0 | ทางเดินปกติ |
| `road` | 1.0 | เดินริมถนนได้ปกติ |
| `crossing` | 1.2 | ต้องรอรถ |
| `stairs` | 1.5 | เดินช้ากว่าและเหนื่อยกว่า |

น้ำหนัก edge สุดท้าย = `haversineMeters(a, b) × multiplier`

> ค่านี้ใช้ **เฉพาะตอนเลือกเส้นทาง** เท่านั้น
> ระยะทางที่แสดงให้ผู้ใช้เห็นเป็นระยะจริง (ไม่คูณ multiplier)

---

## 4. การสร้างกราฟจาก `paths.geojson`

`navigation/RouteGraphBuilder.kt` ทำตามนี้:

1. ทุกพิกัดใน `LineString` กลายเป็น **node**
2. ทุกช่วงระหว่างพิกัดที่ติดกันกลายเป็น **edge** สองทาง (หรือทางเดียวถ้า `oneway`)
3. **merge node ที่ห่างกัน < 1.5 ม.** เข้าด้วยกัน — นี่คือสิ่งที่ทำให้ทางที่ตัดกันเชื่อมถึงกัน
   เพราะตอนเดินสำรวจ GPS ไม่มีทางให้พิกัดตรงกันเป๊ะที่สี่แยก
   (way ใน OSM ที่ใช้ node ร่วมกันจะมีพิกัดตรงกันเป๊ะอยู่แล้ว จึงเชื่อมกันทันที)
4. **snap POI ทุกจุด** ลงบน edge ที่ใกล้ที่สุด แล้วแทรก virtual node ตรงจุดตั้งฉาก
   → ทำให้ A* เริ่ม/จบที่ตัว POI ได้เลย ไม่ต้องเดินไปหา node ที่ใกล้ที่สุดก่อน
   จุดที่ห่างเกิน `DEFAULT_MAX_SNAP_DISTANCE_M` (120 ม.) ถือว่าไปไม่ถึง

### ทำไม 120 ม. ไม่ใช่ 30 ม.
POI ส่วนใหญ่คือ *centroid ของรูปอาคาร* ไม่ใช่จุดที่คนไปยืน อาคารใหญ่อย่างหอประชุม
มีจุดกึ่งกลางห่างถนนที่ใกล้สุดถึง 67 ม. ทั้งที่เดินไปสบายมาก ช่วงท้ายที่ออกจากถนนไปหาหมุด
ถูกวาดเป็นเส้นตรง ซึ่งก็คือหน้าตาของการเดินตัดลานหน้าอาคารจริง ๆ

ส่วนกรณี "ผู้ใช้แตะกลางสนามฟุตบอล" ใช้เกณฑ์คนละตัวและเข้มกว่ามาก —
`TapValidator` (15 ม. = อยู่บนทาง, เกิน 40 ม. = ปฏิเสธ)

---

## 5. ไฟล์ที่ export จากแอป

Surveyor Mode เขียนไฟล์ลง `Documents/` ผ่าน MediaStore

| ไฟล์ | เนื้อหา |
|---|---|
| `pois_surveyed.geojson` | POI ที่เก็บด้วย GPS — โครงสร้างเดียวกับข้อ 2 บวก property `gpsAccuracy` (Float, เมตร) และ `surveyedAt` (ISO-8601) |
| `paths_surveyed.geojson` | เส้นทางที่บันทึกด้วย Track Recording — โครงสร้างเดียวกับข้อ 3 |

เอาไปทับไฟล์ใน `assets/data/` ได้ตรง ๆ property ส่วนเกินจะถูกละเลยตอนอ่าน

---

## 6. ตรวจไฟล์ก่อน commit

```bash
python3 tools/geojson_validate.py
```

ตรวจให้:
- ไฟล์เป็น JSON ที่ parse ได้ และเป็น `FeatureCollection`
- `id` ไม่ซ้ำ
- พิกัดอยู่ในช่วงองศาที่ถูกต้อง (จับกรณีใส่ UTM มา)
- **ไม่ได้สลับ lat/lon** (เช็คว่า lat อยู่ในช่วงของไทย)
- ทุก POI อยู่ใน `bbox` ของ `campus_config.json`
- **ระยะจาก POI ถึงเส้นทางที่ใกล้ที่สุด** — เกณฑ์ต่างกันตามที่มาของจุด:
  - จุดที่สำรวจเอง เกิน **30 ม.** = ผิดพลาด (คนไปยืนตรงนั้นได้ แปลว่ายังไม่ได้บันทึกเส้นทาง)
  - จุดที่มาจาก OSM (`source` ขึ้นต้นด้วย `osm:`) เกิน **120 ม.** = ผิดพลาด, 30–120 ม. = แค่เตือน
- `category` และ `type` เป็นค่าที่รองรับ
