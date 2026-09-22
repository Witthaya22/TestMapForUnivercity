package th.ac.kmutnb.prachin.map.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import th.ac.kmutnb.prachin.map.data.model.HazardSound
import th.ac.kmutnb.prachin.map.data.model.HazardSoundOrigin

/**
 * A sound file the user imported, as catalogued on the device.
 *
 * Only imported sounds have rows here. The generated tones and anything dropped into
 * `assets/sounds/` are part of the build, and cataloguing them would be a copy of the
 * APK's own contents in a table that can then disagree with it - see [HazardSound].
 *
 * The audio itself is not stored here. Room holds the name and the file name; the bytes
 * live in the app's own storage under `hazard_sounds/`, because a blob column would be
 * read into memory on every catalogue query just to render a list of names.
 */
@Entity(tableName = "hazard_sound")
data class HazardSoundEntity(
    @PrimaryKey val id: String,
    /** What the user sees in the picker. Taken from the file name they chose. */
    val name: String,
    /** File name inside the app's `hazard_sounds/` directory. */
    val fileName: String,
    val addedAt: Long,
)

fun HazardSoundEntity.toHazardSound(): HazardSound = HazardSound(
    id = id,
    name = name,
    origin = HazardSoundOrigin.IMPORTED,
    fileName = fileName,
    addedAt = addedAt,
)
