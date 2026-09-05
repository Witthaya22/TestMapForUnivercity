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
  "bbox": { "minLon": 101.3xxx, "minLat": 14.1xxx, "maxLon": 101.3xxx, "maxLat": 14.1xxx },
  "center": { "lon": 101.3xxx, "lat": 14.1xxx },
  "defaultZoom": 16.5,
  "minZoom": 13,
  "maxZoom": 18,
  "styleUrl": "https://tiles.openfreemap.org/styles/liberty"
}
```

| ฟิลด์ | ชนิด | ความหมาย |
|---|---|---|
| `campusName` | String | ชื่อที่โชว์บน UI |
| `bbox` | Object | ขอบเขตที่จะดาวน์โหลดแผนที่ออฟไลน์ |
| `center` | Object | จุดที่แผนที่เปิดมาครั้งแรก |
| `defaultZoom` | Double | zoom เริ่มต้น (16.5 ≈ เห็นทั้งมอพอดี) |
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
        "icon": "ic_gate"
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

## 3. `assets/data/paths.geojson` — โครงข่ายทางเดิน

`FeatureCollection` ของ `LineString` เท่านั้น

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
        "type": "footway",
        "name": "ทางเดินหน้าอาคารบริหาร",
        "oneway": false,
        "lit": true,
        "covered": false
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
4. **snap POI ทุกจุด** ลงบน edge ที่ใกล้ที่สุด แล้วแทรก virtual node ตรงจุดตั้งฉาก
   → ทำให้ A* เริ่ม/จบที่ตัว POI ได้เลย ไม่ต้องเดินไปหา node ที่ใกล้ที่สุดก่อน

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
- **ทุก POI ห่างจาก `paths.geojson` ไม่เกิน 30 ม.** — ถ้าเกินแปลว่าจุดนั้นนำทางไปไม่ถึง
- `category` และ `type` เป็นค่าที่รองรับ
