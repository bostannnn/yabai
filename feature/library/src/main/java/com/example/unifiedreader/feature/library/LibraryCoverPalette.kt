package com.example.unifiedreader.feature.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.ui.theme.AppCoverColors
import com.example.unifiedreader.ui.theme.AppMotion
import com.example.unifiedreader.ui.theme.AppCoverSwatches
import com.example.unifiedreader.ui.theme.Hsl
import com.example.unifiedreader.ui.theme.appCoverColors
import com.example.unifiedreader.ui.theme.isAppThemeDark
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The colours of [item]'s cover for this theme (see [appCoverColors]), or null while they load,
 * when the book has no cover image, or when the cover cannot be read. The grid and lists stay
 * neutral; this is for the reading card, the book page and the reader.
 */
@Composable
fun rememberLibraryCoverColors(
    item: ReadableItem?,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
): AppCoverColors? {
    val imageUrl = item?.let { coverImageUrlProvider(it, LibraryCoverImageVariant.Grid) }?.takeIf(String::isNotBlank)
    val context = LocalContext.current.applicationContext
    // A palette already in memory is there on the first frame, so a known book never flashes.
    val swatches by produceState(LibraryCoverPalettes.inMemory(imageUrl), imageUrl) {
        value = imageUrl?.let { url -> LibraryCoverPalettes.swatches(context, url) }
    }
    val dark = isAppThemeDark
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    return remember(swatches, dark, primary, onPrimary) {
        swatches?.let { appCoverColors(it, dark = dark, fallbackAccent = primary, fallbackOnAccent = onPrimary) }
    }
}

/**
 * Cover palettes, read once per cover from a small decode and kept in memory and in the app's
 * cache directory, next to Coil's copy of the cover.
 */
internal object LibraryCoverPalettes {
    private val memory = LruCache<String, Entry>(MemoryEntries)

    /** What memory already knows about [imageUrl]'s cover, without loading anything. */
    fun inMemory(imageUrl: String?): AppCoverSwatches? = imageUrl?.let { memory.get(it)?.swatches }

    suspend fun swatches(context: Context, imageUrl: String): AppCoverSwatches? {
        memory.get(imageUrl)?.let { return it.swatches }
        val stored = withContext(Dispatchers.IO) { readStored(context, imageUrl) }
        if (stored != null) {
            memory.put(imageUrl, stored)
            return stored.swatches
        }
        val start = LibraryPerfLog.startNanos()
        val pixels = decodeSmallCover(context, imageUrl) ?: return null
        val swatches = withContext(Dispatchers.Default) { coverSwatches(pixels) }
        LibraryPerfLog.logSince("cover.palette extract [${pixels.size} px]", start)
        val entry = Entry(swatches)
        memory.put(imageUrl, entry)
        withContext(Dispatchers.IO) { writeStored(context, imageUrl, entry) }
        return swatches
    }

