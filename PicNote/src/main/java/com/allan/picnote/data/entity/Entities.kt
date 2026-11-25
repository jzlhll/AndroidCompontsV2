package com.allan.picnote.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val MaxNodeTextBytes = 1024 * 1024

@Entity(tableName = "documents", indices = [Index("updated_at")])
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "nodes",
    foreignKeys = [ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["document_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["document_id", "sort_key"])],
)
data class NodeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "sort_key") val sortKey: Long,
    val kind: String,
    @ColumnInfo(name = "body_markdown") val bodyMarkdown: String = "",
    val title: String = "",
)

@Entity(
    tableName = "node_images",
    foreignKeys = [ForeignKey(entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["node_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("node_id"), Index(value = ["document_id", "sort_index"]), Index(value = ["document_id", "hash"])],
)
data class NodeImageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "sort_index") val sortIndex: Int,
    @ColumnInfo(name = "file_name") val fileName: String,
    val hash: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "source_uri") val sourceUri: String? = null,
)

@Entity(
    tableName = "source_images",
    primaryKeys = ["source_uri", "document_id", "file_name"],
    foreignKeys = [ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["document_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("document_id")],
)
data class SourceImageEntity(
    @ColumnInfo(name = "source_uri") val sourceUri: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "source_version") val sourceVersion: String? = null,
    @ColumnInfo(name = "source_added_at") val sourceAddedAt: Long? = null,
)

@Entity(tableName = "media_snapshots")
data class MediaSnapshotEntity(
    @PrimaryKey val uri: String,
    val time: Long,
)

@Entity(tableName = "media_scan_states")
data class MediaScanStateEntity(
    @PrimaryKey val volume: String,
    val version: String,
    @ColumnInfo(name = "scanned_at") val scannedAt: Long,
    @ColumnInfo(name = "baseline_valid") val baselineValid: Boolean,
    val available: Boolean,
)

@Entity(tableName = "pending_media", indices = [Index("volume")])
data class PendingMediaEntity(
    @PrimaryKey val uri: String,
    val volume: String,
    val name: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(defaultValue = "''") val version: String,
)

data class DocumentSummary(
    val id: String,
    val title: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "node_count") val nodeCount: Int,
)

data class NodeContent(val node: NodeEntity, val images: List<NodeImageEntity>)
data class DocumentContent(
    val document: DocumentEntity,
    val nodes: List<NodeContent>,
    val importedSourceUris: Set<String> = emptySet(),
)

fun DocumentContent.chapterNumbers(): Map<String, String> = nodes.asSequence()
    .filter { it.node.kind == "chapter" }
    .mapIndexed { index, content -> content.node.id to (index + 1).toString().padStart(2, '0') }
    .toMap()
