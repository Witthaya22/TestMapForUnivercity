package th.ac.kmutnb.prachin.map.map

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import th.ac.kmutnb.prachin.map.data.assets.AssetPaths
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder

/**
 * Serves the MBTiles pack bundled in `assets/map/` over `127.0.0.1` for `TileSourceMode.BUNDLED`.
 *
 * MapLibre Native fetches every resource by URL; there is no API for handing it a byte array,
 * so a pack that lives inside the APK has to be published over HTTP to be usable at all. The
 * server binds to the loopback interface on a port the OS picks (port 0), which is why
 * `network_security_config.xml` only has to whitelist cleartext for `127.0.0.1` instead of
 * opening it up globally.
 *
 * Two details decide whether the map renders or comes up blank:
 *
 *  * **TMS y-axis.** MBTiles inherits the TMS convention of counting rows from the bottom,
 *    while MapLibre asks for slippy-map rows counted from the top. Without the flip in
 *    [tileFor] the app shows a real map of the wrong place rather than an obvious error.
 *  * **gzip.** Vector tiles are stored gzipped inside MBTiles. Handing those bytes over
 *    without `Content-Encoding: gzip` makes MapLibre try to parse protobuf out of a gzip
 *    stream; it fails silently and the screen stays white. The check in [respondTile] looks
 *    at the magic bytes instead of trusting the file, so a pack built either way works.
 */
