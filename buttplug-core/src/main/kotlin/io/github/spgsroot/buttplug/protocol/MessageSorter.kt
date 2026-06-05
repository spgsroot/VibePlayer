package io.github.spgsroot.buttplug.protocol

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Matches server responses to pending client requests by message ID.
 * Uses CompletableDeferred for coroutine-friendly request/response pairing.
 */
class MessageSorter {
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<ButtplugServerMessage>>()

    /**
     * Register a pending request and return a Deferred that completes when the response arrives.
     */
    fun register(id: Int): CompletableDeferred<ButtplugServerMessage> {
        val deferred = CompletableDeferred<ButtplugServerMessage>()
        pending[id] = deferred
        return deferred
    }

    /**
     * Resolve a server message to its pending request.
     * @return true if the message was matched to a pending request, false if it's a system event (Id=0)
     * or an unmatched message.
     */
    fun resolve(message: ButtplugServerMessage): Boolean {
        val deferred = pending.remove(message.Id) ?: return false
        deferred.complete(message)
        return true
    }

    /**
     * Cancel all pending requests (used on disconnect).
     */
    fun cancelAll(cause: Throwable) {
        pending.values.forEach { it.completeExceptionally(cause) }
        pending.clear()
    }

    /**
     * Number of pending requests.
     */
    fun pendingCount(): Int = pending.size

    fun reset() {
        cancelAll(IllegalStateException("MessageSorter reset"))
    }
}
