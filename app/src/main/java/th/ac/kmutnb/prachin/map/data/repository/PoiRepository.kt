package th.ac.kmutnb.prachin.map.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.geojson.GeoJsonIssue
import th.ac.kmutnb.prachin.map.data.geojson.GeoJsonParser
import th.ac.kmutnb.prachin.map.data.local.PoiDao
import th.ac.kmutnb.prachin.map.data.local.toEntity
import th.ac.kmutnb.prachin.map.data.local.toPoi
import th.ac.kmutnb.prachin.map.data.model.Poi
import th.ac.kmutnb.prachin.map.data.model.PoiCategory
import th.ac.kmutnb.prachin.map.data.prefs.AppPreferences
import java.security.MessageDigest
import java.util.UUID

/** Outcome of importing a POI GeoJSON file. */
data class PoiImportResult(
    val imported: Int,
    val skipped: Int,
    val issues: List<GeoJsonIssue>,
)

/**
 * POIs, backed by Room.
 *
 * The asset file only seeds the table; afterwards the database wins. That ordering matters
 * because the user's own notes and places must survive an app update that ships a revised
 * `pois.geojson`.
 */
class PoiRepository(
    private val poiDao: PoiDao,
    private val campusRepository: CampusRepository,
    private val preferences: AppPreferences,
) {

    val pois: Flow<List<Poi>> = poiDao.observeAll().map { entities -> entities.map { it.toPoi() } }

    fun observe(id: String): Flow<Poi?> = poiDao.observeById(id).map { it?.toPoi() }

    suspend fun find(id: String): Poi? = poiDao.findById(id)?.toPoi()

    suspend fun all(): List<Poi> = poiDao.getAll().map { it.toPoi() }

    /**
     * Copies the shipped POIs into the database, and does it again whenever a new build
     * ships a different `pois.geojson`.
     *
     * Keyed on a digest of the asset rather than a one-shot flag: a corrected gate or a
     * newly added building is worth nothing if it only reaches people who install the app
     * for the first time. Insert-if-absent means an existing row always wins, so re-seeding
     * can add places but can never undo an edit, a move or a note the user made.
     *
     * @return how many rows were actually inserted.
     */
    suspend fun seedIfNeeded(): Int {
        val json = campusRepository.poisAssetJson() ?: return 0
        val digest = digestOf(json)
        val alreadySeeded = preferences.seededPoisDigest.first() == digest && poiDao.count() > 0
        if (alreadySeeded) return 0

        val seed = GeoJsonParser.parsePois(json)
        val inserted = if (seed.items.isEmpty()) {
            0
        } else {
            val rows = poiDao.insertIfAbsent(seed.items.map { it.toEntity() })
                .count { it != -1L }
            // Drop stale seeds the new file no longer has, so a renumbered id leaves a
            // corrected place rather than two of them on the map.
            poiDao.deleteUntouchedSeededExcept(seed.items.map { it.id })
            rows
        }
        preferences.setPoisSeeded(true)
        preferences.setSeededPoisDigest(digest)
        return inserted
    }

    private fun digestOf(json: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(json.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    suspend fun upsert(poi: Poi) = poiDao.upsert(poi.toEntity())

    suspend fun updateNote(id: String, note: String) =
        poiDao.updateNote(id, note, System.currentTimeMillis())

    suspend fun updateDetails(
        id: String,
        name: String,
        description: String,
        note: String,
        category: PoiCategory,
    ) {
        val existing = poiDao.findById(id) ?: return
        poiDao.upsert(
            existing.copy(
                name = name,
                description = description,
                note = note,
                category = category.id,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
      * Moves a POI to a new position.
      *
      * The whole point of shipping OSM data as a starting point is that it can be corrected
      * without a rebuild: a building centroid traced from imagery is not where the entrance
      * is, and a gate derived from an estimated fence line is only as good as that fence.
      * Passing [gpsAccuracy] marks the position as surveyed, which is what the detail sheet
      * shows as evidence the point is now trustworthy.
      */
     suspend fun updateLocation(id: String, point: GeoPoint, gpsAccuracy: Float? = null) {
         val existing = poiDao.findById(id) ?: return
         poiDao.upsert(
             existing.copy(
                 lat = point.lat,
                 lon = point.lon,
                 gpsAccuracy = gpsAccuracy ?: existing.gpsAccuracy,
                 updatedAt = System.currentTimeMillis(),
             ),
         )
     }

    suspend fun delete(id: String) = poiDao.deleteById(id)

    /** Creates a POI at a point the user picked or surveyed. */
    suspend fun createUserPoi(
        name: String,
        point: GeoPoint,
        category: PoiCategory = PoiCategory.CUSTOM,
        description: String = "",
        note: String = "",
        gpsAccuracy: Float? = null,
    ): Poi {
        val now = System.currentTimeMillis()
        val poi = Poi(
            id = "user_${UUID.randomUUID()}",
            name = name,
            shortName = null,
            category = category,
            order = Int.MAX_VALUE,
            description = description,
            note = note,
            icon = null,
            point = point,
            isUserCreated = true,
            gpsAccuracy = gpsAccuracy,
            createdAt = now,
            updatedAt = now,
        )
        poiDao.upsert(poi.toEntity())
        return poi
    }

    // ----------------------------------------------------------------------------------
    // Import / export
    // ----------------------------------------------------------------------------------

    suspend fun exportGeoJson(): String = withContext(Dispatchers.Default) {
        GeoJsonParser.writePois(all())
    }

    /**
     * Imports POIs from a GeoJSON document.
     *
     * @param replaceExisting when true, an incoming feature overwrites the stored POI with
     * the same id; when false those features are counted as skipped.
     */
    suspend fun importGeoJson(json: String, replaceExisting: Boolean): PoiImportResult =
        withContext(Dispatchers.Default) {
            val parsed = GeoJsonParser.parsePois(json)
            if (parsed.items.isEmpty()) {
                return@withContext PoiImportResult(0, 0, parsed.issues)
            }
            if (replaceExisting) {
                poiDao.upsertAll(parsed.items.map { it.toEntity() })
                PoiImportResult(parsed.items.size, 0, parsed.issues)
            } else {
                val inserted = poiDao.insertIfAbsent(parsed.items.map { it.toEntity() })
                val importedCount = inserted.count { it != -1L }
                PoiImportResult(importedCount, parsed.items.size - importedCount, parsed.issues)
            }
        }
}
