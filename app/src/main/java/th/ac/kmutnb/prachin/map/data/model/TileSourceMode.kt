package th.ac.kmutnb.prachin.map.data.model

/** Where map tiles come from. See `docs/OFFLINE.md`. */
enum class TileSourceMode(val id: String) {
    /**
     * Default. Downloads an offline pack from the configured style once, then renders
     * entirely from MapLibre's local database.
     */
    OFFLINE_PACK("offline_pack"),

    /**
     * Renders from an MBTiles file shipped inside the APK, served over loopback by
     * `LocalTileServer`. Never touches the network, not even on first run.
     */
    BUNDLED("bundled"),
    ;

    companion object {
        fun fromId(id: String?): TileSourceMode = entries.firstOrNull { it.id == id } ?: OFFLINE_PACK
    }
}
