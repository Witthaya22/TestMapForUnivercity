package th.ac.kmutnb.prachin.map.ui.khaoyai

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.khaoyai.SpeciesKind
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
fun speciesKindLabel(kind: SpeciesKind): String = stringResource(
    when (kind) {
        SpeciesKind.ANIMAL -> R.string.species_kind_animal
        SpeciesKind.PLANT -> R.string.species_kind_plant
    },
)
