package dev.tethertone.shared

/**
 * Somewhere to put PCM. [write] is expected to block until the device has room,
 * because that blocking *is* the playback clock the whole pipeline paces off.
 */
interface AudioSink {
    /** Bytes the playback loop should hand over per call. */
    val bytesPerWrite: Int

    fun start()

    /**
     * Called once from the playback loop's own thread, before the first write.
     * Audio output is a deadline task competing with ordinary work; on Android
     * a default-priority thread loses often enough to be heard as crackle.
     */
    fun onPlaybackThread() = Unit

    /** 0.0 silent to 1.0 unattenuated. Ignored by sinks without a gain control. */
    fun setVolume(volume: Float) = Unit

    /** Blocking. Returns bytes accepted, or a negative value on error. */
    fun write(data: ByteArray, offset: Int, length: Int): Int

    /**
     * Ask for a playback rate slightly off 1.0 to absorb clock drift.
     * Returns false if the platform will not do it, in which case the caller
     * falls back to letting the jitter buffer's hard ceiling shed packets.
     */
    fun setSpeed(speed: Float): Boolean

    fun stop()
    fun release()
}

expect fun createAudioSink(
    sampleRate: Int,
    channels: Int,
    targetMs: Int,
    preferLowLatency: Boolean,
): AudioSink
