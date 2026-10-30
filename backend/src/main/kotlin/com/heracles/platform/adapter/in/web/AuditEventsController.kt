package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.QueryAuditTrail
import com.heracles.platform.domain.AuditRecord
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class AuditEventResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val occurredAt: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) val actorUserId: String?,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val actorScope: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val action: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val entityType: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) val entityId: String?,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val result: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val fields: List<String>,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) val traceId: String?,
)

data class AuditEventsResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val items: List<AuditEventResponse>,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) val nextCursor: String?,
)

/** Admin-only (see AdminAuthorizationManager). Names and identifiers only, never values. */
@RestController
class AuditEventsController(private val queryAuditTrail: QueryAuditTrail) {

    @Operation(operationId = "listAuditEvents", summary = "Audit trail, newest first (ADMIN only)")
    @GetMapping("/api/v1/admin/audit-events", produces = ["application/json"])
    fun list(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): AuditEventsResponse {
        val page = queryAuditTrail(cursor, limit)
        return AuditEventsResponse(page.items.map { it.toResponse() }, page.nextCursor)
    }

    private fun AuditRecord.toResponse() = AuditEventResponse(
        id.toString(), occurredAt.toString(), actorUserId?.toString(), actorScope.databaseValue, action, entityType,
        entityId, result.name, fields, traceId,
    )
}
