package de.cetruo.desktop

import de.cetruo.desktop.browser.*
import de.cetruo.desktop.editor.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserPreviewCachePolicyTest {
    @Test
    fun originalPreviewStaysCurrentWhenSourceMetadataMatches() {
        assertTrue(
            BrowserPreviewCachePolicy.originalIsCurrent(
                cachedSize = 1234,
                cachedModified = 5678,
                sourceSize = 1234,
                sourceModified = 5678
            )
        )
    }

    @Test
    fun originalPreviewInvalidatesWhenSizeOrModifiedTimeChanges() {
        assertFalse(BrowserPreviewCachePolicy.originalIsCurrent(1234, 5678, 9999, 5678))
        assertFalse(BrowserPreviewCachePolicy.originalIsCurrent(1234, 5678, 1234, 9999))
    }

    @Test
    fun cardPreviewRequiresMatchingSourceMetadataAndRenderSignature() {
        assertTrue(
            BrowserPreviewCachePolicy.cardIsCurrent(
                cachedSize = 1234,
                cachedModified = 5678,
                cachedSignature = "signature-a",
                sourceSize = 1234,
                sourceModified = 5678,
                expectedSignature = "signature-a"
            )
        )

        assertFalse(
            BrowserPreviewCachePolicy.cardIsCurrent(
                cachedSize = 1234,
                cachedModified = 5678,
                cachedSignature = "signature-a",
                sourceSize = 1234,
                sourceModified = 5678,
                expectedSignature = "signature-b"
            )
        )
        assertFalse(
            BrowserPreviewCachePolicy.cardIsCurrent(
                cachedSize = 1234,
                cachedModified = 5678,
                cachedSignature = "signature-a",
                sourceSize = 9999,
                sourceModified = 5678,
                expectedSignature = "signature-a"
            )
        )
        assertFalse(
            BrowserPreviewCachePolicy.cardIsCurrent(
                cachedSize = 1234,
                cachedModified = 5678,
                cachedSignature = "signature-a",
                sourceSize = 1234,
                sourceModified = 9999,
                expectedSignature = "signature-a"
            )
        )
    }
}
