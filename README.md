# นำทาง มจพ. ปราจีนบุรี — แผนที่นำทางแบบออฟไลน์

แอป Android สำหรับนำทางในพื้นที่
**มหาวิทยาลัยเทคโนโลยีพระจอมเกล้าพระนครเหนือ วิทยาเขตปราจีนบุรี**
(129 ม.21 ต.เนินหอม อ.เมือง จ.ปราจีนบุรี 25230)

ทำงาน **ออฟไลน์ 100%** หลังดาวน์โหลดแผนที่ครั้งแรก — เปิดโหมดเครื่องบินก็ยังนำทางได้

นำทางกลางแจ้งด้วย GPS บน **โครงข่ายเส้นทางจริงในมอ คือถนน + ทางเดิน**
เช่นจากประตูมอไปหน้าอาคารคณะ IT — ไม่ใช่แค่ทางเท้า และไม่รวมทางเดินภายในอาคาร
(GPS ใช้ในร่มไม่ได้)

---

## ข้อมูลพิกัดที่ให้มา

แอปมี **ข้อมูลตั้งต้นจาก OpenStreetMap** มาให้แล้ว ติดตั้งแล้วใช้ได้เลย:

| ไฟล์ | มีอะไร |
|---|---|
| `campus_config.json` | bbox 3.19 x 2.05 กม. ครอบเฉพาะพื้นที่วิทยาเขต + เผื่อขอบ 200 ม. |
| `pois.geojson` | 24 จุด — อาคารคณะ หอพัก โรงอาหาร ห้องสมุด โรงยิม ประตู |
| `paths.geojson` | 151 เส้น รวม 29.3 กม. — ถนนในมอ 95 เส้น ทางเดิน 51 เส้น |

สร้างใหม่/อัปเดตได้ด้วย `python3 tools/osm_import.py`

> ⚠️ ข้อมูล OSM เป็น **จุดตั้งต้น ไม่ใช่ผลสำรวจ** — POI คือ centroid ของรูปอาคาร
> คลาดได้หลายเมตร ถ้าอยากให้แม่นระดับเมตรต้องใช้ **Surveyor Mode** ในแอปเดินเก็บทับ
> และ **ห้ามก็อปพิกัดจาก Google Maps มาใส่เด็ดขาด**
> (เหตุผลเต็ม ๆ อยู่ใน [`docs/ACCURACY.md`](docs/ACCURACY.md))

👉 ขั้นตอนละเอียดอยู่ที่ **[SETUP.md](SETUP.md)**

---

## ความสามารถ

| # | ความสามารถ |
|---|---|
| F1 | เปิดครั้งแรก → ดาวน์โหลดแผนที่เฉพาะพื้นที่วิทยาเขต → เด้ง Alert เมื่อพร้อมใช้ออฟไลน์ |
| F2 | เปิดในโหมดเครื่องบิน แผนที่ยังขึ้นครบ ทั้งเส้นถนนและชื่อสถานที่ภาษาไทย |
| F3 | หมุดตำแหน่งตัวเอง พร้อมวงรัศมีความแม่นยำ |
| F4 | เลือกจุดหมายจากรายการ หรือแตะบนแผนที่ |
| F5 | สร้างเส้นทางหลายจุดต่อกัน (หน้ามอ 1 → หน้ามอ 3 → หอชาย) |
| F6 | ระยะทางรวม, เวลาเดินโดยประมาณ, ระยะถึงจุดถัดไปที่อัปเดตสด |
| F7 | ถึงจุดแล้วเด้งรายละเอียด + สั่นเตือน |
| F8 | แก้ไข/เพิ่มรายละเอียดแต่ละจุดเองได้ บันทึกลงเครื่อง |
| F9 | เตือนเมื่อปักจุดนอกเส้นทาง + เสนอย้ายไปจุดใกล้สุดบนเส้นทาง |
| F10 | เตือนเมื่อออกนอกเส้นทาง + คำนวณเส้นทางใหม่ |
| F11 | โหมดสำรวจพิกัด (Surveyor) เก็บพิกัดจริงด้วย GPS แล้ว export GeoJSON |

---

## ของที่ใช้ (ฟรีทั้งหมด ไม่มี API key ไม่ต้องผูกบัตร)

