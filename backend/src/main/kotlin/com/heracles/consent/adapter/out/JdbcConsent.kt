package com.heracles.consent.adapter.out

import com.heracles.consent.api.ConsentType
import com.heracles.consent.application.port.out.ConsentEvents
import com.heracles.consent.application.port.out.ConsentHistoryEntry
import com.heracles.consent.application.port.out.PolicyRepository
import com.heracles.consent.domain.Client
import com.heracles.consent.domain.ConsentAction
import com.heracles.consent.domain.ConsentRecord
import com.heracles.consent.domain.PolicyVersion
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/** SQL names the schema, because the application role's search_path is the platform's. */
@Component
class JdbcPolicyRepository(private val jdbc: JdbcTemplate) : PolicyRepository {
    override fun currentVersions(): Map<ConsentType, PolicyVersion> =
        jdbc.query(
            """
            select distinct on (type) id, type, version, title, markdown
              from consent.policy_version
             where published_at is not null
             order by type, version desc
            """.trimIndent(),
        ) { rs, _ ->
            PolicyVersion(
                rs.getObject("id", UUID::class.java), ConsentType.valueOf(rs.getString("type")),
                rs.getInt("version"), rs.getString("title"), rs.getString("markdown"),
            )
        }.associateBy { it.type }
}

@Component
class JdbcConsentEvents(private val jdbc: JdbcTemplate) : ConsentEvents {
    override fun lastRecords(userId: UUID): Map<ConsentType, ConsentRecord> =
        jdbc.query("select type, action, policy_version_id from consent.current_consent where user_id = ?", { rs, _ ->
            ConsentRecord(ConsentType.valueOf(rs.getString("type")), ConsentAction.valueOf(rs.getString("action")), rs.getObject("policy_version_id", UUID::class.java))
        }, userId).associateBy { it.type }

    override fun history(userId: UUID): List<ConsentHistoryEntry> =
        jdbc.query("select type, action, policy_version_id, client, occurred_at from consent.consent_event where user_id = ? order by seq", { rs, _ ->
            ConsentHistoryEntry(
                ConsentType.valueOf(rs.getString("type")), ConsentAction.valueOf(rs.getString("action")),
                rs.getObject("policy_version_id", UUID::class.java), Client.valueOf(rs.getString("client")), rs.getTimestamp("occurred_at").toInstant(),
            )
        }, userId)

    override fun append(userId: UUID, type: ConsentType, action: ConsentAction, policyVersionId: UUID, client: Client): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into consent.consent_event (id, user_id, type, action, policy_version_id, client) values (?, ?, ?, ?, ?, ?)",
            id, userId, type.name, action.name, policyVersionId, client.name,
        )
        return id
    }
}
