package th.ac.kmutnb.prachin.map.ui.pathlog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import kotlin.math.cos
import kotlin.math.max

/**
 * The shape of a walk, drawn on its own with no map underneath.
 *
 * Answers the one question the exported file cannot: *what did I actually walk?* A
 * basemap cannot answer it either in the places this app is for - draw a forest track over
 * an empty basemap and you get a line in a beige void, with nothing to tell you whether
 * the shape is the path or the receiver wandering.
 *
 * Drawn to scale and to aspect, with north up, so a loop looks like a loop and a
 * dog-leg like a dog-leg. Start and end are marked because a track that comes back to
 * where it began looks identical to one that never left until you can see which end is
 * which.
 */
@Composable
fun TrackShape(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
    lineColour: Color = MaterialTheme.colorScheme.primary,
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    // Fixed green and red rather than theme roles: the legend below says green and red,
    // and this theme's tertiary is a red the start dot was being drawn in - so the
    // picture said the walk started where it ended.
    val startColour = Color(0xFF2E7D32)
    val endColour = Color(0xFFC62828)

    Column(modifier.fillMaxWidth()) {
      Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
      ) {
        if (points.size < 2) {
            Text(
                text = stringResource(R.string.pathlog_shape_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }

        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            drawRect(surface)

            // Degrees are not square on the ground: a degree of longitude is narrower
            // than one of latitude by cos(lat). Without this a walk due east looks
            // longer than the same walk due north.
            val midLat = points.sumOf { it.lat } / points.size
            val lonScale = cos(Math.toRadians(midLat))

            val xs = points.map { it.lon * lonScale }
            val ys = points.map { it.lat }
            val spanX = max(xs.max() - xs.min(), 1e-9)
            val spanY = max(ys.max() - ys.min(), 1e-9)

            val inset = 16.dp.toPx()
            val usableW = size.width - inset * 2
            val usableH = size.height - inset * 2
            // One scale for both axes, so the drawing keeps the walk's real proportions.
            val scale = minOf(usableW / spanX, usableH / spanY)
            val drawnW = spanX * scale
            val drawnH = spanY * scale
            val offsetX = (size.width - drawnW) / 2f
            val offsetY = (size.height - drawnH) / 2f

            fun project(index: Int) = Offset(
                x = (offsetX + (xs[index] - xs.min()) * scale).toFloat(),
                // Screen y grows downwards; latitude grows north, so it is flipped.
                y = (offsetY + drawnH - (ys[index] - ys.min()) * scale).toFloat(),
            )

            val path = Path().apply {
                val first = project(0)
                moveTo(first.x, first.y)
                for (i in 1 until points.size) {
                    val point = project(i)
                    lineTo(point.x, point.y)
                }
            }
            drawPath(
                path = path,
                color = lineColour,
                style = Stroke(width = 5.dp.toPx(), join = StrokeJoin.Round),
            )

            // Every recorded vertex, so a long straight run reads as two corners rather
            // than as a line somebody drew.
            points.indices.forEach { index ->
                drawCircle(lineColour, radius = 3.dp.toPx(), center = project(index))
            }
            drawCircle(startColour, radius = 7.dp.toPx(), center = project(0))
            drawCircle(endColour, radius = 7.dp.toPx(), center = project(points.lastIndex))
        }

      }

        // Below the drawing rather than over it: an end point that lands in the corner
        // was sitting on top of the words explaining what an end point looks like.
        if (points.size >= 2) {
            Text(
                text = stringResource(
                    R.string.pathlog_shape_legend,
                    GeoUtils.haversineMeters(points.first(), points.last()).toInt(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
