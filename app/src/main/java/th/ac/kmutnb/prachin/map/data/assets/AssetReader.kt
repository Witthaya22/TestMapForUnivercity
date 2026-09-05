package th.ac.kmutnb.prachin.map.data.assets

import android.content.Context
import java.io.IOException

/** Thin wrapper over `AssetManager` so repositories stay testable behind an interface. */
interface AssetReader {
    @Throws(IOException::class)
    fun readText(path: String): String

    fun exists(path: String): Boolean
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
}

/** Asset paths, so the strings are not repeated across repositories. */
object AssetPaths {
    const val CAMPUS_CONFIG = "config/campus_config.json"
    const val POIS = "data/pois.geojson"
    const val PATHS = "data/paths.geojson"

    const val BUNDLED_STYLE = "map/style.json"
    const val BUNDLED_MBTILES = "map/kmutnb.mbtiles"
    const val BUNDLED_FONTS_DIR = "map/fonts"
    const val BUNDLED_SPRITE_JSON = "map/sprite.json"
}
