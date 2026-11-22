package th.ac.kmutnb.prachin.map.ui.khaoyai.admin

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.khaoyai.NewsItem
import th.ac.kmutnb.prachin.map.data.khaoyai.Species
import th.ac.kmutnb.prachin.map.data.khaoyai.SpeciesKind
import th.ac.kmutnb.prachin.map.data.khaoyai.Trail
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailDifficulty
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailStation
import th.ac.kmutnb.prachin.map.data.khaoyai.newContentId
import java.io.Serializable
import java.util.Locale

/*
 * What the admin forms hold while being typed into: every field as typed, so a half-typed
 * number is kept rather than rejected. Serializable so a rotation keeps the form.
 * `toX` returns null while a required field is missing or unreadable.
 */

private const val DEFAULT_EMOJI = "🌿"

/** Accepts "1.5" and "1,5" alike; Thai keyboards offer either. */
private fun String.toDecimal(): Double? = trim().replace(',', '.').toDoubleOrNull()
private fun String.toPositiveDouble(): Double? = toDecimal()?.takeIf { it > 0 }
private fun String.toPositiveInt(): Int? = trim().toIntOrNull()?.takeIf { it > 0 }

private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

/**
 * A latitude/longitude typed as two fields. Both blank is "no point", which is allowed;
 * one blank, unreadable or off the globe is an error rather than a silent null.
 */
data class LatLonDraft(val lat: String = "", val lon: String = "") : Serializable {
    val isBlank: Boolean get() = lat.isBlank() && lon.isBlank()
    val point: GeoPoint?
        get() {
            val la = lat.toDecimal()?.takeIf { it in -90.0..90.0 } ?: return null
            val lo = lon.toDecimal()?.takeIf { it in -180.0..180.0 } ?: return null
            return GeoPoint(lat = la, lon = lo)
        }
    val isInvalid: Boolean get() = !isBlank && point == null

    companion object {
        fun of(point: GeoPoint?) = if (point == null) {
            LatLonDraft()
        } else {
            LatLonDraft(String.format(Locale.US, "%.6f", point.lat), String.format(Locale.US, "%.6f", point.lon))
        }
    }
}

data class StationDraft(
    val id: String = newContentId("station"),
    val name: String = "",
    val emoji: String = "",
    val description: String = "",
    val point: LatLonDraft = LatLonDraft(),
    val radiusMeters: String = TrailStation.DEFAULT_RADIUS_METERS.toString(),
    val photo: String = "",
) : Serializable {
    val nameMissing: Boolean get() = name.isBlank()
    val radiusInvalid: Boolean get() = radiusMeters.toPositiveInt() == null
    val isValid: Boolean get() = !nameMissing && !radiusInvalid && !point.isInvalid

    fun toStation(): TrailStation? {
        if (!isValid) return null
        return TrailStation(
            id = id,
            name = name.trim(),
            emoji = emoji.trim(),
            description = description.trim(),
            point = point.point,
            radiusMeters = radiusMeters.toPositiveInt()!!,
            photo = photo,
        )
    }

    companion object {
        fun from(s: TrailStation) = StationDraft(
            id = s.id,
            name = s.name,
            emoji = s.emoji,
            description = s.description,
            point = LatLonDraft.of(s.point),
            radiusMeters = s.radiusMeters.toString(),
            photo = s.photo,
        )
    }
}

