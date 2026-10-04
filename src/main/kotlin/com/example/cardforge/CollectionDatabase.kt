package com.example.cardforge

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

class CollectionDatabase private constructor(
    val root: Path
) : AutoCloseable {
    companion object {
        const val FILE_NAME = ".cardforge.sqlite"

        fun open(root: Path): CollectionDatabase {
            Class.forName("org.sqlite.JDBC")
            return CollectionDatabase(root.toAbsolutePath().normalize()).also { it.initialize() }
        }
    }

    val path: Path = root.resolve(FILE_NAME)
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")

    data class AssetRecord(
        val assetId: String,
        val relativePath: String,
        val status: CardStatus,
        val currentJson: String?,
        val revisionCount: Int,
        val lastSavedAt: String?
    )

    data class HistoryRecord(
        val revisionNumber: Int,
        val savedAt: String,
        val status: CardStatus,
        val changesJson: String
    )

    data class SaveResult(
        val assetId: String,
        val revisionNumber: Int?,
        val changed: Boolean
    )

    init {
        connection.createStatement().use { stmt ->
            stmt.execute("PRAGMA foreign_keys = ON")
            stmt.execute("PRAGMA journal_mode = WAL")
            stmt.execute("PRAGMA busy_timeout = 5000")
        }
    }

    private fun initialize() {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS collection_meta (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    root_path TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    last_opened_at TEXT NOT NULL,
                    default_template TEXT NOT NULL DEFAULT '',
                    description_heading TEXT NOT NULL DEFAULT 'ABILITY / DESCRIPTION',
                    show_artist_copyright INTEGER NOT NULL DEFAULT 1,
                    schema_version INTEGER NOT NULL
                )
                """.trimIndent()
            )
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS assets (
                    asset_id TEXT PRIMARY KEY,
                    relative_path TEXT NOT NULL UNIQUE,
                    status TEXT NOT NULL,
                    sha256 TEXT,
                    file_size INTEGER,
                    modified_at INTEGER,
                    first_seen_at TEXT NOT NULL,
                    last_seen_at TEXT NOT NULL,
                    last_saved_at TEXT,
                    current_json TEXT
                )
                """.trimIndent()
            )
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS revisions (
                    revision_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    asset_id TEXT NOT NULL,
                    revision_number INTEGER NOT NULL,
                    saved_at TEXT NOT NULL,
                    status TEXT NOT NULL,
                    changes_json TEXT NOT NULL,
                    snapshot_json TEXT NOT NULL,
                    FOREIGN KEY(asset_id) REFERENCES assets(asset_id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_revisions_asset ON revisions(asset_id, revision_number DESC)")
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS activity (
                    activity_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    asset_id TEXT NOT NULL,
                    occurred_at TEXT NOT NULL,
                    action TEXT NOT NULL,
                    details TEXT,
                    FOREIGN KEY(asset_id) REFERENCES assets(asset_id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
        }

        // Migrate collections created by earlier versions.
        runCatching { connection.createStatement().use { it.executeUpdate("ALTER TABLE collection_meta ADD COLUMN default_template TEXT NOT NULL DEFAULT ''") } }
        runCatching { connection.createStatement().use { it.executeUpdate("ALTER TABLE collection_meta ADD COLUMN description_heading TEXT NOT NULL DEFAULT 'ABILITY / DESCRIPTION'") } }
        runCatching { connection.createStatement().use { it.executeUpdate("ALTER TABLE collection_meta ADD COLUMN show_artist_copyright INTEGER NOT NULL DEFAULT 1") } }

        val now = Instant.now().toString()
        connection.prepareStatement(
            """
            INSERT INTO collection_meta(id, root_path, created_at, last_opened_at, default_template, description_heading, show_artist_copyright, schema_version)
            VALUES(1, ?, ?, ?, '', 'ABILITY / DESCRIPTION', 1, 1)
            ON CONFLICT(id) DO UPDATE SET root_path=excluded.root_path, last_opened_at=excluded.last_opened_at
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, root.toString())
            ps.setString(2, now)
            ps.setString(3, now)
            ps.executeUpdate()
        }
    }

    fun register(image: Path, assetIdHint: String, statusHint: CardStatus): AssetRecord {
        val absolute = image.toAbsolutePath().normalize()
        val relative = root.relativize(absolute).toString().replace('\\', '/')
        val existingByPath = findAssetByPath(relative)
        val existingByHint = if (assetIdHint.isNotBlank()) findAssetById(assetIdHint) else null
        val hintUsable = existingByHint?.let { record ->
            record.relativePath == relative || !Files.exists(root.resolve(record.relativePath))
        } == true

        val hash = sha256(absolute)

        // Intentionally do not use a hash-only match to claim an existing asset ID.
        // A copied/new image can have identical pixels to a deleted card; automatically
        // inheriting that old record is what caused cards to "move" between neighbours.
        // Renames/moves retain identity through the persistent assetId in the sidecar.
        val record = when {
            existingByPath != null -> existingByPath
            hintUsable -> existingByHint!!
            else -> insertAsset(
                assetId = assetIdHint.ifBlank { UUID.randomUUID().toString() },
                relativePath = relative,
                status = statusHint,
                image = absolute,
                hash = hash
            )
        }

        val now = Instant.now().toString()
        val needsHash = record.relativePath != relative || record.currentJson == null
        val hashForUpdate = if (needsHash) hash else null
        connection.prepareStatement(
            "UPDATE assets SET relative_path=?, last_seen_at=?, file_size=?, modified_at=?, sha256=COALESCE(?, sha256) WHERE asset_id=?"
        ).use { ps ->
            ps.setString(1, relative)
            ps.setString(2, now)
            ps.setLong(3, runCatching { Files.size(absolute) }.getOrDefault(0L))
            ps.setLong(4, runCatching { Files.getLastModifiedTime(absolute).toMillis() }.getOrDefault(0L))
            ps.setString(5, hashForUpdate)
            ps.setString(6, record.assetId)
            ps.executeUpdate()
        }
        return findAssetById(record.assetId) ?: record
    }

    fun getDefaultTemplateName(): String = connection.prepareStatement(
        "SELECT default_template FROM collection_meta WHERE id=1"
    ).use { ps ->
        ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1).orEmpty() else "" }
    }

    fun setDefaultTemplateName(name: String) {
        connection.prepareStatement("UPDATE collection_meta SET default_template=? WHERE id=1").use { ps ->
            ps.setString(1, name)
            ps.executeUpdate()
        }
    }

    fun getCollectionPresentation(): CollectionPresentation = connection.prepareStatement(
        "SELECT description_heading, show_artist_copyright FROM collection_meta WHERE id=1"
    ).use { ps ->
        ps.executeQuery().use { rs ->
            if (rs.next()) CollectionPresentation(
                descriptionHeading = rs.getString(1).orEmpty().ifBlank { "ABILITY / DESCRIPTION" },
                showArtistCopyright = rs.getInt(2) != 0
            ) else CollectionPresentation()
        }
    }

    fun setCollectionPresentation(settings: CollectionPresentation) {
        connection.prepareStatement(
            "UPDATE collection_meta SET description_heading=?, show_artist_copyright=? WHERE id=1"
        ).use { ps ->
            ps.setString(1, settings.descriptionHeading.ifBlank { "ABILITY / DESCRIPTION" })
            ps.setInt(2, if (settings.showArtistCopyright) 1 else 0)
            ps.executeUpdate()
        }
    }

    fun dataSnapshot(assetId: String): CardData? = findAssetById(assetId)?.currentJson?.let {
        runCatching { JsonSupport.mapper.readValue(it, CardData::class.java) }.getOrNull()
    }

    fun assetRelativePath(assetId: String): String? = findAssetById(assetId)?.relativePath

    fun assetIdForPath(image: Path): String? = findAssetByPath(
        root.relativize(image.toAbsolutePath().normalize()).toString().replace('\\', '/')
    )?.assetId

    fun backupTo(target: Path) {
        val destination = target.toAbsolutePath().normalize()
        if (destination == path.toAbsolutePath().normalize()) error("Backup target must differ from the active catalog")
        Files.createDirectories(destination.parent)
        if (Files.exists(destination)) Files.delete(destination)
        connection.createStatement().use { stmt -> stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)") }
        val escaped = destination.toString().replace("'", "''")
        connection.createStatement().use { stmt ->
            stmt.execute("VACUUM INTO '$escaped'")
        }
    }

    fun dataSnapshotForPath(image: Path): CardData? = findAssetByPath(
        root.relativize(image.toAbsolutePath().normalize()).toString().replace('\\', '/')
    )?.currentJson?.let {
        runCatching { JsonSupport.mapper.readValue(it, CardData::class.java) }.getOrNull()
    }

    fun searchIndex(): Map<String, String> = connection.createStatement().use { stmt ->
        stmt.executeQuery("SELECT relative_path, asset_id, status, current_json FROM assets").use { rs ->
            buildMap {
                while (rs.next()) {
                    val relative = rs.getString(1)
                    val assetId = rs.getString(2)
                    val status = rs.getString(3)
                    val json = rs.getString(4).orEmpty()
                    put(relative, (relative + " " + assetId + " " + status + " " + json).lowercase())
                }
            }
        }
    }

    fun save(image: Path, data: CardData): SaveResult {
        val record = register(image, data.assetId, data.status)
        if (data.assetId.isBlank() || data.assetId != record.assetId) data.assetId = record.assetId

        val snapshotJson = JsonSupport.mapper.writeValueAsString(data)
        val previousJson = record.currentJson
        val changed = previousJson == null || normalizeJson(previousJson) != normalizeJson(snapshotJson)
        if (!changed) return SaveResult(record.assetId, null, false)

        val revisionNumber = nextRevisionNumber(record.assetId)
        val changesJson = diffJson(previousJson, snapshotJson)
        val now = Instant.now().toString()

        connection.autoCommit = false
        try {
            connection.prepareStatement(
                "UPDATE assets SET status=?, current_json=?, last_saved_at=?, last_seen_at=? WHERE asset_id=?"
            ).use { ps ->
                ps.setString(1, data.status.name)
                ps.setString(2, snapshotJson)
                ps.setString(3, now)
                ps.setString(4, now)
                ps.setString(5, record.assetId)
                ps.executeUpdate()
            }
            connection.prepareStatement(
                "INSERT INTO revisions(asset_id, revision_number, saved_at, status, changes_json, snapshot_json) VALUES(?,?,?,?,?,?)"
            ).use { ps ->
                ps.setString(1, record.assetId)
                ps.setInt(2, revisionNumber)
                ps.setString(3, now)
                ps.setString(4, data.status.name)
                ps.setString(5, changesJson)
                ps.setString(6, snapshotJson)
                ps.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
        return SaveResult(record.assetId, revisionNumber, true)
    }

    fun recordActivity(assetId: String, action: String, details: String = "") {
        connection.prepareStatement(
            "INSERT INTO activity(asset_id, occurred_at, action, details) VALUES(?,?,?,?)"
        ).use { ps ->
            ps.setString(1, assetId)
            ps.setString(2, Instant.now().toString())
            ps.setString(3, action)
            ps.setString(4, details.ifBlank { null })
            ps.executeUpdate()
        }
    }

    fun history(assetId: String): List<HistoryRecord> = connection.prepareStatement(
        "SELECT revision_number, saved_at, status, changes_json FROM revisions WHERE asset_id=? ORDER BY revision_number DESC"
    ).use { ps ->
        ps.setString(1, assetId)
        ps.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        HistoryRecord(
                            rs.getInt(1),
                            rs.getString(2),
                            runCatching { CardStatus.valueOf(rs.getString(3)) }.getOrDefault(CardStatus.IN_PROGRESS),
                            rs.getString(4)
                        )
                    )
                }
            }
        }
    }

    fun usedCollectorNumbers(excludingAssetId: String? = null): Set<String> = connection.createStatement().use { stmt ->
        stmt.executeQuery("SELECT asset_id, current_json FROM assets WHERE current_json IS NOT NULL").use { rs ->
            buildSet {
                while (rs.next()) {
                    if (excludingAssetId != null && rs.getString(1) == excludingAssetId) continue
                    val json = rs.getString(2).orEmpty()
                    val number = runCatching { JsonSupport.mapper.readValue(json, CardData::class.java).collectorNumber.trim() }.getOrDefault("")
                    if (number.isNotBlank()) add(number)
                }
            }
        }
    }

    fun countAssets(): Int = connection.createStatement().use { stmt ->
        stmt.executeQuery("SELECT COUNT(*) FROM assets").use { rs ->
            rs.next()
            rs.getInt(1)
        }
    }

    fun countRevisions(assetId: String): Int = connection.prepareStatement(
        "SELECT COUNT(*) FROM revisions WHERE asset_id=?"
    ).use { ps ->
        ps.setString(1, assetId)
        ps.executeQuery().use { rs ->
            rs.next()
            rs.getInt(1)
        }
    }

    private fun findAssetById(assetId: String): AssetRecord? = connection.prepareStatement(
        """
        SELECT asset_id, relative_path, status, current_json, last_saved_at,
               (SELECT COUNT(*) FROM revisions r WHERE r.asset_id=a.asset_id)
        FROM assets a WHERE asset_id=?
        """.trimIndent()
    ).use { ps ->
        ps.setString(1, assetId)
        ps.executeQuery().use { rs -> if (rs.next()) readAsset(rs) else null }
    }

    private fun findAssetByPath(relativePath: String): AssetRecord? = connection.prepareStatement(
        """
        SELECT asset_id, relative_path, status, current_json, last_saved_at,
               (SELECT COUNT(*) FROM revisions r WHERE r.asset_id=a.asset_id)
        FROM assets a WHERE relative_path=?
        """.trimIndent()
    ).use { ps ->
        ps.setString(1, relativePath)
        ps.executeQuery().use { rs -> if (rs.next()) readAsset(rs) else null }
    }


    private fun readAsset(rs: java.sql.ResultSet): AssetRecord = AssetRecord(
        assetId = rs.getString(1),
        relativePath = rs.getString(2),
        status = runCatching { CardStatus.valueOf(rs.getString(3)) }.getOrDefault(CardStatus.IN_PROGRESS),
        currentJson = rs.getString(4),
        revisionCount = rs.getInt(6),
        lastSavedAt = rs.getString(5)
    )

    private fun insertAsset(
        assetId: String,
        relativePath: String,
        status: CardStatus,
        image: Path,
        hash: String
    ): AssetRecord {
        val now = Instant.now().toString()
        connection.prepareStatement(
            "INSERT INTO assets(asset_id, relative_path, status, sha256, file_size, modified_at, first_seen_at, last_seen_at) VALUES(?,?,?,?,?,?,?,?)"
        ).use { ps ->
            ps.setString(1, assetId)
            ps.setString(2, relativePath)
            ps.setString(3, status.name)
            ps.setString(4, hash)
            ps.setLong(5, runCatching { Files.size(image) }.getOrDefault(0L))
            ps.setLong(6, runCatching { Files.getLastModifiedTime(image).toMillis() }.getOrDefault(0L))
            ps.setString(7, now)
            ps.setString(8, now)
            ps.executeUpdate()
        }
        return findAssetById(assetId)!!
    }

    private fun nextRevisionNumber(assetId: String): Int = connection.prepareStatement(
        "SELECT COALESCE(MAX(revision_number),0)+1 FROM revisions WHERE asset_id=?"
    ).use { ps ->
        ps.setString(1, assetId)
        ps.executeQuery().use { rs ->
            rs.next()
            rs.getInt(1)
        }
    }

    private fun normalizeJson(json: String): String = JsonSupport.mapper.readTree(json).toString()

    private fun diffJson(beforeJson: String?, afterJson: String): String {
        if (beforeJson == null) return "{\"initialSave\":true}"
        val before = JsonSupport.mapper.readTree(beforeJson)
        val after = JsonSupport.mapper.readTree(afterJson)
        val result = JsonSupport.mapper.createObjectNode()
        val names = mutableSetOf<String>()
        before.fieldNames().forEachRemaining { names.add(it) }
        after.fieldNames().forEachRemaining { names.add(it) }
        for (name in names.sorted()) {
            val oldValue = before.get(name)
            val newValue = after.get(name)
            if (oldValue == newValue) continue
            val change = JsonSupport.mapper.createObjectNode()
            if (oldValue != null) change.set<com.fasterxml.jackson.databind.JsonNode>("before", oldValue)
            else change.putNull("before")
            if (newValue != null) change.set<com.fasterxml.jackson.databind.JsonNode>("after", newValue)
            else change.putNull("after")
            result.set<com.fasterxml.jackson.databind.JsonNode>(name, change)
        }
        return JsonSupport.mapper.writeValueAsString(result)
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun close() {
        runCatching { connection.close() }
    }
}
