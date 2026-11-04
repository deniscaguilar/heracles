package com.heracles.consent.adapter.out

import com.heracles.consent.api.ConsentType
import com.heracles.consent.application.port.out.PolicyAdmin
import com.heracles.consent.domain.DraftAlreadyExistsException
import com.heracles.consent.domain.PolicyDocument
import com.heracles.consent.domain.PolicyVersionSummary
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.util.UUID

/** Writes go through the owner's functions (which check the admin scope); reads are plain selects under the row-level policy. */
@Component
class JdbcPolicyAdmin(private val jdbc: JdbcTemplate) : PolicyAdmin {
    override fun createDraft(type: ConsentType, title: String, markdown: String, actor: UUID): UUID = try {
        jdbc.queryForObject("select consent.create_draft(?, ?, ?, ?)", UUID::class.java, type.name, title, markdown, actor)!!
    } catch (e: DuplicateKeyException) {
        throw DraftAlreadyExistsException(type)
    }

    override fun updateDraft(id: UUID, title: String, markdown: String): Boolean =
        jdbc.queryForObject("select consent.update_draft(?, ?, ?)", Boolean::class.javaObjectType, id, title, markdown) == true

    override fun discardDraft(id: UUID): Boolean =
        jdbc.queryForObject("select consent.discard_draft(?)", Boolean::class.javaObjectType, id) == true

    override fun publish(id: UUID, actor: UUID): Int? =
        jdbc.queryForObject("select consent.publish_version(?, ?)", Int::class.javaObjectType, id, actor)

    override fun find(id: UUID): PolicyDocument? =
        jdbc.query("select id, type, version, title, created_at, published_at, 0 as accepted, markdown from consent.policy_version where id = ?", { rs, _ ->
            PolicyDocument(summary(rs), rs.getString("markdown"))
        }, id).firstOrNull()

    override fun list(): List<PolicyVersionSummary> {
        val counts = jdbc.query("select type, version, accepted from consent.version_counts()") { rs, _ ->
            (rs.getString("type") to rs.getInt("version")) to rs.getLong("accepted")
        }.toMap()
        return jdbc.query(
            "select id, type, version, title, created_at, published_at, 0 as accepted from consent.policy_version order by type, version desc nulls first",
        ) { rs, _ -> summary(rs) }.map { it.copy(accepted = it.version?.let { v -> counts[it.type.name to v] } ?: 0L) }
    }

    private fun summary(rs: ResultSet) = PolicyVersionSummary(
        id = rs.getObject("id", UUID::class.java), type = ConsentType.valueOf(rs.getString("type")),
        version = rs.getObject("version") as Int?, title = rs.getString("title"),
        createdAt = rs.getTimestamp("created_at").toInstant(), publishedAt = rs.getTimestamp("published_at")?.toInstant(),
        accepted = rs.getLong("accepted"),
    )
}
