package com.heracles.platform.application

import com.heracles.platform.application.port.out.AuditTrail
import com.heracles.platform.domain.AccessScope
import com.heracles.platform.domain.AppUser
import com.heracles.platform.domain.AuditEvent
import com.heracles.platform.domain.AuditResult
import com.heracles.platform.domain.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

private class RecordingTrail : AuditTrail {
    val events = mutableListOf<AuditEvent>()
    override fun append(event: AuditEvent): UUID = UUID.randomUUID().also { events += event }
}

class AuthorizerTest {
    private val trail = RecordingTrail()
    private val authorizer = Authorizer(RecordAudit(trail))

    private fun user(role: UserRole) = AppUser(UUID.randomUUID(), "sub-${role.name}", role)

    @Test
    fun `a role in the allowed set passes and leaves no event`() {
        assertThat(authorizer.authorize(user(UserRole.ADMIN), setOf(UserRole.ADMIN), "admin.access")).isTrue()
        assertThat(trail.events).isEmpty()
    }

    @Test
    fun `a role outside the allowed set is denied and a DENIED event is recorded`() {
        val caller = user(UserRole.USER)
        assertThat(authorizer.authorize(caller, setOf(UserRole.ADMIN), "admin.access", "trace-7")).isFalse()

        assertThat(trail.events).hasSize(1)
        val event = trail.events.single()
        assertThat(event.result).isEqualTo(AuditResult.DENIED)
        assertThat(event.actorUserId).isEqualTo(caller.id)
        assertThat(event.actorScope).isEqualTo(AccessScope.USER)
        assertThat(event.action).isEqualTo("admin.access")
        assertThat(event.traceId).isEqualTo("trace-7")
        assertThat(event.fields).isEmpty()
    }

    @Test
    fun `a moderator does not reach an admin only route`() {
        assertThat(authorizer.authorize(user(UserRole.MODERATOR), setOf(UserRole.ADMIN), "admin.access")).isFalse()
        assertThat(authorizer.authorize(user(UserRole.MODERATOR), setOf(UserRole.ADMIN, UserRole.MODERATOR), "admin.access")).isTrue()
    }
}
