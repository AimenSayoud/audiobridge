package dev.audiobridge.shared

actual fun createAudioSink(
    sampleRate: Int,
    channels: Int,
    targetMs: Int,
    preferLowLatency: Boolean,
): AudioSink = AudioTrackSink(sampleRate, channels, targetMs, preferLowLatency)
