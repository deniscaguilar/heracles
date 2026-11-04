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
import com.heracles.profile.domain.AnamnesisInvalidException
import com.heracles.profile.domain.AnamnesisNotFoundException
import com.heracles.profile.domain.AnamnesisRevision
import com.heracles.profile.domain.BiologicalSex
import com.heracles.profile.domain.Catalog
import com.heracles.profile.domain.CatalogKind
import com.heracles.profile.domain.ItemInput
import com.heracles.profile.domain.Eligibility
import com.heracles.profile.domain.ItemKind
import com.heracles.profile.domain.ScreeningAnswer
import com.heracles.profile.domain.ScreeningQuestion
import com.heracles.profile.domain.TriageRules
import com.heracles.profile.api.EligibilityStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class AnamnesisUseCasesTest {
    private class Tx : Transactions {
        override fun <T> asUser(userId: UUID, block: () -> T): T = block()
        override fun <T> asAdmin(block: () -> T): T = block()
        override fun <T> asSystem(block: () -> T): T = block()
    }

    private class Catalogs : CatalogRepository {
        private val codes = mapOf(
            CatalogKind.OBJECTIVE to listOf("GENERAL_HEALTH"), CatalogKind.TRAINING_LEVEL to listOf("BEGINNER"),
            CatalogKind.ALLERGY to listOf("PEANUT", "MILK"),
        )
        override fun catalog() = Catalog(codes.mapValues { it.value.toSet() })
        override fun codes(kind: CatalogKind) = codes[kind].orEmpty()
    }

    private class Store : AnamnesisRepository {
        val rows = mutableMapOf<UUID, MutableList<AnamnesisRevision>>()
        override fun append(userId: UUID, input: AnamnesisInput, eligibility: Eligibility, createdAt: Instant): AnamnesisRevision {
            val list = rows.getOrPut(userId) { mutableListOf() }
            return AnamnesisRevision(UUID.randomUUID(), list.size + 1, createdAt, input, eligibility.eligible, eligibility.reasons).also { list += it }
        }
        override fun latest(userId: UUID) = rows[userId]?.lastOrNull()
        override fun summaries(userId: UUID) = rows[userId].orEmpty().map { RevisionSummary(it.revision, it.createdAt, it.eligible) }
        override fun find(userId: UUID, revision: Int) = rows[userId]?.firstOrNull { it.revision == revision }
    }

    private class Audit : AuditLog {
        val calls = mutableListOf<Triple<String, List<String>, Outcome>>()
        override fun record(actorUserId: UUID?, actorScope: Scope, action: String, entityType: String, entityId: String?, outcome: Outcome, fields: List<String>) {
            calls += Triple(action, fields, outcome)
        }
    }

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val clock = object : ClockProvider {
        override fun now(): Instant = Instant.parse("2026-11-04T15:00:00Z")
        override val zone: ZoneId = this@AnamnesisUseCasesTest.zone
    }
    private val store = Store()
    private val audit = Audit()
    private val rules = TriageRules(1, 18, setOf(ScreeningAnswer.YES, ScreeningAnswer.PREFER_NOT_TO_SAY), ScreeningQuestion.entries.toSet())
    private val save = SaveAnamnesis(Tx(), Catalogs(), store, audit, clock, rules)
    private val user = UUID.randomUUID()

    private val allNo = ScreeningQuestion.entries.associateWith { ScreeningAnswer.NO }

    private fun input(
        allergy: String = "PEANUT", birth: LocalDate = LocalDate.of(1990, 5, 1), screening: Map<ScreeningQuestion, ScreeningAnswer> = allNo,
    ) = AnamnesisInput("GENERAL_HEALTH", "BEGINNER", birth, BiologicalSex.MALE, listOf(ItemInput(ItemKind.ALLERGY, allergy)), screening)

    @Test
    fun `saving twice creates two revisions and the first stays readable`() {
        save(user, input("PEANUT"))
        save(user, input("MILK"))

        val current = GetMyAnamnesis(Tx(), store)(user)
        assertThat(current.revision).isEqualTo(2)
        assertThat(current.input.items.single().catalogCode).isEqualTo("MILK")
        assertThat(GetRevision(Tx(), store)(user, 1).input.items.single().catalogCode).isEqualTo("PEANUT")
        assertThat(ListRevisions(Tx(), store)(user).map { it.revision }).containsExactly(1, 2)
    }

    @Test
    fun `an invalid anamnesis saves nothing and records no audit line`() {
        assertThatThrownBy { save(user, input("NOT_IN_CATALOG")) }.isInstanceOf(AnamnesisInvalidException::class.java)
        assertThat(store.rows).isEmpty()
        assertThat(audit.calls).isEmpty()
    }

    @Test
    fun `the birth date is checked against today in the clock's zone`() {
        // 2026-11-04T15:00Z is 2026-11-04 in Sao Paulo; tomorrow is in the future
        assertThatThrownBy { save(user, input(birth = LocalDate.of(2026, 11, 5))) }.isInstanceOf(AnamnesisInvalidException::class.java)
        save(user, input(birth = LocalDate.of(2026, 11, 4)))
    }

    @Test
    fun `the audit line names fields and carries no value`() {
        save(user, input("PEANUT"))
        assertThat(audit.calls).containsExactly(Triple("anamnesis.saved", listOf("revision"), Outcome.SUCCESS))
    }

    @Test
    fun `a minor is not eligible, the change is audited once, and redoing it as an adult removes the mark (CA12)`() {
        val getEligibility = GetEligibility(store)
        assertThat(getEligibility(user)).isEqualTo(EligibilityStatus.UNKNOWN)

        assertThat(save(user, input(birth = LocalDate.of(2012, 1, 1))).eligible).isFalse()
        assertThat(getEligibility(user)).isEqualTo(EligibilityStatus.NOT_ELIGIBLE_FOR_AUTOMATIC_PLAN)
        save(user, input(birth = LocalDate.of(2012, 2, 1))) // still ineligible: no new "changed" line
        assertThat(audit.calls.count { it.first == "eligibility.changed" }).isEqualTo(1)

        assertThat(save(user, input()).eligible).isTrue()
        assertThat(getEligibility(user)).isEqualTo(EligibilityStatus.ELIGIBLE)
        assertThat(audit.calls.count { it.first == "eligibility.changed" }).isEqualTo(2)
        assertThat(audit.calls.filter { it.first == "eligibility.changed" }.all { it.second == listOf("eligible", "rulesVersion") }).isTrue()
    }

    @Test
    fun `an eligible first save records no eligibility change`() {
        save(user, input())
        assertThat(audit.calls.map { it.first }).containsExactly("anamnesis.saved")
    }

    @Test
    fun `a screening answer that is not a plain no makes the profile ineligible`() {
        val saved = save(user, input(screening = allNo + (ScreeningQuestion.EATING_DISORDER_HISTORY to ScreeningAnswer.PREFER_NOT_TO_SAY)))
        assertThat(saved.eligible).isFalse()
        assertThat(saved.ineligibilityReasons).containsExactly("EATING_DISORDER_HISTORY")
    }

    @Test
    fun `a person with no anamnesis or no such revision gets not found`() {
        assertThatThrownBy { GetMyAnamnesis(Tx(), store)(user) }.isInstanceOf(AnamnesisNotFoundException::class.java)
        save(user, input())
        assertThatThrownBy { GetRevision(Tx(), store)(user, 9) }.isInstanceOf(AnamnesisNotFoundException::class.java)
        assertThatThrownBy { GetRevision(Tx(), store)(UUID.randomUUID(), 1) }.isInstanceOf(AnamnesisNotFoundException::class.java)
    }

    @Test
    fun `the catalog lists codes in order`() {
        assertThat(GetCatalog(Catalogs())(CatalogKind.ALLERGY)).containsExactly("PEANUT", "MILK")
    }
}
