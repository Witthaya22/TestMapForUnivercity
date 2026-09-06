# OFFLINE.md — กลไกออฟไลน์

แอปมีโหมดแหล่งแผนที่ 2 แบบ สลับได้ที่ **ตั้งค่า → แหล่งแผนที่**
ทั้งสองแบบ **ไม่ต้องมี API key และไม่มีค่าใช้จ่าย**

| โหมด | ค่า enum | ต้องต่อเน็ตไหม | ขนาด | เหมาะกับ |
|---|---|---|---|---|
| ดาวน์โหลดครั้งแรก (ค่าเริ่มต้น) | `TileSourceMode.OFFLINE_PACK` | ครั้งแรกครั้งเดียว | ~5–30 MB (ในเครื่อง) | ผู้ใช้ทั่วไป |
| ฝังมากับแอป | `TileSourceMode.BUNDLED` | ไม่ต้องเลย | เพิ่มขนาด APK เท่าไฟล์ MBTiles | แจกในที่ไม่มีเน็ต / เผื่อ OpenFreeMap ปิดบริการ |

## ดาวน์โหลดแค่ไหน

`bbox` ใน `campus_config.json` คือ **พื้นที่เดียว** ที่ถูกดาวน์โหลด ไม่ใช่ทั้งจังหวัดหรือทั้งประเทศ
ตอนนี้คือ **3.19 x 2.05 กม. = 6.5 ตร.กม.** = รั้ววิทยาเขต + เผื่อขอบ 200 ม.

ที่ z13–18 คิดเป็น **ประมาณ 462 vector tile** — น้อยกว่าที่คนมักคาดกันมาก เพราะ vector tile
หนึ่งใบครอบพื้นที่เท่ากับ raster tile แต่เก็บข้อมูลทั้งชั้น ไม่ต้องมีหลายใบต่อ zoom
พื้นที่เท่านี้จึงได้ pack ระดับไม่กี่ MB บวก glyph ภาษาไทยอีกราว 2 MB

ปรับพื้นที่ได้ที่ `python3 tools/osm_import.py --margin <เมตร>` แล้วดาวน์โหลดใหม่

---

## Plan A — MapLibre OfflineManager (ค่าเริ่มต้น)

### หลักการ
MapLibre มี `OfflineManager` ในตัวที่ดาวน์โหลด **ทุก resource ที่ style ต้องใช้** ลง SQLite
ในเครื่อง (`file:///data/data/<pkg>/databases/mbgl-offline.db`) แล้ว intercept การขอ resource
ทั้งหมดตอน render ให้อ่านจากฐานนี้ก่อนเสมอ

ที่ดาวน์โหลดมาไม่ได้มีแต่ tile:
- vector tiles (`.pbf`) ทุก z/x/y ใน bbox ตั้งแต่ minZoom ถึง maxZoom
- **glyphs** (ฟอนต์ที่ตัดเป็นช่วง ๆ ช่วงละ 256 ตัวอักษร) — จำเป็นสำหรับตัวหนังสือไทย
- sprite sheet (ไอคอน POI ของ style)
- ตัว `style.json` เอง

### โค้ด
อยู่ที่ `data/repository/OfflineMapRepository.kt`

```kotlin
val definition = OfflineTilePyramidRegionDefinition(
    config.styleUrl,
    LatLngBounds.from(bbox.maxLat, bbox.maxLon, bbox.minLat, bbox.minLon),
    /* minZoom = */ config.minZoom,
    /* maxZoom = */ config.maxZoom,
    context.resources.displayMetrics.density,
    /* includeIdeographs = */ false,
)
```

### 🔴 กับดักที่ต้องระวัง

#### 1. `includeIdeographs` ต้องเป็น `false`
ชื่อพารามิเตอร์กำกวมมาก **`true` = ไม่ดาวน์โหลด glyph** เพราะจะไป render ด้วยฟอนต์ในเครื่องแทน
(ตั้งใจไว้ประหยัดพื้นที่สำหรับภาษาจีน/ญี่ปุ่น/เกาหลี ที่มีตัวอักษรเป็นหมื่นตัว)

แต่ตัวอักษรไทยถูกจัดอยู่ในกลุ่มนี้ด้วย พอตั้ง `true` แล้วออฟไลน์
→ **ชื่อสถานที่ภาษาไทยหายหมด** เหลือแต่เส้นถนนเปล่า ๆ

ตั้ง `false` จะดาวน์โหลด glyph ครบ ใหญ่ขึ้นไม่กี่ MB แลกกับชื่อสถานที่ที่อ่านออก

#### 2. tile count limit ค่าเริ่มต้นแค่ 6000
```kotlin
offlineManager.setOfflineMapboxTileCountLimit(50_000)
```
พื้นที่ 6.5 ตร.กม. ที่ z13–18 ใช้ประมาณ 462 ใบ ซึ่งอยู่ใต้ลิมิตเดิมสบาย ๆ
แต่ยังตั้งไว้เพราะการขยาย `--margin` หรือดัน `maxZoom` ขึ้นทำให้จำนวนพุ่งเร็วมาก
และอาการเวลาชนลิมิตคือ `mapboxTileCountLimitExceeded` แล้วดาวน์โหลดค้างกลางทาง
โดยไม่บอกสาเหตุชัด ๆ ซึ่งหาสาเหตุยากกว่าการตั้งเผื่อไว้ตั้งแต่แรกมาก

#### 3. ต้อง `setDownloadState(INACTIVE)` เมื่อเสร็จ
ไม่งั้น observer จะทำงานค้างกินแบตต่อไป

