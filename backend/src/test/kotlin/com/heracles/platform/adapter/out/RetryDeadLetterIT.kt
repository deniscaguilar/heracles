package com.heracles.platform.adapter.out

import com.heracles.platform.application.ConsumeTask
import com.heracles.platform.application.DeliveryOutcome
import com.heracles.platform.application.ProcessDelivery
import com.heracles.platform.application.port.out.ProcessedMessages
import com.heracles.platform.application.port.out.TaskHandler
import com.heracles.platform.application.port.out.TaskStatuses
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.QueueMessage
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
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
import java.util.concurrent.atomic.AtomicInteger

/** E1-S6.6 (CA14): a message that always fails is retried with a growing wait, then goes to the DLQ and its task is FAILED. */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetryDeadLetterIT(
    @Autowired private val processed: ProcessedMessages,
    @Autowired private val statuses: TaskStatuses,
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

    private fun newTask(): UUID = transactionContext.run(AccessContext.system()) {
        jdbc.queryForObject("insert into task (type) values ('probe.note') returning id", UUID::class.java)!!
    }

    private fun taskRow(id: UUID) = superuser.queryForMap("select status, attempts, last_error_code from platform.task where id = ?", id)

    /** Runs the consumer until [stop] says the work is over; waits are short so the test stays fast. */
    private fun pump(queue: SqsTaskQueue, processor: ProcessDelivery, stop: (List<DeliveryOutcome>) -> Boolean): List<DeliveryOutcome> {
        val outcomes = mutableListOf<DeliveryOutcome>()
        val deadline = System.currentTimeMillis() + 30_000
        while (!stop(outcomes) && System.currentTimeMillis() < deadline) {
            queue.receive(10, 1).forEach { outcomes += processor(it) }
        }
        return outcomes
    }

    private fun processor(queue: SqsTaskQueue, deadLetters: SqsDeadLetters, handler: TaskHandler, maxAttempts: Int) = ProcessDelivery(
        ConsumeTask(transactionContext, processed, queue, mapOf("probe.note" to handler), consumer = "it-${UUID.randomUUID()}"),
        queue, deadLetters, statuses, maxAttempts, delay = { Duration.ofSeconds(1) },
    )

    @Test
    fun `a message that always fails is retried, then lands in the DLQ and its task is FAILED`() {
        val queue = queue("work"); val dlq = queue("dlq")
        val taskId = newTask()
        val calls = AtomicInteger()
        val processor = processor(queue, SqsDeadLetters(dlq), { calls.incrementAndGet(); error("this text must not be stored") }, maxAttempts = 3)
        val message = QueueMessage(UUID.randomUUID().toString(), "probe.note", mapOf("taskId" to taskId.toString()))
        queue.send(message)

        val outcomes = pump(queue, processor) { DeliveryOutcome.DEAD_LETTERED in it }

        assertThat(outcomes).containsExactly(DeliveryOutcome.RETRY_SCHEDULED, DeliveryOutcome.RETRY_SCHEDULED, DeliveryOutcome.DEAD_LETTERED)
        assertThat(calls.get()).isEqualTo(3)
        assertThat(dlq.receive(10, 1).map { it.message }).containsExactly(message)
        Thread.sleep(1500)
        assertThat(queue.receive(10, 1)).describedAs("the original is gone").isEmpty()
        val row = taskRow(taskId)
        assertThat(row["status"]).isEqualTo("FAILED")
        assertThat(row["attempts"]).isEqualTo(3)
        assertThat(row["last_error_code"]).isEqualTo("IllegalStateException")
    }

    @Test
    fun `a message that fails twice and then works ends DONE and never reaches the DLQ`() {
        val queue = queue("work"); val dlq = queue("dlq")
        val taskId = newTask()
        val calls = AtomicInteger()
        val processor = processor(queue, SqsDeadLetters(dlq), { if (calls.incrementAndGet() < 3) error("flaky") }, maxAttempts = 5)
        queue.send(QueueMessage(UUID.randomUUID().toString(), "probe.note", mapOf("taskId" to taskId.toString())))

        val outcomes = pump(queue, processor) { DeliveryOutcome.PROCESSED in it }

        assertThat(outcomes).containsExactly(DeliveryOutcome.RETRY_SCHEDULED, DeliveryOutcome.RETRY_SCHEDULED, DeliveryOutcome.PROCESSED)
        assertThat(dlq.receive(10, 1)).isEmpty()
        assertThat(taskRow(taskId)["status"]).isEqualTo("DONE")
    }

    @Test
    fun `while retrying the task shows PROCESSING with the attempt count`() {
        val queue = queue("work"); val dlq = queue("dlq")
        val taskId = newTask()
        val processor = processor(queue, SqsDeadLetters(dlq), { error("down") }, maxAttempts = 5)
        queue.send(QueueMessage(UUID.randomUUID().toString(), "probe.note", mapOf("taskId" to taskId.toString())))

        pump(queue, processor) { it.isNotEmpty() }

        val row = taskRow(taskId)
        assertThat(row["status"]).isEqualTo("PROCESSING")
        assertThat(row["attempts"]).isEqualTo(1)
        assertThat(row["last_error_code"]).isEqualTo("IllegalStateException")
    }
}
