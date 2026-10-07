package de.cetruo.desktop

import de.cetruo.desktop.browser.*
import de.cetruo.desktop.editor.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ArtworkPanMathTest {
    @Test
    fun sliderEndpointsMapToActualRange() {
        assertEquals(-40.0, ArtworkPanMath.sliderToActual(-1.0, -40.0, 60.0), 1e-9)
        assertEquals(10.0, ArtworkPanMath.sliderToActual(0.0, -40.0, 60.0), 1e-9)
        assertEquals(60.0, ArtworkPanMath.sliderToActual(1.0, -40.0, 60.0), 1e-9)
    }

    @Test
    fun actualOffsetsMapBackToSliderRange() {
        assertEquals(-1.0, ArtworkPanMath.actualToSlider(-40.0, -40.0, 60.0), 1e-9)
        assertEquals(0.0, ArtworkPanMath.actualToSlider(10.0, -40.0, 60.0), 1e-9)
        assertEquals(1.0, ArtworkPanMath.actualToSlider(60.0, -40.0, 60.0), 1e-9)
    }

    @Test
    fun conversionsClampOutsideValues() {
        assertEquals(-40.0, ArtworkPanMath.sliderToActual(-3.0, -40.0, 60.0), 1e-9)
        assertEquals(60.0, ArtworkPanMath.sliderToActual(3.0, -40.0, 60.0), 1e-9)
        assertEquals(-1.0, ArtworkPanMath.actualToSlider(-100.0, -40.0, 60.0), 1e-9)
        assertEquals(1.0, ArtworkPanMath.actualToSlider(100.0, -40.0, 60.0), 1e-9)
    }

    @Test
    fun degenerateRangeMapsToZero() {
        assertEquals(0.0, ArtworkPanMath.sliderToActual(0.5, 12.0, 12.0), 1e-9)
        assertEquals(0.0, ArtworkPanMath.actualToSlider(12.0, 12.0, 12.0), 1e-9)
    }
}