### ความคืบหน้า
```kotlin
val percent = if (status.requiredResourceCount > 0)
    100.0 * status.completedResourceCount / status.requiredResourceCount
else 0.0
```
`requiredResourceCount` เป็นค่าประมาณที่ **จะขยับขึ้นระหว่างดาวน์โหลด** เพราะ MapLibre
ยังไม่รู้จำนวน resource ทั้งหมดจนกว่าจะ parse tile ที่โหลดมาแล้ว
→ % อาจเด้งถอยหลังได้ในช่วงแรก UI จึงแสดงจำนวน MB ที่โหลดแล้วควบคู่ไปด้วย

### เมื่อสำเร็จ
1. เขียน `dataStore[OFFLINE_READY] = true`
2. เด้ง `AlertDialog`:
   > **ดาวน์โหลดแผนที่เสร็จแล้ว**
   > ตอนนี้คุณใช้แผนที่และนำทางในพื้นที่ มจพ. ปราจีนบุรี ได้แบบไม่ต้องต่อเน็ตแล้ว
   > (ขนาดไฟล์ XX MB)

### หน้าจัดการแผนที่ออฟไลน์
**ตั้งค่า → จัดการแผนที่ออฟไลน์** — แสดงขนาดจริงบนดิสก์, วันที่ดาวน์โหลดล่าสุด,
ปุ่มดาวน์โหลดซ้ำ/อัปเดต, ปุ่มลบ

---

## Plan B — MBTiles ฝังใน assets

ใช้เมื่ออยากให้แอปไม่ต้องต่อเน็ตเลยแม้แต่ครั้งแรก หรือกัน OpenFreeMap ปิดบริการ

### สร้างไฟล์
`bash tools/build_tiles.sh` (รายละเอียดใน `SETUP.md` ข้อ 4)
ผลลัพธ์: `app/src/main/assets/map/kmutnb.mbtiles` + `fonts/` + `sprite*`

### เสิร์ฟให้ MapLibre
MapLibre Native อ่าน tile จาก URL เท่านั้น ไม่มี API ให้ป้อน byte array ตรง ๆ
จึงต้องมี HTTP server เล็ก ๆ ในโปรเซสเดียวกัน — `map/LocalTileServer.kt` (NanoHTTPD)
bind ที่ `127.0.0.1` พอร์ตว่างที่ OS แจกให้ (port 0)

| Route | ทำอะไร |
|---|---|
| `/style.json` | อ่าน style จาก assets แล้ว rewrite URL ของ `sources`, `glyphs`, `sprite` ให้ชี้มาที่ `http://127.0.0.1:PORT/...` |
| `/tiles/{z}/{x}/{y}.pbf` | query MBTiles |
| `/fonts/{fontstack}/{range}.pbf` | อ่านจาก assets |
| `/sprite.json`, `/sprite.png`, `/sprite@2x.*` | อ่านจาก assets |

### 🔴 กับดัก 2 อย่างที่ทำให้จอขาว

#### 1. TMS vs XYZ — แกน Y กลับหัว
MBTiles spec สืบทอดจาก TMS ซึ่งนับ Y **จากล่างขึ้นบน**
แต่ MapLibre (และ slippy map ทั่วไป) นับ **จากบนลงล่าง**

```kotlin
val tmsY = (1 shl zoom) - 1 - y
```

ถ้าไม่แปลง อาการที่เจอคือ **แผนที่กลับหัว** หรือขึ้นพื้นที่ผิดที่ไปเลย
(ที่ z13 พลาดไป 8000 ใบ = คนละจังหวัด) — ไม่ใช่จอขาว จึงหลอกตาว่า "มันก็ขึ้นนะ แค่ผิดที่"

#### 2. Gzip
vector tile ที่เก็บใน MBTiles ถูก **gzip ไว้แล้ว** (ตาม spec)
ถ้าส่ง byte ดิบออกไปโดยไม่บอก MapLibre จะพยายาม parse protobuf จาก gzip stream แล้วพัง
→ **จอขาว ไม่มี error ให้เห็นใน logcat ระดับ default**

แก้ได้ 2 ทาง — แอปนี้เลือกทางแรกเพราะไม่ต้องเสีย CPU:
1. ส่ง header `Content-Encoding: gzip` แล้วให้ MapLibre แตกเอง
2. gunzip ใน server ก่อนส่ง

### Manifest
`android:networkSecurityConfig="@xml/network_security_config"` อนุญาต cleartext
**เฉพาะ `127.0.0.1` และ `localhost`** — ไม่ใช้ `usesCleartextTraffic="true"` แบบเปิดหมด
เพราะนั่นจะเปิดช่องให้ทุกโดเมน

---

## ตรวจว่าออฟไลน์จริงไหม

1. ติดตั้งใหม่ เปิดครั้งแรกโดยมีเน็ต → รอดาวน์โหลดจนเด้ง Alert
2. **ปิดแอปสนิท** (swipe ออกจาก recents)
3. เปิด **โหมดเครื่องบิน**
4. เปิดแอปใหม่ → แผนที่ต้องขึ้นครบ **รวมทั้งชื่อสถานที่ภาษาไทย**

ถ้าเส้นถนนขึ้นแต่ตัวหนังสือหาย = `includeIdeographs` ตั้งผิดเป็น `true`
ถ้าจอเทาว่างเปล่า = pack ยังดาวน์โหลดไม่ครบ หรือ bbox ที่กรอกไม่ครอบตำแหน่งที่ดู
