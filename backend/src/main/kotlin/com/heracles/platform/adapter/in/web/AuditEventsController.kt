package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.QueryAuditTrail
import com.heracles.platform.domain.AuditRecord
import com.heracles.platform.domain.InvalidCursorException
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class AuditEventResponse(
    val id: String,
    val occurredAt: String,
    val actorUserId: String?,
    val actorScope: String,
    val action: String,
    val entityType: String,
    val entityId: String?,
    val result: String,
    val fields: List<String>,
    val traceId: String?,
)

data class AuditEventsResponse(val items: List<AuditEventResponse>, val nextCursor: String?)

/** Admin-only (see AdminAuthorizationManager). Names and identifiers only, never values. */
@RestController
class AuditEventsController(private val queryAuditTrail: QueryAuditTrail) {

    @GetMapping("/api/v1/admin/audit-events")
    fun list(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): AuditEventsResponse {
        val page = queryAuditTrail(cursor, limit)
        return AuditEventsResponse(page.items.map { it.toResponse() }, page.nextCursor)
    }

    // The global error handler (E1-S5.1) will take this over; the body shape is already the standard one.
    @ExceptionHandler(InvalidCursorException::class)
    fun invalidCursor(): ResponseEntity<String> =
        ResponseEntity.badRequest().contentType(MediaType.parseMediaType("application/problem+json"))
            .body("""{"type":"about:blank","title":"Bad Request","status":400,"code":"invalid_cursor"}""")

    private fun AuditRecord.toResponse() = AuditEventResponse(
        id.toString(), occurredAt.toString(), actorUserId?.toString(), actorScope.databaseValue, action, entityType,
        entityId, result.name, fields, traceId,
    )
}
