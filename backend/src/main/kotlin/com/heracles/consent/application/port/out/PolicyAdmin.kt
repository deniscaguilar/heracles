package com.heracles.consent.application.port.out

import com.heracles.consent.api.ConsentType
import com.heracles.consent.domain.PolicyDocument
import com.heracles.consent.domain.PolicyVersionSummary
import java.util.UUID

/** The backoffice side of the policies. Every call runs in the admin scope; the database refuses any other. */
interface PolicyAdmin {
    /** @throws com.heracles.consent.domain.DraftAlreadyExistsException when the type already has a draft. */
    fun createDraft(type: ConsentType, title: String, markdown: String, actor: UUID): UUID
    fun updateDraft(id: UUID, title: String, markdown: String): Boolean
    fun discardDraft(id: UUID): Boolean

    /** The number given to the version, or null when there is no such draft. */
    fun publish(id: UUID, actor: UUID): Int?
    fun find(id: UUID): PolicyDocument?

    /** Every version of every type (drafts included), with how many people hold each published one. Newest first within a type. */
    fun list(): List<PolicyVersionSummary>
}
