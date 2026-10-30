package com.heracles.platform.application.port.out

import com.heracles.platform.domain.ChargeRequest
import com.heracles.platform.domain.ChargeResult
import com.heracles.platform.domain.StorageKey
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** The time, always with a zone: Sao Paulo (CLAUDE.md, section 9). Real: the system clock. Fake: a fixed instant. */
interface Clock {
    fun instant(): Instant
    val zone: ZoneId get() = ZoneId.of("America/Sao_Paulo")
    fun now(): ZonedDateTime = instant().atZone(zone)
}

/** Files (exam documents and the like). Real: S3 with presigned URLs. Local: LocalStack. */
interface FileStorage {
    fun put(key: StorageKey, content: ByteArray, contentType: String)

    /** Null when there is no such file. */
    fun get(key: StorageKey): ByteArray?

    fun delete(key: StorageKey)

    /** A time-limited link the holder can download from without credentials. */
    fun downloadUrl(key: StorageKey, validFor: Duration): String
}

/** A language model. Only through the AI gateway, with consent and anonymization (ADR-0007); no real adapter in this stage. */
interface LlmClient {
    fun complete(prompt: String): String
}

/** Text out of a document image or PDF. No real adapter in this stage. */
interface OcrClient {
    fun extractText(document: ByteArray, contentType: String): String
}

/** Charges money. No real adapter in this stage. */
interface PaymentGateway {
    fun charge(request: ChargeRequest): ChargeResult
}

/** Push, e-mail and the like. Carries identifiers and template names, never health data. No real adapter in this stage. */
interface NotificationChannel {
    fun send(userId: UUID, template: String, params: Map<String, String>)
}
