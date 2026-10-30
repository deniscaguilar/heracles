package com.heracles.platform.config

import com.heracles.platform.application.Authorizer
import com.heracles.platform.application.GetMe
import com.heracles.platform.application.ProvisionUser
import com.heracles.platform.application.PublishOutbox
import com.heracles.platform.application.QueryAuditTrail
import com.heracles.platform.application.RecordAudit
import com.heracles.platform.application.port.out.AuditReader
import com.heracles.platform.application.port.out.AuditTrail
import com.heracles.platform.application.port.out.OutboxStore
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.application.port.out.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock

/** Use cases are plain classes (no framework in `application`); this is where they are wired. */
@Configuration
@EnableScheduling
class PlatformConfig {
    @Bean
    fun provisionUser(transactions: TransactionContext, users: UserRepository) = ProvisionUser(transactions, users)

    @Bean
    fun getMe(transactions: TransactionContext, users: UserRepository) = GetMe(transactions, users)

    @Bean
    fun recordAudit(trail: AuditTrail) = RecordAudit(trail)

    @Bean
    fun authorizer(recordAudit: RecordAudit) = Authorizer(recordAudit)

    @Bean
    fun queryAuditTrail(transactions: TransactionContext, reader: AuditReader) = QueryAuditTrail(transactions, reader)

    /** Replaced by the Clock port with the Sao Paulo zone in E1-S6.10. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun publishOutbox(transactions: TransactionContext, store: OutboxStore, queue: TaskQueue, clock: Clock) =
        PublishOutbox(transactions, store, queue, clock)
}
