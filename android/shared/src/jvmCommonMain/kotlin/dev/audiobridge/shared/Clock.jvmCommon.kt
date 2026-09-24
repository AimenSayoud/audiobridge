package dev.audiobridge.shared

actual fun monotonicMillis(): Long = System.nanoTime() / 1_000_000
