package io.github.spgsroot.buttplug.protocol

import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe message ID generator.
 * IDs start from 1 (0 reserved for server events).
 */
class MessageIdGenerator {
    private val counter = AtomicInteger(1)

    fun nextId(): Int = counter.getAndIncrement()
    fun reset() { counter.set(1) }
    fun current(): Int = counter.get()
}
