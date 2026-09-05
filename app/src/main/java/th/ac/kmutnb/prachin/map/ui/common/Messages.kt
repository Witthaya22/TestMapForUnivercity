package th.ac.kmutnb.prachin.map.ui.common

import androidx.annotation.StringRes
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.config.ConfigProblem
import th.ac.kmutnb.prachin.map.data.geojson.GeoJsonIssue
import th.ac.kmutnb.prachin.map.data.model.PoiCategory
import th.ac.kmutnb.prachin.map.navigation.model.PathType

/**
 * Maps domain enums onto the Thai wording in `strings.xml`.
 *
 * The data and navigation layers deliberately carry enums rather than messages, so nothing
 * below the UI has to know about resources or the display language.
 */

@get:StringRes
val ConfigProblem.messageRes: Int
    get() = when (this) {
        ConfigProblem.PLACEHOLDER_COORDINATES -> R.string.config_error_placeholder
        ConfigProblem.BBOX_INVERTED -> R.string.config_error_bbox_inverted
        ConfigProblem.OUT_OF_DEGREE_RANGE -> R.string.config_error_out_of_range
        ConfigProblem.CENTER_OUTSIDE_BBOX -> R.string.config_error_center_outside
        ConfigProblem.ZOOM_RANGE_INVALID -> R.string.config_error_zoom_range
        ConfigProblem.MAX_ZOOM_TOO_HIGH -> R.string.config_error_max_zoom
        ConfigProblem.STYLE_NEEDS_API_KEY -> R.string.config_error_api_key
        ConfigProblem.MALFORMED -> R.string.config_error_malformed
    }

@get:StringRes
val GeoJsonIssue.Reason.messageRes: Int
    get() = when (this) {
        GeoJsonIssue.Reason.MISSING_ID -> R.string.geojson_error_missing_id
        GeoJsonIssue.Reason.DUPLICATE_ID -> R.string.geojson_error_duplicate_id
        GeoJsonIssue.Reason.MISSING_NAME -> R.string.geojson_error_missing_name
        GeoJsonIssue.Reason.WRONG_GEOMETRY -> R.string.geojson_error_wrong_geometry
        GeoJsonIssue.Reason.BAD_COORDINATES -> R.string.geojson_error_bad_coordinates
        GeoJsonIssue.Reason.OUT_OF_DEGREE_RANGE -> R.string.geojson_error_out_of_range
        GeoJsonIssue.Reason.UNKNOWN_PATH_TYPE -> R.string.geojson_error_unknown_path_type
    }

@get:StringRes
val PoiCategory.labelRes: Int
    get() = when (this) {
        PoiCategory.GATE -> R.string.category_gate
        PoiCategory.DORM -> R.string.category_dorm
        PoiCategory.ACADEMIC -> R.string.category_academic
        PoiCategory.CANTEEN -> R.string.category_canteen
        PoiCategory.SPORT -> R.string.category_sport
        PoiCategory.PARKING -> R.string.category_parking
        PoiCategory.SERVICE -> R.string.category_service
        PoiCategory.CUSTOM -> R.string.category_custom
    }

@get:StringRes
val PathType.labelRes: Int
    get() = when (this) {
        PathType.FOOTWAY -> R.string.track_type_footway
        PathType.ROAD -> R.string.track_type_road
        PathType.CROSSING -> R.string.track_type_crossing
        PathType.STAIRS -> R.string.track_type_stairs
    }
