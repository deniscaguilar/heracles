package com.heracles.platform.application

import com.heracles.platform.domain.AccessScope
import com.heracles.platform.domain.AppUser
import com.heracles.platform.domain.AuditEvent
import com.heracles.platform.domain.AuditResult
import com.heracles.platform.domain.UserRole

/**
 * Decides by role (read from the database, never from the token) and leaves a DENIED event when it says no.
 * [action] is the audit action name, for example `admin.access`.
 */
class Authorizer(private val recordAudit: RecordAudit) {
    fun authorize(user: AppUser, allowed: Set<UserRole>, action: String, traceId: String? = null): Boolean {
        if (user.role in allowed) return true
        recordAudit(
            AuditEvent(
                actorUserId = user.id, actorScope = AccessScope.USER, action = action, entityType = "route",
                entityId = null, result = AuditResult.DENIED, fields = emptyList(), traceId = traceId,
            ),
        )
        return false
    }
}
