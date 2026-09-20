# CLAUDE.md — บริบทโปรเจกต์สำหรับ AI Agent

## โปรเจกต์นี้คืออะไร
แอป Android (Kotlin) แผนที่นำทางในพื้นที่ **มจพ. วิทยาเขตปราจีนบุรี**
ทำงาน **ออฟไลน์ 100%** หลังดาวน์โหลดแผนที่ครั้งแรก

**ขอบเขตการนำทาง:** นำทางกลางแจ้งด้วย GPS บน **โครงข่ายเส้นทางจริงในมอ = ถนน + ทางเดิน**
เช่น จากประตูมอไปหน้าอาคารคณะ ไม่ใช่แค่ทางเท้า และ **ไม่รวมทางเดินภายในอาคาร**
เพราะ GPS ใช้ในร่มไม่ได้ (ดู `docs/ACCURACY.md` หัวข้อ "ข้อจำกัดที่แก้ไม่ได้")

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
9. **ระบบเก็บพิกัด GPS (`ui/gpslog/`) กับ POI (`ui/survey/`) ต้องแยกกัน**
   ไฟล์ที่ export จากหน้าเก็บพิกัด GPS ต้องมีเฉพาะ**ค่าที่วัดได้** (พิกัด ความคลาดเคลื่อน
   ดาวเทียม จำนวนครั้งที่วัด เวลา) ห้ามใส่ชื่อสถานที่ / หมวดหมู่ / คณะ / อาคาร ลงไป
   และจุดที่เก็บต้องไม่เข้ากราฟ routing — อ่าน `docs/GPS_LOGGING.md` ก่อนแก้
10. ห้าม import `androidx.compose.material.icons.*` — `material-icons-core` หยุดที่ 1.7.8
   และไม่อยู่ใน Compose BOM ที่โปรเจกต์นี้ใช้แล้ว ให้ใช้ vector drawable ใน `res/drawable/`
   ผ่าน `painterResource(R.drawable.ic_*)` แทน

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
| `app/src/main/assets/config/campus_config.json` | bbox / center / zoom / styleUrl — กรอกแล้วจากข้อมูล OSM |
| `app/src/main/assets/data/pois.geojson` | จุดต่าง ๆ ในมอ (ตั้งต้นจาก OSM) |
| `app/src/main/assets/data/paths.geojson` | โครงข่ายเส้นทาง **ถนน + ทางเดิน** (กราฟ routing + ตรวจปักจุดผิดทาง) |
| `tools/osm_import.py` | ดึง bbox / POI / เส้นทาง จาก OpenStreetMap มาเขียนไฟล์ 3 ตัวข้างบน |
| `core/geo/GeoUtils.kt` | สูตรระยะทางทั้งหมด — แก้ที่นี่ที่เดียว |
| `navigation/AStarRouter.kt` | หาเส้นทาง |
| `navigation/RouteGraphBuilder.kt` | สร้างกราฟ + merge node 1.5 ม. + snap POI ลงถนน |
| `data/repository/OfflineMapRepository.kt` | ดาวน์โหลด/จัดการแผนที่ออฟไลน์ |
| `map/LocalTileServer.kt` | เสิร์ฟ MBTiles โหมด BUNDLED (NanoHTTPD บน 127.0.0.1) |
| `survey/PointSurveySession.kt` | เฉลี่ย GPS fix ด้วย median — ใช้ร่วมกันทั้ง POI surveyor และหน้าเก็บพิกัด GPS |
| `ui/gpslog/GpsLogScreen.kt` | หน้าเก็บพิกัด GPS บนแผนที่ (เห็นตัวเองเดิน + เก็บจุด + ส่งออก) |
| `data/geojson/GpsPointExporter.kt` | เขียนไฟล์ GeoJSON / CSV ของค่าที่วัดได้ — ชื่อฟิลด์ต้องตรงกับ `data/model/GpsPoint.kt` |
| `data/local/AppDatabase.kt` | Room version **3** (`poi`, `walk_path`, `gps_point`, `route_history`) — เพิ่มตารางต้องเขียน migration จริง ห้าม destructive |

## คำสั่งที่ใช้บ่อย
```bash
./gradlew assembleDebug            # build (Windows: gradlew.bat)
./gradlew test                     # unit test
./gradlew installDebug             # ติดตั้งลงเครื่อง
python3 tools/osm_import.py        # ดึงข้อมูลจาก OSM มาเขียน config + pois + paths ใหม่
python3 tools/geojson_validate.py  # ตรวจไฟล์พิกัดก่อน commit
bash tools/build_tiles.sh          # สร้าง MBTiles ใหม่ (โหมด BUNDLED)
```

> บนเครื่องนี้ Gradle จะเลือก JRE ของ VS Code (ไม่มี `jlink`) แล้ว `compileDebugJavaWithJavac` พัง
> ต้องตั้ง `JAVA_HOME` เป็น JBR ของ Android Studio ก่อนสั่ง build
> PowerShell: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`

## เอกสารที่ต้องอ่านก่อนแก้เรื่องนั้น ๆ
| หัวข้อ | อ่าน |
|---|---|
| พิกัดไม่ตรง / ความแม่นยำ GPS | `docs/ACCURACY.md` |
| รูปแบบไฟล์ GeoJSON และ config | `docs/DATA_FORMAT.md` |
| กลไกออฟไลน์ / จอขาว | `docs/OFFLINE.md` |
| ระบบเก็บพิกัด GPS + ไฟล์ที่ส่งออก | `docs/GPS_LOGGING.md` |

## เรื่องพิกัดไม่ตรง (อ่าน `docs/ACCURACY.md` ก่อนแก้)
**ห้ามก็อปพิกัดจาก Google Maps มาใส่** ภาพดาวเทียม Google มี offset กับข้อมูล OSM ได้ 3–15 ม.

แหล่งพิกัดที่ใช้ได้มี 2 ทางเท่านั้น เรียงตามความแม่น:
1. **Surveyor Mode** ในแอป — เก็บพิกัดจริงด้วย GPS เครื่องเดียวกับที่ใช้นำทาง offset เป็นศูนย์โดยนิยาม
2. **OpenStreetMap** ผ่าน `tools/osm_import.py` — เป็นชุดข้อมูลเดียวกับที่ basemap วาดมา
   จึงไม่มีปัญหา offset แบบ Google แต่เป็นแค่ข้อมูลตั้งต้น (จุด POI = centroid ของอาคาร
   คลาดได้หลายเมตร) จุดที่มี `needsSurvey: true` ควรเดินสำรวจทับ

## Definition of Done ของทุกฟีเจอร์
- `./gradlew assembleDebug` ผ่าน + `./gradlew test` เขียว
- ทดสอบในโหมดเครื่องบินแล้วยังทำงาน
- String ที่ผู้ใช้เห็นทุกตัวอยู่ใน `strings.xml` เป็นภาษาไทย
- ไม่มี `TODO` / `FIXME` ค้าง
