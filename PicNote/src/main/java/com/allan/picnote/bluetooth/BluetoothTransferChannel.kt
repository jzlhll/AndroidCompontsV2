package com.allan.picnote.bluetooth

import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import android.system.ErrnoException
import android.system.OsConstants
import androidx.annotation.StringRes
import com.allan.picnote.R
import com.allan.picnote.data.BluetoothArchiveMaxBytes
import com.allan.picnote.logdNoFile
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

val PicNoteBluetoothServiceUuid: UUID = UUID.fromString("0c64dc62-4f8f-4aa5-bb58-fc206e4b5c75")
const val BluetoothConnectTimeoutMillis = 60_000L
const val BluetoothApprovalTimeoutMillis = 60_000L
const val BluetoothImportTimeoutMillis = 180_000L

private const val ProtocolMagic = 0x504E4254
private const val ProtocolVersion = 1
private const val MaxControlBytes = 1024
private const val MaxFileNameBytes = 255
private const val DigestBytes = 32
private const val ControlTimeoutMillis = 10_000L
private const val TransferStallMillis = 30_000L

private const val Hello = 1
private const val Offer = 2
private const val Accepted = 3
private const val Imported = 4
private const val Failure = 5
private const val BeginBody = 6

data class BluetoothArchiveOffer(val name: String, val size: Long, val digest: ByteArray)

class BluetoothTransferException(@StringRes val reason: Int) : IOException("Bluetooth transfer failed ($reason)")

enum class BluetoothReceiptFailure(val code: Int, @StringRes val reason: Int) {
    Rejected(1, R.string.bluetooth_transfer_rejected),
    Timeout(2, R.string.bluetooth_transfer_approval_timeout),
    Integrity(3, R.string.bluetooth_transfer_integrity),
    Version(4, R.string.error_bluetooth_archive_version),
    Legacy(5, R.string.error_bluetooth_archive_legacy),
    Invalid(6, R.string.error_bluetooth_archive_invalid),
    Limit(7, R.string.error_bluetooth_archive_limit),
    Space(8, R.string.error_bluetooth_archive_no_space),
    Storage(9, R.string.bluetooth_transfer_peer_storage),
    Protocol(10, R.string.bluetooth_transfer_invalid_control),
}

/** 负责单连接的长度前缀控制消息、定长文件流与超时关断。 */
class BluetoothTransferChannel(private val socket: BluetoothSocket) {
    private val input = DataInputStream(socket.inputStream)
    private val output = DataOutputStream(socket.outputStream)

    suspend fun handshake() = runBluetoothSocketIo(socket, ControlTimeoutMillis) {
        writeControl(Hello) {
            writeInt(ProtocolMagic)
            writeInt(ProtocolVersion)
        }
        readControl(Hello) {
            if (readInt() != ProtocolMagic || readInt() != ProtocolVersion) {
                throw BluetoothTransferException(R.string.bluetooth_transfer_protocol_version)
            }
        }
    }

    suspend fun sendOffer(offer: BluetoothArchiveOffer) = runBluetoothSocketIo(socket, ControlTimeoutMillis) {
        val name = offer.name.toByteArray(Charsets.UTF_8)
        validateOffer(offer.name, name.size, offer.size)
        check(offer.digest.size == DigestBytes)
        writeControl(Offer) {
            writeShort(name.size)
            write(name)
            writeLong(offer.size)
            write(offer.digest)
        }
    }

    suspend fun readOffer(): BluetoothArchiveOffer = runBluetoothSocketIo(socket, ControlTimeoutMillis) {
        readControl(Offer) {
            val nameSize = readUnsignedShort()
            if (nameSize !in 1..MaxFileNameBytes) invalidControl()
            val name = try {
                val nameBytes = ByteArray(nameSize).also(::readFully)
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(nameBytes)).toString()
            } catch (_: CharacterCodingException) {
                invalidControl()
            }
            val size = readLong()
            validateOffer(name, nameSize, size)
            BluetoothArchiveOffer(name, size, ByteArray(DigestBytes).also(::readFully))
        }
    }

    suspend fun sendApproval() = sendStatus(Accepted)
    suspend fun sendImported() = sendStatus(Imported)
    suspend fun sendBodyStart() = sendStatus(BeginBody)

    suspend fun awaitApproval() = runBluetoothSocketIo(
        socket, BluetoothApprovalTimeoutMillis + ControlTimeoutMillis, R.string.bluetooth_transfer_approval_timeout,
    ) { readControl(Accepted, allowFailure = true) {} }

    suspend fun awaitImported() = runBluetoothSocketIo(
        socket, BluetoothImportTimeoutMillis, R.string.bluetooth_transfer_unconfirmed,
    ) { readControl(Imported, allowFailure = true) {} }

    suspend fun awaitBodyStart() = runBluetoothSocketIo(
        socket, BluetoothApprovalTimeoutMillis + 2 * ControlTimeoutMillis,
    ) { readControl(BeginBody) {} }

    suspend fun sendFailure(failure: BluetoothReceiptFailure) = runBluetoothSocketIo(socket, ControlTimeoutMillis) {
        writeControl(Failure) { writeByte(failure.code) }
    }

    suspend fun sendFile(file: File, length: Long, onBodyWritten: () -> Unit, onProgress: (Long) -> Unit) {
        runBluetoothSocketIo(socket, TransferStallMillis) { heartbeat ->
            file.inputStream().buffered().use { source ->
                copyFile(source, output, length, heartbeat, onComplete = onBodyWritten, onProgress = onProgress)
            }
            output.flush()
        }
    }

    suspend fun receiveFile(file: File, offer: BluetoothArchiveOffer, onProgress: (Long) -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256")
        runBluetoothSocketIo(socket, TransferStallMillis) { heartbeat ->
            val stream = try {
                file.outputStream()
            } catch (e: IOException) {
                throw BluetoothTransferException(bluetoothStorageReason(e))
            }
            stream.use { target ->
                copyFile(input, target, offer.size, heartbeat, digest, onProgress = onProgress)
            }
        }
        if (!MessageDigest.isEqual(offer.digest, digest.digest())) {
            throw BluetoothTransferException(R.string.bluetooth_transfer_integrity)
        }
    }

    private suspend fun sendStatus(status: Int) = runBluetoothSocketIo(socket, ControlTimeoutMillis) {
        writeControl(status) {}
    }

    private fun writeControl(type: Int, payload: DataOutputStream.() -> Unit) {
        val frame = ByteArrayOutputStream()
        DataOutputStream(frame).apply {
            writeByte(type)
            payload()
        }
        output.writeInt(frame.size())
        frame.writeTo(output)
        output.flush()
    }

    private fun <T> readControl(
        expectedType: Int,
        allowFailure: Boolean = false,
        payload: DataInputStream.() -> T,
    ): T {
        val size = input.readInt()
        if (size !in 1..MaxControlBytes) invalidControl()
        val frame = DataInputStream(ByteArrayInputStream(ByteArray(size).also(input::readFully)))
        val actualType = frame.readUnsignedByte()
        if (allowFailure && actualType == Failure && size == 2) {
            val code = frame.readUnsignedByte()
            val failure = BluetoothReceiptFailure.entries.firstOrNull { it.code == code } ?: invalidControl()
            throw BluetoothPeerException(failure)
        }
        if (actualType != expectedType) invalidControl()
        val result = frame.payload()
        if (frame.available() != 0) invalidControl()
        return result
    }
}

