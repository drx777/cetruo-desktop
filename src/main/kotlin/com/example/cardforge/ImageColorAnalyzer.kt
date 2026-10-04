package com.example.cardforge

import javafx.scene.paint.Color
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

object ImageColorAnalyzer {
    fun dominantColor(path: Path): Color? = runCatching { ImageIO.read(path.toFile()) }.getOrNull()?.let(::dominantColor)

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

    private class Bucket {
        var r=0.0; var g=0.0; var b=0.0; var weight=0.0
        fun add(red:Int, green:Int, blue:Int, amount:Double) { r+=red*amount; g+=green*amount; b+=blue*amount; weight+=amount }
    }
}
