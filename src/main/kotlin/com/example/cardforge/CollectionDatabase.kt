package com.example.cardforge

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

class CollectionDatabase private constructor(val root: Path) : AutoCloseable {
    companion object {
        const val FILE_NAME = ".cardforge.sqlite"
        fun open(root: Path): CollectionDatabase {
            val normalized = root.toAbsolutePath().normalize()
            Class.forName("org.sqlite.JDBC")
            val database = StartupProfiler.measure(
                "sqlite connect",
                detail = { it.path.fileName.toString() }
            ) {
                CollectionDatabase(normalized)
            }
            StartupProfiler.measure("sqlite initialize/schema") {
                database.initialize()
            }
            return database
        }
    }

    val path: Path = root.resolve(FILE_NAME)
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")

    data class AssetRecord(val assetId: String, val relativePath: String, val status: CardStatus, val currentJson: String?, val revisionCount: Int, val lastSavedAt: String?)
    data class HistoryRecord(val revisionNumber: Int, val savedAt: String, val status: CardStatus, val changesJson: String)
    data class SaveResult(val assetId: String, val revisionNumber: Int?, val changed: Boolean)

    init {
        connection.createStatement().use {
            it.execute("PRAGMA foreign_keys = ON")
            it.execute("PRAGMA journal_mode = WAL")
            it.execute("PRAGMA busy_timeout = 5000")
        }
    }

    private fun initialize() {
        connection.createStatement().use { s ->
            s.executeUpdate("""CREATE TABLE IF NOT EXISTS collection_meta (id INTEGER PRIMARY KEY CHECK(id=1), root_path TEXT NOT NULL, created_at TEXT NOT NULL, last_opened_at TEXT NOT NULL, default_template TEXT NOT NULL DEFAULT '', description_heading TEXT NOT NULL DEFAULT 'ABILITY / DESCRIPTION', show_artist_copyright INTEGER NOT NULL DEFAULT 1, bleed_opacity REAL NOT NULL DEFAULT 1.0, foreground_opacity REAL NOT NULL DEFAULT 1.0, schema_version INTEGER NOT NULL)""")
            s.executeUpdate("""CREATE TABLE IF NOT EXISTS assets (asset_id TEXT PRIMARY KEY, relative_path TEXT NOT NULL UNIQUE, status TEXT NOT NULL, sha256 TEXT, file_size INTEGER, modified_at INTEGER, first_seen_at TEXT NOT NULL, last_seen_at TEXT NOT NULL, last_saved_at TEXT, current_json TEXT)""")
            s.executeUpdate("""CREATE TABLE IF NOT EXISTS revisions (revision_id INTEGER PRIMARY KEY AUTOINCREMENT, asset_id TEXT NOT NULL, revision_number INTEGER NOT NULL, saved_at TEXT NOT NULL, status TEXT NOT NULL, changes_json TEXT NOT NULL, snapshot_json TEXT NOT NULL, FOREIGN KEY(asset_id) REFERENCES assets(asset_id) ON DELETE CASCADE)""")
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_revisions_asset ON revisions(asset_id, revision_number DESC)")
            s.executeUpdate("""CREATE TABLE IF NOT EXISTS activity (activity_id INTEGER PRIMARY KEY AUTOINCREMENT, asset_id TEXT NOT NULL, occurred_at TEXT NOT NULL, action TEXT NOT NULL, details TEXT, FOREIGN KEY(asset_id) REFERENCES assets(asset_id) ON DELETE CASCADE)""")
        }
        fun migrate(sql: String) { runCatching { connection.createStatement().use { it.executeUpdate(sql) } } }
        migrate("ALTER TABLE collection_meta ADD COLUMN default_template TEXT NOT NULL DEFAULT ''")
        migrate("ALTER TABLE collection_meta ADD COLUMN description_heading TEXT NOT NULL DEFAULT 'ABILITY / DESCRIPTION'")
        migrate("ALTER TABLE collection_meta ADD COLUMN show_artist_copyright INTEGER NOT NULL DEFAULT 1")
        migrate("ALTER TABLE collection_meta ADD COLUMN bleed_opacity REAL NOT NULL DEFAULT 1.0")
        migrate("ALTER TABLE collection_meta ADD COLUMN foreground_opacity REAL NOT NULL DEFAULT 1.0")
        val now = Instant.now().toString()
        connection.prepareStatement("""INSERT INTO collection_meta(id,root_path,created_at,last_opened_at,default_template,description_heading,show_artist_copyright,bleed_opacity,foreground_opacity,schema_version) VALUES(1,?,?,?,'','ABILITY / DESCRIPTION',1,1.0,1.0,2) ON CONFLICT(id) DO UPDATE SET root_path=excluded.root_path,last_opened_at=excluded.last_opened_at""").use {
            it.setString(1, root.toString()); it.setString(2, now); it.setString(3, now); it.executeUpdate()
        }
    }

