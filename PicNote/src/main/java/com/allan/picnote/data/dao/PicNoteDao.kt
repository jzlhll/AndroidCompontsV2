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

    @Query("SELECT id, node_id, document_id, sort_index, file_name, hash, width, height, source_uri FROM node_images WHERE document_id = :id ORDER BY sort_index")
    suspend fun images(id: String): List<NodeImageEntity>

    @Query("SELECT file_name FROM node_images WHERE document_id = :id")
    suspend fun referencedFiles(id: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDocument(value: DocumentEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
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
    @Query("SELECT * FROM media_changes")
    suspend fun changes(): List<MediaChangeEntity>
    @Query("SELECT p.* FROM pending_media p JOIN media_scan_states s ON p.volume = s.volume WHERE s.available = 1 AND s.baseline_valid = 1 ORDER BY p.added_at DESC, p.uri")
    suspend fun pending(): List<PendingMediaEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSnapshots(values: List<MediaSnapshotEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putScanStates(values: List<MediaScanStateEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putChanges(values: List<MediaChangeEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPending(values: List<PendingMediaEntity>)
    @Query("DELETE FROM media_changes")
    suspend fun clearChanges()
    @Query("DELETE FROM media_snapshots WHERE substr(uri, 1, length(:prefix)) = :prefix")
    suspend fun clearSnapshots(prefix: String)
    @Query("DELETE FROM pending_media WHERE volume = :volume")
    suspend fun clearPendingVolume(volume: String)
    @Query("UPDATE node_images SET source_uri = NULL WHERE substr(source_uri, 1, length(:prefix)) = :prefix")
    suspend fun clearSourceUris(prefix: String)
    @Query("DELETE FROM pending_media WHERE uri IN (:uris)")
    suspend fun consumePending(uris: List<String>)
    @Query("DELETE FROM pending_media")
    suspend fun clearPending()
    @Query("DELETE FROM pending_media WHERE volume = :volume AND uri NOT IN (SELECT uri FROM media_snapshots)")
    suspend fun cleanMissingPending(volume: String)
    @Query("UPDATE media_scan_states SET baseline_valid = 0")
    suspend fun invalidateBaselines()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSourceImages(values: List<SourceImageEntity>)
    @Query("DELETE FROM source_images WHERE document_id = :id AND file_name NOT IN (SELECT file_name FROM node_images WHERE document_id = :id)")
    suspend fun cleanSourceImages(id: String)
}
