package th.ac.kmutnb.prachin.map.data.khaoyai

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import th.ac.kmutnb.prachin.map.data.assets.AssetPaths
import th.ac.kmutnb.prachin.map.data.assets.AssetReader

/**
 * The trails, news and species the app shows, editable from the admin screen.
 *
 * shortcut: edits live in memory only and are lost when the process dies; move to Room
 * (a migration from version 8) or a backend once admin edits must survive a restart.
 */
class KhaoYaiContentRepository(private val assetReader: AssetReader) {

    // Lazy so the small asset is read when content is first shown, not in Application.onCreate.
    private val state by lazy { MutableStateFlow(loadSample()) }

    val content: StateFlow<KhaoYaiContent> get() = state.asStateFlow()

    fun upsertTrail(trail: Trail) =
        state.update { it.copy(trails = it.trails.upsertBy(trail) { t -> t.id }) }

    fun deleteTrail(id: String) =
        state.update { it.copy(trails = it.trails.filterNot { t -> t.id == id }) }

    fun upsertNews(item: NewsItem) =
        state.update { it.copy(news = it.news.upsertBy(item) { n -> n.id }) }

    fun deleteNews(id: String) =
        state.update { it.copy(news = it.news.filterNot { n -> n.id == id }) }

    fun upsertSpecies(item: Species) =
        state.update { it.copy(species = it.species.upsertBy(item) { s -> s.id }) }

    fun deleteSpecies(id: String) =
        state.update { it.copy(species = it.species.filterNot { s -> s.id == id }) }

    /** Throws away every admin edit and goes back to the bundled sample. */
    fun resetToSample() {
        state.value = loadSample()
    }

    private fun loadSample(): KhaoYaiContent = try {
        KhaoYaiContentParser.parse(assetReader.readText(AssetPaths.KHAOYAI_CONTENT))
    } catch (e: Exception) {
        // A broken sample file should leave empty lists, not a crash on the home screen;
        // KhaoYaiContentTest fails the build on the shipped file instead.
        Log.e(TAG, "cannot read ${AssetPaths.KHAOYAI_CONTENT}", e)
        KhaoYaiContent()
    }

    private companion object {
        const val TAG = "KhaoYaiContent"
    }
}
