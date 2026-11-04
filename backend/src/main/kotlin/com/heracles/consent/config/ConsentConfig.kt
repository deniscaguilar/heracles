package com.heracles.consent.config

import com.heracles.consent.adapter.`in`.web.ConsentBarrierInterceptor
import com.heracles.consent.api.ConsentMessages
import com.heracles.consent.application.AcceptRequiredConsents
import com.heracles.consent.application.ConfirmConsentChange
import com.heracles.consent.application.CheckConsent
import com.heracles.consent.application.ConsentExport
import com.heracles.consent.application.CreateDraft
import com.heracles.consent.application.DiscardDraft
import com.heracles.consent.application.ListPolicies
import com.heracles.consent.application.PreviewVersion
import com.heracles.consent.application.PublishVersion
import com.heracles.consent.application.UpdateDraft
import com.heracles.consent.application.GetCurrentPolicy
import com.heracles.consent.application.ListMyConsents
import com.heracles.consent.application.SetOptionalConsent
import com.heracles.consent.application.port.out.ConsentEvents
import com.heracles.consent.application.port.out.PolicyAdmin
import com.heracles.consent.application.port.out.PolicyRepository
import com.heracles.platform.api.AuditLog
import com.heracles.platform.api.EventPublisher
import com.heracles.platform.api.Notifier
import com.heracles.platform.api.TaskHandlerRegistration
import com.heracles.platform.api.Transactions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Use cases are plain classes; this is where they are wired. */
@Configuration
class ConsentConfig {
    @Bean
    fun listMyConsents(transactions: Transactions, policies: PolicyRepository, events: ConsentEvents) =
        ListMyConsents(transactions, policies, events)

    @Bean
    fun getCurrentPolicy(policies: PolicyRepository) = GetCurrentPolicy(policies)

    @Bean
    fun acceptRequiredConsents(
        transactions: Transactions, policies: PolicyRepository, events: ConsentEvents, audit: AuditLog, publisher: EventPublisher,
    ) = AcceptRequiredConsents(transactions, policies, events, audit, publisher)

    @Bean
    fun setOptionalConsent(
        transactions: Transactions, policies: PolicyRepository, events: ConsentEvents, audit: AuditLog, publisher: EventPublisher,
    ) = SetOptionalConsent(transactions, policies, events, audit, publisher)

    @Bean
    fun createDraft(transactions: Transactions, admin: PolicyAdmin, audit: AuditLog) = CreateDraft(transactions, admin, audit)

    @Bean
    fun updateDraft(transactions: Transactions, admin: PolicyAdmin, audit: AuditLog) = UpdateDraft(transactions, admin, audit)

    @Bean
    fun discardDraft(transactions: Transactions, admin: PolicyAdmin, audit: AuditLog) = DiscardDraft(transactions, admin, audit)

    @Bean
    fun publishVersion(transactions: Transactions, admin: PolicyAdmin, audit: AuditLog) = PublishVersion(transactions, admin, audit)

    @Bean
    fun previewVersion(transactions: Transactions, admin: PolicyAdmin) = PreviewVersion(transactions, admin)

    @Bean
    fun listPolicies(transactions: Transactions, admin: PolicyAdmin) = ListPolicies(transactions, admin)

    @Bean
    fun confirmConsentChange(notifier: Notifier) = ConfirmConsentChange(notifier)

    @Bean
    fun consentAcceptedHandler(confirm: ConfirmConsentChange) = TaskHandlerRegistration(ConsentMessages.ACCEPTED, confirm::handle)

    @Bean
    fun consentRevokedHandler(confirm: ConfirmConsentChange) = TaskHandlerRegistration(ConsentMessages.REVOKED, confirm::handle)

    @Bean
    fun consentExport(events: ConsentEvents) = ConsentExport(events)

    @Bean
    fun checkConsent(policies: PolicyRepository, events: ConsentEvents) = CheckConsent(policies, events)
}

/** Kept apart from the use cases: the barrier needs the checker, which needs the use cases. */
@Configuration
class ConsentWebConfig(private val barrier: ConsentBarrierInterceptor) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(barrier).addPathPatterns("/api/**")
    }
}
