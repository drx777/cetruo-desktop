package com.example.cardforge

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.ImageTranscoder

data class TemplateRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val radius: Double = 0.0
)

data class TemplateText(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val align: String = "LEFT"
)

data class TemplateVisualStyle(
    val outerFrameInset: Double = 8.0,
    val innerFrameInset: Double = 16.0,
    val materialDepth: Double = 3.0,
    val railDepth: Double = 2.0,
    val railStrokeWidth: Double = 1.5,
    val descriptionDepth: Double = 4.0,
    val statsJewelInset: Double = 4.0,
    val statsJewelCut: Double = 10.0,
    val rarityFrames: Boolean = true
)

data class CardTemplate(
    val name: String,
    val description: String = "",
    val svgFile: String,
    val width: Double,
    val height: Double,
    val art: TemplateRect,
    val titleBox: TemplateRect,
    val titleText: TemplateText,
    val costText: TemplateText,
    val typeBox: TemplateRect,
    val typeText: TemplateText,
    val rarityText: TemplateText,
    val descriptionBox: TemplateRect,
    val descriptionHeading: TemplateText,
    val descriptionText: TemplateText,
    val flavorText: TemplateText,
    val footerText: TemplateText,
    val statsBox: TemplateRect,
    val statsText: TemplateText,
    val visualStyle: TemplateVisualStyle = TemplateVisualStyle()
)

object TemplateRepository {
    const val DIRECTORY_NAME = "templates"

    private val candidates: List<Path>
        get() = buildList {
            runCatching {
                Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
            }.getOrNull()?.let { add(it.resolve(DIRECTORY_NAME).normalize()) }
            runCatching {
                val location = Paths.get(TemplateRepository::class.java.protectionDomain.codeSource.location.toURI())
                    .toAbsolutePath().normalize()
                val base = if (Files.isDirectory(location)) location else location.parent
                if (base != null) add(base.resolve(DIRECTORY_NAME).normalize())
            }.getOrNull()
            add(Paths.get(DIRECTORY_NAME).toAbsolutePath().normalize())
        }.distinct()

    private data class CacheEntry(val size: Long, val modified: Long, val image: Image)
    private val imageCache = object : LinkedHashMap<Path, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Path, CacheEntry>?): Boolean = size > 12
    }

    fun load(): Pair<List<CardTemplate>, List<String>> {
        val directory = candidates.firstOrNull { Files.isDirectory(it) }
            ?: return emptyList<CardTemplate>() to emptyList()
        val errors = mutableListOf<String>()
        val templates = mutableListOf<CardTemplate>()
        Files.list(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json", ignoreCase = true) }
                .sorted(compareBy { it.fileName.toString().lowercase() })
                .forEach { path ->
                    runCatching { JsonSupport.mapper.readValue(Files.readString(path), CardTemplate::class.java) }
                        .onSuccess { templates.add(it) }
                        .onFailure { errors.add("${path.fileName}: ${it.message ?: "invalid JSON"}") }
                }
        }
        return templates to errors
    }

    fun resolveSvg(template: CardTemplate): Path? {
        val directory = candidates.firstOrNull { Files.isDirectory(it) } ?: return null
        val path = directory.resolve(template.svgFile).normalize()
        return path.takeIf { it.startsWith(directory) && Files.isRegularFile(it) }
    }

    fun rasterize(template: CardTemplate): Image? {
        val path = resolveSvg(template) ?: return null
        val size = runCatching { Files.size(path) }.getOrDefault(-1L)
        val modified = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(-1L)
        imageCache[path]?.takeIf { it.size == size && it.modified == modified }?.let { return it.image }
        val image = rasterizeSvg(path, template.width, template.height) ?: return null
        imageCache[path] = CacheEntry(size, modified, image)
        return image
    }

    private fun rasterizeSvg(path: Path, width: Double, height: Double): Image? = runCatching {
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
        SwingFXUtils.toFXImage(transcoder.image(), null)
    }.getOrNull()

    fun clearCache() = imageCache.clear()
}
