package com.heracles.platform.domain

/**
 * Where a file lives in storage: ids and a type, like `users/<uuid>/exams/<uuid>.pdf`. Never a person's name, a
 * document title or anything that says what the file is about. The refusal never repeats the offending text.
 */
data class StorageKey(val value: String) {
    init {
        require(value.length <= 512 && PATTERN.matches(value)) { "storage key must be path segments of identifiers" }
    }

    private companion object {
        val PATTERN = Regex("^[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*(\\.[A-Za-z0-9]{1,8})?$")
    }
}

/** Money is whole cents (CLAUDE.md, section 9), the currency is an ISO 4217 code. */
data class ChargeRequest(val amountCents: Long, val currency: String = "BRL", val reference: String) {
    init {
        require(amountCents > 0) { "amount must be positive" }
        require(CURRENCY.matches(currency)) { "currency must be an ISO 4217 code" }
        require(reference.isNotBlank()) { "reference is required" }
    }

    private companion object {
        val CURRENCY = Regex("^[A-Z]{3}$")
    }
}

enum class ChargeStatus { SUCCEEDED, DECLINED }

data class ChargeResult(val paymentId: String, val status: ChargeStatus)