| ส่วน | ของที่ใช้ | License |
|---|---|---|
| Map engine | [MapLibre Native Android SDK](https://maplibre.org/maplibre-native/android/api/) 13.6.0 | BSD-2 |
| Vector tiles | [OpenFreeMap](https://openfreemap.org/) style `liberty` | ODbL (ข้อมูล OSM) |
| Tiles สำรอง | MBTiles ที่ gen เองด้วย [tilemaker](https://tilemaker.org/) | ODbL |
| ตำแหน่ง | `android.location.LocationManager` (AOSP) | — |
| ข้อมูลถนน/อาคาร | [OpenStreetMap](https://www.openstreetmap.org/) ผ่าน Overpass API | ODbL |
| Local tile server | [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) 2.3.1 | BSD |
| ฐานข้อมูล | Room 2.8.4 | Apache-2 |
| Settings | DataStore Preferences | Apache-2 |
| UI | Jetpack Compose + Material 3 | Apache-2 |
| Routing | A* เขียนเอง | — |

**สิ่งที่จงใจไม่ใช้:**
- ❌ Google Maps SDK / Places API / Directions API — เสียเงินและต้องผูกบัตร
- ❌ `play-services-location` (FusedLocationProvider) — ทำให้แอปใช้ไม่ได้บนเครื่องที่ไม่มี GMS
- ❌ GraphHopper — ไฟล์ ~40 MB สำหรับพื้นที่แค่ 2 ตร.กม. ไม่คุ้ม

---

## build

ต้องมี: JDK 21+ (Gradle จะโหลด toolchain ให้เองถ้าไม่มี), Android SDK Platform 36

```bash
# Windows
gradlew.bat assembleDebug
gradlew.bat installDebug
gradlew.bat test

# Linux / macOS
./gradlew assembleDebug
./gradlew installDebug
./gradlew test
```

ข้อมูลพิกัด:
```bash
python3 tools/osm_import.py        # ดึงจาก OSM มาเขียน config + pois + paths
python3 tools/geojson_validate.py  # ตรวจก่อน commit ทุกครั้ง
```

---

## เอกสาร

| ไฟล์ | เนื้อหา |
|---|---|
| [SETUP.md](SETUP.md) | ดึงข้อมูลจาก OSM, สำรวจพิกัด, สร้าง MBTiles |
| [docs/ACCURACY.md](docs/ACCURACY.md) | ทำไมหมุดไม่ตรง และแก้ยังไง — **อ่านก่อนแก้เรื่องพิกัด** |
| [docs/OFFLINE.md](docs/OFFLINE.md) | กลไกออฟไลน์ทั้ง 2 แบบ และกับดักที่ทำให้จอขาว |
| [docs/DATA_FORMAT.md](docs/DATA_FORMAT.md) | รูปแบบ GeoJSON และ config |
| [CLAUDE.md](CLAUDE.md) | บริบทโปรเจกต์สำหรับ AI agent |

---

## ข้อจำกัดที่รู้ตัว

- **GPS cold start ตอนไม่มีเน็ตช้า 30–90 วินาที** เพราะไม่มี A-GPS มาช่วย — ยืนกลางแจ้งรอ
- **ในอาคารใช้ไม่ได้** GPS ทะลุหลังคาคอนกรีตไม่ได้
- ใต้ต้นไม้ทึบหรือระหว่างตึกสูง อาจคลาดเคลื่อน 10–30 ม. จาก multipath
- **ข้อมูลจาก OSM ยังไม่ได้สำรวจ** หมุดอาจคลาด 5–15 ม. จากตำแหน่งจริง
  โดยเฉพาะจุดที่เป็น centroid ของอาคารใหญ่ — ใช้ Surveyor Mode เก็บทับเมื่อไปถึงหน้างาน
- **ประตูมอยังไม่ครบ** OSM มี `barrier=gate` ในเขตมอแค่จุดเดียวและไม่มีชื่อ
  ประตู 1–5 ต้องเดินไปเก็บพิกัดเอง
- ทางลัด/ทางเดินเลียบอาคารที่ไม่มีใครวาดลง OSM ก็ยังต้องเดินบันทึกเอง

---

## เครดิตข้อมูล

ข้อมูลแผนที่จาก © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) ภายใต้ ODbL
