package com.heracles.profile.application

import com.heracles.platform.api.ExportContributor
import com.heracles.profile.application.port.out.AnamnesisRepository
import com.heracles.profile.domain.AnamnesisRevision
import java.util.UUID

/** What the export file says about the anamnesis (spec RF9): every revision, with its items. The row-level security decides whose. */
class AnamnesisExport(private val anamneses: AnamnesisRepository) : ExportContributor {
    override val section = "anamnesis"

    override fun export(userId: UUID): Any =
        anamneses.summaries(userId).mapNotNull { anamneses.find(userId, it.revision) }.map { it.toExport() }

    private fun AnamnesisRevision.toExport() = mapOf(
        "revision" to revision,
        "createdAt" to createdAt.toString(),
        "objective" to input.objective,
        "trainingLevel" to input.trainingLevel,
        "birthDate" to input.birthDate.toString(),
        "biologicalSex" to input.biologicalSex?.name,
        "screening" to input.screening.entries.associate { it.key.name to it.value.name },
        "eligible" to eligible,
        "ineligibilityReasons" to ineligibilityReasons,
        "items" to input.items.map {
            mapOf("kind" to it.kind.name, "catalogCode" to it.catalogCode, "otherText" to it.otherText, "bodyRegion" to it.bodyRegion, "note" to it.note)
        },
    )
}