data class TrailDraft(
    val name: String = "",
    val emoji: String = "",
    val photo: String = "",
    val distanceKm: String = "",
    val durationMinutes: String = "",
    val difficulty: TrailDifficulty = TrailDifficulty.EASY,
    val startPoint: String = "",
    val endPoint: String = "",
    val summary: String = "",
    val description: String = "",
    val stations: List<StationDraft> = emptyList(),
    val needsGuide: Boolean = false,
    val isOpen: Boolean = true,
    val openNote: String = "",
    val trailhead: LatLonDraft = LatLonDraft(),
    val entryRadiusMeters: String = Trail.DEFAULT_ENTRY_RADIUS_METERS.toString(),
    val demoUnlocked: Boolean = false,
    val sourceUrl: String = "",
) : Serializable {
    val nameMissing: Boolean get() = name.isBlank()
    val distanceInvalid: Boolean get() = distanceKm.toPositiveDouble() == null
    val durationInvalid: Boolean get() = durationMinutes.toPositiveInt() == null
    val entryRadiusInvalid: Boolean get() = entryRadiusMeters.toPositiveInt() == null
    val isValid: Boolean
        get() = !nameMissing && !distanceInvalid && !durationInvalid && !entryRadiusInvalid &&
            !trailhead.isInvalid && stations.all { it.isValid }

    /** @param path kept from the trail being edited; the form does not draw lines. */
    fun toTrail(id: String, path: List<GeoPoint> = emptyList()): Trail? {
        if (!isValid) return null
        return Trail(
            id = id,
            name = name.trim(),
            emoji = emoji.trim().ifEmpty { DEFAULT_EMOJI },
            photo = photo,
            distanceKm = distanceKm.toPositiveDouble()!!,
            durationMinutes = durationMinutes.toPositiveInt()!!,
            difficulty = difficulty,
            startPoint = startPoint.trim(),
            endPoint = endPoint.trim(),
            summary = summary.trim(),
            description = description.trim(),
            stations = stations.mapNotNull { it.toStation() },
            needsGuide = needsGuide,
            isOpen = isOpen,
            openNote = openNote.trim(),
            trailhead = trailhead.point,
            path = path,
            entryRadiusMeters = entryRadiusMeters.toPositiveInt()!!,
            demoUnlocked = demoUnlocked,
            sourceUrl = sourceUrl.trim(),
        )
    }

    companion object {
        fun from(trail: Trail) = TrailDraft(
            name = trail.name,
            emoji = trail.emoji,
            photo = trail.photo,
            distanceKm = trail.distanceKm.toString(),
            durationMinutes = trail.durationMinutes.toString(),
            difficulty = trail.difficulty,
            startPoint = trail.startPoint,
            endPoint = trail.endPoint,
            summary = trail.summary,
            description = trail.description,
            stations = trail.stations.map(StationDraft::from),
            needsGuide = trail.needsGuide,
            isOpen = trail.isOpen,
            openNote = trail.openNote,
            trailhead = LatLonDraft.of(trail.trailhead),
            entryRadiusMeters = trail.entryRadiusMeters.toString(),
            demoUnlocked = trail.demoUnlocked,
            sourceUrl = trail.sourceUrl,
        )
    }
}

data class NewsDraft(
    val title: String = "",
    val emoji: String = "",
    /** `yyyy-MM-dd`, Gregorian, so news sorts by date as plain text. */
    val date: String = "",
    val category: String = "",
    val summary: String = "",
    val body: String = "",
    val photo: String = "",
    val sourceName: String = "",
    val sourceUrl: String = "",
) : Serializable {
    val titleMissing: Boolean get() = title.isBlank()
    val dateInvalid: Boolean get() = !ISO_DATE.matches(date.trim())
    val isValid: Boolean get() = !titleMissing && !dateInvalid

    fun toNews(id: String): NewsItem? {
        if (!isValid) return null
        return NewsItem(
            id = id,
            title = title.trim(),
            emoji = emoji.trim().ifEmpty { "📢" },
            date = date.trim(),
            category = category.trim(),
            summary = summary.trim(),
            body = body.trim(),
            photo = photo,
            sourceName = sourceName.trim(),
            sourceUrl = sourceUrl.trim(),
        )
    }

    companion object {
        fun from(item: NewsItem) = NewsDraft(
            title = item.title,
            emoji = item.emoji,
            date = item.date,
            category = item.category,
            summary = item.summary,
            body = item.body,
            photo = item.photo,
            sourceName = item.sourceName,
            sourceUrl = item.sourceUrl,
        )
    }
}

data class SpeciesDraft(
    val kind: SpeciesKind = SpeciesKind.ANIMAL,
    val name: String = "",
    val scientificName: String = "",
    val emoji: String = "",
    val conservationStatus: String = "",
    val habitat: String = "",
    val description: String = "",
    val tip: String = "",
    val photo: String = "",
) : Serializable {
    val nameMissing: Boolean get() = name.isBlank()
    val isValid: Boolean get() = !nameMissing

    fun toSpecies(id: String): Species? {
        if (!isValid) return null
        return Species(
            id = id,
            kind = kind,
            name = name.trim(),
            scientificName = scientificName.trim(),
            emoji = emoji.trim().ifEmpty { if (kind == SpeciesKind.ANIMAL) "🐾" else DEFAULT_EMOJI },
            conservationStatus = conservationStatus.trim(),
            habitat = habitat.trim(),
            description = description.trim(),
            tip = tip.trim(),
            photo = photo,
        )
    }

    companion object {
        fun from(item: Species) = SpeciesDraft(
            kind = item.kind,
            name = item.name,
            scientificName = item.scientificName,
            emoji = item.emoji,
            conservationStatus = item.conservationStatus,
            habitat = item.habitat,
            description = item.description,
            tip = item.tip,
            photo = item.photo,
        )
    }
}
