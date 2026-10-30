package com.heracles.platform.application

import com.heracles.platform.application.port.out.AuditTrail
import com.heracles.platform.domain.AuditEvent
import java.util.UUID

/**
 * Records one audit event. [AuditEvent] already refuses values in place of field names, so a bad event
 * never gets to the trail. Joins the caller's transaction, so the action and its audit commit together.
 */
class RecordAudit(private val trail: AuditTrail) {
    operator fun invoke(event: AuditEvent): UUID = trail.append(event)
}
