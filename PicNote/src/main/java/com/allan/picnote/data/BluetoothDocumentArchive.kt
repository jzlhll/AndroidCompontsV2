package com.allan.picnote.data

import android.database.sqlite.SQLiteFullException
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.system.ErrnoException
import android.system.OsConstants
import android.util.JsonReader
import android.util.JsonToken
import androidx.annotation.StringRes
import com.allan.picnote.R
import com.allan.picnote.data.entity.DocumentContent
import com.allan.picnote.logEx
import com.allan.picnote.loge
import com.au.module_android.Globals
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

const val BluetoothArchiveMaxBytes = 256L * 1024 * 1024
const val BluetoothArchiveDirectory = "bluetooth_archives"

private const val ArchiveFormat = "picnote-document"
private const val ArchiveVersion = 1
private const val ManifestEntry = "manifest.json"
private const val MaxEntries = 1025
private const val MaxNodes = 4096
private const val MaxImageReferences = 8192
private const val MaxManifestBytes = 4L * 1024 * 1024
private const val MaxNodeTextBytes = 1024 * 1024
private const val MaxMediaBytes = 64L * 1024 * 1024
private const val MaxDecodedImageBytes = 128L * 1024 * 1024
private const val MaxCentralDirectoryBytes = 1024L * 1024
private const val ZipEndHeaderSize = 22
private const val MaxZipCommentBytes = 65535
private val Sha256 = Regex("[a-f0-9]{64}")
private val MediaEntry = Regex("media/(?:[a-f0-9]{64}|[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}|launcher)\\.webp")
private val UuidWebpFile = Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}\\.webp")
private val EditableNodeKinds = setOf("introduction", "chapter", "body", "code")

class BluetoothArchiveException(@StringRes val messageResId: Int, cause: Throwable? = null) :
    IOException(Globals.app.getString(messageResId), cause)

@Serializable
data class BluetoothArchiveManifest(
    val format: String,
    val formatVersion: Int,
    val title: String,
    val nodes: List<BluetoothArchiveNode>,
)

@Serializable
data class BluetoothArchiveNode(
    val kind: String,
    val title: String,
    val bodyMarkdown: String,
    val images: List<BluetoothArchiveImage>,
)

@Serializable
data class BluetoothArchiveImage(
    val entry: String,
    val fileName: String,
    val width: Int,
    val height: Int,
    val hash: String,
    val webpSha256: String,
)

data class BluetoothArchiveSnapshot(
    val manifest: BluetoothArchiveManifest,
    val media: Collection<BluetoothArchiveMedia>,
    val unpackedMediaBytes: Long,
)
data class BluetoothArchiveMedia(val file: File, val size: Long, val crc: Long, val sha256: String) {
    val entry: String get() = "media/${file.name}"
}

/** 生成并验证可编辑文档包，在独立目录还原素材文件名。 */
class BluetoothDocumentArchive(private val root: File) {
    fun openSession(prefix: String): File {
        Files.createDirectories(root.toPath())
        return Files.createTempDirectory(root.toPath(), prefix).toFile()
    }

    suspend fun copySnapshot(
        directory: File,
        content: DocumentContent,
        imageFile: (String) -> File,
    ): BluetoothArchiveSnapshot {
        requireArchiveLimit(content.nodes.size <= MaxNodes)
        var unpackedMediaBytes = 0L
        var imageCount = 0
        var textBytes = content.document.title.encodeToByteArray().size.toLong()
        val byFileName = mutableMapOf<String, BluetoothArchiveMedia>()
        val bySha256 = linkedMapOf<String, BluetoothArchiveMedia>()
        val nodes = content.nodes.map { (node, images) ->
            currentCoroutineContext().ensureActive()
            textBytes += nodeTextBytes(node.title, node.bodyMarkdown)
            imageCount += images.size
            requireArchiveLimit(textBytes <= MaxManifestBytes && imageCount <= MaxImageReferences)
            val references = images.map { image ->
                validateImageSize(image.width, image.height)
                requireValidArchive(Sha256.matches(image.hash) && isSupportedMediaFileName(image.fileName, image.hash))
                val media = byFileName.getOrPut(image.fileName) {
                    requireArchiveLimit(byFileName.size < MaxEntries - 1)
                    val source = imageFile(image.fileName)
                    if (!source.isFile || !source.canRead()) throw BluetoothArchiveException(R.string.error_export_image_unreadable)
                    val target = File(directory, image.fileName)
                    val digest = MessageDigest.getInstance("SHA-256")
                    val crc = CRC32()
                    val limit = Math.min(MaxMediaBytes, BluetoothArchiveMaxBytes - unpackedMediaBytes)
                    val size = source.inputStream().use { input ->
                        target.outputStream().use { output -> copyLimited(input, output, limit, digest, crc) }
                    }
                    requireValidArchive(size > 0)
                    unpackedMediaBytes += size
                    val sha256 = digest.hexDigest()
                    val existing = bySha256[sha256]
                    if (existing != null) {
                        deleteBluetoothArchiveFiles(target)
                        existing
                    } else {
                        BluetoothArchiveMedia(target, size, crc.value, sha256).also { bySha256[sha256] = it }
                    }
                }
                BluetoothArchiveImage(media.entry, image.fileName, image.width, image.height, image.hash, media.sha256)
            }
            BluetoothArchiveNode(node.kind, node.title, node.bodyMarkdown, references)
        }
        val manifest = BluetoothArchiveManifest(ArchiveFormat, ArchiveVersion, content.document.title, nodes)
        return BluetoothArchiveSnapshot(manifest, bySha256.values, unpackedMediaBytes)
    }

