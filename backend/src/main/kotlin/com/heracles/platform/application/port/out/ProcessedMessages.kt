package com.heracles.platform.application.port.out

/** Idempotency marker of the consumers. Joins the caller's transaction, so the marker and the effect commit together. */
interface ProcessedMessages {
    /** True the first time this consumer sees this message id; false for a repeat (skip the effect). Callable from any scope. */
    fun markProcessed(consumer: String, messageId: String): Boolean
}
