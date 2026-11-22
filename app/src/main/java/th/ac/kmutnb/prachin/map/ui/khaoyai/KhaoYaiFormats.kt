package th.ac.kmutnb.prachin.map.ui.khaoyai

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.khaoyai.SpeciesKind
import th.ac.kmutnb.prachin.map.data.khaoyai.StationKind
import th.ac.kmutnb.prachin.map.data.khaoyai.Trail
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailOpenState
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailDifficulty
import th.ac.kmutnb.prachin.map.ui.theme.ForestLeaf
import th.ac.kmutnb.prachin.map.ui.theme.HornbillAmber

object KhaoYaiFormats {

    /** `2026-11-18` → `18 พ.ย. 2569`; any other text is shown as it was typed. */
    fun thaiShortDate(iso: String, monthNames: List<String>): String {
        val parts = iso.trim().split("-")
        if (parts.size != 3 || monthNames.size < 12) return iso
        val year = parts[0].toIntOrNull() ?: return iso
        val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return iso
        val day = parts[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return iso
        return "$day ${monthNames[month - 1]} ${year + BUDDHIST_ERA_OFFSET}"
    }

    private const val BUDDHIST_ERA_OFFSET = 543
}

@Composable
fun thaiDate(iso: String): String =
    KhaoYaiFormats.thaiShortDate(iso, stringArrayResource(R.array.thai_month_short).toList())

@Composable
fun trailDuration(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.trail_duration_minutes, rest)
        rest == 0 -> stringResource(R.string.trail_duration_hours, hours)
        else -> stringResource(R.string.trail_duration_hours_minutes, hours, rest)
    }
}

@Composable
fun difficultyLabel(difficulty: TrailDifficulty): String = stringResource(
    when (difficulty) {
        TrailDifficulty.EASY -> R.string.difficulty_easy
        TrailDifficulty.MODERATE -> R.string.difficulty_moderate
        TrailDifficulty.HARD -> R.string.difficulty_hard
    },
)

fun difficultyColor(difficulty: TrailDifficulty): Color = when (difficulty) {
    TrailDifficulty.EASY -> ForestLeaf
    TrailDifficulty.MODERATE -> HornbillAmber
    TrailDifficulty.HARD -> Color(0xFFC0502D)
}

@Composable
fun openStateLabel(state: TrailOpenState): String = stringResource(
    when (state) {
        TrailOpenState.OPEN_NOW -> R.string.open_state_open
        TrailOpenState.NOT_YET -> R.string.open_state_not_yet
        TrailOpenState.ENDED_TODAY -> R.string.open_state_ended
        TrailOpenState.SEASON_CLOSED -> R.string.open_state_season
        TrailOpenState.CLOSED -> R.string.open_state_closed
    },
)

fun openStateColor(state: TrailOpenState): Color =
    if (state == TrailOpenState.OPEN_NOW) Color(0xFF2E7D32) else Color(0xFFC62828)

/** `เริ่มเดินได้ 08:00–14:00 น.` and any closed season, or blank when the trail has no limits. */
@Composable
fun trailHours(trail: Trail): String {
    val parts = mutableListOf<String>()
    if (trail.startFrom.isNotBlank() || trail.startUntil.isNotBlank()) {
        parts += stringResource(R.string.trail_hours, trail.startFrom.ifBlank { "-" }, trail.startUntil.ifBlank { "-" })
    }
    if (trail.seasonClosedFrom.isNotBlank() && trail.seasonClosedUntil.isNotBlank()) {
        parts += stringResource(R.string.trail_season_closed, trail.seasonClosedFrom, trail.seasonClosedUntil)
    }
    return parts.joinToString(" · ")
}

@Composable
fun stationKindLabel(kind: StationKind): String = stringResource(
    when (kind) {
        StationKind.NATURE -> R.string.station_kind_nature
        StationKind.ATTRACTION -> R.string.station_kind_attraction
        StationKind.VIEWPOINT -> R.string.station_kind_viewpoint
        StationKind.WATERFALL -> R.string.station_kind_waterfall
        StationKind.REST -> R.string.station_kind_rest
    },
)

@Composable
fun speciesKindLabel(kind: SpeciesKind): String = stringResource(
    when (kind) {
        SpeciesKind.ANIMAL -> R.string.species_kind_animal
        SpeciesKind.PLANT -> R.string.species_kind_plant
    },
)
