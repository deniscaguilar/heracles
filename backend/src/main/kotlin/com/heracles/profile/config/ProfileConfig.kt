package com.heracles.profile.config

import com.heracles.platform.api.AuditLog
import com.heracles.platform.api.ClockProvider
import com.heracles.platform.api.Transactions
import com.heracles.profile.application.AnamnesisExport
import com.heracles.profile.application.GetCatalog
import com.heracles.profile.application.GetMyAnamnesis
import com.heracles.profile.application.GetRevision
import com.heracles.profile.application.ListRevisions
import com.heracles.profile.application.SaveAnamnesis
import com.heracles.profile.application.port.out.AnamnesisRepository
import com.heracles.profile.application.port.out.CatalogRepository
import com.heracles.profile.application.GetEligibility
import com.heracles.profile.domain.ScreeningAnswer
import com.heracles.profile.domain.ScreeningQuestion
import com.heracles.profile.domain.TriageRules
import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.json.JsonMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Use cases are plain classes; this is where they are wired. */
@Configuration
class ProfileConfig {
    @Bean
    fun getCatalog(catalogs: CatalogRepository) = GetCatalog(catalogs)

    /** Read once at startup from the versioned file; a broken file stops the application instead of letting everyone through. */
    @Bean
    fun triageRules(mapper: JsonMapper): TriageRules =
        ClassPathResource("triage-rules.json").inputStream.use { stream ->
            val node = mapper.readTree(stream)
            TriageRules(
                version = node.path("version").asInt(),
                minimumAgeYears = node.path("minimumAgeYears").asInt(),
                blockingAnswers = node.path("blockingAnswers").toList().map { ScreeningAnswer.valueOf(it.asString()) }.toSet(),
                questions = node.path("questions").toList().map { ScreeningQuestion.valueOf(it.asString()) }.toSet(),
            ).also { require(it.version >= 1 && it.minimumAgeYears >= 1 && it.blockingAnswers.isNotEmpty()) { "triage-rules.json is incomplete" } }
        }

    @Bean
    fun saveAnamnesis(
        transactions: Transactions, catalogs: CatalogRepository, anamneses: AnamnesisRepository, audit: AuditLog, clock: ClockProvider,
        rules: TriageRules,
    ) = SaveAnamnesis(transactions, catalogs, anamneses, audit, clock, rules)

    @Bean
    fun anamnesisExport(anamneses: AnamnesisRepository) = AnamnesisExport(anamneses)

    @Bean
    fun getEligibility(anamneses: AnamnesisRepository) = GetEligibility(anamneses)

    @Bean
    fun getMyAnamnesis(transactions: Transactions, anamneses: AnamnesisRepository) = GetMyAnamnesis(transactions, anamneses)

    @Bean
    fun listRevisions(transactions: Transactions, anamneses: AnamnesisRepository) = ListRevisions(transactions, anamneses)

    @Bean
    fun getRevision(transactions: Transactions, anamneses: AnamnesisRepository) = GetRevision(transactions, anamneses)
}
