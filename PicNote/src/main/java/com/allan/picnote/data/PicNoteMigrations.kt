package com.allan.picnote.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE node_images RENAME COLUMN source_identity TO source_uri")
        // 旧版通过保存的快照标识关联相册 URI。无法关联的旧标识仍随素材保留。
        db.execSQL("""
            UPDATE node_images SET source_uri = COALESCE(
                (SELECT uri FROM media_snapshots WHERE identity = node_images.source_uri),
                (SELECT uri FROM pending_media WHERE identity = node_images.source_uri),
                source_uri
            ) WHERE source_uri IS NOT NULL
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE source_images (source_uri TEXT NOT NULL, document_id TEXT NOT NULL, file_name TEXT NOT NULL,
                PRIMARY KEY(source_uri, document_id, file_name),
                FOREIGN KEY(document_id) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE CASCADE)
        """.trimIndent())
        db.execSQL("CREATE INDEX index_source_images_document_id ON source_images(document_id)")
        db.execSQL("""
            INSERT OR IGNORE INTO source_images (source_uri, document_id, file_name)
            SELECT source_uri, document_id, file_name FROM node_images WHERE source_uri IS NOT NULL
        """.trimIndent())

        db.execSQL("CREATE TABLE media_changes_new (uri TEXT NOT NULL PRIMARY KEY, volume TEXT NOT NULL)")
        db.execSQL("""
            INSERT OR IGNORE INTO media_changes_new (uri, volume)
            SELECT COALESCE(s.uri, p.uri), c.volume FROM media_changes c
            LEFT JOIN media_snapshots s ON s.identity = c.identity
            LEFT JOIN pending_media p ON p.identity = c.identity
            WHERE COALESCE(s.uri, p.uri) IS NOT NULL
        """.trimIndent())
        db.execSQL("DROP TABLE media_changes")
        db.execSQL("ALTER TABLE media_changes_new RENAME TO media_changes")

        db.execSQL("CREATE TABLE media_snapshots_new (uri TEXT NOT NULL PRIMARY KEY, time INTEGER NOT NULL)")
        db.execSQL("INSERT INTO media_snapshots_new SELECT uri, MAX(added_at) FROM media_snapshots GROUP BY uri")
        db.execSQL("DROP TABLE media_snapshots")
        db.execSQL("ALTER TABLE media_snapshots_new RENAME TO media_snapshots")

        db.execSQL("CREATE TABLE pending_media_new (uri TEXT NOT NULL PRIMARY KEY, volume TEXT NOT NULL, name TEXT NOT NULL, added_at INTEGER NOT NULL)")
        db.execSQL("INSERT OR REPLACE INTO pending_media_new SELECT uri, volume, name, added_at FROM pending_media ORDER BY added_at")
        db.execSQL("DROP TABLE pending_media")
        db.execSQL("ALTER TABLE pending_media_new RENAME TO pending_media")
        db.execSQL("CREATE INDEX index_pending_media_volume ON pending_media(volume)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Android 12 所带 SQLite 尚不支持 DROP COLUMN。
        db.execSQL("""
            CREATE TABLE node_images_new (id TEXT NOT NULL PRIMARY KEY, node_id TEXT NOT NULL,
                document_id TEXT NOT NULL, sort_index INTEGER NOT NULL, file_name TEXT NOT NULL,
                hash TEXT NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, source_uri TEXT,
                FOREIGN KEY(node_id) REFERENCES nodes(id) ON UPDATE NO ACTION ON DELETE CASCADE)
        """.trimIndent())
        db.execSQL("""
            INSERT INTO node_images_new (id, node_id, document_id, sort_index, file_name, hash, width, height, source_uri)
            SELECT id, node_id, document_id, sort_index, file_name, hash, width, height, source_uri FROM node_images
        """.trimIndent())
        db.execSQL("DROP TABLE node_images")
        db.execSQL("ALTER TABLE node_images_new RENAME TO node_images")
        db.execSQL("CREATE INDEX index_node_images_node_id ON node_images(node_id)")
        db.execSQL("CREATE INDEX index_node_images_document_id ON node_images(document_id)")
        db.execSQL("CREATE INDEX index_node_images_document_id_hash ON node_images(document_id, hash)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE source_images ADD COLUMN source_version TEXT")
        db.execSQL("ALTER TABLE source_images ADD COLUMN source_added_at INTEGER")
        db.execSQL("ALTER TABLE pending_media ADD COLUMN version TEXT NOT NULL DEFAULT ''")
        db.execSQL("""
            UPDATE pending_media SET version = COALESCE(
                (SELECT version FROM media_scan_states WHERE volume = pending_media.volume), '')
        """.trimIndent())
        db.execSQL("""
            INSERT OR IGNORE INTO source_images (source_uri, document_id, file_name)
            SELECT source_uri, document_id, file_name FROM node_images WHERE source_uri IS NOT NULL
        """.trimIndent())
        // 只有在旧版主映射仍有效、且 URI 与扫描快照能关联到同一卷时回填身份。
        db.execSQL("""
            UPDATE source_images SET
                source_version = (SELECT version FROM media_scan_states
                    WHERE available = 1 AND baseline_valid = 1
                        AND substr(source_images.source_uri, 1, length('content://media/' || volume || '/images/media/')) = 'content://media/' || volume || '/images/media/'),
                source_added_at = (SELECT time FROM media_snapshots WHERE uri = source_images.source_uri)
            WHERE EXISTS (SELECT 1 FROM node_images n
                WHERE n.document_id = source_images.document_id AND n.file_name = source_images.file_name
                    AND n.source_uri = source_images.source_uri)
                AND EXISTS (SELECT 1 FROM media_snapshots WHERE uri = source_images.source_uri)
        """.trimIndent())
        db.execSQL("DROP TABLE media_changes")
        db.execSQL("DROP INDEX index_nodes_document_id")
        db.execSQL("CREATE INDEX index_nodes_document_id_sort_key ON nodes(document_id, sort_key)")
        db.execSQL("DROP INDEX index_node_images_document_id")
        db.execSQL("CREATE INDEX index_node_images_document_id_sort_index ON node_images(document_id, sort_index)")
        db.execSQL("CREATE INDEX index_documents_updated_at ON documents(updated_at)")
    }
}