    /** About 60 × 90 pixels of the cover, through Coil so a cover already on disk is not fetched again. */
    private suspend fun decodeSmallCover(context: Context, imageUrl: String): IntArray? {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(DecodeWidth, DecodeHeight)
            .scale(Scale.FILL)
            .precision(Precision.EXACT)
            .allowHardware(false)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val bitmap = (context.imageLoader.execute(request) as? SuccessResult)
            ?.drawable
            ?.let { it as? BitmapDrawable }
            ?.bitmap
            ?: return null
        return IntArray(bitmap.width * bitmap.height).also { pixels ->
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }

    private fun storedFile(context: Context, imageUrl: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(imageUrl.toByteArray())
        val name = digest.joinToString("") { byte -> "%02x".format(byte) }
        return File(File(context.cacheDir, StoreDirectory), name)
    }

    private fun readStored(context: Context, imageUrl: String): Entry? = runCatching {
        val file = storedFile(context, imageUrl)
        if (!file.exists()) return null
        val text = file.readText().trim()
        if (text == NoPalette) return Entry(null)
        val parts = text.split(',')
        Entry(
            AppCoverSwatches(
                ambient = parts[0].toLong(16).toInt(),
                ambientSecond = parts[1].toLong(16).toInt(),
                accent = parts.getOrNull(2)?.takeIf(String::isNotEmpty)?.toLong(16)?.toInt(),
            ),
        )
    }.getOrNull()

    private fun writeStored(context: Context, imageUrl: String, entry: Entry) {
        runCatching {
            val file = storedFile(context, imageUrl)
            file.parentFile?.mkdirs()
            val swatches = entry.swatches
            file.writeText(
                if (swatches == null) {
                    NoPalette
                } else {
                    listOf(swatches.ambient, swatches.ambientSecond, swatches.accent)
                        .joinToString(",") { value -> value?.let { Integer.toHexString(it) }.orEmpty() }
                },
            )
        }
    }

    /** Null [swatches] remembers a cover that has none, so it is not decoded again. */
    private class Entry(val swatches: AppCoverSwatches?)

    private const val MemoryEntries = 256
    private const val DecodeWidth = 60
    private const val DecodeHeight = 90
    private const val StoreDirectory = "cover_palettes"
    private const val NoPalette = "none"
}

/**
 * A cover's swatches from its pixels: the colours grouped into clusters of near neighbours, the
 * two largest as the ambient pair, and as the accent the most saturated cluster among those neither
 * too dark nor too light (L 0.22 to 0.78), weighted by its share of the cover. Saturation counts
 * far more than area (squared against a fourth root), so a small coral sun beats a wide dull sky.
 * A cover with no colourful cluster gets no accent. Null when no pixel is opaque.
 */
internal fun coverSwatches(pixels: IntArray): AppCoverSwatches? {
    val counts = IntArray(BucketCount)
    val red = LongArray(BucketCount)
    val green = LongArray(BucketCount)
    val blue = LongArray(BucketCount)
    var total = 0
    for (pixel in pixels) {
        if ((pixel ushr 24) < OpaqueAlpha) continue
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val bucket = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
        counts[bucket]++
        red[bucket] += r.toLong()
        green[bucket] += g.toLong()
        blue[bucket] += b.toLong()
        total++
    }
    if (total == 0) return null
    val clusters = mutableListOf<Cluster>()
    counts.indices
        .filter { counts[it] > 0 }
        .sortedByDescending { counts[it] }
        .forEach { bucket ->
            val count = counts[bucket]
            val r = (red[bucket] / count).toFloat()
            val g = (green[bucket] / count).toFloat()
            val b = (blue[bucket] / count).toFloat()
            val near = clusters.firstOrNull { it.distanceSquared(r, g, b) <= MergeDistanceSquared }
                ?: clusters.takeIf { it.size >= MaxClusters }?.minBy { it.distanceSquared(r, g, b) }
            if (near != null) near.add(r, g, b, count) else clusters += Cluster(r, g, b, count)
        }
    clusters.sortByDescending(Cluster::count)
    val ambient = clusters[0].argb()
    val ambientSecond = clusters.getOrNull(1)?.argb() ?: ambient
    val accent = clusters
        .filter { cluster -> cluster.count >= total * AccentMinShare }
        .map { cluster -> cluster to Hsl.of(cluster.argb()) }
        .filter { (_, hsl) -> hsl.l in AccentMinLightness..AccentMaxLightness && hsl.s >= AccentMinSaturation }
        .maxByOrNull { (cluster, hsl) -> hsl.s * hsl.s * sqrt(sqrt(cluster.count.toFloat() / total)) }
        ?.first
        ?.argb()
    return AppCoverSwatches(ambient = ambient, ambientSecond = ambientSecond, accent = accent)
}

private class Cluster(var r: Float, var g: Float, var b: Float, var count: Int) {
    fun add(r2: Float, g2: Float, b2: Float, count2: Int) {
        val sum = count + count2
        r = (r * count + r2 * count2) / sum
        g = (g * count + g2 * count2) / sum
        b = (b * count + b2 * count2) / sum
        count = sum
    }

    fun distanceSquared(r2: Float, g2: Float, b2: Float): Float =
        (r - r2) * (r - r2) + (g - g2) * (g - g2) + (b - b2) * (b - b2)

    fun argb(): Int = (0xFF shl 24) or
        (r.toInt().coerceIn(0, 255) shl 16) or
        (g.toInt().coerceIn(0, 255) shl 8) or
        b.toInt().coerceIn(0, 255)
}

private const val BucketCount = 4096
private const val OpaqueAlpha = 128
private const val MaxClusters = 16
private const val MergeDistanceSquared = 40f * 40f
private const val AccentMinShare = 0.01f
private const val AccentMinLightness = 0.22f
private const val AccentMaxLightness = 0.78f
private const val AccentMinSaturation = 0.25f

/**
 * [target], with every colour easing to its new value (the effects spring), so a palette that
 * arrives after the first frame fades in instead of switching.
 */
@Composable
internal fun animateCoverColors(target: AppCoverColors): AppCoverColors {
    @Composable
    fun animate(color: Color, label: String): Color =
        animateColorAsState(targetValue = color, animationSpec = AppMotion.effects(), label = label).value
    return AppCoverColors(
        tintStart = animate(target.tintStart, "coverTintStart"),
        tintEnd = animate(target.tintEnd, "coverTintEnd"),
        content = animate(target.content, "coverContent"),
        track = animate(target.track, "coverTrack"),
        accent = animate(target.accent, "coverAccent"),
        onAccent = animate(target.onAccent, "coverOnAccent"),
        accentContainer = animate(target.accentContainer, "coverAccentContainer"),
        onAccentContainer = animate(target.onAccentContainer, "coverOnAccentContainer"),
    )
}

/**
 * The cover tint behind a card or a page: the first ambient colour, with the second glowing in
 * from the far corner. Gradients only; no live blur.
 */
internal fun Modifier.coverTint(colors: AppCoverColors): Modifier = drawBehind {
    drawRect(colors.tintStart)
    drawRect(
        Brush.radialGradient(
            colors = listOf(colors.tintEnd, colors.tintEnd.copy(alpha = 0f)),
            center = Offset(size.width, size.height),
            radius = max(size.width, size.height) * CoverTintReach,
        ),
    )
}

private const val CoverTintReach = 0.95f
