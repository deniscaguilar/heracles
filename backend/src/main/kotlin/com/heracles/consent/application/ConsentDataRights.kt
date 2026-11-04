package com.heracles.consent.application

import com.heracles.consent.application.port.out.ConsentEvents
import com.heracles.platform.api.ExportContributor
import java.util.UUID

/** What the export file says about consent (spec RF9): the person's own history, version by version. */
class ConsentExport(private val events: ConsentEvents) : ExportContributor {
    override val section = "consents"

    override fun export(userId: UUID): Any = events.history(userId).map {
        mapOf(
            "type" to it.type.name, "action" to it.action.name, "policyVersionId" to it.policyVersionId.toString(),
            "client" to it.client.name, "occurredAt" to it.occurredAt.toString(),
        )
    }
}