    fun register(image: Path, assetIdHint: String, statusHint: CardStatus): AssetRecord {
        val absolute = image.toAbsolutePath().normalize()
        val relative = relative(absolute)
        val existingByPath = findAssetByPath(relative)
        val existingByHint = assetIdHint.takeIf { it.isNotBlank() }?.let(::findAssetById)
        val hintUsable = existingByHint?.let { it.relativePath == relative || !Files.exists(root.resolve(it.relativePath)) } == true
        val size = runCatching { Files.size(absolute) }.getOrDefault(0L)
        val modified = runCatching { Files.getLastModifiedTime(absolute).toMillis() }.getOrDefault(0L)

        // Critical startup optimization: do not read/hash an unchanged image just to register it.
        val record = existingByPath ?: if (hintUsable) existingByHint!! else insertAsset(
            assetIdHint.ifBlank { UUID.randomUUID().toString() }, relative, statusHint, size, modified, sha256(absolute)
        )
        val metadata = assetMetadata(record.assetId)
        val changedOnDisk = metadata == null || metadata.first != size || metadata.second != modified
        val moved = record.relativePath != relative
        val hash = if (changedOnDisk || moved) sha256(absolute) else null
        val now = Instant.now().toString()
        connection.prepareStatement("UPDATE assets SET relative_path=?,last_seen_at=?,file_size=?,modified_at=?,sha256=COALESCE(?,sha256) WHERE asset_id=?").use {
            it.setString(1, relative); it.setString(2, now); it.setLong(3, size); it.setLong(4, modified); it.setString(5, hash); it.setString(6, record.assetId); it.executeUpdate()
        }
        return findAssetById(record.assetId) ?: record
    }

    fun getDefaultTemplateName(): String = connection.prepareStatement("SELECT default_template FROM collection_meta WHERE id=1").use { p -> p.executeQuery().use { if (it.next()) it.getString(1).orEmpty() else "" } }
    fun setDefaultTemplateName(name: String) { connection.prepareStatement("UPDATE collection_meta SET default_template=? WHERE id=1").use { it.setString(1,name); it.executeUpdate() } }

    fun getCollectionPresentation(): CollectionPresentation = connection.prepareStatement("SELECT description_heading,show_artist_copyright,bleed_opacity,foreground_opacity FROM collection_meta WHERE id=1").use { p ->
        p.executeQuery().use { r -> if (r.next()) CollectionPresentation(r.getString(1).orEmpty().ifBlank { "ABILITY / DESCRIPTION" }, r.getInt(2)!=0, r.getDouble(3).coerceIn(0.0,1.0), r.getDouble(4).coerceIn(0.0,1.0)) else CollectionPresentation() }
    }
    fun setCollectionPresentation(settings: CollectionPresentation) {
        connection.prepareStatement("UPDATE collection_meta SET description_heading=?,show_artist_copyright=?,bleed_opacity=?,foreground_opacity=? WHERE id=1").use {
            it.setString(1, settings.descriptionHeading.ifBlank { "ABILITY / DESCRIPTION" }); it.setInt(2, if(settings.showArtistCopyright)1 else 0); it.setDouble(3,settings.bleedOpacity.coerceIn(0.0,1.0)); it.setDouble(4,settings.foregroundOpacity.coerceIn(0.0,1.0)); it.executeUpdate()
        }
    }

    fun dataSnapshot(assetId: String): CardData? = findAssetById(assetId)?.currentJson?.let(::decode)
    fun dataSnapshotForPath(image: Path): CardData? = findAssetByPath(relative(image))?.currentJson?.let(::decode)
    fun assetRelativePath(assetId: String): String? = findAssetById(assetId)?.relativePath
    fun assetIdForPath(image: Path): String? = findAssetByPath(relative(image))?.assetId

    /**
     * Detach a catalog asset from a filesystem path that disappeared. The row and its history
     * are retained under an internal tombstone path so a different file later created with the
     * same filename cannot inherit the deleted card's metadata.
     */
    fun markMissing(image: Path): String? {
        val relative = relative(image)
        val record = findAssetByPath(relative) ?: return null
        val tombstone = ".cardforge-missing/${record.assetId}/${relative.replace('\\', '/')}"
        connection.prepareStatement("UPDATE assets SET relative_path=?,last_seen_at=? WHERE asset_id=?").use {
            it.setString(1, tombstone)
            it.setString(2, Instant.now().toString())
            it.setString(3, record.assetId)
            it.executeUpdate()
        }
        recordActivity(record.assetId, "MISSING", relative)
        return record.assetId
    }