    suspend fun write(destination: File, snapshot: BluetoothArchiveSnapshot, digest: MessageDigest?) {
        validateManifest(snapshot.manifest)
        val manifest = Json.encodeToString(snapshot.manifest).encodeToByteArray()
        requireArchiveLimit(manifest.size <= MaxManifestBytes && snapshot.unpackedMediaBytes + manifest.size <= BluetoothArchiveMaxBytes)
        val file = destination.outputStream()
        val output = if (digest == null) file else DigestOutputStream(file, digest)
        ZipOutputStream(output.buffered()).use { zip ->
            zip.setLevel(5)
            zip.putNextEntry(ZipEntry(ManifestEntry))
            zip.write(manifest)
            zip.closeEntry()
            snapshot.media.forEach { media ->
                currentCoroutineContext().ensureActive()
                val entry = ZipEntry(media.entry).apply {
                    method = ZipEntry.STORED
                    size = media.size
                    compressedSize = media.size
                    crc = media.crc
                }
                zip.putNextEntry(entry)
                media.file.inputStream().use { copyLimited(it, zip, media.size) }
                zip.closeEntry()
            }
        }
        requireArchiveLimit(destination.length() <= BluetoothArchiveMaxBytes)
    }

    suspend fun read(archive: File, mediaDirectory: File): BluetoothArchiveManifest {
        requireValidArchive(archive.isFile)
        requireArchiveLimit(archive.length() <= BluetoothArchiveMaxBytes)
        val entryCount = readZipEntryCount(archive)
        try {
            ZipFile(archive).use { zip ->
                val entries = linkedMapOf<String, ZipEntry>()
                zip.entries().asSequence().forEach { entry ->
                    requireArchiveLimit(entries.size < MaxEntries)
                    requireValidArchive(!entry.isDirectory && entries.put(entry.name, entry) == null)
                    requireValidArchive(entry.method == ZipEntry.STORED || entry.method == ZipEntry.DEFLATED)
                }
                requireValidArchive(entries.size == entryCount)
                val manifestEntry = entries.remove(ManifestEntry) ?: rejectMissingManifest(entries.keys)
                val manifestBytes = ByteArrayOutputStream()
                var remaining = BluetoothArchiveMaxBytes - readEntry(zip, manifestEntry, manifestBytes, MaxManifestBytes)
                val manifest = parseManifest(manifestBytes.toByteArray())
                val references = validateManifest(manifest)
                requireValidArchive(entries.keys == references.keys)
                if (references.isNotEmpty()) Files.createDirectory(mediaDirectory.toPath())
                references.forEach { (entryName, images) ->
                    currentCoroutineContext().ensureActive()
                    val image = images.first()
                    val file = File(mediaDirectory, image.fileName)
                    val digest = MessageDigest.getInstance("SHA-256")
                    val limit = Math.min(remaining, MaxMediaBytes)
                    val size = FileOutputStream(file).use { output ->
                        val read = readEntry(zip, entries.getValue(entryName), output, limit, digest)
                        output.fd.sync()
                        read
                    }
                    remaining -= size
                    requireValidArchive(digest.hexDigest() == image.webpSha256)
                    validateWebp(file, image)
                    images.map { it.fileName }.distinct().drop(1).forEach { fileName ->
                        requireArchiveLimit(size <= remaining)
                        FileOutputStream(File(mediaDirectory, fileName)).use { output ->
                            val copied = file.inputStream().use { input -> copyLimited(input, output, size) }
                            requireValidArchive(copied == size)
                            output.fd.sync()
                            remaining -= copied
                        }
                    }
                }
                return manifest
            }
        } catch (e: ZipException) {
            throw BluetoothArchiveException(R.string.error_bluetooth_archive_invalid, e)
        } catch (e: EOFException) {
            throw BluetoothArchiveException(R.string.error_bluetooth_archive_invalid, e)
        }
    }

