package com.heracles.consent.application

import com.heracles.consent.api.ConsentType
import com.heracles.consent.application.port.out.PolicyAdmin
import com.heracles.consent.domain.Block
import com.heracles.consent.domain.DraftNotFoundException
import com.heracles.consent.domain.PolicyBlocks
import com.heracles.consent.domain.PolicyDocument
import com.heracles.consent.domain.PolicyVersionSummary
import com.heracles.platform.api.AuditLog
import com.heracles.platform.api.Outcome
import com.heracles.platform.api.Scope
import com.heracles.platform.api.Transactions
import java.util.UUID

/** A draft or a published text, with the blocks a client would draw (so the administrator sees what people will see). */
data class PolicyPreview(val document: PolicyDocument, val blocks: List<Block>)

private fun audit(audit: AuditLog, actor: UUID, action: String, entityId: UUID, vararg fields: String) = audit.record(
    actorUserId = actor, actorScope = Scope.ADMIN, action = action, entityType = "policy_version",
    entityId = entityId.toString(), outcome = Outcome.SUCCESS, fields = fields.toList(),
)

/** Writing refuses text outside the restricted format ([com.heracles.consent.domain.InvalidPolicyTextException]). */
class CreateDraft(private val transactions: Transactions, private val admin: PolicyAdmin, private val audit: AuditLog) {
    operator fun invoke(actor: UUID, type: ConsentType, title: String, markdown: String): UUID {
        PolicyBlocks.parse(markdown)
        return transactions.asAdmin {
            admin.createDraft(type, title, markdown, actor).also { audit(audit, actor, "policy.draft_created", it, "type", "title", "markdown") }
        }
    }
}

class UpdateDraft(private val transactions: Transactions, private val admin: PolicyAdmin, private val audit: AuditLog) {
    operator fun invoke(actor: UUID, id: UUID, title: String, markdown: String) {
        PolicyBlocks.parse(markdown)
        transactions.asAdmin {
            if (!admin.updateDraft(id, title, markdown)) throw DraftNotFoundException()
            audit(audit, actor, "policy.draft_updated", id, "title", "markdown")
        }
    }
}

class DiscardDraft(private val transactions: Transactions, private val admin: PolicyAdmin, private val audit: AuditLog) {
    operator fun invoke(actor: UUID, id: UUID) = transactions.asAdmin {
        if (!admin.discardDraft(id)) throw DraftNotFoundException()
        audit(audit, actor, "policy.draft_discarded", id)
    }
}

/** Returns the number given to the new current version. */
class PublishVersion(private val transactions: Transactions, private val admin: PolicyAdmin, private val audit: AuditLog) {
    operator fun invoke(actor: UUID, id: UUID): Int = transactions.asAdmin {
        val number = admin.publish(id, actor) ?: throw DraftNotFoundException()
        audit(audit, actor, "policy.published", id, "type", "version")
        number
    }
}

class PreviewVersion(private val transactions: Transactions, private val admin: PolicyAdmin) {
    operator fun invoke(id: UUID): PolicyPreview = transactions.asAdmin {
        val document = admin.find(id) ?: throw DraftNotFoundException()
        PolicyPreview(document, PolicyBlocks.parse(document.markdown))
    }
}

class ListPolicies(private val transactions: Transactions, private val admin: PolicyAdmin) {
    operator fun invoke(): List<PolicyVersionSummary> = transactions.asAdmin { admin.list() }
}
