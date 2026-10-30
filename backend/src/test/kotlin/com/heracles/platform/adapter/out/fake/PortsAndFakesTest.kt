package com.heracles.platform.adapter.out.fake

import com.heracles.platform.adapter.out.SystemClock
import com.heracles.platform.domain.ChargeRequest
import com.heracles.platform.domain.ChargeStatus
import com.heracles.platform.domain.StorageKey
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class PortsAndFakesTest {
    @Test
    fun `the system clock tells the time in Sao Paulo, and a fixed clock stays put`() {
        assertThat(SystemClock().zone).isEqualTo(ZoneId.of("America/Sao_Paulo"))
        val fixed = FixedClock(Instant.parse("2026-10-30T15:00:00Z"))
        assertThat(fixed.now().hour).isEqualTo(12) // 15:00 UTC is 12:00 in Sao Paulo (UTC-3)
        assertThat(fixed.instant()).isEqualTo(Instant.parse("2026-10-30T15:00:00Z"))
    }

    @Test
    fun `a storage key is made of identifiers, never of names or free text`() {
        val user = UUID.randomUUID()
        assertThat(StorageKey("users/$user/exams/${UUID.randomUUID()}.pdf").value).startsWith("users/")
        listOf("../etc/passwd", "users/joao@example.com/exame.pdf", "exame de sangue.pdf", "glicemia 7,2", "", "/absolute", "a//b", "a".repeat(513))
            .forEach { bad ->
                assertThatThrownBy { StorageKey(bad) }.describedAs(bad).isInstanceOf(IllegalArgumentException::class.java)
                    .message().doesNotContain(bad.take(6).ifBlank { "#" })
            }
    }

    @Test
    fun `the fake language model and OCR answer from memory, with no network`() {
        assertThat(FakeLlmClient("fixed answer").complete("any prompt")).isEqualTo("fixed answer")
        assertThat(FakeOcrClient("text of the document").extractText(byteArrayOf(1, 2, 3), "application/pdf")).isEqualTo("text of the document")
    }

    @Test
    fun `the fake payment gateway charges in cents and remembers what it was asked`() {
        val gateway = FakePaymentGateway()
        val result = gateway.charge(ChargeRequest(amountCents = 4990, currency = "BRL", reference = "sub-1"))
        assertThat(result.status).isEqualTo(ChargeStatus.SUCCEEDED)
        assertThat(gateway.charges).extracting<Long> { it.amountCents }.containsExactly(4990L)
        assertThatThrownBy { ChargeRequest(amountCents = 0, currency = "BRL", reference = "x") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ChargeRequest(amountCents = 100, currency = "brl", reference = "x") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `the fake notification channel only records`() {
        val channel = RecordingNotificationChannel()
        val user = UUID.randomUUID()
        channel.send(user, "workout.reminder", mapOf("workoutId" to "w-1"))
        assertThat(channel.sent).containsExactly(RecordingNotificationChannel.Sent(user, "workout.reminder", mapOf("workoutId" to "w-1")))
    }
}
