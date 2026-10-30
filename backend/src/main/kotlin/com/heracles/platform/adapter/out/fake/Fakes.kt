package com.heracles.platform.adapter.out.fake

import com.heracles.platform.application.port.out.Clock
import com.heracles.platform.application.port.out.LlmClient
import com.heracles.platform.application.port.out.NotificationChannel
import com.heracles.platform.application.port.out.OcrClient
import com.heracles.platform.application.port.out.PaymentGateway
import com.heracles.platform.domain.ChargeRequest
import com.heracles.platform.domain.ChargeResult
import com.heracles.platform.domain.ChargeStatus
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

// Stand-ins for ports with no real adapter yet. They answer from memory and never open a connection.
// They are wired only when `heracles.adapters.fake.enabled=true` (LocalAdaptersConfig).

class FixedClock(private val at: Instant) : Clock {
    override fun instant(): Instant = at
}

class FakeLlmClient(private val answer: String = "[fake language model answer]") : LlmClient {
    override fun complete(prompt: String): String = answer
}

class FakeOcrClient(private val text: String = "[fake OCR text]") : OcrClient {
    override fun extractText(document: ByteArray, contentType: String): String = text
}

class FakePaymentGateway : PaymentGateway {
    val charges = CopyOnWriteArrayList<ChargeRequest>()

    override fun charge(request: ChargeRequest): ChargeResult {
        charges += request
        return ChargeResult("fake-${UUID.randomUUID()}", ChargeStatus.SUCCEEDED)
    }
}

class RecordingNotificationChannel : NotificationChannel {
    data class Sent(val userId: UUID, val template: String, val params: Map<String, String>)

    val sent = CopyOnWriteArrayList<Sent>()

    override fun send(userId: UUID, template: String, params: Map<String, String>) {
        sent += Sent(userId, template, params)
    }
}
