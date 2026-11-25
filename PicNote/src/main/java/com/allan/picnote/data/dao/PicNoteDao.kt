package com.allan.picnote.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.allan.picnote.data.entity.*

@Dao
interface PicNoteDao {
    @Query("SELECT d.id, d.title, d.updated_at, (SELECT COUNT(*) FROM nodes n WHERE n.document_id = d.id) AS node_count FROM documents d ORDER BY updated_at DESC")
    suspend fun documents(): List<DocumentSummary>

    @Query("SELECT id, title, updated_at FROM documents WHERE id = :id")
    suspend fun document(id: String): DocumentEntity?

    @Query("SELECT id, document_id, sort_key, kind, body_markdown, title FROM nodes WHERE document_id = :id ORDER BY sort_key, id")
    suspend fun nodes(id: String): List<NodeEntity>

    @Query("SELECT id, document_id, sort_key, kind, body_markdown, title FROM nodes WHERE document_id = :documentId AND id = :nodeId")
    suspend fun node(documentId: String, nodeId: String): NodeEntity?

    @Query("SELECT :nodeId IN (SELECT id FROM nodes WHERE document_id = :documentId ORDER BY sort_key, id LIMIT 2)")
    suspend fun isFixedNode(documentId: String, nodeId: String): Boolean

    @Query("SELECT id, node_id, document_id, sort_index, file_name, hash, width, height, source_uri FROM node_images WHERE document_id = :id ORDER BY sort_index, id")
    suspend fun images(id: String): List<NodeImageEntity>

    @Query("SELECT id, node_id, document_id, sort_index, file_name, hash, width, height, source_uri FROM node_images WHERE node_id = :nodeId AND document_id = :documentId ORDER BY sort_index, id")
    suspend fun nodeImages(documentId: String, nodeId: String): List<NodeImageEntity>

    @Query("SELECT file_name FROM node_images WHERE document_id = :id")
    suspend fun referencedFiles(id: String): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun putDocument(value: DocumentEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun putNodes(values: List<NodeEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putImages(values: List<NodeImageEntity>)

    @Query("UPDATE documents SET title = :title, updated_at = :time WHERE id = :id")
    suspend fun updateDocumentTitle(id: String, title: String, time: Long)
    @Query("UPDATE documents SET updated_at = :time WHERE id = :id")
    suspend fun touchDocument(id: String, time: Long)
    @Query("UPDATE nodes SET sort_key = :key WHERE id = :id")
    suspend fun setSortKey(id: String, key: Long)
    @Query("UPDATE nodes SET kind = :kind, body_markdown = :body, title = :title WHERE id = :id")
    suspend fun updateNode(id: String, kind: String, body: String, title: String)
    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)
    @Query("DELETE FROM nodes WHERE id = :id")
    suspend fun deleteNode(id: String)
    @Query("DELETE FROM node_images WHERE node_id = :id")
    suspend fun deleteImages(id: String)

    @Query("SELECT uri, time FROM media_snapshots WHERE substr(uri, 1, length(:prefix)) = :prefix ORDER BY time DESC, uri")
    suspend fun snapshots(prefix: String): List<MediaSnapshotEntity>
    @Query("SELECT * FROM media_scan_states")
    suspend fun scanStates(): List<MediaScanStateEntity>
    @Query("SELECT p.* FROM pending_media p JOIN media_scan_states s ON p.volume = s.volume AND p.version = s.version WHERE s.available = 1 AND s.baseline_valid = 1 ORDER BY p.added_at DESC, p.uri")
    suspend fun pending(): List<PendingMediaEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSnapshots(values: List<MediaSnapshotEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putScanStates(values: List<MediaScanStateEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPending(values: List<PendingMediaEntity>)
    @Query("DELETE FROM media_snapshots WHERE substr(uri, 1, length(:prefix)) = :prefix")
    suspend fun clearSnapshots(prefix: String)
    @Query("DELETE FROM media_snapshots WHERE uri IN (:uris)")
    suspend fun deleteSnapshots(uris: List<String>)
    @Query("DELETE FROM pending_media WHERE volume = :volume")
    suspend fun clearPendingVolume(volume: String)
    @Query("DELETE FROM pending_media WHERE uri IN (:uris)")
    suspend fun consumePending(uris: List<String>)
    @Query("DELETE FROM pending_media")
    suspend fun clearPending()
    @Query("DELETE FROM pending_media WHERE volume = :volume AND NOT EXISTS (SELECT 1 FROM media_snapshots s WHERE s.uri = pending_media.uri AND s.time = pending_media.added_at)")
    suspend fun cleanMissingPending(volume: String)
    @Query("UPDATE media_scan_states SET baseline_valid = 0")
    suspend fun invalidateBaselines()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSourceImages(values: List<SourceImageEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun keepSourceImages(values: List<SourceImageEntity>)
    @Query("SELECT source_uri, document_id, file_name, source_version, source_added_at FROM source_images WHERE document_id = :id")
    suspend fun sourceImages(id: String): List<SourceImageEntity>
    @Query("""
        SELECT a.source_uri, a.document_id, a.file_name, a.source_version, a.source_added_at FROM source_images a
        JOIN media_snapshots s ON s.uri = a.source_uri AND s.time = a.source_added_at
        JOIN media_scan_states v ON v.version = a.source_version AND v.baseline_valid = 1 AND v.available = 1
            AND substr(a.source_uri, 1, length('content://media/' || v.volume || '/images/media/')) = 'content://media/' || v.volume || '/images/media/'
        WHERE a.document_id = :id
            AND EXISTS (SELECT 1 FROM node_images n WHERE n.document_id = a.document_id AND n.file_name = a.file_name)
    """)
    suspend fun activeSourceImages(id: String): List<SourceImageEntity>
    @Query("DELETE FROM source_images WHERE document_id = :id AND file_name NOT IN (SELECT file_name FROM node_images WHERE document_id = :id)")
    suspend fun cleanSourceImages(id: String)
}
