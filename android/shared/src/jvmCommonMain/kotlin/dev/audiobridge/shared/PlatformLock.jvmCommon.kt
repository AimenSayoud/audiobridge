package dev.audiobridge.shared

import java.util.concurrent.locks.ReentrantLock

actual class PlatformLock actual constructor() {
    private val lock = ReentrantLock()
    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
