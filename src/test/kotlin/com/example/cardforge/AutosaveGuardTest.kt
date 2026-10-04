package com.example.cardforge

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutosaveGuardTest {
    @Test
    fun successfulSaveAllowsTransitionAndForwardsStatusPreference() {
        val calls = mutableListOf<Boolean>()
        val guard = AutosaveGuard { showStatus ->
            calls += showStatus
            true
        }

        assertTrue(guard.ensureSaved(showStatus = true))
        assertTrue(guard.ensureSaved(showStatus = false))
        assertTrue(calls == listOf(true, false))
    }

    @Test
    fun failedSaveBlocksTransition() {
        val guard = AutosaveGuard { false }
        assertFalse(guard.ensureSaved())
    }
}
