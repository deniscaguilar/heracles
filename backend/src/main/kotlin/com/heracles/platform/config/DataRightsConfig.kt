package com.heracles.platform.config

import com.heracles.platform.api.AuditLog
import com.heracles.platform.api.ErasureStep
import com.heracles.platform.api.ExportContributor
import com.heracles.platform.api.TaskGateway
import com.heracles.platform.api.TaskHandlerRegistration
import com.heracles.platform.api.Transactions
import com.heracles.platform.application.CancelErasure
import com.heracles.platform.application.ErasureRules
import com.heracles.platform.application.ExportFilesErasure
import com.heracles.platform.application.GetErasure
import com.heracles.platform.application.QueueDueErasures
import com.heracles.platform.application.RequestErasure
import com.heracles.platform.application.RunErasure
import com.heracles.platform.application.port.out.AccountErasures
import com.heracles.platform.application.port.out.AccountRecords
import com.heracles.platform.application.port.out.IdentityProvider
import com.heracles.platform.application.ExpireExports
import com.heracles.platform.application.ExportRules
import com.heracles.platform.application.GetExport
import com.heracles.platform.application.RequestExport
import com.heracles.platform.application.RunExport
import com.heracles.platform.application.port.out.Clock
import com.heracles.platform.application.port.out.DataExports
import com.heracles.platform.application.port.out.FileStorage
import com.heracles.platform.application.port.out.PersonalData
import com.heracles.platform.application.port.out.TaskReader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

/** The data subject's rights (spec RF9): export now; erasure joins in E2-S8.4. */
@Configuration
class DataRightsConfig {
    @Bean
    fun requestExport(transactions: Transactions, tasks: TaskGateway, audit: AuditLog) = RequestExport(transactions, tasks, audit)

    @Bean
    fun runExport(
        contributors: List<ExportContributor>, personal: PersonalData, storage: FileStorage, exports: DataExports,
        clock: Clock, audit: AuditLog, mapper: JsonMapper,
    ) = RunExport(contributors, personal, storage, exports, clock, audit) { mapper.writeValueAsBytes(it) }

    @Bean
    fun exportTaskHandler(run: RunExport) = TaskHandlerRegistration(ExportRules.TASK_TYPE, run::handle)

    @Bean
    fun getExport(transactions: Transactions, tasks: TaskReader, exports: DataExports, storage: FileStorage, clock: Clock) =
        GetExport(transactions, tasks, exports, storage, clock)

    @Bean
    fun requestErasure(transactions: Transactions, erasures: AccountErasures, audit: AuditLog, clock: Clock) = RequestErasure(transactions, erasures, audit, clock)

    @Bean
    fun getErasure(transactions: Transactions, erasures: AccountErasures) = GetErasure(transactions, erasures)

    @Bean
    fun cancelErasure(transactions: Transactions, erasures: AccountErasures, audit: AuditLog) = CancelErasure(transactions, erasures, audit)

    @Bean
    fun queueDueErasures(transactions: Transactions, erasures: AccountErasures, tasks: TaskGateway, clock: Clock) =
        QueueDueErasures(transactions, erasures, tasks, clock)

    @Bean
    fun exportFilesErasure(exports: DataExports, storage: FileStorage) = ExportFilesErasure(exports, storage)

    @Bean
    fun runErasure(
        steps: List<ErasureStep>, records: AccountRecords, identity: IdentityProvider, erasures: AccountErasures, audit: AuditLog,
    ) = RunErasure(steps, records, identity, erasures, audit)

    @Bean
    fun erasureTaskHandler(run: RunErasure) = TaskHandlerRegistration(ErasureRules.TASK_TYPE, run::handle)

    @Bean
    fun expireExports(transactions: Transactions, exports: DataExports, storage: FileStorage, clock: Clock) =
        ExpireExports(transactions, exports, storage, clock)
}
