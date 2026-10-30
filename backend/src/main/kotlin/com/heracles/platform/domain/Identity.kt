package com.heracles.platform.domain

/** Who the identity provider says is calling: only the subject (`sub`); roles live in the database (ADR-0008). */
data class Identity(val subject: String) {
    init {
        require(subject.isNotBlank()) { "subject must not be blank" }
    }
}

/** The credential was missing, malformed, expired, forged or meant for someone else. The reason is never exposed. */
class AuthenticationFailedException(cause: Throwable? = null) : RuntimeException("authentication failed", cause)
