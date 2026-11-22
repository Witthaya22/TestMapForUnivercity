package th.ac.kmutnb.prachin.map.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import th.ac.kmutnb.prachin.map.data.local.SosReportDao
import th.ac.kmutnb.prachin.map.data.local.toEntity
import th.ac.kmutnb.prachin.map.data.local.toReport
import th.ac.kmutnb.prachin.map.data.model.SosCategory
import th.ac.kmutnb.prachin.map.data.model.SosReport
import th.ac.kmutnb.prachin.map.data.model.SosStatus
import th.ac.kmutnb.prachin.map.location.LocationFix
import java.util.UUID

/**
 * Calls for help and their handling.
 *
 * shortcut: stored on this phone only - the admin screen on the same phone sees them, the
 * park's staff on theirs do not. Sending to staff needs a server (or SMS gateway) behind
 * [send]; until then the SOS screen says so and offers the phone and SMS instead.
 */
class SosRepository(private val dao: SosReportDao) {

    val reports: Flow<List<SosReport>> = dao.observeAll().map { list -> list.map { it.toReport() } }

    val newCount: Flow<Int> = reports.map { list -> list.count { it.status == SosStatus.NEW } }

    suspend fun send(
        username: String,
        fix: LocationFix?,
        category: SosCategory,
        message: String,
        trailName: String,
        now: Long = System.currentTimeMillis(),
    ): SosReport {
        val report = SosReport(
            id = "sos_${UUID.randomUUID()}",
            createdAt = now,
            username = username,
            point = fix?.point,
            accuracyMeters = fix?.accuracyMeters,
            category = category,
            message = message.trim(),
            trailName = trailName,
        )
        dao.insert(report.toEntity())
        return report
    }

    suspend fun setStatus(id: String, status: SosStatus, note: String = "", now: Long = System.currentTimeMillis()) =
        dao.updateStatus(id, status.id, if (status == SosStatus.NEW) null else now, note)

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun deleteResolved() = dao.deleteWithStatus(SosStatus.RESOLVED.id)
}