    private fun parseManifest(bytes: ByteArray): BluetoothArchiveManifest {
        try {
            val text = bytes.decodeToString(throwOnInvalidSequence = true)
            // 先确认类型和版本，识别字段结构可能已经改变的更新版本。
            JsonReader(StringReader(text)).use { reader ->
                var format: String? = null
                var version: Int? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "format" -> {
                            requireValidArchive(format == null && reader.peek() == JsonToken.STRING)
                            format = reader.nextString()
                        }
                        "formatVersion" -> {
                            requireValidArchive(version == null && reader.peek() == JsonToken.NUMBER)
                            version = reader.nextInt()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                requireValidArchive(reader.peek() == JsonToken.END_DOCUMENT)
                requireValidArchive(format == ArchiveFormat && version != null && version > 0)
                if (version != ArchiveVersion) throw BluetoothArchiveException(R.string.error_bluetooth_archive_version)
            }
            return Json.decodeFromString<BluetoothArchiveManifest>(text)
        } catch (e: BluetoothArchiveException) {
            throw e
        } catch (e: Exception) {
            throw BluetoothArchiveException(R.string.error_bluetooth_archive_invalid, e)
        }
    }

    private fun validateManifest(manifest: BluetoothArchiveManifest): Map<String, List<BluetoothArchiveImage>> {
        val nodes = manifest.nodes
        requireArchiveLimit(nodes.size <= MaxNodes && manifest.title.encodeToByteArray().size <= MaxNodeTextBytes)
        requireValidArchive(manifest.title.isNotBlank() && nodes.size >= 2)
        requireValidArchive(nodes[0].kind == "title" && nodes[0].title == manifest.title && nodes[1].kind == "introduction")
        val references = mutableMapOf<String, MutableList<BluetoothArchiveImage>>()
        val hashes = mutableSetOf<String>()
        val entriesByFileName = mutableMapOf<String, String>()
        val entriesByWebpHash = mutableMapOf<String, String>()
        var imageCount = 0
        nodes.forEachIndexed { index, node ->
            nodeTextBytes(node.title, node.bodyMarkdown)
            imageCount += node.images.size
            requireArchiveLimit(imageCount <= MaxImageReferences)
            requireValidArchive(index < 2 || node.kind in EditableNodeKinds)
            requireValidArchive(index == 1 || node.kind != "introduction" || node.bodyMarkdown.isNotBlank())
            requireValidArchive(node.kind != "chapter" || node.title.isNotBlank())
            requireValidArchive(node.kind != "code" || (parseCodeBlock(node.bodyMarkdown)?.code ?: node.bodyMarkdown).isNotBlank())
            requireValidArchive(node.kind != "body" || node.bodyMarkdown.isNotBlank() || node.images.isNotEmpty())
            requireValidArchive(node.kind == "body" || node.images.isEmpty())
            node.images.forEach { image ->
                requireValidArchive(MediaEntry.matches(image.entry) && Sha256.matches(image.hash) && Sha256.matches(image.webpSha256))
                requireValidArchive(isSupportedMediaFileName(image.fileName, image.hash) && hashes.add(image.hash))
                validateImageSize(image.width, image.height)
                requireValidArchive(entriesByFileName.getOrPut(image.fileName) { image.entry } == image.entry)
                requireValidArchive(entriesByWebpHash.getOrPut(image.webpSha256) { image.entry } == image.entry)
                val images = references.getOrPut(image.entry) { mutableListOf() }
                val first = images.firstOrNull()
                requireValidArchive(first == null ||
                    first.webpSha256 == image.webpSha256 && first.width == image.width && first.height == image.height)
                images += image
            }
        }
        requireArchiveLimit(references.size < MaxEntries && entriesByFileName.size < MaxEntries)
        requireValidArchive(references.all { (entry, images) -> images.any { entry == "media/${it.fileName}" } })
        return references
    }

    private fun isSupportedMediaFileName(fileName: String, hash: String): Boolean =
        fileName == "$hash.webp" || fileName == "launcher.webp" || UuidWebpFile.matches(fileName)

    private fun nodeTextBytes(title: String, body: String): Int {
        val size = title.encodeToByteArray().size + body.encodeToByteArray().size
        // 单行文字留出 Room 查询及 CursorWindow 的容量。
        requireArchiveLimit(size <= MaxNodeTextBytes)
        return size
    }

    private fun rejectMissingManifest(entries: Set<String>): Nothing {
        val markdown = entries.singleOrNull { it.endsWith(".md") && '/' !in it && '\\' !in it && it.encodeToByteArray().size <= 255 }
        if (markdown != null && entries.all { it == markdown || MediaEntry.matches(it) }) {
            throw BluetoothArchiveException(R.string.error_bluetooth_archive_legacy)
        }
        throw BluetoothArchiveException(R.string.error_bluetooth_archive_invalid)
    }

    private fun validateImageSize(width: Int, height: Int) {
        requireValidArchive(width in 1..16383 && height in 1..16383)
        requireArchiveLimit(width.toLong() * height * 4 <= MaxDecodedImageBytes)
    }

    private fun validateWebp(file: File, image: BluetoothArchiveImage) {
        try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                validateImageSize(width, height)
                requireValidArchive(info.mimeType == "image/webp" && !info.isAnimated && width == image.width && height == image.height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            }
            bitmap.recycle()
        } catch (e: ImageDecoder.DecodeException) {
            throw BluetoothArchiveException(R.string.error_bluetooth_archive_invalid, e)
        } catch (e: OutOfMemoryError) {
            throw BluetoothArchiveException(R.string.error_bluetooth_archive_limit, e)
        }
    }

