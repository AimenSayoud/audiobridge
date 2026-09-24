package dev.audiobridge.shared

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine

/**
 * javax.sound sink, used by :desktopSink. Its job is to let the shared protocol
 * and jitter-buffer code be exercised on a laptop, with no phone and no adb.
 *
 * SourceDataLine has no speed control, so [setSpeed] declines and the jitter
 * buffer falls back to its hard ceiling. That is the intended fallback path,
 * and running the desktop sink is how it gets tested.
 */
private class SourceDataLineSink(
    private val sampleRate: Int,
    private val channels: Int,
    targetMs: Int,
) : AudioSink {

    private val format = AudioFormat(sampleRate.toFloat(), 16, channels, true, false)
    private val deviceBuffer = (sampleRate * channels * 2 * targetMs / 1000).coerceAtLeast(4096)
    private var line: SourceDataLine? = null

    override val bytesPerWrite: Int = sampleRate * channels * 2 * 10 / 1000

    override fun start() {
        val info = DataLine.Info(SourceDataLine::class.java, format)
        check(AudioSystem.isLineSupported(info)) { "no output line for $format" }
        val opened = AudioSystem.getLine(info) as SourceDataLine
        opened.open(format, deviceBuffer)
        opened.start()
        line = opened
    }

    override fun write(data: ByteArray, offset: Int, length: Int): Int =
        line?.write(data, offset, length) ?: -1

    override fun setSpeed(speed: Float): Boolean = false

    override fun setVolume(volume: Float) {
        runCatching {
            val control = line?.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl ?: return
            // Linear 0..1 onto the line's dB range, with a floor so 0 is silent.
            val db = if (volume <= 0.0001f) control.minimum
                     else (20.0 * kotlin.math.log10(volume.toDouble())).toFloat()
            control.value = db.coerceIn(control.minimum, control.maximum)
        }
    }

    override fun stop() {
        runCatching { line?.drain(); line?.stop() }
    }

    override fun release() {
        runCatching { line?.close() }
        line = null
    }
}

actual fun createAudioSink(
    sampleRate: Int,
    channels: Int,
    targetMs: Int,
    preferLowLatency: Boolean,
): AudioSink = SourceDataLineSink(sampleRate, channels, targetMs)
