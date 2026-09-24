package dev.tethertone.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * A silent server must eventually count as a dead one.
 *
 * The server sends a PING every second, so fifteen seconds of nothing means the
 * connection is gone — but a half-open TCP socket (Wi-Fi dropped, Mac slept,
 * cable pulled) can leave a blocking read waiting forever with no error. Left
 * like that the client sits there looking connected and playing silence,
 * because the reconnect logic never gets told anything went wrong.
 */
private const val READ_TIMEOUT_MS = 15_000

private class SocketStream(private val socket: Socket) : ByteStream {
    private val input: InputStream = BufferedInputStream(socket.getInputStream(), 1 shl 16)
    private val output: OutputStream = BufferedOutputStream(socket.getOutputStream(), 1 shl 14)

    override suspend fun readFully(dest: ByteArray, offset: Int, length: Int) = withContext(Dispatchers.IO) {
        var read = 0
        while (read < length) {
            val n = try {
                input.read(dest, offset + read, length - read)
            } catch (e: SocketTimeoutException) {
                throw IOException("no data from the server for ${READ_TIMEOUT_MS / 1000}s")
            }
            if (n < 0) throw EOFException("stream closed after $read/$length bytes")
            read += n
        }
    }

    override suspend fun readLine(limit: Int): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val bytes = ArrayList<Byte>(64)
        while (true) {
            val b = try {
                input.read()
            } catch (e: SocketTimeoutException) {
                throw IOException("the server accepted the connection but never replied")
            }
            if (b < 0) throw EOFException("stream closed while reading a line")
            if (b == '\n'.code) break
            bytes += b.toByte()
            if (bytes.size > limit) throw IllegalStateException("line exceeded $limit bytes")
        }
        sb.append(bytes.toByteArray().decodeToString())
        sb.toString()
    }

    override suspend fun write(src: ByteArray, offset: Int, length: Int) = withContext(Dispatchers.IO) {
        output.write(src, offset, length)
        output.flush()
    }

    override fun close() {
        runCatching { socket.close() }
    }
}

actual suspend fun openStream(host: String, port: Int, connectTimeoutMs: Int): ByteStream =
    withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            socket.tcpNoDelay = true          // audio packets are small and must not wait for Nagle
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = READ_TIMEOUT_MS
            SocketStream(socket)
        } catch (e: Throwable) {
            runCatching { socket.close() }
            throw e
        }
    }
