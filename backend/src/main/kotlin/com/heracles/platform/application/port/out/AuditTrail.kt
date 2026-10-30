package com.heracles.platform.application.port.out

import com.heracles.platform.domain.AuditEvent
import java.util.UUID

/** Append-only: there is no way to read, change or remove an event through this port. */
interface AuditTrail {
    fun append(event: AuditEvent): UUID
}
