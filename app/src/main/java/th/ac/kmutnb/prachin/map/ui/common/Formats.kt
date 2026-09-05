package th.ac.kmutnb.prachin.map.ui.common

import android.content.Context
import th.ac.kmutnb.prachin.map.R
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Thai-facing formatting of distances, durations and file sizes. */
object Formats {

    /**
     * `240 ม.` below a kilometre, `1.2 กม.` above it.
     *
     * Under 100 m the exact metre count is shown rather than being rounded to the nearest
     * ten: that band is where the user is closing on a waypoint and a value that steps
     * 30 -> 20 -> 10 -> arrived reads as broken. From 100 m up the last digit is only GPS
     * noise, so it is rounded away to stop the panel flickering.
     */
    fun distance(context: Context, meters: Double): String = when {
        meters >= 1_000.0 -> context.getString(
            R.string.distance_kilometers,
            String.format(Locale.getDefault(), "%.1f", meters / 1_000.0),
        )

        meters >= 100.0 -> context.getString(
            R.string.distance_meters,
            (meters / 10.0).roundToInt() * 10,
        )

        else -> context.getString(R.string.distance_meters, meters.roundToInt().coerceAtLeast(0))
    }

    /** `~3 นาที`, or hours and minutes for anything over an hour. */
    fun duration(context: Context, seconds: Int): String {
        if (seconds < 60) return context.getString(R.string.duration_less_than_a_minute)
        val totalMinutes = Math.ceil(seconds / 60.0).toInt()
        if (totalMinutes < 60) return context.getString(R.string.duration_minutes, totalMinutes)
        return context.getString(
            R.string.duration_hours_minutes,
            totalMinutes / 60,
            totalMinutes % 60,
        )
    }

    /** Binary-ish file size for the offline pack screens. */
    fun fileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
    }

    /** Percentage clamped to 0..100 for progress readouts. */
    fun percent(fraction: Double): Int = (fraction * 100.0).roundToLong().toInt().coerceIn(0, 100)
}
