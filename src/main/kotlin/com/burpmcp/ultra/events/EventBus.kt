package com.burpmcp.ultra.events

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.time.Instant

/**
 * Represents a single event emitted by a Burp Suite handler.
 *
 * @property id Monotonically increasing event identifier used for cursor-based polling.
 * @property type Dot-separated event category (e.g. "proxy.request", "scanner.issue").
 * @property timestamp ISO-8601 timestamp of when the event was created.
 * @property data Arbitrary JSON payload specific to the event type.
 */
@Serializable
data class BurpEvent(
    val id: Long,
    val type: String,
    val timestamp: String,
    val data: JsonObject
)

/**
 * Thread-safe event bus backed by a bounded ring buffer.
 *
 * Events are emitted by Burp handler callbacks and consumed by MCP clients
 * through cursor-based polling (getEvents / getEventsByType) or push-based
 * subscriptions (subscribe / unsubscribe).
 *
 * When the buffer reaches [maxBufferSize], the oldest events are evicted to
 * keep memory usage bounded.
 *
 * All public methods are safe to call from any thread.
 *
 * @param maxBufferSize Maximum number of events retained in the ring buffer.
 */
class EventBus(private val maxBufferSize: Int = 10000) {
    private val buffer = ConcurrentLinkedDeque<BurpEvent>()
    private val idCounter = AtomicLong(0)
    private val subscribers = CopyOnWriteArrayList<EventSubscriber>()

    /**
     * Emits a new event, appending it to the ring buffer and notifying
     * all matching subscribers.
     *
     * @param type Dot-separated event type string.
     * @param data JSON payload for the event.
     */
    fun emit(type: String, data: JsonObject) {
        val event = BurpEvent(
            id = idCounter.incrementAndGet(),
            type = type,
            timestamp = Instant.now().toString(),
            data = data
        )

        buffer.addLast(event)

        // Evict oldest events when the buffer exceeds capacity
        while (buffer.size > maxBufferSize) {
            buffer.pollFirst()
        }

        // Notify matching subscribers (type filter is empty == wildcard)
        subscribers.forEach { subscriber ->
            if (subscriber.types.isEmpty() || subscriber.types.contains(type)) {
                try {
                    subscriber.callback(event)
                } catch (_: Exception) {
                    // Swallow subscriber exceptions to avoid disrupting emitters
                }
            }
        }
    }

    /**
     * Returns events whose id is strictly greater than [sinceId], up to
     * [maxEvents] results. Used for cursor-based polling by MCP clients.
     */
    fun getEvents(sinceId: Long = 0, maxEvents: Int = 200): List<BurpEvent> {
        return buffer.filter { it.id > sinceId }.take(maxEvents)
    }

    /**
     * Returns events matching any of the given [types] whose id is strictly
     * greater than [sinceId], up to [maxEvents] results.
     */
    fun getEventsByType(
        types: List<String>,
        sinceId: Long = 0,
        maxEvents: Int = 200
    ): List<BurpEvent> {
        return buffer.filter { it.id > sinceId && it.type in types }.take(maxEvents)
    }

    /**
     * Registers a push-based subscriber. When [types] is empty the subscriber
     * receives all events; otherwise only events whose type is in the list.
     *
     * @return A subscription id that can be passed to [unsubscribe].
     */
    fun subscribe(types: List<String>, callback: (BurpEvent) -> Unit): String {
        val id = "sub-${idCounter.incrementAndGet()}"
        subscribers.add(EventSubscriber(id, types, callback))
        return id
    }

    /**
     * Removes a previously registered subscriber by its [subscriptionId].
     */
    fun unsubscribe(subscriptionId: String) {
        subscribers.removeIf { it.id == subscriptionId }
    }

    /**
     * Clears the entire buffer and removes all subscribers. Called during
     * extension unload.
     */
    fun clear() {
        buffer.clear()
        subscribers.clear()
    }

    /** Number of events currently in the buffer. */
    fun size(): Int = buffer.size

    /** The id of the most recently emitted event (0 if none). */
    fun lastId(): Long = idCounter.get()
}

/**
 * Internal holder for a push-based event subscription.
 *
 * @property id Unique subscription identifier.
 * @property types Event types this subscriber is interested in (empty = all).
 * @property callback Invoked on the emitter's thread for each matching event.
 */
data class EventSubscriber(
    val id: String,
    val types: List<String>,
    val callback: (BurpEvent) -> Unit
)
