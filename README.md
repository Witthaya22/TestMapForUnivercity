# นำทาง มจพ. ปราจีนบุรี — แผนที่นำทางเดินเท้าแบบออฟไลน์

แอป Android สำหรับนำทางเดินเท้าภายใน
**มหาวิทยาลัยเทคโนโลยีพระจอมเกล้าพระนครเหนือ วิทยาเขตปราจีนบุรี**
(129 ม.21 ต.เนินหอม อ.เมือง จ.ปราจีนบุรี 25230)

ทำงาน **ออฟไลน์ 100%** หลังดาวน์โหลดแผนที่ครั้งแรก — เปิดโหมดเครื่องบินก็ยังนำทางได้

---

## ⚠️ ก่อนใช้งานจริงต้องทำ 3 อย่าง

แอปนี้ **จงใจไม่ใส่พิกัดสำเร็จรูปมาให้** เพราะพิกัดที่ก็อปมาจากที่อื่นจะทำให้หมุดไม่ตรง
(เหตุผลเต็ม ๆ อยู่ใน [`docs/ACCURACY.md`](docs/ACCURACY.md))

1. **กรอก bounding box** ลง `app/src/main/assets/config/campus_config.json`
2. **สำรวจพิกัดจุดต่าง ๆ** ด้วย Surveyor Mode ในแอป → ทับ `assets/data/pois.geojson`
3. **บันทึกเส้นทางเดิน** ด้วย Track Recording → ทับ `assets/data/paths.geojson`

ถ้ายังไม่ทำ แอปจะขึ้นข้อความภาษาไทยบอกว่าต้องตั้งค่าอะไรก่อน

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
| F9 | เตือนเมื่อปักจุดนอกเส้นทางเดิน + เสนอย้ายไปจุดใกล้สุดบนทาง |
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

ตรวจไฟล์พิกัดก่อน commit:
```bash
python3 tools/geojson_validate.py
```

---

## เอกสาร

| ไฟล์ | เนื้อหา |
|---|---|
| [SETUP.md](SETUP.md) | ขั้นตอนหา bbox, สำรวจพิกัด, สร้าง MBTiles |
| [docs/ACCURACY.md](docs/ACCURACY.md) | ทำไมหมุดไม่ตรง และแก้ยังไง — **อ่านก่อนแก้เรื่องพิกัด** |
| [docs/OFFLINE.md](docs/OFFLINE.md) | กลไกออฟไลน์ทั้ง 2 แบบ และกับดักที่ทำให้จอขาว |
| [docs/DATA_FORMAT.md](docs/DATA_FORMAT.md) | รูปแบบ GeoJSON และ config |
| [CLAUDE.md](CLAUDE.md) | บริบทโปรเจกต์สำหรับ AI agent |

---

## ข้อจำกัดที่รู้ตัว

- **GPS cold start ตอนไม่มีเน็ตช้า 30–90 วินาที** เพราะไม่มี A-GPS มาช่วย — ยืนกลางแจ้งรอ
- **ในอาคารใช้ไม่ได้** GPS ทะลุหลังคาคอนกรีตไม่ได้
- ใต้ต้นไม้ทึบหรือระหว่างตึกสูง อาจคลาดเคลื่อน 10–30 ม. จาก multipath
- ข้อมูลทางเดินเท้าใน OSM ในเขตมหาวิทยาลัยมักไม่ครบ จึงต้องเดินสำรวจเอง (ข้อ 3 ข้างบน)

---

## เครดิตข้อมูล

ข้อมูลแผนที่จาก © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) ภายใต้ ODbL
