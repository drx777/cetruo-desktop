package com.example.cardforge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppPlatformTest {
    @Test
    fun initialWindowSizeUsesDesiredSizeOnLargeDisplays() {
        val size = AppPlatform.initialWindowSizeFor(2560.0, 1440.0)

        assertEquals(1660.0, size.width)
        assertEquals(1040.0, size.height)
    }

    @Test
    fun initialWindowSizeRespectsVisualBoundsOnSmallDisplays() {
        val size = AppPlatform.initialWindowSizeFor(1024.0, 700.0)

        assertEquals(1024.0, size.width)
        assertEquals(700.0, size.height)
    }

    @Test
    fun initialWindowSizeUsesConfiguredVisualFractionsBeforeMinimums() {
        val size = AppPlatform.initialWindowSizeFor(1400.0, 900.0)

        assertEquals(1316.0, size.width, 0.001)
        assertEquals(837.0, size.height, 0.001)
    }

    @Test
    fun packagedUiResourcesArePresent() {
        assertNotNull(AppPlatform::class.java.getResource("/icons/card-forge-icon.png"))
        assertNotNull(AppPlatform::class.java.getResource("/cardforge.css"))
    }
}
