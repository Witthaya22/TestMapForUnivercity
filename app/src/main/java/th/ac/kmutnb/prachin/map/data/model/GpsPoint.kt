package th.ac.kmutnb.prachin.map.data.model

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint

/**
 * How much to trust a recorded coordinate, as three bands rather than a raw number.
 *
 * The thresholds are the ones the rest of the app already works to: 5 m is about the width
 * of a campus footpath, so anything better tells two paths apart; 10 m is the point where a
 * fix can no longer say which side of a road it is on. A single colour per band is what
 * makes a screenful of markers readable at a glance.
 */
enum class GpsFixQuality {
    /** <= 5 m. */
    GOOD,

    /** <= 10 m. */
    FAIR,

    /** > 10 m - usable as a rough position, not as a surveyed one. */
    POOR,

    ;

    companion object {
        const val GOOD_MAX_METERS = 5f
        const val FAIR_MAX_METERS = 10f

        fun of(accuracyMeters: Float): GpsFixQuality = when {
            accuracyMeters <= GOOD_MAX_METERS -> GOOD
            accuracyMeters <= FAIR_MAX_METERS -> FAIR
            else -> POOR
        }
    }
}

/**
 * Which of the two collecting screens a reading came from.
 *
 * Recorded because the two are not equivalent evidence. On the map screen the surveyor
 * could see where the device thought it was standing and would have noticed it sitting in
 * the wrong building; on the readout screen they could not. Months later, that is the
 * difference between a reading somebody checked and one they only took - and it is the
 * kind of thing nobody remembers unless the file says so.
 */
enum class GpsCaptureMode(val id: String) {
    /** The map screen: position, accuracy ring and the walked trail all visible. */
    MAP("map"),

    /** The readout screen: numbers only, no map. */
    READOUT("readout"),
    ;

    companion object {
        /**
         * Unknown values read back as [MAP], which is also what rows recorded before this
         * field existed are migrated to - every one of them came from the map screen,
         * because it was the only one there was.
         */
        fun fromId(id: String?): GpsCaptureMode = entries.firstOrNull { it.id == id } ?: MAP
    }
}

/**
 * One coordinate measured in the field, with the receiver's own account of how good it was.
 *
 * Deliberately **not** a [Poi]. A POI answers "what is this place and how do I route to it",
 * and carries a category, a description and a position that anyone may later correct by
 * hand. A [GpsPoint] answers only "where did this device think it was standing, and how
 * sure was it" - it is a measurement, so nothing about the campus leaks into it and nothing
 * in it is ever edited afterwards except the note. That separation is what keeps the
 * exported file a clean set of GPS readings rather than a half-finished map.
 *
 * Every field here survives into the exported GeoJSON and CSV under this exact name; see
 * `docs/GPS_LOGGING.md`.
 */
data class GpsPoint(
    val id: String,
    /** Short running label, `P001`, `P002`... - what the surveyor writes on their sheet. */
    val code: String,
    val point: GeoPoint,
    /** Horizontal radius at 68% confidence, as Android reports it; mean of the samples. */
    val accuracyMeters: Float,
    /** Median altitude above the WGS84 ellipsoid, or null if no fix reported one. */
    val elevationMeters: Double?,
    val verticalAccuracyMeters: Float?,
    val satellitesUsed: Int,
    val satellitesVisible: Int,
    /** Accepted fixes averaged into this coordinate. */
    val sampleCount: Int,
    /** Fixes thrown away for being too coarse while measuring this point. */
    val rejectedCount: Int,
    /** Distance from the result to the furthest accepted sample - the repeatability. */
    val spreadMeters: Double,
    /** Seconds spent standing at the point. */
    val durationSeconds: Int,
    /** Free text from the surveyor, empty when they did not add any. */
    val note: String,
    val recordedAt: Long,
    /** Which screen it was taken on; see [GpsCaptureMode]. */
    val captureMode: GpsCaptureMode,
) {
    val quality: GpsFixQuality get() = GpsFixQuality.of(accuracyMeters)
}
