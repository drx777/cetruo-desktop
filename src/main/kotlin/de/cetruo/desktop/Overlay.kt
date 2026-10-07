package de.cetruo.desktop

import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import java.awt.image.BufferedImage
import javafx.scene.paint.Color
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.roundToInt
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.ImageTranscoder

/** A user-extensible SVG decoration stored under overlays/. */
data class BackgroundOverlay(
    val name: String,
    val path: Path?,
    val description: String = ""
)

object OverlayRepository {
    const val DIRECTORY_NAME = "overlays"

    private val candidates: List<Path>
        get() = listOf(
            Paths.get(DIRECTORY_NAME),
            Paths.get(System.getProperty("user.dir"), DIRECTORY_NAME)
        ).map { it.toAbsolutePath().normalize() }.distinct()

    private data class CacheKey(val path: Path, val width: Int, val height: Int, val tint: String)
    private data class CacheEntry(val size: Long, val modified: Long, val image: Image)
    private val rasterCache = object : LinkedHashMap<CacheKey, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, CacheEntry>?): Boolean = size > 24
    }

    fun load(): Pair<List<BackgroundOverlay>, List<String>> {
        val directory = candidates.firstOrNull { Files.isDirectory(it) }
            ?: return listOf(BackgroundOverlay("None", null, "No background overlay")) to emptyList()

        val errors = mutableListOf<String>()
        val overlays = mutableListOf(BackgroundOverlay("None", null, "No background overlay"))
        Files.list(directory).use { stream ->
            stream.filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".svg", ignoreCase = true) }
                .sorted(compareBy { path -> path.fileName.toString().lowercase() })
                .forEach { path ->
                    val name = path.fileName.toString().substringBeforeLast('.')
                        .replace('_', ' ').replace('-', ' ').split(' ')
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
                    overlays.add(BackgroundOverlay(name, path, "SVG overlay: ${path.fileName}"))
                }
        }
        return overlays to errors
    }

    fun resolve(fileName: String): Path? {
        if (fileName.isBlank()) return null
        return candidates.asSequence().mapNotNull { directory ->
            val path = directory.resolve(fileName).normalize()
            path.takeIf { it.startsWith(directory) && Files.isRegularFile(it) }
        }.firstOrNull()
    }

    fun rasterize(path: Path, width: Double, height: Double, tint: String? = null): Image? {
        val absolute = path.toAbsolutePath().normalize()
        val w = width.coerceAtLeast(1.0).toInt()
        val h = height.coerceAtLeast(1.0).toInt()
        val size = runCatching { Files.size(absolute) }.getOrDefault(-1L)
        val modified = runCatching { Files.getLastModifiedTime(absolute).toMillis() }.getOrDefault(-1L)
        val tintKey = tint?.trim()?.uppercase() ?: ""
        val key = CacheKey(absolute, w, h, tintKey)
        rasterCache[key]?.takeIf { it.size == size && it.modified == modified }?.let { return it.image }
        val image = runCatching { rasterizeSvg(absolute, w.toDouble(), h.toDouble(), tintKey) }.getOrNull() ?: return null
        rasterCache[key] = CacheEntry(size, modified, image)
        return image
    }

    private fun rasterizeSvg(path: Path, width: Double, height: Double, tint: String): Image {
        class BufferedImageTranscoder : ImageTranscoder() {
            private var buffered: BufferedImage? = null
            override fun createImage(w: Int, h: Int): BufferedImage {
                val image = BufferedImage(w.coerceAtLeast(1), h.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
                buffered = image
                return image
            }
            override fun writeImage(image: BufferedImage, output: TranscoderOutput) = Unit
            fun image(): BufferedImage = requireNotNull(buffered)
        }
        val transcoder = BufferedImageTranscoder()
        transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, width.toFloat())
        transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, height.toFloat())
        transcoder.transcode(TranscoderInput(path.toUri().toString()), TranscoderOutput(ByteArrayOutputStream()))
        val raster = transcoder.image()
        val tinted = if (tint.isBlank()) raster else tintAlphaMask(raster, tint)
        return SwingFXUtils.toFXImage(tinted, null)
    }

    private fun tintAlphaMask(source: BufferedImage, tint: String): BufferedImage {
        val color = Color.web(tint)
        val rr = (color.red * 255.0).roundToInt().coerceIn(0, 255)
        val gg = (color.green * 255.0).roundToInt().coerceIn(0, 255)
        val bb = (color.blue * 255.0).roundToInt().coerceIn(0, 255)
        val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val argb = source.getRGB(x, y)
                val alpha = (argb ushr 24) and 0xFF
                out.setRGB(x, y, (alpha shl 24) or (rr shl 16) or (gg shl 8) or bb)
            }
        }
        return out
    }

    fun clearCache() = rasterCache.clear()
}
