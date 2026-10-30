package com.heracles.platform.domain

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** A stored audit event, as the backoffice reads it: names and identifiers only. */
data class AuditRecord(
    val id: UUID,
    val occurredAt: Instant,
    val actorUserId: UUID?,
    val actorScope: AccessScope,
    val action: String,
    val entityType: String,
    val entityId: String?,
    val result: AuditResult,
    val fields: List<String>,
    val traceId: String?,
)

class InvalidCursorException : IllegalArgumentException("invalid cursor")

/** Where the next page starts: the last event seen, in the (time, id) order. Opaque to the caller. */
data class AuditCursor(val occurredAt: Instant, val id: UUID) {
    fun encode(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("$occurredAt|$id".toByteArray(StandardCharsets.UTF_8))

    companion object {
        fun decode(value: String): AuditCursor =
            try {
                val (time, id) = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split('|')
                AuditCursor(Instant.parse(time), UUID.fromString(id))
            } catch (e: Exception) {
                throw InvalidCursorException()
            }
    }
}
