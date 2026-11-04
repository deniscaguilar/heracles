package com.heracles.profile.application

import com.heracles.platform.api.AuditLog
import com.heracles.platform.api.ClockProvider
import com.heracles.platform.api.Outcome
import com.heracles.platform.api.Scope
import com.heracles.platform.api.Transactions
import com.heracles.profile.application.port.out.AnamnesisRepository
import com.heracles.profile.application.port.out.CatalogRepository
import com.heracles.profile.application.port.out.RevisionSummary
import com.heracles.profile.domain.AnamnesisInput
import com.heracles.profile.domain.AnamnesisNotFoundException
import com.heracles.profile.domain.AnamnesisRevision
import com.heracles.profile.domain.AnamnesisValidator
import com.heracles.profile.domain.CatalogKind
import com.heracles.profile.domain.TriageRules
import com.heracles.profile.api.EligibilityStatus
import java.time.LocalDate
import java.util.UUID

/** The values a person may pick for a list of the anamnesis. */
class GetCatalog(private val catalogs: CatalogRepository) {
    operator fun invoke(kind: CatalogKind): List<String> = catalogs.codes(kind)
}

/** Validates and appends a new revision; the previous ones stay as they were (spec RF8, CA11). */
class SaveAnamnesis(
    private val transactions: Transactions,
    private val catalogs: CatalogRepository,
    private val anamneses: AnamnesisRepository,
    private val audit: AuditLog,
    private val clock: ClockProvider,
    private val rules: TriageRules,
) {
    operator fun invoke(userId: UUID, input: AnamnesisInput): AnamnesisRevision = transactions.asUser(userId) {
        val now = clock.now()
        val valid = AnamnesisValidator(catalogs.catalog()).validate(input, LocalDate.ofInstant(now, clock.zone))
        val today = LocalDate.ofInstant(now, clock.zone)
        val eligibility = rules.evaluate(valid.birthDate, valid.screening, today)
        val wasEligible = anamneses.latest(userId)?.eligible ?: true
        val saved = anamneses.append(userId, valid, eligibility, now)
        audit.record(
            actorUserId = userId, actorScope = Scope.USER, action = "anamnesis.saved", entityType = "anamnesis",
            entityId = saved.id.toString(), outcome = Outcome.SUCCESS, fields = listOf("revision"),
        )
        if (wasEligible != eligibility.eligible) {
            audit.record(
                actorUserId = userId, actorScope = Scope.USER, action = "eligibility.changed", entityType = "anamnesis",
                entityId = saved.id.toString(), outcome = Outcome.SUCCESS, fields = listOf("eligible", "rulesVersion"),
            )
        }
        saved
    }
}

/** What other modules ask (through `profile.api`): may this person have an automatic plan? The adapter opens the transaction. */
class GetEligibility(private val anamneses: AnamnesisRepository) {
    operator fun invoke(userId: UUID): EligibilityStatus {
        val latest = anamneses.latest(userId) ?: return EligibilityStatus.UNKNOWN
        return if (latest.eligible) EligibilityStatus.ELIGIBLE else EligibilityStatus.NOT_ELIGIBLE_FOR_AUTOMATIC_PLAN
    }
}

class GetMyAnamnesis(private val transactions: Transactions, private val anamneses: AnamnesisRepository) {
    operator fun invoke(userId: UUID): AnamnesisRevision =
        transactions.asUser(userId) { anamneses.latest(userId) } ?: throw AnamnesisNotFoundException()
}

class ListRevisions(private val transactions: Transactions, private val anamneses: AnamnesisRepository) {
    operator fun invoke(userId: UUID): List<RevisionSummary> = transactions.asUser(userId) { anamneses.summaries(userId) }
}

class GetRevision(private val transactions: Transactions, private val anamneses: AnamnesisRepository) {
    operator fun invoke(userId: UUID, revision: Int): AnamnesisRevision =
        transactions.asUser(userId) { anamneses.find(userId, revision) } ?: throw AnamnesisNotFoundException()
}
