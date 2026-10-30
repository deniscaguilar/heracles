package com.heracles.platform.adapter.out

import com.heracles.platform.application.ConsumeTask
import com.heracles.platform.application.DeliveryOutcome
import com.heracles.platform.application.ProcessDelivery
import com.heracles.platform.application.RecordAudit
import com.heracles.platform.application.ReprocessDeadLetter
import com.heracles.platform.application.port.out.ProcessedMessages
import com.heracles.platform.application.port.out.TaskHandler
import com.heracles.platform.application.port.out.TaskStatuses
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.AppUser
import com.heracles.platform.domain.DeadLetterNotFoundException
import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.UserRole
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import java.net.URI
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** E1-S6.8 (CA14): reprocessing returns a dead message to the queue and its effect happens exactly once. */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReprocessDeadLetterIT(
    @Autowired private val processed: ProcessedMessages,
    @Autowired private val statuses: TaskStatuses,
    @Autowired private val recordAudit: RecordAudit,
    @Autowired private val transactionContext: TransactionContext,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val elasticMq = GenericContainer(
        DockerImageName.parse("softwaremill/elasticmq-native:1.7.1@sha256:e4580ab9ad1bd5cd37b4ba04911bc5ccc8cd2d9ab4de56ece65acee71c24e05c"),
    ).withExposedPorts(9324).waitingFor(Wait.forListeningPort())
    private lateinit var sqs: SqsClient
    private val superuser = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )

    @BeforeAll
    fun start() {
        elasticMq.start()
        sqs = SqsClient.builder().endpointOverride(URI("http://${elasticMq.host}:${elasticMq.getMappedPort(9324)}")).region(Region.SA_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))).build()
    }

    @AfterAll
    fun stop() {
        sqs.close()
        elasticMq.stop()
    }

    private fun queue(name: String, visibilitySeconds: Int = 1) = SqsTaskQueue(
        sqs,
        sqs.createQueue {
            it.queueName("$name-${UUID.randomUUID()}")
            it.attributes(mapOf(QueueAttributeName.VISIBILITY_TIMEOUT to visibilitySeconds.toString()))
        }.queueUrl(),
    )

    @Test
    fun `a dead message is reprocessed once, back in the queue, the effect happens once and the task ends DONE`() {
        val work = queue("work"); val dlqQueue = queue("dlq")
        val deadLetters = SqsDeadLetters(dlqQueue)
        val user = UUID.randomUUID()
        val note = "dead_${UUID.randomUUID()}".take(40).replace('-', '_')
        val taskId = transactionContext.run(AccessContext.system()) {
            jdbc.queryForObject("insert into task (user_id, type) values (?, 'probe.note') returning id", UUID::class.java, user)!!
        }
        val broken = AtomicBoolean(true)
        val handler = TaskHandler { m ->
            if (broken.get()) error("the downstream service is down")
            jdbc.update("insert into test_user_note (user_id, body) values (?::uuid, ?)", m.ids.getValue("userId"), m.ids.getValue("note"))
        }
        val processor = ProcessDelivery(
            ConsumeTask(transactionContext, processed, work, mapOf("probe.note" to handler), consumer = "it-${UUID.randomUUID()}"),
            work, deadLetters, statuses, maxAttempts = 2, delay = { Duration.ofSeconds(1) },
        )
        val message = QueueMessage(UUID.randomUUID().toString(), "probe.note", mapOf("taskId" to taskId.toString(), "userId" to user.toString(), "note" to note))
        work.send(message)

        // It fails until it is dead-lettered.
        val outcomes = mutableListOf<DeliveryOutcome>()
        val deadline = System.currentTimeMillis() + 30_000
        while (DeliveryOutcome.DEAD_LETTERED !in outcomes && System.currentTimeMillis() < deadline) {
            work.receive(10, 1).forEach { outcomes += processor(it) }
        }
        assertThat(outcomes).endsWith(DeliveryOutcome.DEAD_LETTERED)
        assertThat(superuser.queryForObject("select status from platform.task where id = ?", String::class.java, taskId)).isEqualTo("FAILED")
        assertThat(superuser.queryForObject("select count(*) from platform.test_user_note where body = ?", Int::class.java, note)).isZero()

        // An admin reprocesses it after the problem was fixed.
        broken.set(false)
        val admin = AppUser(UUID.randomUUID(), "sub-admin", UserRole.ADMIN)
        ReprocessDeadLetter(deadLetters, work, statuses, recordAudit)(admin, message.id)

        assertThat(superuser.queryForObject("select status from platform.task where id = ?", String::class.java, taskId)).isEqualTo("PENDING")
        val again = mutableListOf<DeliveryOutcome>()
        val deadline2 = System.currentTimeMillis() + 30_000
        while (DeliveryOutcome.PROCESSED !in again && System.currentTimeMillis() < deadline2) {
            work.receive(10, 1).forEach { again += processor(it) }
        }
        assertThat(again).containsExactly(DeliveryOutcome.PROCESSED)
        assertThat(superuser.queryForObject("select count(*) from platform.test_user_note where body = ?", Int::class.java, note)).isEqualTo(1)
        assertThat(superuser.queryForObject("select status from platform.task where id = ?", String::class.java, taskId)).isEqualTo("DONE")

        // The dead letter queue is empty, and the same id cannot be reprocessed twice.
        Thread.sleep(1500)
        assertThat(dlqQueue.receive(10, 1)).isEmpty()
        assertThatThrownBy { ReprocessDeadLetter(deadLetters, work, statuses, recordAudit)(admin, message.id) }
            .isInstanceOf(DeadLetterNotFoundException::class.java)

        // And the action left an audit event naming the admin and the message id.
        val audit = transactionContext.run(AccessContext.admin()) {
            jdbc.queryForList("select action, result, entity_id, actor_scope from audit_event where actor_user_id = ?", admin.id)
        }
        assertThat(audit).containsExactly(mapOf("action" to "dead_letter.reprocess", "result" to "SUCCESS", "entity_id" to message.id, "actor_scope" to "admin"))
    }

    @Test
    fun `finding a message leaves the others in the dead letter queue`() {
        val dlqQueue = queue("dlq")
        val deadLetters = SqsDeadLetters(dlqQueue)
        val messages = List(4) { QueueMessage(UUID.randomUUID().toString(), "probe.note", emptyMap()) }
        messages.forEach(deadLetters::send)

        val found = deadLetters.find(messages[2].id)
        assertThat(found?.message).isEqualTo(messages[2])
        deadLetters.remove(found!!.receipt)

        Thread.sleep(1500)
        val left = generateSequence { dlqQueue.receive(10, 1).takeIf { it.isNotEmpty() } }.flatten()
            .onEach { dlqQueue.acknowledge(it.receipt) }.map { it.message.id }.toList()
        assertThat(left).containsExactlyInAnyOrder(messages[0].id, messages[1].id, messages[3].id)
    }
}
