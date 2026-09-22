package th.ac.kmutnb.prachin.map.data.assets

import android.content.Context
import java.io.IOException

/** Thin wrapper over `AssetManager` so repositories stay testable behind an interface. */
interface AssetReader {
    @Throws(IOException::class)
    fun readText(path: String): String

    fun exists(path: String): Boolean

    /**
     * File names directly inside [path], or empty when the folder is absent.
     *
     * Empty rather than an error: a folder people are invited to drop files into is
     * normally empty, and that is not a broken install.
     */
    fun list(path: String): List<String>
}

class AndroidAssetReader(context: Context) : AssetReader {

    private val assets = context.applicationContext.assets

    override fun readText(path: String): String =
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    override fun exists(path: String): Boolean = try {
        assets.open(path).close()
        true
    } catch (_: IOException) {
        false
    }

    override fun list(path: String): List<String> = try {
        assets.list(path)?.toList().orEmpty()
    } catch (_: IOException) {
        emptyList()
    }
}

/** Asset paths, so the strings are not repeated across repositories. */
object AssetPaths {
    const val CAMPUS_CONFIG = "config/campus_config.json"
    const val POIS = "data/pois.geojson"
    const val PATHS = "data/paths.geojson"

    /** Drop audio files in here and they become warning sounds; see `docs/HAZARDS.md`. */
    const val HAZARD_SOUNDS_DIR = "sounds"

    const val BUNDLED_STYLE = "map/style.json"
    const val BUNDLED_MBTILES = "map/kmutnb.mbtiles"
    const val BUNDLED_FONTS_DIR = "map/fonts"
    const val BUNDLED_SPRITE_JSON = "map/sprite.json"
}
