package com.heracles.profile.adapter.out

import com.heracles.profile.application.port.out.AnamnesisRepository
import com.heracles.profile.application.port.out.CatalogRepository
import com.heracles.profile.application.port.out.RevisionSummary
import com.heracles.profile.domain.AnamnesisInput
import com.heracles.profile.domain.AnamnesisRevision
import com.heracles.profile.domain.BiologicalSex
import com.heracles.profile.domain.Catalog
import com.heracles.profile.domain.CatalogKind
import com.heracles.profile.domain.Eligibility
import com.heracles.profile.domain.ItemInput
import com.heracles.profile.domain.ScreeningAnswer
import com.heracles.profile.domain.ScreeningQuestion
import com.heracles.profile.domain.ItemKind
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** SQL names the schema, because the application role's search_path is the platform's. */
@Component
class JdbcCatalogRepository(private val jdbc: JdbcTemplate) : CatalogRepository {
    override fun catalog(): Catalog = Catalog(
        jdbc.query("select kind, code from profile.catalog_item") { rs, _ -> CatalogKind.valueOf(rs.getString("kind")) to rs.getString("code") }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() },
    )

    override fun codes(kind: CatalogKind): List<String> =
        jdbc.queryForList("select code from profile.catalog_item where kind = ? order by sort_order", String::class.java, kind.name).filterNotNull()
}

@Component
class JdbcAnamnesisRepository(private val jdbc: JdbcTemplate) : AnamnesisRepository {
    override fun append(userId: UUID, input: AnamnesisInput, eligibility: Eligibility, createdAt: Instant): AnamnesisRevision {
        // Two saves of the same person at once would pick the same number: the lock lasts until the transaction ends.
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0)) is not null", Boolean::class.java, "anamnesis:$userId")
        val revision = jdbc.queryForObject(
            "select coalesce(max(revision), 0) + 1 from profile.anamnesis_revision where user_id = ?", Int::class.java, userId,
        )!!
        val id = UUID.randomUUID()
        jdbc.update(
            """
            insert into profile.anamnesis_revision
                (id, user_id, revision, objective, training_level, birth_date, biological_sex,
                 screening, eligible, ineligibility_reasons, triage_rules_version)
            values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            """.trimIndent(),
            id, userId, revision, input.objective, input.trainingLevel, input.birthDate, input.biologicalSex?.name,
            screeningJson(input.screening), eligibility.eligible, eligibility.reasons.toTypedArray(), eligibility.rulesVersion,
        )
        input.items.forEach {
            jdbc.update(
                """
                insert into profile.anamnesis_item (revision_id, user_id, kind, catalog_code, other_text, body_region, note)
                values (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                id, userId, it.kind.name, it.catalogCode, it.otherText, it.bodyRegion, it.note,
            )
        }
        return find(userId, revision)!!
    }

    override fun latest(userId: UUID): AnamnesisRevision? =
        jdbc.query("select * from profile.anamnesis_revision where user_id = ? order by revision desc limit 1", ::revisionOf, userId).firstOrNull()

    override fun summaries(userId: UUID): List<RevisionSummary> =
        jdbc.query("select revision, created_at, eligible from profile.anamnesis_revision where user_id = ? order by revision", { rs, _ ->
            RevisionSummary(rs.getInt("revision"), rs.getTimestamp("created_at").toInstant(), rs.getBoolean("eligible"))
        }, userId)

    override fun find(userId: UUID, revision: Int): AnamnesisRevision? =
        jdbc.query("select * from profile.anamnesis_revision where user_id = ? and revision = ?", ::revisionOf, userId, revision).firstOrNull()

    private fun revisionOf(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): AnamnesisRevision {
        val id = rs.getObject("id", UUID::class.java)
        val items = jdbc.query(
            "select kind, catalog_code, other_text, body_region, note from profile.anamnesis_item where revision_id = ? order by kind, id",
            { r, _ ->
                ItemInput(ItemKind.valueOf(r.getString("kind")), r.getString("catalog_code"), r.getString("other_text"), r.getString("body_region"), r.getString("note"))
            },
            id,
        )
        return AnamnesisRevision(
            id = id, revision = rs.getInt("revision"), createdAt = rs.getTimestamp("created_at").toInstant(),
            input = AnamnesisInput(
                rs.getString("objective"), rs.getString("training_level"), rs.getObject("birth_date", LocalDate::class.java),
                rs.getString("biological_sex")?.let { BiologicalSex.valueOf(it) }, items, screeningOf(rs.getString("screening")),
            ),
            eligible = rs.getBoolean("eligible"),
            ineligibilityReasons = (rs.getArray("ineligibility_reasons").array as Array<*>).map { it.toString() },
        )
    }

    // The keys and values are enum names, so building the JSON by hand cannot carry anything else.
    private fun screeningJson(screening: Map<ScreeningQuestion, ScreeningAnswer>): String =
        screening.entries.joinToString(prefix = "{", postfix = "}") { "\"${it.key.name}\":\"${it.value.name}\"" }

    private fun screeningOf(json: String): Map<ScreeningQuestion, ScreeningAnswer> =
        Regex("\"([A-Z_]+)\"\\s*:\\s*\"([A-Z_]+)\"").findAll(json).associate {
            ScreeningQuestion.valueOf(it.groupValues[1]) to ScreeningAnswer.valueOf(it.groupValues[2])
        }
}
