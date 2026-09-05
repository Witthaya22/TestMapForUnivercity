# CLAUDE.md — บริบทโปรเจกต์สำหรับ AI Agent

## โปรเจกต์นี้คืออะไร
แอป Android (Kotlin) แผนที่นำทางเดินเท้าภายใน **มจพ. วิทยาเขตปราจีนบุรี**
ทำงาน **ออฟไลน์ 100%** หลังดาวน์โหลดแผนที่ครั้งแรก

- Gradle root: `TestMapForUnivercity/` (module เดียวคือ `:app`)
- Package / applicationId: `th.ac.kmutnb.prachin.map`
- minSdk 24 · targetSdk 36 · compileSdk 36.1 · AGP 9.2.1 · Kotlin 2.2.10 · Gradle 9.4.1
- AGP 9 มี **built-in Kotlin support** → ไม่ต้องใส่ปลั๊กอิน `org.jetbrains.kotlin.android`

## กฎที่ห้ามละเมิด
1. ห้ามเพิ่ม dependency ที่เสียเงินหรือต้องมี API key — โดยเฉพาะ Google Maps SDK / Places / Directions
2. ห้ามใช้ `com.google.android.gms:play-services-location` — ใช้ `android.location.LocationManager` เท่านั้น
   (แอปต้องทำงานบนเครื่องที่ไม่มี Google Play Services และไม่มีเน็ต)
3. ห้ามมี network call ตอน runtime ปกติ — ยกเว้นตอนดาวน์โหลด offline region ครั้งแรก
   `network_security_config.xml` บล็อก cleartext ทุกโดเมนยกเว้น `127.0.0.1` (tile server ในเครื่อง)
4. พิกัดใน GeoJSON = `[lon, lat]` / ใน MapLibre = `LatLng(lat, lon)` — **อย่าสลับ**
   แปลงที่ `core/geo/GeoUtils.kt` ที่เดียวเท่านั้น ห้ามแปลงมือกระจายตามไฟล์
5. `includeIdeographs` ของ `OfflineTilePyramidRegionDefinition` ต้องเป็น `false`
   ถ้าเป็น `true` MapLibre จะไม่ดาวน์โหลด glyph → **ชื่อสถานที่ภาษาไทยหายตอนออฟไลน์**
6. MBTiles ใช้ TMS Y-axis → ต้องแปลง `tmsY = (1 shl z) - 1 - y` ก่อน query
7. vector tile ใน MBTiles ถูก gzip ไว้ → ต้องตั้ง header `Content-Encoding: gzip` ไม่งั้นจอขาว
8. String ที่ผู้ใช้เห็นต้องอยู่ใน `res/values/strings.xml` เป็นภาษาไทยทั้งหมด
   โค้ดและคอมเมนต์เป็นภาษาอังกฤษ

## Stack
| ส่วน | ของที่ใช้ | เวอร์ชัน | License |
|---|---|---|---|
| Map engine | `org.maplibre.gl:android-sdk` | 13.6.0 | BSD-2 |
| Annotation plugin | `org.maplibre.gl:android-plugin-annotation-v9` | 3.0.2 | BSD-2 |
| GeoJSON / Turf | `org.maplibre.gl:android-sdk-geojson`, `-turf` | 6.0.1 | BSD-2 |
| Vector tiles | OpenFreeMap style `liberty` (ไม่ต้องมี key) | — | ODbL data |
| Tiles สำรอง | MBTiles ที่ gen เองด้วย tilemaker | — | ODbL |
| ตำแหน่ง | `android.location.LocationManager` (`GPS_PROVIDER`) | AOSP | — |
| Local tile server | `org.nanohttpd:nanohttpd` | 2.3.1 | BSD |
| DB | Room + KSP | 2.8.4 | Apache-2 |
| Prefs | DataStore Preferences | 1.2.1 | Apache-2 |
| UI | Compose BOM + Material 3 | 2026.02.01 | Apache-2 |
| Routing | A* เขียนเอง (ไม่ใช้ GraphHopper) | — | — |

## ไฟล์สำคัญ
| ไฟล์ | หน้าที่ |
|---|---|
| `app/src/main/assets/config/campus_config.json` | bbox / center / zoom / styleUrl — **ต้องกรอกก่อนใช้** |
| `app/src/main/assets/data/pois.geojson` | จุดต่าง ๆ ในมอ |
| `app/src/main/assets/data/paths.geojson` | โครงข่ายทางเดิน (กราฟ routing + ตรวจปักจุดผิดทาง) |
| `core/geo/GeoUtils.kt` | สูตรระยะทางทั้งหมด — แก้ที่นี่ที่เดียว |
| `navigation/AStarRouter.kt` | หาเส้นทาง |
| `data/repository/OfflineMapRepository.kt` | ดาวน์โหลด/จัดการแผนที่ออฟไลน์ |
| `map/LocalTileServer.kt` | เสิร์ฟ MBTiles โหมด BUNDLED |

## คำสั่งที่ใช้บ่อย
```bash
./gradlew assembleDebug            # build (Windows: gradlew.bat)
./gradlew test                     # unit test
./gradlew installDebug             # ติดตั้งลงเครื่อง
python3 tools/geojson_validate.py  # ตรวจไฟล์พิกัดก่อน commit
bash tools/build_tiles.sh          # สร้าง MBTiles ใหม่ (โหมด BUNDLED)
```

## เรื่องพิกัดไม่ตรง (อ่าน `docs/ACCURACY.md` ก่อนแก้)
ห้ามก็อปพิกัดจาก Google Maps มาใส่ ภาพดาวเทียม Google มี offset กับข้อมูล OSM ได้ 3–15 ม.
ให้ใช้ **Surveyor Mode** ในแอปเก็บพิกัดจริงจาก GPS แล้ว export ทับ `pois.geojson` เท่านั้น

## Definition of Done ของทุกฟีเจอร์
- `./gradlew assembleDebug` ผ่าน + `./gradlew test` เขียว
- ทดสอบในโหมดเครื่องบินแล้วยังทำงาน
- String ที่ผู้ใช้เห็นทุกตัวอยู่ใน `strings.xml` เป็นภาษาไทย
- ไม่มี `TODO` / `FIXME` ค้าง