    private suspend fun readEntry(
        zip: ZipFile,
        entry: ZipEntry,
        output: OutputStream,
        limit: Long,
        digest: MessageDigest? = null,
    ): Long {
        requireArchiveLimit(entry.size <= limit)
        requireValidArchive(entry.size > 0)
        val crc = CRC32()
        val size = zip.getInputStream(entry).use { copyLimited(it, output, limit, digest, crc) }
        requireValidArchive(size == entry.size && crc.value == entry.crc)
        return size
    }

    private fun readZipEntryCount(file: File): Int {
        // 先限制中央目录的大小和条目数，再交给 ZipFile 建索引。
        val fileSize = file.length()
        val count = Math.min(fileSize, (ZipEndHeaderSize + MaxZipCommentBytes).toLong()).toInt()
        val tailOffset = fileSize - count
        val tail = ByteBuffer.allocate(count).order(ByteOrder.LITTLE_ENDIAN)
        RandomAccessFile(file, "r").use {
            it.seek(tailOffset)
            it.readFully(tail.array())
        }
        for (offset in (count - ZipEndHeaderSize) downTo 0) {
            if (tail.getInt(offset) != 0x06054b50) continue
            val commentLength = tail.getShort(offset + 20).toInt() and 0xffff
            if (offset + ZipEndHeaderSize + commentLength != count) continue
            val localCount = tail.getShort(offset + 8).toInt() and 0xffff
            val totalCount = tail.getShort(offset + 10).toInt() and 0xffff
            val directoryBytes = tail.getInt(offset + 12).toLong() and 0xffffffffL
            val directoryOffset = tail.getInt(offset + 16).toLong() and 0xffffffffL
            requireValidArchive(tail.getInt(offset + 4) == 0 && localCount == totalCount &&
                directoryOffset + directoryBytes == tailOffset + offset)
            requireArchiveLimit(totalCount <= MaxEntries && directoryBytes <= MaxCentralDirectoryBytes)
            return totalCount
        }
        throw BluetoothArchiveException(R.string.error_bluetooth_archive_invalid)
    }
}

fun bluetoothArchiveStorageFailure(error: Exception): BluetoothArchiveException =
    BluetoothArchiveException(bluetoothStorageReason(error), error)

@StringRes
fun bluetoothStorageReason(error: Exception): Int {
    val noSpace = generateSequence<Throwable>(error) { it.cause }.any {
        it is SQLiteFullException || it is ErrnoException && (it.errno == OsConstants.ENOSPC || it.errno == OsConstants.EDQUOT) ||
            it.message?.contains("No space left on device", ignoreCase = true) == true ||
            it.message?.contains("Disk quota exceeded", ignoreCase = true) == true
    }
    return if (noSpace) R.string.error_bluetooth_archive_no_space else R.string.error_bluetooth_archive_io
}

fun deleteBluetoothArchiveFiles(directory: File) {
    try {
        if (!directory.deleteRecursively() && directory.exists()) loge("Delete Bluetooth archive files failed")
    } catch (e: Exception) {
        logEx(throwable = e, message = "Delete Bluetooth archive files failed")
    }
}

private fun requireValidArchive(valid: Boolean) {
    if (!valid) throw BluetoothArchiveException(R.string.error_bluetooth_archive_invalid)
}

private fun requireArchiveLimit(valid: Boolean) {
    if (!valid) throw BluetoothArchiveException(R.string.error_bluetooth_archive_limit)
}

private suspend fun copyLimited(
    input: InputStream,
    output: OutputStream,
    limit: Long,
    digest: MessageDigest? = null,
    crc: CRC32? = null,
): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val size = input.read(buffer)
        if (size < 0) return total
        total += size
        requireArchiveLimit(total <= limit)
        output.write(buffer, 0, size)
        digest?.update(buffer, 0, size)
        crc?.update(buffer, 0, size)
    }
}

private fun MessageDigest.hexDigest(): String = digest().joinToString("") { "%02x".format(it) }