    /**
     * Reattach a newly discovered file to a missing asset when the file content hash matches.
     * This preserves identity across moves/renames while avoiding accidental metadata transfer
     * when an unrelated file reuses an old filename.
     */
    fun reconcileAdded(image: Path): String? {
        val absolute = image.toAbsolutePath().normalize()
        val relative = relative(absolute)
        findAssetByPath(relative)?.let { return it.assetId }

        val hash = sha256(absolute)
        val missing = connection.prepareStatement(
            "SELECT asset_id FROM assets WHERE sha256=? AND relative_path LIKE '.cardforge-missing/%' ORDER BY last_seen_at DESC LIMIT 1"
        ).use { statement ->
            statement.setString(1, hash)
            statement.executeQuery().use { result -> if (result.next()) result.getString(1) else null }
        } ?: return null

        val size = runCatching { Files.size(absolute) }.getOrDefault(0L)
        val modified = runCatching { Files.getLastModifiedTime(absolute).toMillis() }.getOrDefault(0L)
        connection.prepareStatement(
            "UPDATE assets SET relative_path=?,file_size=?,modified_at=?,sha256=?,last_seen_at=? WHERE asset_id=?"
        ).use {
            it.setString(1, relative)
            it.setLong(2, size)
            it.setLong(3, modified)
            it.setString(4, hash)
            it.setString(5, Instant.now().toString())
            it.setString(6, missing)
            it.executeUpdate()
        }
        recordActivity(missing, "REATTACHED", relative)
        return missing
    }

    fun searchIndex(): Map<String,String> = StartupProfiler.measure(
        "sqlite search index",
        detail = { "${it.size} entries" }
    ) {
        connection.createStatement().use { s ->
            s.executeQuery("SELECT relative_path,asset_id,status,current_json FROM assets WHERE relative_path NOT LIKE '.cardforge-missing/%'").use { r ->
                buildMap {
                    while (r.next()) {
                        val p = r.getString(1)
                        put(p, (p + " " + r.getString(2) + " " + r.getString(3) + " " + r.getString(4).orEmpty()).lowercase())
                    }
                }
            }
        }
    }

    fun save(image: Path, data: CardData): SaveResult {
        val record=register(image,data.assetId,data.status)
        data.assetId=record.assetId
        val json=JsonSupport.mapper.writeValueAsString(data)
        if(record.currentJson!=null && normalizeJson(record.currentJson)==normalizeJson(json)) return SaveResult(record.assetId,null,false)
        val rev=nextRevisionNumber(record.assetId); val now=Instant.now().toString(); val changes=diffJson(record.currentJson,json)
        connection.autoCommit=false
        try {
            connection.prepareStatement("UPDATE assets SET status=?,current_json=?,last_saved_at=?,last_seen_at=? WHERE asset_id=?").use { it.setString(1,data.status.name);it.setString(2,json);it.setString(3,now);it.setString(4,now);it.setString(5,record.assetId);it.executeUpdate() }
            connection.prepareStatement("INSERT INTO revisions(asset_id,revision_number,saved_at,status,changes_json,snapshot_json) VALUES(?,?,?,?,?,?)").use { it.setString(1,record.assetId);it.setInt(2,rev);it.setString(3,now);it.setString(4,data.status.name);it.setString(5,changes);it.setString(6,json);it.executeUpdate() }
            connection.commit()
        } catch(e:Exception){ connection.rollback(); throw e } finally { connection.autoCommit=true }
        return SaveResult(record.assetId,rev,true)
    }

