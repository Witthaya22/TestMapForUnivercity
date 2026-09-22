package th.ac.kmutnb.prachin.map.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import th.ac.kmutnb.prachin.map.data.assets.AssetPaths
import th.ac.kmutnb.prachin.map.data.assets.AssetReader
import th.ac.kmutnb.prachin.map.data.local.HazardSoundDao
import th.ac.kmutnb.prachin.map.data.local.HazardSoundEntity
import th.ac.kmutnb.prachin.map.data.local.toHazardSound
import th.ac.kmutnb.prachin.map.data.model.HazardSound
import java.io.File
import java.util.UUID

/** Why an imported file was not accepted as a warning sound. */
enum class HazardSoundRejection {
    /** The picked file could not be read, or the app has no access to it any more. */
    UNREADABLE,

    /** Bigger than a warning tone has any reason to be. */
    TOO_LARGE,

    /** Not audio, or in a format this device cannot decode. */
    NOT_PLAYABLE,

    /** Long enough that the spoken warning would arrive too late to act on. */
    TOO_LONG,
}

/** What importing a sound produced: the new entry, or the reason there is none. */
sealed interface HazardSoundImport {
    data class Added(val sound: HazardSound) : HazardSoundImport
    data class Rejected(val reason: HazardSoundRejection) : HazardSoundImport
}

/**
 * The sounds available to play in front of a hazard warning.
 *
 * [sounds] is the two generated tones, then whatever audio was dropped into
 * `assets/sounds/` before the build, then whatever the user imported on this phone - one
 * list in picker order, because a catalogue this size is read whole or not at all.
 *
 * Dropping a file into `assets/sounds/` is the way to give everyone the same warning
 * sounds: no code to edit, and the id it gets (`asset:siren.mp3`) means the same thing on
 * every phone running the build, so a shared `hazards.geojson` that names it still works.
 * An imported file cannot do that - it never leaves the phone it was picked on.
 *
 * Imported audio is **copied** into the app's own storage rather than referenced by the
 * content URI it was picked from. A URI is a loan - it survives neither a reboot nor the
 * user tidying up their Downloads folder - and a warning whose sound has silently gone
 * missing is exactly the failure this app cannot have. Copying also keeps the whole
 * feature offline: the file is on the device before it is ever needed.
 */
class HazardSoundRepository(
    context: Context,
    private val hazardSoundDao: HazardSoundDao,
    private val assetReader: AssetReader,
) {

    private val appContext = context.applicationContext

    /**
     * What was shipped in `assets/sounds/`.
     *
     * Read once: the folder is part of the APK and cannot change while the app runs, and
     * this list is rebuilt on every change to the imported ones.
     */
    private val assetSounds: List<HazardSound> by lazy {
        assetReader.list(AssetPaths.HAZARD_SOUNDS_DIR)
            .sorted()
            .mapNotNull { HazardSound.fromAssetFileName(it) }
    }

    val sounds: Flow<List<HazardSound>> = hazardSoundDao.observeAll().map { rows ->
        HazardSound.bundled + assetSounds + rows.map { it.toHazardSound() }
    }

    /** Where an imported sound's audio lives. The other origins live inside the APK. */
    fun fileFor(sound: HazardSound): File = File(soundsDir(appContext), sound.fileName)

    /**
     * Copies a picked audio file in and catalogues it.
     *
     * Checked after copying rather than before: the limits are about the decoded audio,
     * and the only way to know a file decodes on *this* device is to ask this device's
     * decoder. A rejected copy is deleted again, so a failed import leaves nothing behind.
     */
    suspend fun import(source: Uri): HazardSoundImport = withContext(Dispatchers.IO) {
        val displayName = displayNameOf(source)
        val fileName = "${UUID.randomUUID()}.${extensionOf(displayName)}"
        val target = File(soundsDir(appContext), fileName)

        val copied = runCatching {
            appContext.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("cannot open the chosen file for reading")
        }
        if (copied.isFailure) {
            Log.w(TAG, "could not read the picked sound", copied.exceptionOrNull())
            target.delete()
            return@withContext HazardSoundImport.Rejected(HazardSoundRejection.UNREADABLE)
        }

        if (target.length() > MAX_BYTES) {
            target.delete()
            return@withContext HazardSoundImport.Rejected(HazardSoundRejection.TOO_LARGE)
        }

        val durationMs = durationOf(target)
        if (durationMs == null) {
            target.delete()
            return@withContext HazardSoundImport.Rejected(HazardSoundRejection.NOT_PLAYABLE)
        }
        if (durationMs > MAX_DURATION_MS) {
            target.delete()
            return@withContext HazardSoundImport.Rejected(HazardSoundRejection.TOO_LONG)
        }

        val entity = HazardSoundEntity(
            id = "sound_${UUID.randomUUID()}",
            // Left empty when the provider would not say; the picker then shows a Thai
            // placeholder from strings.xml rather than storing one in the database.
            name = displayName.substringBeforeLast('.').trim(),
            fileName = fileName,
            addedAt = System.currentTimeMillis(),
        )
        hazardSoundDao.upsert(entity)
        HazardSoundImport.Added(entity.toHazardSound())
    }

    /**
     * Forgets an imported sound and deletes its audio.
     *
     * Hazards still pointing at it are left alone: [th.ac.kmutnb.prachin.map.data.model
     * .resolveHazardSound] falls back to the severity's tone for an id it cannot find, so
     * they keep warning. Rewriting those rows would be the only way to lose the user's
     * choice permanently if they import the sound again.
     */
    suspend fun delete(id: String) {
        val entity = hazardSoundDao.findById(id) ?: return
        hazardSoundDao.deleteById(id)
        withContext(Dispatchers.IO) { File(soundsDir(appContext), entity.fileName).delete() }
    }

    /** The file name the provider reports, or empty when it will not say. */
    private fun displayNameOf(uri: Uri): String {
        val fromProvider = runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun extensionOf(displayName: String): String =
        displayName.substringAfterLast('.', "")
            .filter { it.isLetterOrDigit() }
            .lowercase()
            .take(5)
            .ifBlank { "snd" }

    /** @return the clip's length in milliseconds, or null when nothing can decode it. */
    private fun durationOf(file: File): Long? {
        // Released by hand rather than with `use`: MediaMetadataRetriever only became
        // AutoCloseable in API 29, and this app supports 24.
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: RuntimeException) {
            Log.w(TAG, "the picked file does not decode as audio", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        private const val TAG = "HazardSoundRepository"

        /** Imported sounds live here, inside the app's own storage. */
        fun soundsDir(context: Context): File =
            File(context.applicationContext.filesDir, "hazard_sounds").apply { mkdirs() }

        /**
         * Two megabytes is far more than a warning tone needs and far less than a song,
         * which is the mistake this is here to catch.
         */
        const val MAX_BYTES = 2L * 1024 * 1024

        /**
         * A tone plays *before* the spoken warning, so its whole length is delay on the
         * words that say what the hazard is. Ten seconds is generous for that and still
         * far short of somebody picking a song by mistake, which is what this catches.
         */
        const val MAX_DURATION_MS = 10_000L
    }
}
