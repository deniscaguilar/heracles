package com.heracles.consent.adapter.out

import com.heracles.consent.api.ConsentChecker
import com.heracles.consent.api.ConsentType
import com.heracles.consent.application.CheckConsent
import com.heracles.platform.api.Transactions
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

/**
 * Answers for other modules. Inside an open transaction (which already carries the person's scope) it just reads;
 * outside, it opens one for that person. Any failure is "no" (fail closed), logged without the person's data.
 */
@Component
class DatabaseConsentChecker(private val check: CheckConsent, private val transactions: Transactions) : ConsentChecker {
    private val log = LoggerFactory.getLogger(DatabaseConsentChecker::class.java)

    override fun hasConsented(userId: UUID, type: ConsentType): Boolean = try {
        if (TransactionSynchronizationManager.isActualTransactionActive()) check(userId, type)
        else transactions.asUser(userId) { check(userId, type) }
    } catch (e: Exception) {
        log.warn("consent check failed; answering no ({})", e.javaClass.simpleName)
        false
    }
}
