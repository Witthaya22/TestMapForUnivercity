package th.ac.kmutnb.prachin.map.map

import th.ac.kmutnb.prachin.map.data.model.TileSourceMode
import th.ac.kmutnb.prachin.map.data.repository.CampusRepository

/**
 * Decides which style MapLibre should load.
 *
 * In [TileSourceMode.OFFLINE_PACK] that is the remote style URL - MapLibre serves it, and
 * every tile and glyph it references, from the downloaded offline database, so nothing is
 * actually fetched over the network once the pack exists.
 */
class MapStyleProvider(private val campusRepository: CampusRepository) {

    suspend fun styleUri(): String = campusRepository.config().styleUrl
}
