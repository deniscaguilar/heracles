package com.heracles.consent.application

import com.heracles.consent.api.ConsentType
import com.heracles.consent.application.port.out.ConsentEvents
import com.heracles.consent.application.port.out.PolicyRepository
import com.heracles.consent.domain.Client
import com.heracles.consent.domain.ConsentAction
import com.heracles.consent.domain.ConsentRecord
import com.heracles.consent.domain.PolicyVersion
import com.heracles.platform.api.AuditLog
import com.heracles.platform.api.EventPublisher
import com.heracles.platform.api.Outcome
import com.heracles.platform.api.Scope
import com.heracles.platform.api.Transactions
import java.util.UUID

/** Fakes of the ports, so the use cases run with no Spring and no database. */
class FakeTransactions : Transactions {
    var depth = 0
    override fun <T> asUser(userId: UUID, block: () -> T): T { depth++; try { return block() } finally { depth-- } }
    override fun <T> asAdmin(block: () -> T): T = block()
    override fun <T> asSystem(block: () -> T): T = block()
}

class FakePolicies : PolicyRepository {
    val versions = mutableMapOf<ConsentType, PolicyVersion>()
    fun publish(type: ConsentType): PolicyVersion =
        PolicyVersion(UUID.randomUUID(), type, (versions[type]?.version ?: 0) + 1, "T", "text").also { versions[type] = it }
    override fun currentVersions(): Map<ConsentType, PolicyVersion> = versions.toMap()
}

data class StoredEvent(val id: UUID, val userId: UUID, val record: ConsentRecord, val client: Client)

class FakeConsentEvents : ConsentEvents {
    val events = mutableListOf<StoredEvent>()
    override fun lastRecords(userId: UUID): Map<ConsentType, ConsentRecord> =
        events.filter { it.userId == userId }.groupBy { it.record.type }.mapValues { it.value.last().record }
    override fun history(userId: UUID) = events.filter { it.userId == userId }.map {
        com.heracles.consent.application.port.out.ConsentHistoryEntry(it.record.type, it.record.action, it.record.policyVersionId, it.client, java.time.Instant.EPOCH)
    }
    override fun append(userId: UUID, type: ConsentType, action: ConsentAction, policyVersionId: UUID, client: Client): UUID {
        val id = UUID.randomUUID()
        events += StoredEvent(id, userId, ConsentRecord(type, action, policyVersionId), client)
        return id
    }
}

data class AuditCall(val action: String, val entityType: String, val entityId: String?, val fields: List<String>, val outcome: Outcome)

class RecordingAuditLog : AuditLog {
    val calls = mutableListOf<AuditCall>()
    override fun record(actorUserId: UUID?, actorScope: Scope, action: String, entityType: String, entityId: String?, outcome: Outcome, fields: List<String>) {
        calls += AuditCall(action, entityType, entityId, fields, outcome)
    }
}

data class Published(val aggregateType: String, val aggregateId: String, val type: String, val ids: Map<String, String>)

class RecordingPublisher : EventPublisher {
    val published = mutableListOf<Published>()
    override fun publish(aggregateType: String, aggregateId: String, type: String, ids: Map<String, String>) {
        published += Published(aggregateType, aggregateId, type, ids)
    }
}
