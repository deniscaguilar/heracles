package com.heracles.profile.adapter.out

import com.heracles.platform.api.ErasureStep
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/** Deletes the anamnesis and its revisions. Safe to run again: the second run finds nothing. */
@Component
class JdbcProfileErasure(private val jdbc: JdbcTemplate) : ErasureStep {
    override val order = 10

    override fun erase(userId: UUID) {
        jdbc.queryForObject("select profile.erase_user(?)", Int::class.javaObjectType, userId)
    }
}
