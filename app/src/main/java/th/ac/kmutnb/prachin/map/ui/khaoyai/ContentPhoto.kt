package th.ac.kmutnb.prachin.map.ui.khaoyai

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.khaoyai.PhotoCredit
import th.ac.kmutnb.prachin.map.data.khaoyai.PhotoRef
import java.io.File

/**
 * A trail, species or news photo, cropped to fill its box. Until it has loaded, or when
 * there is none, the [fallbackEmoji] on [brush] stands in, so a card never shows a hole.
 *
 * Decoded off the main thread at roughly the size it is drawn, and kept in a small cache:
 * a list scrolling past twenty photos must not decode twenty full-size JPEGs every pass.
 */
@Composable
fun ContentPhoto(
    photo: String,
    fallbackEmoji: String,
    modifier: Modifier = Modifier,
    brush: Brush = ForestBrush,
    contentDescription: String? = null,
    /** With no photo, show the drawn Khao Yai landscape behind the emoji instead of a flat colour. */
    landscapeFallback: Boolean = false,
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.background(brush), contentAlignment = Alignment.Center) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt().coerceIn(64, MAX_DECODE_PX)
        // produceState keeps its value when the keys change, so clear it to what is cached
        // for the new photo first; otherwise an edited photo would keep showing the old one.
        val bitmap by produceState<ImageBitmap?>(initialValue = PhotoCache.peek(photo, widthPx), photo, widthPx) {
            value = PhotoCache.peek(photo, widthPx)
            if (photo.isNotBlank() && value == null) value = PhotoCache.load(context, photo, widthPx)
        }
        val loaded = bitmap
        if (loaded == null) {
            if (landscapeFallback) {
                Image(
                    painter = painterResource(R.drawable.bg_khaoyai_hero),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(fallbackEmoji, fontSize = (maxHeight.value * 0.4f).coerceIn(18f, 72f).sp)
        } else {
            Image(
                bitmap = loaded,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** "ภาพ: author · licence" under a bundled photo; nothing for an admin's own photo. */
@Composable
fun PhotoCreditLine(photo: String, modifier: Modifier = Modifier) {
    val credit = rememberPhotoCredit(photo) ?: return
    Text(
        text = stringResource(R.string.photo_credit, credit.author, credit.license),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun rememberPhotoCredit(photo: String): PhotoCredit? {
    val context = LocalContext.current
    return remember(photo) {
        (context.applicationContext as MapApplication).container.khaoYaiContentRepository.photoCredits[photo]
    }
}

private object PhotoCache {
    // Sized in kilobytes of decoded pixels: about a dozen card-sized photos.
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_KB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4 / 1024
    }

    private fun key(photo: String, widthPx: Int) = "$photo@${bucket(widthPx)}"

    /** Width rounded up to a few sizes, so a card and its detail page share a decode. */
    private fun bucket(widthPx: Int) = listOf(256, 512, 1024).firstOrNull { it >= widthPx } ?: MAX_DECODE_PX

    fun peek(photo: String, widthPx: Int): ImageBitmap? = if (photo.isBlank()) null else cache.get(key(photo, widthPx))

    suspend fun load(context: Context, photo: String, widthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(key(photo, widthPx))?.let { return@withContext it }
        runCatching {
            fun open() = if (PhotoRef.isFile(photo)) {
                File(context.filesDir, PhotoRef.filePath(photo)).inputStream()
            } else {
                context.assets.open(photo)
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open().use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= bucket(widthPx)) sample *= 2
            open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?.asImageBitmap()
        }.getOrNull()?.also { cache.put(key(photo, widthPx), it) }
    }
}

private const val MAX_DECODE_PX = 1280
private const val CACHE_KB = 24 * 1024
