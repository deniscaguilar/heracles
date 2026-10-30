package com.heracles.platform.config

import com.heracles.platform.application.Authorizer
import com.heracles.platform.application.GetMe
import com.heracles.platform.application.GetTaskStatus
import com.heracles.platform.application.ProvisionUser
import com.heracles.platform.application.CollectQueueMetrics
import com.heracles.platform.application.ConsumeTask
import com.heracles.platform.application.ProcessDelivery
import com.heracles.platform.application.ReprocessDeadLetter
import com.heracles.platform.application.PublishOutbox
import com.heracles.platform.application.TaskHandlerRegistration
import com.heracles.platform.application.QueryAuditTrail
import com.heracles.platform.application.RecordAudit
import com.heracles.platform.application.port.out.AuditReader
import com.heracles.platform.application.port.out.Clock
import com.heracles.platform.application.port.out.AuditTrail
import com.heracles.platform.application.port.out.DeadLetters
import com.heracles.platform.application.port.out.PipelineAges
import com.heracles.platform.application.port.out.ProcessedMessages
import com.heracles.platform.application.port.out.QueueDepths
import com.heracles.platform.application.port.out.QueueMetrics
import com.heracles.platform.application.port.out.TaskReader
import com.heracles.platform.application.port.out.TaskStatuses
import com.heracles.platform.application.port.out.OutboxStore
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.application.port.out.UserRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

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


    @Bean
    fun publishOutbox(transactions: TransactionContext, store: OutboxStore, queue: TaskQueue, clock: Clock) =
        PublishOutbox(transactions, store, queue, clock)

    @Bean
    fun consumeTask(
        transactions: TransactionContext, processed: ProcessedMessages, queue: TaskQueue,
        registrations: ObjectProvider<TaskHandlerRegistration>,
    ) = ConsumeTask(transactions, processed, queue, registrations.orderedStream().toList().associate { it.type to it.handler })

    @Bean
    fun processDelivery(consume: ConsumeTask, queue: TaskQueue, deadLetters: DeadLetters, statuses: TaskStatuses, properties: QueueProperties) =
        ProcessDelivery(consume, queue, deadLetters, statuses, properties.maxAttempts)

    @Bean
    fun getTaskStatus(transactions: TransactionContext, tasks: TaskReader) = GetTaskStatus(transactions, tasks)

    @Bean
    fun reprocessDeadLetter(deadLetters: DeadLetters, queue: TaskQueue, statuses: TaskStatuses, recordAudit: RecordAudit) =
        ReprocessDeadLetter(deadLetters, queue, statuses, recordAudit)

    @Bean
    fun collectQueueMetrics(depths: QueueDepths, ages: PipelineAges, metrics: QueueMetrics) = CollectQueueMetrics(depths, ages, metrics)
}
