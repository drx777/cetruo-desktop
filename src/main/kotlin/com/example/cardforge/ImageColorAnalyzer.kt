package com.example.cardforge

import javafx.scene.paint.Color
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ImageColorAnalyzer {
    fun dominantColor(path: Path): Color? = runCatching {
        ImageIO.createImageInputStream(path.toFile())?.use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return@use ImageIO.read(path.toFile())
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0).coerceAtLeast(1)
                val height = reader.getHeight(0).coerceAtLeast(1)
                // Theme matching only needs representative color information. Ask the
                // decoder for a small subsampled image instead of decoding a multi-megapixel
                // source in full, which keeps first-card initialization and bulk actions fast.
                val subsample = (max(width, height) / 512).coerceAtLeast(1)
                val param: ImageReadParam = reader.defaultReadParam
                param.setSourceSubsampling(subsample, subsample, 0, 0)
                reader.read(0, param)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()?.let(::dominantColor)

    fun dominantColor(image: BufferedImage): Color? {
        if (image.width <= 0 || image.height <= 0) return null
        val bins = HashMap<Int, Bucket>()
        val step = max(1, max(image.width, image.height) / 180)
        for (y in 0 until image.height step step) for (x in 0 until image.width step step) {
            val argb = image.getRGB(x, y)
            if (((argb ushr 24) and 0xff) < 96) continue
            val r = (argb ushr 16) and 0xff
            val g = (argb ushr 8) and 0xff
            val b = argb and 0xff
            val hi = max(r, max(g, b)); val lo = min(r, min(g, b))
            val brightness = (r + g + b) / 3.0
            if (brightness < 24.0 || brightness > 238.0) continue
            val saturation = if (hi == 0) 0.0 else (hi - lo).toDouble() / hi
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            bins.getOrPut(key) { Bucket() }.add(r, g, b, 0.35 + saturation * 1.65)
        }
        val winner = bins.values.maxByOrNull { it.weight } ?: return null
        return Color.rgb((winner.r / winner.weight).toInt().coerceIn(0,255), (winner.g / winner.weight).toInt().coerceIn(0,255), (winner.b / winner.weight).toInt().coerceIn(0,255))
    }

    /** Finds the coordinated card scheme whose frame/accent palette is closest to the artwork. */
    fun bestMatchingScheme(color: Color, schemes: List<ColorScheme>): ColorScheme? = schemes.minByOrNull { scheme ->
        val candidates = listOf(scheme.frameColor, scheme.accentColor, scheme.overlayColor).mapNotNull { hex ->
            runCatching { Color.web(hex) }.getOrNull()
        }
        candidates.minOfOrNull { candidate -> perceptualDistance(color, candidate) } ?: Double.MAX_VALUE
    }

    private fun perceptualDistance(a: Color, b: Color): Double {
        val hueDelta = abs(a.hue - b.hue).let { min(it, 360.0 - it) } / 180.0
        val satDelta = abs(a.saturation - b.saturation)
        val brightDelta = abs(a.brightness - b.brightness)
        // Hue is the strongest signal for choosing a theme, while saturation/brightness
        // prevent a vivid image from being matched to an unrelated neutral palette.
        return hueDelta * 2.2 + satDelta * 0.75 + brightDelta * 0.45
    }

    private class Bucket {
        var r=0.0; var g=0.0; var b=0.0; var weight=0.0
        fun add(red:Int, green:Int, blue:Int, amount:Double) { r+=red*amount; g+=green*amount; b+=blue*amount; weight+=amount }
    }
}
