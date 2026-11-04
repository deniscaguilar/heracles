package com.heracles.profile.adapter.out

import com.heracles.platform.api.Transactions
import com.heracles.profile.api.EligibilityQuery
import com.heracles.profile.api.EligibilityStatus
import com.heracles.profile.application.GetEligibility
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

/** Inside an open transaction (which already carries the person's scope) it just reads; outside, it opens one. */
@Component
class DatabaseEligibilityQuery(private val getEligibility: GetEligibility, private val transactions: Transactions) : EligibilityQuery {
    override fun statusOf(userId: UUID): EligibilityStatus =
        if (TransactionSynchronizationManager.isActualTransactionActive()) getEligibility(userId)
        else transactions.asUser(userId) { getEligibility(userId) }
}
