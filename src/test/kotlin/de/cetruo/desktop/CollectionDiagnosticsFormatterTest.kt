package de.cetruo.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CollectionDiagnosticsFormatterTest {
    private val formatter = CollectionDiagnosticsFormatter { it.name.lowercase() }

    @Test
    fun formatsEmptyHistory() {
        assertEquals("No saved revisions yet.", formatter.history(emptyList()))
    }

    @Test
    fun formatsDatabaseInfo() {
        val text = formatter.databaseInfo(
            CollectionDiagnosticsInfo(
                collectionRoot = Path.of("/collection"),
                databasePath = Path.of("/collection/.cetruo.sqlite"),
                trackedImages = 10,
                visibleImages = 4,
                totalImages = 10,
                assetId = "",
                relativePath = "cards/a.png",
                statusLabel = "New",
                revisionCount = 2,
                templateName = "Classic",
                usesCollectionDefaultTemplate = true,
                descriptionHeading = "ABILITY / DESCRIPTION",
                showArtistCopyright = true,
                backgroundColor = "#000000",
                backgroundOverlay = "",
                backgroundOverlayPlacement = "CENTER",
                nestedCollectionsSkipped = 1
            )
        )

        assertContains(text, "Images visible: 4/10")
        assertContains(text, "Current image ID: not assigned")
        assertContains(text, "Template: Classic (collection default)")
        assertContains(text, "Background overlay: None (CENTER)")
    }
}
