package com.example.cardforge

import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import javafx.scene.paint.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Owns browser preview request de-duplication and caches for both original-image thumbnails
 * and rendered-card thumbnails.
 *
 * Full-resolution editor/source-image caching remains outside this class.
 */
class BrowserPreviewCoordinator(
    private val cardSignature: (Path, CardData?) -> String,
    private val cardDataFor: (Path, Color?) -> CardData,
    private val renderCardPreview: (Image, CardData) -> Image?
) {
    private data class CachedImage(val size: Long, val modified: Long, val image: Image)
    private data class CardPreviewEntry(
        val image: Image,
        val sourceSize: Long,
        val sourceModified: Long,
        val renderSignature: String
    )

    private val thumbnailCache = object : LinkedHashMap<Path, CachedImage>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Path, CachedImage>?): Boolean = size > 1200
    }
    private val cardThumbnailCache = object : LinkedHashMap<Path, CardPreviewEntry>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Path, CardPreviewEntry>?): Boolean = size > 240
    }

    private val executor: ExecutorService = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "card-forge-thumbnail").apply { isDaemon = true }
    }
    private val thumbnailLoads = ConcurrentHashMap.newKeySet<Path>()
    private val thumbnailWaiters = ConcurrentHashMap<Path, CopyOnWriteArrayList<(Image?) -> Unit>>()
    private val cardThumbnailLoads = ConcurrentHashMap.newKeySet<Path>()
    private val cardThumbnailWaiters = ConcurrentHashMap<Path, CopyOnWriteArrayList<(Image?) -> Unit>>()

    fun requestOriginal(path: Path, onLoaded: (Image?) -> Unit = {}) {
        val absolute = path.toAbsolutePath().normalize()
        val size = runCatching { Files.size(absolute) }.getOrDefault(-1L)
        val modified = runCatching { Files.getLastModifiedTime(absolute).toMillis() }.getOrDefault(-1L)

        synchronized(thumbnailCache) {
            thumbnailCache[absolute]?.takeIf {
                BrowserPreviewCachePolicy.originalIsCurrent(
                    cachedSize = it.size,
                    cachedModified = it.modified,
                    sourceSize = size,
                    sourceModified = modified
                )
            }?.let { cached ->
                Platform.runLater { onLoaded(cached.image) }
                return
            }
        }

        thumbnailWaiters.computeIfAbsent(absolute) { CopyOnWriteArrayList() }.add(onLoaded)
        if (!thumbnailLoads.add(absolute)) return

        executor.submit {
            val fxImage = runCatching {
                val buffered = ImageIO.read(absolute.toFile()) ?: return@runCatching null
                SwingFXUtils.toFXImage(scaleThumbnail(buffered, 180), null)
            }.getOrNull()?.takeIf { it.width > 0.0 && it.height > 0.0 }

            Platform.runLater {
                try {
                    val finalImage = fxImage ?: runCatching {
                        Image(absolute.toUri().toString(), 180.0, 180.0, true, true, false)
                    }.getOrNull()?.takeIf { !it.isError && it.width > 0.0 && it.height > 0.0 }

                    if (finalImage != null) {
                        synchronized(thumbnailCache) {
                            thumbnailCache[absolute] = CachedImage(size, modified, finalImage)
                        }
                    }
                    thumbnailWaiters.remove(absolute).orEmpty().forEach { callback -> callback(finalImage) }
                } finally {
                    thumbnailLoads.remove(absolute)
                }
            }
        }
    }

    fun requestCard(path: Path, onLoaded: (Image?) -> Unit) {
        val absolute = path.toAbsolutePath().normalize()
        val size = runCatching { Files.size(absolute) }.getOrDefault(-1L)
        val modified = runCatching { Files.getLastModifiedTime(absolute).toMillis() }.getOrDefault(-1L)
        val expectedSignature = cardSignature(absolute, null)

        synchronized(cardThumbnailCache) {
            cardThumbnailCache[absolute]?.takeIf {
                BrowserPreviewCachePolicy.cardIsCurrent(
                    cachedSize = it.sourceSize,
                    cachedModified = it.sourceModified,
                    cachedSignature = it.renderSignature,
                    sourceSize = size,
                    sourceModified = modified,
                    expectedSignature = expectedSignature
                )
            }?.let { cached ->
                Platform.runLater { onLoaded(cached.image) }
                return
            }
        }

        cardThumbnailWaiters.computeIfAbsent(absolute) { CopyOnWriteArrayList() }.add(onLoaded)
        if (!cardThumbnailLoads.add(absolute)) return

        executor.submit {
            val decoded = runCatching {
                val buffered = ImageIO.read(absolute.toFile()) ?: return@runCatching null
                val derivedColor = ImageColorAnalyzer.dominantColor(buffered)
                val scaled = scaleThumbnail(buffered, 900)
                SwingFXUtils.toFXImage(scaled, null) to derivedColor
            }.getOrNull()
            val source = decoded?.first?.takeIf { it.width > 0.0 && it.height > 0.0 }
            val derivedColor = decoded?.second

            Platform.runLater {
                try {
                    val data = cardDataFor(absolute, derivedColor)
                    val effectiveSource = source ?: runCatching {
                        Image(absolute.toUri().toString(), 900.0, 900.0, true, true, false)
                    }.getOrNull()?.takeIf { !it.isError && it.width > 0.0 && it.height > 0.0 }

                    val preview = if (effectiveSource != null) renderCardPreview(effectiveSource, data) else null
                    if (preview != null) {
                        synchronized(cardThumbnailCache) {
                            cardThumbnailCache[absolute] = CardPreviewEntry(
                                image = preview,
                                sourceSize = size,
                                sourceModified = modified,
                                renderSignature = cardSignature(absolute, data)
                            )
                        }
                    }
                    cardThumbnailWaiters.remove(absolute).orEmpty().forEach { callback -> callback(preview) }
                } finally {
                    cardThumbnailLoads.remove(absolute)
                }
            }
        }
    }

    fun removeOriginal(path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        synchronized(thumbnailCache) { thumbnailCache.remove(normalized) }
    }

    fun removeCard(path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        synchronized(cardThumbnailCache) { cardThumbnailCache.remove(normalized) }
    }

    fun clearOriginals() = synchronized(thumbnailCache) { thumbnailCache.clear() }

    fun clearCards() = synchronized(cardThumbnailCache) { cardThumbnailCache.clear() }

    fun clearAll() {
        clearOriginals()
        clearCards()
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun scaleThumbnail(source: BufferedImage, maxSize: Int): BufferedImage {
        val scale = minOf(1.0, maxSize.toDouble() / maxOf(source.width, source.height).coerceAtLeast(1))
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        if (width == source.width && height == source.height) return source

        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaled.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(source, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        return scaled
    }
}
