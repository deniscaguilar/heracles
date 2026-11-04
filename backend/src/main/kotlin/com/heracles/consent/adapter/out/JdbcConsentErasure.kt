package com.heracles.consent.adapter.out

import com.heracles.platform.api.ErasureStep
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The proof of acceptance stays, without a link to the person: the identifier becomes a random UUID that is kept nowhere.
 * Safe to run again: nothing is left under the old identifier, so the second run changes no row.
 */
@Component
class JdbcConsentErasure(private val jdbc: JdbcTemplate) : ErasureStep {
    override val order = 20

    override fun erase(userId: UUID) {
        jdbc.queryForObject("select consent.pseudonymize_user(?, ?)", Int::class.javaObjectType, userId, UUID.randomUUID())
    }
}
