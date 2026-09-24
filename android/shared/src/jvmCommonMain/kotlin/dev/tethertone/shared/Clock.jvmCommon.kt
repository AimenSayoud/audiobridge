package dev.tethertone.shared

actual fun monotonicMillis(): Long = System.nanoTime() / 1_000_000
