package com.example.cardforge

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CollectionDatabaseIdentityTest {
    @Test
    fun moveOrRenameReattachesMatchingFileToSameAsset() {
        val root = createTempDirectory("cardforge-db-test")
        val original = root.resolve("original.png")
        Files.write(original, byteArrayOf(1, 2, 3, 4))

        CollectionDatabase.open(root).use { db ->
            val data = CardData(title = "Preserved")
            db.save(original, data)
            val assetId = data.assetId

            db.markMissing(original)
            Files.delete(original)

            val renamed = root.resolve("renamed.png")
            Files.write(renamed, byteArrayOf(1, 2, 3, 4))

            assertEquals(assetId, db.reconcileAdded(renamed))
            assertEquals(assetId, db.assetIdForPath(renamed))
            assertEquals("Preserved", db.dataSnapshotForPath(renamed)?.title)
        }
    }

    @Test
    fun unrelatedReplacementAtOldFilenameDoesNotInheritMetadata() {
        val root = createTempDirectory("cardforge-db-test")
        val original = root.resolve("card.png")
        Files.write(original, byteArrayOf(5, 6, 7, 8))

        CollectionDatabase.open(root).use { db ->
            val data = CardData(title = "Old metadata")
            db.save(original, data)
            val oldAssetId = data.assetId

            db.markMissing(original)
            Files.delete(original)

            Files.write(original, byteArrayOf(9, 9, 9, 9))

            assertNull(db.reconcileAdded(original))
            val replacement = CardData(title = "Replacement")
            db.save(original, replacement)

            assertNotEquals(oldAssetId, replacement.assetId)
            assertEquals("Replacement", db.dataSnapshotForPath(original)?.title)
        }
    }
}
