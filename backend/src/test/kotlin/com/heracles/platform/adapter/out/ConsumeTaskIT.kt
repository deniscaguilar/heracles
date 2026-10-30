package com.heracles.platform.adapter.out

import com.heracles.platform.application.ConsumeOutcome
import com.heracles.platform.application.ConsumeTask
import com.heracles.platform.application.port.out.ProcessedMessages
import com.heracles.platform.application.port.out.TaskHandler
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage
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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** E1-S6.5 (CA13): the same message twice has one effect; the marker and the effect commit together. */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsumeTaskIT(
    @Autowired private val processed: ProcessedMessages,
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

    private fun newQueue(visibilitySeconds: Int = 30) = SqsTaskQueue(
        sqs,
        sqs.createQueue {
            it.queueName("consume-${UUID.randomUUID()}")
            it.attributes(mapOf(QueueAttributeName.VISIBILITY_TIMEOUT to visibilitySeconds.toString()))
        }.queueUrl(),
    )

    /** The business effect: one note for the user named in the message. Runs in the user's scope, in the same transaction as the marker. */
    private val writeNote = TaskHandler { message ->
        jdbc.update("insert into test_user_note (user_id, body) values (?::uuid, ?)", message.ids.getValue("userId"), message.ids.getValue("note"))
    }

    private fun consumer(queue: TaskQueue, handler: TaskHandler = writeNote) =
        ConsumeTask(transactionContext, processed, queue, mapOf("probe.note" to handler), consumer = "it-${UUID.randomUUID()}")

    private fun message(user: UUID, note: String) =
        QueueMessage(UUID.randomUUID().toString(), "probe.note", mapOf("userId" to user.toString(), "note" to note))

    private fun notes(note: String) = superuser.queryForObject("select count(*) from platform.test_user_note where body = ?", Int::class.java, note)

    @Test
    fun `the same message delivered twice has one effect and the queue ends empty`() {
        val queue = newQueue()
        val consume = consumer(queue)
        val user = UUID.randomUUID()
        val note = "twice-${UUID.randomUUID()}".take(40).replace('-', '_')
        val message = message(user, note)
        queue.send(message)
        queue.send(message) // at-least-once delivery: the same id arrives again

        val outcomes = generateSequence { queue.receive(10, 1).takeIf { it.isNotEmpty() } }.flatten().map { consume(it) }.toList()

        assertThat(outcomes).containsExactlyInAnyOrder(ConsumeOutcome.PROCESSED, ConsumeOutcome.DUPLICATE)
        assertThat(notes(note)).isEqualTo(1)
        assertThat(queue.receive(10, 1)).isEmpty()
    }

    @Test
    fun `a failure leaves neither the effect nor the marker, and the redelivery succeeds`() {
        val queue = newQueue(visibilitySeconds = 1)
        val user = UUID.randomUUID()
        val note = "retry_${UUID.randomUUID()}".take(40).replace('-', '_')
        val message = message(user, note)
        queue.send(message)
        val failsOnce = AtomicBoolean(true)
        val consume = consumer(queue) { m ->
            writeNote.handle(m) // the effect is written first...
            if (failsOnce.getAndSet(false)) error("crash after the effect") // ...then the handler fails
        }

        val first = queue.receive(1, 2).single()
        assertThatThrownBy { consume(first) }.hasMessageContaining("crash")
        assertThat(notes(note)).describedAs("effect rolled back").isZero()

        Thread.sleep(1500) // not acknowledged: the queue delivers it again
        val second = queue.receive(1, 2).single()
        assertThat(consume(second)).isEqualTo(ConsumeOutcome.PROCESSED)
        assertThat(notes(note)).isEqualTo(1)
    }

    @Test
    fun `two consumers handling the same message at once produce one effect`() {
        // Receipts here are made up, so acknowledging is a no-op: this test is about the marker, not the queue.
        val queue = object : TaskQueue by newQueue() {
            override fun acknowledge(receipt: String) = Unit
        }
        val user = UUID.randomUUID()
        val note = "race_${UUID.randomUUID()}".take(40).replace('-', '_')
        val delivery = ReceivedMessage(message(user, note), "r-1")
        val ready = CountDownLatch(2)
        val slow = TaskHandler { m -> writeNote.handle(m); Thread.sleep(300) }
        val consume = consumer(queue, slow)
        val pool = Executors.newFixedThreadPool(2)

        val outcomes = List(2) { pool.submit<ConsumeOutcome> { ready.countDown(); ready.await(); consume(delivery) } }
            .map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertThat(notes(note)).isEqualTo(1)
        assertThat(outcomes).containsExactlyInAnyOrder(ConsumeOutcome.PROCESSED, ConsumeOutcome.DUPLICATE)
    }
}