class BluetoothPeerException(val failure: BluetoothReceiptFailure) : IOException()

private fun validateOffer(name: String, nameSize: Int, size: Long) {
    if (nameSize !in 1..MaxFileNameBytes ||
        !name.endsWith(".zip", ignoreCase = true) ||
        name.any { it == '/' || it == '\\' || Character.isISOControl(it) } ||
        size <= 0
    ) invalidControl()
    if (size > BluetoothArchiveMaxBytes) throw BluetoothTransferException(R.string.error_bluetooth_archive_limit)
}

private fun invalidControl(): Nothing = throw BluetoothTransferException(R.string.bluetooth_transfer_invalid_control)

private suspend fun copyFile(
    source: InputStream,
    target: OutputStream,
    length: Long,
    heartbeat: () -> Unit,
    digest: MessageDigest? = null,
    onComplete: () -> Unit = {},
    onProgress: (Long) -> Unit,
) {
    val buffer = ByteArray(32 * 1024)
    var copied = 0L
    var lastUpdate = 0L
    while (copied < length) {
        currentCoroutineContext().ensureActive()
        val count = source.read(buffer, 0, min(length - copied, buffer.size.toLong()).toInt())
        if (count <= 0) throw EOFException()
        try {
            target.write(buffer, 0, count)
        } catch (e: IOException) {
            if (digest != null) throw BluetoothTransferException(bluetoothStorageReason(e))
            throw e
        }
        digest?.update(buffer, 0, count)
        copied += count
        if (copied == length) onComplete()
        heartbeat()
        val now = SystemClock.elapsedRealtime()
        if (copied == length || now - lastUpdate >= 100L) {
            lastUpdate = now
            withContext(Dispatchers.Main.immediate) { onProgress(copied) }
        }
    }
}

suspend fun <T> runBluetoothSocketIo(
    socket: Closeable,
    timeoutMillis: Long? = null,
    @StringRes timeoutReason: Int = R.string.bluetooth_transfer_timeout,
    block: suspend (heartbeat: () -> Unit) -> T,
): T = coroutineScope {
    val lastProgress = AtomicLong(SystemClock.elapsedRealtime())
    var operationFinished = false
    var timedOut = false
    val abort = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            if (timeoutMillis == null) awaitCancellation()
            else {
                var remaining: Long = timeoutMillis
                while (remaining > 0L) {
                    delay(remaining.milliseconds)
                    remaining = timeoutMillis - (SystemClock.elapsedRealtime() - lastProgress.get())
                }
                timedOut = true
            }
        } finally {
            if (!operationFinished) closeBluetoothSocket(socket)
        }
    }
    try {
        val result = withContext(Dispatchers.IO) { block { lastProgress.set(SystemClock.elapsedRealtime()) } }
        if (timedOut) throw BluetoothTransferException(timeoutReason)
        result
    } catch (e: IOException) {
        if (timedOut) throw BluetoothTransferException(timeoutReason)
        throw e
    } finally {
        operationFinished = true
        abort.cancel()
    }
}

suspend fun closeBluetoothSocket(socket: Closeable?) {
    if (socket == null) return
    withContext(NonCancellable + Dispatchers.IO) {
        try {
            socket.close()
        } catch (e: IOException) {
            logdNoFile("Close Bluetooth socket failed: ${e.javaClass.simpleName}")
        }
    }
}

@StringRes
fun bluetoothStorageReason(error: Exception): Int {
    val outOfSpace = generateSequence<Throwable>(error) { it.cause }
        .filterIsInstance<ErrnoException>()
        .any { it.errno == OsConstants.ENOSPC || it.errno == OsConstants.EDQUOT }
    return if (outOfSpace) R.string.error_bluetooth_archive_no_space else R.string.error_bluetooth_archive_io
}
