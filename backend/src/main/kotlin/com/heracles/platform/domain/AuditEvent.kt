package com.heracles.platform.domain

import java.util.UUID

enum class AuditResult { SUCCESS, DENIED, FAILURE }

/** An audit event is made of names and identifiers only; health values never get here (CLAUDE.md, section 4). */
class InvalidAuditEventException(reason: String) : IllegalArgumentException(reason)

data class AuditEvent(
    val actorUserId: UUID?,
    val actorScope: AccessScope,
    val action: String,
    val entityType: String,
    val entityId: String?,
    val result: AuditResult,
    /** Names of the fields involved (for example `name`), never their values. */
    val fields: List<String>,
    val traceId: String?,
) {
    init {
        if (!ACTION.matches(action)) throw InvalidAuditEventException("action must be a dotted lowercase name")
        if (!ENTITY_TYPE.matches(entityType)) throw InvalidAuditEventException("entityType must be a lowercase name")
        // The offending text is never repeated in the message, since it may be personal or health data.
        val bad = fields.indexOfFirst { !FIELD_NAME.matches(it) }
        if (bad >= 0) throw InvalidAuditEventException("fields[$bad] is not a field name")
    }

    private companion object {
        val ACTION = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)*$")
        val ENTITY_TYPE = Regex("^[a-z][a-z0-9_]{0,63}$")
        // Same rule as platform.is_field_names in the database.
        val FIELD_NAME = Regex("^[A-Za-z][A-Za-z0-9_.]{0,63}$")
    }
}
