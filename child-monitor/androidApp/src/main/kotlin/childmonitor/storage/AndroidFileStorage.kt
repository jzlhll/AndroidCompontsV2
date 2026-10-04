package childmonitor.storage

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import java.nio.ByteBuffer
import childmonitor.model.MediaInfo
import childmonitor.model.SessionManifest
import childmonitor.platform.FileStorage
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 持久目录的唯一文件适配，所有业务路径均相对根目录并限制在沙盒内。 */
class AndroidFileStorage(context: Context) : FileStorage {
    val root = File(context.applicationContext.filesDir, "child-monitor")
    private val thumbnails = File(context.applicationContext.cacheDir, "child-monitor-thumbnails")
    fun resolve(relative: String): File {
        require(!relative.startsWith('/') && relative.split('/').none { it == ".." || it.isEmpty() })
        val file = File(root, relative).canonicalFile
        require(file.path.startsWith(root.canonicalPath + File.separator))
        return file
    }
    override suspend fun usedBytes(): Long = withContext(Dispatchers.IO) {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
    override suspend fun deleteVideo(relativePath: String, stagingPath: String, sessionId: String) = withContext(Dispatchers.IO) {
        require(relativePath.endsWith("/$sessionId/video.mp4") && stagingPath.endsWith("/$sessionId/recording.pending.mp4"))
        listOf(resolve(relativePath), resolve(stagingPath), File(thumbnails, "$sessionId.jpg")).forEach {
            check(!it.exists() || it.delete()) { "Video deletion failed" }
        }
    }
    override suspend fun availableBytes(): Long = withContext(Dispatchers.IO) {
        StatFs(root.parentFile!!.absolutePath).availableBytes
    }
    override suspend fun create(manifest: SessionManifest) = withContext(Dispatchers.IO) {
        val dir = resolve(manifest.relativeDir)
        check(!dir.exists() && dir.mkdirs()) { "Session directory already exists or cannot be created" }
        writeManifest(manifest)
    }
    override suspend fun writeManifest(manifest: SessionManifest) = withContext(Dispatchers.IO) {
        require(manifest.formatVersion == 1)
        val temporary = resolve("${manifest.relativeDir}/manifest.json.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(Json.encodeToString(manifest).toByteArray())
            stream.fd.sync()
        }
        Os.rename(temporary.absolutePath, resolve("${manifest.relativeDir}/manifest.json").absolutePath)
        val directory = Os.open(resolve(manifest.relativeDir).absolutePath, OsConstants.O_RDONLY, 0)
        try { Os.fsync(directory) } finally { Os.close(directory) }
    }
    override suspend fun manifests(): List<SessionManifest> = withContext(Dispatchers.IO) {
        val sessions = File(root, "sessions")
        sessions.listFiles().orEmpty().filter { it.isDirectory }.flatMap { date ->
            date.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { dir ->
                try {
                    Json.decodeFromString<SessionManifest>(File(dir, "manifest.json").readText()).also {
                        require(it.formatVersion == 1 && resolve(it.relativeDir) == dir.canonicalFile)
                    }
                } catch (_: Exception) { null }
            }
        }
    }
    override suspend fun exists(relativePath: String) = withContext(Dispatchers.IO) { resolve(relativePath).isFile }
    override suspend fun probe(relativePath: String): MediaInfo? = withContext(Dispatchers.IO) {
        val file = resolve(relativePath)
        if (!file.isFile) return@withContext null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var video = -1
            var width = 0
            var height = 0
            var durationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                check(!mime.startsWith("audio/"))
                if (mime.startsWith("video/")) {
                    check(video == -1)
                    video = i
                    width = format.getInteger(MediaFormat.KEY_WIDTH)
                    height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    durationUs = format.getLong(MediaFormat.KEY_DURATION)
                }
            }
            check(video >= 0 && width > 0 && height > 0 && durationUs > 0)
            val shortEdge = if (width < height) width else height
            val longEdge = if (width > height) width else height
            check(shortEdge <= 480 && longEdge <= 854)
            extractor.selectTrack(video)
            check(extractor.sampleTime >= 0)
            val buffer = ByteBuffer.allocate(width * height * 4)
            check(extractor.readSampleData(buffer, 0) > 0)
            extractor.seekTo(durationUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            check(extractor.sampleTime >= 0 && extractor.readSampleData(buffer, 0) > 0)
            MediaInfo(durationUs, width, height)
        } catch (_: Exception) { null }
        finally { extractor.release() }
    }
    override suspend fun move(source: String, destination: String) = withContext(Dispatchers.IO) {
        val from = resolve(source)
        val to = resolve(destination)
        check(!to.exists()) { "Final media already exists" }
        Os.rename(from.absolutePath, to.absolutePath)
    }
    override suspend fun deleteSession(relativeDir: String, sessionId: String) = withContext(Dispatchers.IO) {
        require(relativeDir.startsWith("sessions/") && relativeDir.split('/').size == 3 && relativeDir.endsWith("/$sessionId"))
        val dir = resolve(relativeDir)
        check(!dir.exists() || dir.deleteRecursively()) { "Session deletion failed" }
        val thumbnail = File(thumbnails, "$sessionId.jpg")
        check(!thumbnail.exists() || thumbnail.delete()) { "Thumbnail deletion failed" }
    }
}
