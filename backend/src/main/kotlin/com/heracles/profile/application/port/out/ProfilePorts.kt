package com.heracles.profile.application.port.out

import com.heracles.profile.domain.AnamnesisInput
import com.heracles.profile.domain.AnamnesisRevision
import com.heracles.profile.domain.Catalog
import com.heracles.profile.domain.CatalogKind
import com.heracles.profile.domain.Eligibility
import java.time.Instant
import java.util.UUID

interface CatalogRepository {
    fun catalog(): Catalog

    /** The codes of one catalog, in display order. */
    fun codes(kind: CatalogKind): List<String>
}

data class RevisionSummary(val revision: Int, val createdAt: Instant, val eligible: Boolean)

interface AnamnesisRepository {
    /** Appends the next revision of this person (the row-level security of the open transaction decides whose). */
    fun append(userId: UUID, input: AnamnesisInput, eligibility: Eligibility, createdAt: Instant): AnamnesisRevision

    fun latest(userId: UUID): AnamnesisRevision?
    fun summaries(userId: UUID): List<RevisionSummary>
    fun find(userId: UUID, revision: Int): AnamnesisRevision?
}
