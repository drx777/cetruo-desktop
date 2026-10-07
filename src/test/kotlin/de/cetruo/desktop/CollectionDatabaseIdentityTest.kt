package de.cetruo.desktop

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CollectionDatabaseIdentityTest {

    @Test
    fun legacyCatalogIsMigratedToCetruoFilenameOnOpen() {
        val root = createTempDirectory("cetruo-db-migration-test")
        val image = root.resolve("card.png")
        Files.write(image, byteArrayOf(1, 3, 3, 7))

        CollectionDatabase.open(root).use { db ->
            val data = CardData(title = "Migrated")
            db.save(image, data)
        }

        val current = root.resolve(CollectionDatabase.FILE_NAME)
        val legacy = root.resolve(CollectionDatabase.LEGACY_FILE_NAME)
        Files.move(current, legacy)

        assertEquals(true, Files.exists(legacy))
        assertEquals(false, Files.exists(current))

        CollectionDatabase.open(root).use { db ->
            assertEquals("Migrated", db.dataSnapshotForPath(image)?.title)
            assertEquals(current, db.path)
        }

        assertEquals(true, Files.exists(current))
        assertEquals(false, Files.exists(legacy))
    }


    @Test
    fun legacyMissingTombstoneCanStillReattach() {
        val root = createTempDirectory("cetruo-legacy-missing-test")
        val original = root.resolve("old.png")
        Files.write(original, byteArrayOf(4, 2, 4, 2))

        val assetId = CollectionDatabase.open(root).use { db ->
            val data = CardData(title = "Legacy tombstone")
            db.save(original, data)
            data.assetId
        }

        java.sql.DriverManager.getConnection("jdbc:sqlite:${root.resolve(CollectionDatabase.FILE_NAME).toAbsolutePath()}").use { connection ->
            connection.prepareStatement("UPDATE assets SET relative_path=? WHERE asset_id=?").use { statement ->
                statement.setString(1, ".cardforge-missing/$assetId/old.png")
                statement.setString(2, assetId)
                statement.executeUpdate()
            }
        }
        Files.delete(original)

        val renamed = root.resolve("new.png")
        Files.write(renamed, byteArrayOf(4, 2, 4, 2))

        CollectionDatabase.open(root).use { db ->
            assertEquals(assetId, db.reconcileAdded(renamed))
            assertEquals("Legacy tombstone", db.dataSnapshotForPath(renamed)?.title)
        }
    }

    @Test
    fun moveOrRenameReattachesMatchingFileToSameAsset() {
        val root = createTempDirectory("cetruo-db-test")
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
        val root = createTempDirectory("cetruo-db-test")
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
