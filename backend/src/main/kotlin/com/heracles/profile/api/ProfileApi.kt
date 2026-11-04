package com.heracles.profile.api

import java.util.UUID

/** Where a person stands for the automatic plan (spec RF10). Without an anamnesis nobody can have a plan: [UNKNOWN]. */
enum class EligibilityStatus { ELIGIBLE, NOT_ELIGIBLE_FOR_AUTOMATIC_PLAN, UNKNOWN }

/** The question `training` and `nutrition` ask. Reads the last revision; works inside or outside a user transaction. */
interface EligibilityQuery {
    fun statusOf(userId: UUID): EligibilityStatus
}