class LocalTileServer(
    context: Context,
    private val mbtiles: File,
) : NanoHTTPD(LOOPBACK, 0) {

    private val assets = context.applicationContext.assets
    private var database: SQLiteDatabase? = null

    /** Usable only after [start]; the port is assigned by the OS. */
    val baseUrl: String get() = "http://$LOOPBACK:$listeningPort"

    /** The URL to hand to MapLibre's `Style.Builder().fromUri(...)`. */
    val styleUrl: String get() = "$baseUrl/style.json"

    @Throws(IOException::class)
    override fun start() {
        // SO_REUSEADDR, and do not let a stuck client hold a worker thread forever.
        super.start(SOCKET_READ_TIMEOUT_MS, false)
        database = SQLiteDatabase.openDatabase(
            mbtiles.absolutePath, null, SQLiteDatabase.OPEN_READONLY,
        )
        Log.i(TAG, "serving ${mbtiles.name} at $baseUrl")
    }

    override fun stop() {
        database?.close()
        database = null
        super.stop()
    }

    /**
     * NanoHTTPD would otherwise gzip responses for clients that accept it - including the
     * tiles, which are gzipped already. Double compression is what MapLibre cannot unpick.
     */
    override fun useGzipWhenAccepted(response: Response): Boolean = false

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri.orEmpty()
        return try {
            when {
                path == "/style.json" -> respondStyle()
                path.startsWith("/tiles/") -> respondTile(path)
                path.startsWith("/fonts/") -> respondFont(path)
                path.startsWith("/sprite") -> respondSprite(path)
                else -> notFound("no route for $path")
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to serve $path", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
    }

    // ----------------------------------------------------------------------------------
    // Routes
    // ----------------------------------------------------------------------------------

    /**
     * Rewrites the bundled style so every URL in it points back at this server.
     *
     * The style shipped by `tools/build_tiles.sh` references its sources, glyphs and sprite
     * with relative or placeholder URLs; MapLibre resolves them itself and would go to the
     * network. Rewriting here rather than at build time keeps the port out of the asset,
     * which is necessary because the port is only known once the socket is bound.
     */
    private fun respondStyle(): Response {
        val style = JSONObject(assets.open(AssetPaths.BUNDLED_STYLE).use {
            it.readBytes().toString(Charsets.UTF_8)
        })

        style.put("glyphs", "$baseUrl/fonts/{fontstack}/{range}.pbf")
        style.put("sprite", "$baseUrl/sprite")

        val sources = style.optJSONObject("sources")
        if (sources != null) {
            for (name in sources.keys()) {
                val source = sources.optJSONObject(name) ?: continue
                if (source.optString("type") != "vector") continue
                // `url` would send MapLibre off to fetch a TileJSON document; the explicit
                // tile template replaces it.
                source.remove("url")
                source.put("tiles", listOf("$baseUrl/tiles/{z}/{x}/{y}.pbf").toJsonArray())
                source.put("scheme", "xyz") // the y-flip happens here, not in the client
            }
        }

        return newFixedLengthResponse(
            Response.Status.OK, "application/json", style.toString(),
        )
    }

    private fun respondTile(path: String): Response {
        // /tiles/{z}/{x}/{y}.pbf
        val parts = path.removePrefix("/tiles/").split('/')
        if (parts.size != 3) return notFound("malformed tile path $path")
        val zoom = parts[0].toIntOrNull()
        val column = parts[1].toIntOrNull()
        val row = parts[2].substringBefore('.').toIntOrNull()
        if (zoom == null || column == null || row == null) return notFound("bad tile coords $path")

        val bytes = tileFor(zoom, column, row)
            // A missing tile is normal: the pack only covers the campus bbox, and MapLibre
            // asks for the whole viewport. 204 tells it "nothing here" without logging noise.
            ?: return newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PBF, null)

        val response = newFixedLengthResponse(
            Response.Status.OK, MIME_PBF, ByteArrayInputStream(bytes), bytes.size.toLong(),
        )
        if (bytes.size >= 2 && bytes[0] == GZIP_MAGIC_0 && bytes[1] == GZIP_MAGIC_1) {
            response.addHeader("Content-Encoding", "gzip")
        }
        return response
    }

    private fun respondFont(path: String): Response {
        // /fonts/{fontstack}/{range}.pbf, where the stack may list several fonts by comma.
        val remainder = path.removePrefix("/fonts/")
        val separator = remainder.lastIndexOf('/')
        if (separator <= 0) return notFound("malformed font path $path")
        val stack = URLDecoder.decode(remainder.substring(0, separator), "UTF-8")
        val range = remainder.substring(separator + 1)

        // MapLibre asks for the whole stack at once and expects the first font that has the
        // range. Thai labels come out of whichever face in the stack carries Thai glyphs.
        for (font in stack.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            val asset = "${AssetPaths.BUNDLED_FONTS_DIR}/$font/$range"
            val bytes = readAssetOrNull(asset) ?: continue
            return newFixedLengthResponse(
                Response.Status.OK, MIME_PBF, ByteArrayInputStream(bytes), bytes.size.toLong(),
            )
        }
        return notFound("no font asset for $stack/$range")
    }

    private fun respondSprite(path: String): Response {
        // /sprite.json, /sprite.png, /sprite@2x.json, /sprite@2x.png
        val name = path.removePrefix("/").ifEmpty { return notFound(path) }
        val bytes = readAssetOrNull("map/$name") ?: return notFound("no sprite asset $name")
        val mime = if (name.endsWith(".png")) "image/png" else "application/json"
        return newFixedLengthResponse(
            Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong(),
        )
    }

    // ----------------------------------------------------------------------------------
    // Storage
    // ----------------------------------------------------------------------------------

    private fun tileFor(zoom: Int, column: Int, row: Int): ByteArray? {
        val db = database ?: return null
        // MBTiles rows count from the bottom (TMS); MapLibre counts from the top.
        val tmsRow = (1 shl zoom) - 1 - row
        return db.rawQuery(
            "SELECT tile_data FROM tiles" +
                " WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? LIMIT 1",
            arrayOf(zoom.toString(), column.toString(), tmsRow.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getBlob(0) else null
        }
    }

    private fun readAssetOrNull(path: String): ByteArray? = try {
        assets.open(path).use(InputStream::readBytes)
    } catch (_: IOException) {
        null
    }

    private fun notFound(detail: String): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, detail)

    private fun List<String>.toJsonArray() = org.json.JSONArray().also { array ->
        forEach(array::put)
    }

    companion object {
        private const val TAG = "LocalTileServer"

        /** Loopback only. Nothing outside the device can reach this server. */
        const val LOOPBACK = "127.0.0.1"

        private const val SOCKET_READ_TIMEOUT_MS = 10_000
        private const val MIME_PBF = "application/x-protobuf"
        private const val GZIP_MAGIC_0 = 0x1f.toByte()
        private const val GZIP_MAGIC_1 = 0x8b.toByte()

        /**
         * Copies the MBTiles pack out of `assets/` into app storage, returning the file.
         *
         * SQLite needs a real path and cannot read a compressed asset stream, so the pack has
         * to be materialised once. The copy is skipped when a file of the same size is already
         * there, which makes this cheap on every launch after the first.
         */
        @Throws(IOException::class)
        fun materialiseMbtiles(context: Context): File {
            val target = File(context.applicationContext.filesDir, "kmutnb.mbtiles")
            val assets = context.applicationContext.assets
            // openFd only works on an asset stored uncompressed, which is what the
            // `noCompress += "mbtiles"` in build.gradle.kts guarantees. If a build ever
            // loses that setting, fall back to copying once instead of failing outright.
            val expected = runCatching {
                assets.openFd(AssetPaths.BUNDLED_MBTILES).use { it.length }
            }.getOrNull()
            if (target.isFile && (expected == null || target.length() == expected)) return target

            assets.open(AssetPaths.BUNDLED_MBTILES).use { input ->
                target.outputStream().use(input::copyTo)
            }
            return target
        }
    }
}
