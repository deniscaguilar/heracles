package com.heracles.platform.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class QueueMessageTest {
    private val id = UUID.randomUUID().toString()

    @Test
    fun `a message carries a type and identifiers`() {
        val message = QueueMessage(id, "exam.import", mapOf("examId" to UUID.randomUUID().toString(), "userId" to UUID.randomUUID().toString()))
        assertThat(message.type).isEqualTo("exam.import")
        assertThat(message.ids).containsKeys("examId", "userId")
    }

    @Test
    fun `values that are not identifiers are refused, since a queue message may not carry health or personal data`() {
        listOf("7,2 mg/dL", "joao@example.com", "123.456.789-09", "glicemia em jejum", "", "a".repeat(129)).forEach { value ->
            assertThatThrownBy { QueueMessage(id, "exam.import", mapOf("examId" to value)) }
                .describedAs(value).isInstanceOf(IllegalArgumentException::class.java).message().doesNotContain(value.take(5).ifBlank { "#" })
        }
    }

    @Test
    fun `keys and the type must be names`() {
        assertThatThrownBy { QueueMessage(id, "Exam Import", emptyMap()) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { QueueMessage(id, "exam.import", mapOf("exam id" to "1")) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { QueueMessage("not an id!", "exam.import", emptyMap()) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