    fun recordActivity(assetId:String, action:String, details:String="") { connection.prepareStatement("INSERT INTO activity(asset_id,occurred_at,action,details) VALUES(?,?,?,?)").use { it.setString(1,assetId);it.setString(2,Instant.now().toString());it.setString(3,action);it.setString(4,details.ifBlank { null });it.executeUpdate() } }
    fun history(assetId:String):List<HistoryRecord> = connection.prepareStatement("SELECT revision_number,saved_at,status,changes_json FROM revisions WHERE asset_id=? ORDER BY revision_number DESC").use { p -> p.setString(1,assetId);p.executeQuery().use { r -> buildList { while(r.next()) add(HistoryRecord(r.getInt(1),r.getString(2),runCatching{CardStatus.valueOf(r.getString(3))}.getOrDefault(CardStatus.IN_PROGRESS),r.getString(4))) } } }
    fun usedCollectorNumbers(excludingAssetId:String?=null):Set<String> = connection.createStatement().use { s -> s.executeQuery("SELECT asset_id,current_json FROM assets WHERE current_json IS NOT NULL").use { r -> buildSet { while(r.next()){ if(excludingAssetId==r.getString(1)) continue; decode(r.getString(2))?.collectorNumber?.trim()?.takeIf{it.isNotBlank()}?.let(::add) } } } }
    fun countAssets():Int=connection.createStatement().use { s->s.executeQuery("SELECT COUNT(*) FROM assets").use{it.next();it.getInt(1)} }
    fun countRevisions(assetId:String):Int=connection.prepareStatement("SELECT COUNT(*) FROM revisions WHERE asset_id=?").use{it.setString(1,assetId);it.executeQuery().use{r->r.next();r.getInt(1)}}

    fun backupTo(target:Path){ val d=target.toAbsolutePath().normalize();require(d!=path.toAbsolutePath().normalize());Files.createDirectories(d.parent);if(Files.exists(d))Files.delete(d);connection.createStatement().use{it.execute("PRAGMA wal_checkpoint(TRUNCATE)")};val e=d.toString().replace("'","''");connection.createStatement().use{it.execute("VACUUM INTO '$e'")} }

    private fun relative(image:Path)=root.relativize(image.toAbsolutePath().normalize()).toString().replace('\\','/')
    private fun decode(json:String):CardData?=runCatching{JsonSupport.mapper.readValue(json,CardData::class.java)}.getOrNull()
    private fun assetMetadata(id:String):Pair<Long,Long>?=connection.prepareStatement("SELECT file_size,modified_at FROM assets WHERE asset_id=?").use{it.setString(1,id);it.executeQuery().use{r->if(r.next())r.getLong(1) to r.getLong(2) else null}}
    private fun findAssetById(id:String):AssetRecord?=findAsset("asset_id",id)
    private fun findAssetByPath(p:String):AssetRecord?=findAsset("relative_path",p)
    private fun findAsset(column:String,value:String):AssetRecord?=connection.prepareStatement("SELECT asset_id,relative_path,status,current_json,last_saved_at,(SELECT COUNT(*) FROM revisions r WHERE r.asset_id=a.asset_id) FROM assets a WHERE $column=?").use{it.setString(1,value);it.executeQuery().use{r->if(r.next())AssetRecord(r.getString(1),r.getString(2),runCatching{CardStatus.valueOf(r.getString(3))}.getOrDefault(CardStatus.IN_PROGRESS),r.getString(4),r.getInt(6),r.getString(5)) else null}}
    private fun insertAsset(id:String,relative:String,status:CardStatus,size:Long,modified:Long,hash:String):AssetRecord{val now=Instant.now().toString();connection.prepareStatement("INSERT INTO assets(asset_id,relative_path,status,sha256,file_size,modified_at,first_seen_at,last_seen_at) VALUES(?,?,?,?,?,?,?,?)").use{it.setString(1,id);it.setString(2,relative);it.setString(3,status.name);it.setString(4,hash);it.setLong(5,size);it.setLong(6,modified);it.setString(7,now);it.setString(8,now);it.executeUpdate()};return findAssetById(id)!!}
    private fun nextRevisionNumber(id:String):Int=connection.prepareStatement("SELECT COALESCE(MAX(revision_number),0)+1 FROM revisions WHERE asset_id=?").use{it.setString(1,id);it.executeQuery().use{r->r.next();r.getInt(1)}}
    private fun normalizeJson(json:String)=runCatching{JsonSupport.mapper.readTree(json)}.getOrNull()?.toString()?:json
    private fun diffJson(before:String?,after:String):String { val b=before?.let{runCatching{JsonSupport.mapper.readTree(it)}.getOrNull()};val a=runCatching{JsonSupport.mapper.readTree(after)}.getOrNull()?:return "{}";val changes=JsonSupport.mapper.createObjectNode();a.fieldNames().forEachRemaining{n->if(b?.get(n)!=a.get(n))changes.set<com.fasterxml.jackson.databind.JsonNode>(n,a.get(n))};return changes.toString() }
    private fun sha256(file:Path):String { val md=MessageDigest.getInstance("SHA-256");Files.newInputStream(file).use{input->val buf=ByteArray(64*1024);while(true){val n=input.read(buf);if(n<0)break;md.update(buf,0,n)}};return md.digest().joinToString(""){"%02x".format(it)} }
    override fun close(){connection.close()}
}
