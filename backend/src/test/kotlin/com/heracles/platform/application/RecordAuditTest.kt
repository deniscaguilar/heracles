package com.heracles.platform.application

import com.heracles.platform.application.port.out.AuditTrail
import com.heracles.platform.domain.AccessScope
import com.heracles.platform.domain.AuditEvent
import com.heracles.platform.domain.AuditResult
import com.heracles.platform.domain.InvalidAuditEventException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

private class FakeAuditTrail : AuditTrail {
    val events = mutableListOf<AuditEvent>()
    override fun append(event: AuditEvent): UUID = UUID.randomUUID().also { events += event }
}

class RecordAuditTest {
    private val trail = FakeAuditTrail()
    private val recordAudit = RecordAudit(trail)

    private fun event(fields: List<String> = listOf("name", "reference_range.min"), action: String = "exam.view") = AuditEvent(
        actorUserId = UUID.randomUUID(), actorScope = AccessScope.USER, action = action, entityType = "exam",
        entityId = "e-1", result = AuditResult.SUCCESS, fields = fields, traceId = "trace-1",
    )

    @Test
    fun `an event with field names is appended to the trail`() {
        val event = event()
        recordAudit(event)
        assertThat(trail.events).containsExactly(event)
    }

    @Test
    fun `an event with no fields is accepted`() {
        recordAudit(event(fields = emptyList()))
        assertThat(trail.events).hasSize(1)
    }

    @Test
    fun `a value in place of a field name is refused and nothing is written`() {
        listOf("joao@example.com", "7,2 mg/dL", "12345678901", "name=Joao", "", " name", "a".repeat(65)).forEach { value ->
            assertThatThrownBy { recordAudit(event(fields = listOf("name", value))) }
                .describedAs(value).isInstanceOf(InvalidAuditEventException::class.java)
        }
        assertThat(trail.events).isEmpty()
    }

    @Test
    fun `the message of a refusal never repeats the offending value`() {
        assertThatThrownBy { event(fields = listOf("joao@example.com")) }
            .isInstanceOf(InvalidAuditEventException::class.java)
            .message().doesNotContain("joao")
    }

    @Test
    fun `an action must be a dotted lowercase name`() {
        assertThatThrownBy { event(action = "Viewed Exam 7,2") }.isInstanceOf(InvalidAuditEventException::class.java)
        assertThat(event(action = "exam.view").action).isEqualTo("exam.view")
    }
}
