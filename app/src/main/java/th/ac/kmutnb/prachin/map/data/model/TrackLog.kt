package th.ac.kmutnb.prachin.map.data.model

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.navigation.model.PathType
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath

/** Which of the two recording screens a track was walked on. */
enum class TrackCaptureMode(val id: String) {
    /** The map screen: the line draws itself under the walker as they go. */
    MAP("map"),

    /** The readout screen: numbers only, no map to render. */
    READOUT("readout"),
    ;

    companion object {
        fun fromId(id: String?): TrackCaptureMode = entries.firstOrNull { it.id == id } ?: MAP
    }
}

/**
 * A path someone made by walking it.
 *
 * The counterpart of [GpsPoint] for lines, and kept to the same discipline: it holds what
 * the receiver observed while walking, and **nothing about what the path is**. No name of
 * a building, no faculty, no category - those belong to a [Poi]. A track is a measurement,
 * and the reason the export is worth keeping for years is that it stays one.
 *
 * The field list is short on purpose. Someone standing in a forest with one hand on the
 * phone will answer "what do I call this" and nothing else; every further question is one
 * more thing to get wrong while the battery runs down.
 *
 * Unlike a logged point, a track can be switched into the routing graph - see
 * [isUsedForRouting]. That is the whole reason to walk one where no map has a path: the
 * line you just made becomes the line you can be guided along.
 */
data class TrackLog(
    val id: String,
    /** Short running label, `T001`, `T002`... - how the walker refers to it later. */
    val code: String,
    /** Simplified geometry, in the order it was walked. */
    val points: List<GeoPoint>,
    /** Metres actually walked, measured before simplification. */
    val lengthMeters: Double,
    /** Mean accuracy of the accepted fixes. */
    val averageAccuracyMeters: Float,
    /** The worst accepted fix - a good mean hides the corner walked under a tree. */
    val worstAccuracyMeters: Float,
    /** Median satellites used over the walk. */
    val satellitesUsed: Int,
    /** Median satellites visible over the walk. */
    val satellitesVisible: Int,
    /** Fixes accepted, before the spacing filter thinned them into vertices. */
    val fixCount: Int,
    /** Fixes discarded for being too coarse; high means heavy cover. */
    val rejectedCount: Int,
    /** Seconds spent walking it. */
    val durationSeconds: Int,
    /** Free text from the walker. The only field that is not a measurement. */
    val note: String,
    /**
     * Whether the router may use this line.
     *
     * A switch rather than something implied by existing, because a survey accumulates
     * walks that were wrong: the path that turned out to be someone's driveway, the one
     * walked twice. Switching a track off leaves the measurement intact and takes it out
     * of the graph, which is what "keep the record, drop the mistake" has to mean.
     */
    val isUsedForRouting: Boolean,
    val recordedAt: Long,
    val captureMode: TrackCaptureMode,
    /**
     * What is known about each vertex, index for index with [points].
     *
     * Empty for tracks saved before per-vertex accuracy was recorded: for those only the
     * whole-walk figures above were ever kept, and [vertexInfoAt] answers "unknown" rather
     * than inventing a number. Always read through [vertexInfoAt], never by index.
     */
    val vertexInfo: List<TrackVertexInfo> = emptyList(),
) {
    /** Vertices in the stored line, after simplification. */
    val vertexCount: Int get() = points.size

    /** Banded the same way a logged point is, so the colours mean one thing app-wide. */
    val quality: GpsFixQuality get() = GpsFixQuality.of(averageAccuracyMeters)

    /** The record for vertex [index], or "walked, accuracy unknown" when none was kept. */
    fun vertexInfoAt(index: Int): TrackVertexInfo =
        if (vertexInfo.size == points.size) vertexInfo[index] else TrackVertexInfo.UNKNOWN

    /** Every vertex's record, padded for tracks that predate per-vertex accuracy. */
    val vertexInfoOrUnknown: List<TrackVertexInfo>
        get() = List(points.size) { vertexInfoAt(it) }

    /** How far vertex [index] now sits from where the receiver put it; null if never moved. */
    fun movedMetersAt(index: Int): Double? =
        vertexInfoAt(index).originalPoint?.let { GeoUtils.haversineMeters(it, points[index]) }

    /** Vertices a person placed or moved by hand rather than walked. */
    val editedVertexCount: Int
        get() = points.indices.count { vertexInfoAt(it).source != VertexSource.WALKED }

    /** Vertices whose own fix accuracy is on record. */
    val knownAccuracyCount: Int
        get() = points.indices.count { vertexInfoAt(it).accuracyMeters != null }
}

/** How a vertex of a track came to be where it is. */
enum class VertexSource(val id: String) {
    /** A fix the receiver reported while somebody walked the path. */
    WALKED("walked"),

    /** Put there by hand on the map; no receiver was involved. */
    PLACED("placed"),

    /** Walked, then dragged by hand; where the receiver put it is kept in the record. */
    MOVED("moved"),
    ;

    companion object {
        fun fromId(id: String?): VertexSource = entries.firstOrNull { it.id == id } ?: WALKED
    }
}

/**
 * What is known about one vertex of a track.
 *
 * Each vertex that was walked is one real fix - the recorder thins fixes and simplifies the
 * line, but never averages them - so the accuracy the receiver reported for that fix is
 * the honest answer to "how far off could this point be". It is the receiver's own 68%
 * estimate, not a measured error: nothing on the phone knows where the path truly is.
 *
 * A hand edit does not erase the measurement. A moved vertex keeps both the accuracy its
 * fix had and the position the receiver gave it, so an exported file can always tell a
 * walked line from one somebody corrected by eye against a basemap.
 */
data class TrackVertexInfo(
    /** The fix's own accuracy, metres; null when it was never recorded or never existed. */
    val accuracyMeters: Float? = null,
    val source: VertexSource = VertexSource.WALKED,
    /** Where the receiver put this vertex before it was moved by hand; null otherwise. */
    val originalPoint: GeoPoint? = null,
) {
    companion object {
        /** A walked vertex from a track saved before per-vertex accuracy existed. */
        val UNKNOWN = TrackVertexInfo()

        fun walked(accuracyMeters: Float) = TrackVertexInfo(accuracyMeters = accuracyMeters)

        val PLACED = TrackVertexInfo(source = VertexSource.PLACED)
    }
}

/**
 * The routable form of a track.
 *
 * Everything the router needs is the geometry; the rest of [WalkPath] carries distinctions
 * this log deliberately does not ask for. A walked track is a footway - whether it is
 * lit, covered or one-way is a property of a campus path someone is describing, not of a
 * line somebody measured, and asking for it at the moment the walk ends is how a survey
 * acquires four fields of guesses.
 */
fun TrackLog.toWalkPath(): WalkPath = WalkPath(
    id = id,
    type = PathType.FOOTWAY,
    points = points,
    name = note.ifBlank { code },
)
