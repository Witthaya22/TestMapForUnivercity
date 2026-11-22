package th.ac.kmutnb.prachin.map.data.khaoyai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import th.ac.kmutnb.prachin.map.data.assets.AssetPaths
import th.ac.kmutnb.prachin.map.data.assets.AssetReader
import java.io.File
import java.util.UUID

/**
 * The trails, news and species the app shows, editable from the admin screen.
 *
 * The bundled sample is read once; after the first admin edit the whole content is saved
 * to [CONTENT_FILE] in app storage and read from there on every later start, so edits
 * survive a restart. "Reset to sample" deletes that file.
 *
 * shortcut: the saved copy lives on this one device; several admins on several phones
 * need a server to share it. A newer bundled sample does not replace a saved copy either,
 * until an admin resets.
 */
class KhaoYaiContentRepository(
    private val context: Context,
    private val assetReader: AssetReader,
    private val scope: CoroutineScope,
) {
    private val dir = File(context.filesDir, DIR)
    private val savedFile = File(dir, CONTENT_FILE)
    private val writeLock = Mutex()

    // Lazy so the files are read when content is first shown, not in Application.onCreate.
    private val state by lazy { MutableStateFlow(loadSaved() ?: loadSample()) }

    val content: StateFlow<KhaoYaiContent> get() = state.asStateFlow()

    /** Author and licence of each bundled photo, keyed by its asset path. */
    val photoCredits: Map<String, PhotoCredit> by lazy {
        runCatching { KhaoYaiContentJson.parseCredits(assetReader.readText(AssetPaths.PHOTO_CREDITS)) }
            .onFailure { Log.e(TAG, "cannot read photo credits", it) }
            .getOrDefault(emptyMap())
    }

    fun upsertTrail(trail: Trail) = edit { it.copy(trails = it.trails.upsertBy(trail) { t -> t.id }) }

    fun deleteTrail(id: String) = edit { it.copy(trails = it.trails.filterNot { t -> t.id == id }) }

    fun upsertNews(item: NewsItem) = edit { it.copy(news = it.news.upsertBy(item) { n -> n.id }) }

    fun deleteNews(id: String) = edit { it.copy(news = it.news.filterNot { n -> n.id == id }) }

    fun upsertSpecies(item: Species) = edit { it.copy(species = it.species.upsertBy(item) { s -> s.id }) }

    fun deleteSpecies(id: String) = edit { it.copy(species = it.species.filterNot { s -> s.id == id }) }

    /** Throws away every admin edit and goes back to the bundled sample. */
    fun resetToSample() {
        state.value = loadSample()
        scope.launch(Dispatchers.IO) { writeLock.withLock { savedFile.delete() } }
    }

    /**
     * Copies a picked image into app storage, scaled down to [MAX_PHOTO_PX] on its longer
     * side, and returns its [PhotoRef]. The picker's own URI stops working once the
     * permission it came with lapses, so the content cannot point at it.
     */
    suspend fun importPhoto(source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO_PX) sample *= 2
            val bitmap = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val relative = "$DIR/$PHOTOS_DIR/${UUID.randomUUID()}.jpg"
            val target = File(context.filesDir, relative).apply { parentFile?.mkdirs() }
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            PhotoRef.ofFile(relative)
        }.onFailure { Log.e(TAG, "cannot import photo", it) }.getOrNull()
    }

    private fun edit(transform: (KhaoYaiContent) -> KhaoYaiContent) {
        state.update(transform)
        scope.launch(Dispatchers.IO) { save() }
    }

    /**
     * Writes what is current when the lock is taken, not what was current when the save
     * was queued: two quick edits may start their saves in either order, and the file must
     * still end on the later one. Written beside and renamed over, so a crash mid-write
     * leaves the last good copy.
     */
    private suspend fun save() = writeLock.withLock {
        val content = state.value
        runCatching {
            dir.mkdirs()
            val temp = File(dir, "$CONTENT_FILE.tmp")
            temp.writeText(KhaoYaiContentJson.write(content), Charsets.UTF_8)
            if (!temp.renameTo(savedFile)) {
                savedFile.delete()
                check(temp.renameTo(savedFile)) { "rename failed" }
            }
        }.onFailure { Log.e(TAG, "cannot save content", it) }
    }

    private fun loadSaved(): KhaoYaiContent? {
        if (!savedFile.isFile) return null
        return runCatching { KhaoYaiContentJson.parse(savedFile.readText(Charsets.UTF_8)) }
            .onFailure { Log.e(TAG, "saved content unreadable, using the sample", it) }
            .getOrNull()
    }

    private fun loadSample(): KhaoYaiContent = try {
        KhaoYaiContentJson.parse(assetReader.readText(AssetPaths.KHAOYAI_CONTENT))
    } catch (e: Exception) {
        // A broken sample file should leave empty lists, not a crash on the home screen;
        // KhaoYaiContentTest fails the build on the shipped file instead.
        Log.e(TAG, "cannot read ${AssetPaths.KHAOYAI_CONTENT}", e)
        KhaoYaiContent()
    }

    private companion object {
        const val TAG = "KhaoYaiContent"
        const val DIR = "khaoyai"
        const val CONTENT_FILE = "content.json"
        const val PHOTOS_DIR = "photos"
        const val MAX_PHOTO_PX = 1280
        const val JPEG_QUALITY = 82
    }
}
