package th.ac.kmutnb.prachin.map.data.repository

import kotlinx.coroutines.flow.first
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.khaoyai.KhaoYaiContentRepository
import th.ac.kmutnb.prachin.map.data.local.TouristPositionEntity
import th.ac.kmutnb.prachin.map.data.model.AccountForm
import th.ac.kmutnb.prachin.map.data.model.SosCategory
import th.ac.kmutnb.prachin.map.data.model.SosReport
import th.ac.kmutnb.prachin.map.data.model.SosStatus
import th.ac.kmutnb.prachin.map.data.model.UserRole
import th.ac.kmutnb.prachin.map.data.prefs.AppPreferences
import java.util.UUID
import kotlin.random.Random

/**
 * Sample tourists on the trails and sample emergency calls over the last three months, so
 * the officer's map, search and reports can be tried before anyone has walked Khao Yai
 * with the app. Every row is flagged demo, shown as an example, and removable in one go
 * from the admin screen.
 *
 * shortcut: remove the seeding (or ship with it switched off) once real data flows in.
 */
class DemoDataSeeder(
    private val accounts: AccountRepository,
    private val tracking: TrackingRepository,
    private val sos: SosRepository,
    private val content: KhaoYaiContentRepository,
    private val preferences: AppPreferences,
) {
    suspend fun seedOnce() {
        if (preferences.demoSeeded.first()) return
        seed()
        preferences.setDemoSeeded(true)
    }

    suspend fun clear() {
        accounts.accounts.first().filter { it.isDemo }.forEach { tracking.deleteFor(it.id) }
        sos.deleteDemo()
        accounts.deleteDemo()
    }

    suspend fun seed(now: Long = System.currentTimeMillis()) {
        accounts.ensureSeeded()
        val trails = content.content.value.trails.filter { it.path.size >= 2 }
        if (trails.isEmpty()) return
        val random = Random(SEED)
        val people = DEMO_PEOPLE.mapIndexedNotNull { i, (name, nationality) ->
            val username = "demo_tourist_${i + 1}"
            val result = accounts.create(
                AccountForm(username, "Demo12345", "Demo12345", name, "08${(10000000 + i * 1234567) % 100000000}", "$username@demo.local", nationality),
                UserRole.TOURIST, emptySet(), isDemo = true,
            )
            (result as? RegisterResult.Success)?.account
        }

        // Each walks part of a trail: positions along its line, a minute apart, ending recently.
        val positions = people.flatMapIndexed { i, person ->
            val trail = trails[i % trails.size]
            val steps = 12 + random.nextInt(20)
            val start = random.nextInt((trail.path.size - steps).coerceAtLeast(1))
            val end = now - (i * 7 + random.nextInt(5)) * 60_000L
            (0 until steps).mapNotNull { s ->
                val p = trail.path.getOrNull(start + s) ?: return@mapNotNull null
                TouristPositionEntity(
                    userId = person.id,
                    trailId = trail.id,
                    lat = p.lat + (random.nextDouble() - 0.5) * 0.00006,
                    lon = p.lon + (random.nextDouble() - 0.5) * 0.00006,
                    accuracyMeters = 4f + random.nextFloat() * 8f,
                    recordedAt = end - (steps - s) * 60_000L,
                    synced = false,
                )
            }
        }
        tracking.insertDemo(positions)

        // Calls spread over 90 days, weighted the way a park's calls tend to fall.
        val weights = listOf(
            SosCategory.LOST to 5, SosCategory.ACCIDENT to 4, SosCategory.WILDLIFE to 3,
            SosCategory.HOSPITAL to 2, SosCategory.POLICE to 1, SosCategory.OTHER to 1,
        )
        val pool = weights.flatMap { (c, n) -> List(n) { c } }
        val reports = (0 until DEMO_CALLS).map { k ->
            val trail = trails[random.nextInt(trails.size)]
            val p = trail.path[random.nextInt(trail.path.size)]
            val person = people.getOrNull(random.nextInt(people.size.coerceAtLeast(1)))
            val age = (random.nextDouble() * 90 * DAY_MS).toLong()
            val status = when {
                k < 2 -> SosStatus.NEW
                k < 4 -> SosStatus.IN_PROGRESS
                else -> SosStatus.RESOLVED
            }
            SosReport(
                id = "sos_demo_${UUID.randomUUID()}",
                createdAt = if (k < 4) now - k * 9 * 60_000L else now - age,
                userId = person?.id.orEmpty(),
                touristId = person?.memberId.orEmpty(),
                username = person?.fullName.orEmpty(),
                point = GeoPoint(p.lat, p.lon),
                accuracyMeters = 5f + random.nextFloat() * 10f,
                category = pool[random.nextInt(pool.size)],
                message = "(ตัวอย่าง) ${SAMPLE_MESSAGES[random.nextInt(SAMPLE_MESSAGES.size)]}",
                trailName = trail.name,
                status = status,
                handledAt = if (status == SosStatus.NEW) null else now,
                isDemo = true,
            )
        }
        sos.insertDemo(reports)
    }

    private companion object {
        const val SEED = 2569
        const val DEMO_CALLS = 24
        const val DAY_MS = 24 * 60 * 60 * 1000L
        val DEMO_PEOPLE = listOf(
            "สมชาย ใจดี" to "ไทย",
            "สุดา รักป่า" to "ไทย",
            "Anna Schmidt" to "เยอรมัน",
            "John Miller" to "อเมริกัน",
            "ปิยะ ทองดี" to "ไทย",
            "Yuki Tanaka" to "ญี่ปุ่น",
        )
        val SAMPLE_MESSAGES = listOf(
            "ข้อเท้าพลิก เดินต่อไม่ได้",
            "หาป้ายบอกทางไม่เจอ",
            "เจอช้างอยู่กลางทาง",
            "เพื่อนหน้ามืด ต้องการไปโรงพยาบาล",
            "ทรัพย์สินหาย ต้องการแจ้งความ",
            "ฝนตกหนัก น้ำในลำห้วยขึ้น",
        )
    }
}
