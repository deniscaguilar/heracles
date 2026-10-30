package com.heracles.platform.domain

import java.time.Duration
import java.util.UUID

/** A message waiting in the outbox to be sent to the queue. [attempts] counts the failed tries so far. */
data class OutboxEntry(val id: UUID, val type: String, val payload: Map<String, String>, val attempts: Int)

/** Wait before the next try: 5 s, doubling with each failed attempt, up to 15 minutes. */
object Backoff {
    private val BASE: Duration = Duration.ofSeconds(5)
    val MAX: Duration = Duration.ofMinutes(15)

    fun delay(attempts: Int): Duration {
        val doublings = (attempts - 1).coerceIn(0, 10) // 5 s * 2^10 already passes the ceiling
        return BASE.multipliedBy(1L shl doublings).coerceAtMost(MAX)
    }
}
