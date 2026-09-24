package dev.audiobridge.shared

/** A connected, bidirectional byte stream. Implementations are blocking under the hood. */
interface ByteStream {
    suspend fun readFully(dest: ByteArray, offset: Int, length: Int)

    /** Reads up to and including the next `\n`, returning the line without it. */
    suspend fun readLine(limit: Int = 8192): String

    suspend fun write(src: ByteArray, offset: Int = 0, length: Int = src.size)

    fun close()
}

expect suspend fun openStream(host: String, port: Int, connectTimeoutMs: Int): ByteStream
