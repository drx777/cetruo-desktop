package de.cetruo.desktop

object ArtworkPanMath {
    fun sliderToActual(value: Double, min: Double, max: Double): Double {
        if (max - min <= 1e-9) return 0.0
        val t = ((value + 1.0) / 2.0).coerceIn(0.0, 1.0)
        return min + (max - min) * t
    }

    fun actualToSlider(value: Double, min: Double, max: Double): Double {
        if (max - min <= 1e-9) return 0.0
        val t = ((value.coerceIn(min, max) - min) / (max - min)).coerceIn(0.0, 1.0)
        return t * 2.0 - 1.0
    }
}
