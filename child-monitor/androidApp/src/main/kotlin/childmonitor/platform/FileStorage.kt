package childmonitor.platform

import childmonitor.model.MediaInfo
import childmonitor.model.SessionManifest

interface FileStorage : StorageCapacity {
    suspend fun usedBytes(): Long
    suspend fun deleteVideo(relativePath: String, stagingPath: String, sessionId: String)
    suspend fun create(manifest: SessionManifest)
    suspend fun writeManifest(manifest: SessionManifest)
    suspend fun manifests(): List<SessionManifest>
    suspend fun probe(relativePath: String): MediaInfo?
    suspend fun exists(relativePath: String): Boolean
    suspend fun move(source: String, destination: String)
    suspend fun deleteSession(relativeDir: String, sessionId: String)
}
