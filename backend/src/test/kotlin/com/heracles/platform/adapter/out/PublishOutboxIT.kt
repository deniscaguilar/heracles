package com.heracles.platform.adapter.out

import com.heracles.platform.application.PublishOutbox
import com.heracles.platform.application.port.out.Outbox
import com.heracles.platform.application.port.out.OutboxStore
import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.Backoff
import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
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
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private class MutableClock(var now: Instant) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    override fun instant(): Instant = now
}

/** E1-S6.4 (CA12): the outbox reaches the queue, also after the publisher was stopped; failures wait longer each time. */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublishOutboxIT(
    @Autowired private val outbox: Outbox,
    @Autowired private val store: OutboxStore,
    @Autowired private val transactionContext: TransactionContext,
) {
    private val elasticMq = GenericContainer(
        DockerImageName.parse("softwaremill/elasticmq-native:1.7.1@sha256:e4580ab9ad1bd5cd37b4ba04911bc5ccc8cd2d9ab4de56ece65acee71c24e05c"),
    ).withExposedPorts(9324).waitingFor(Wait.forListeningPort())
    private lateinit var sqs: SqsClient
    private lateinit var queue: SqsTaskQueue
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
        queue = SqsTaskQueue(sqs, sqs.createQueue { it.queueName("outbox-${UUID.randomUUID()}") }.queueUrl())
    }

    @AfterAll
    fun stop() {
        sqs.close()
        elasticMq.stop()
    }

    @BeforeEach
    fun cleanOutbox() {
        // Other tests leave rows behind; this suite starts from an empty outbox.
        superuser.update("update platform.outbox_message set published_at = now() where published_at is null")
    }

    private fun append(): String {
        val message = QueueMessage(UUID.randomUUID().toString(), "exam.imported", mapOf("examId" to "e-${UUID.randomUUID()}".take(12)))
        transactionContext.run(AccessContext.system()) { outbox.append("exam", "e-1", message) }
        return message.id
    }

    private fun drain(): List<String> = generateSequence { queue.receive(10, 1).takeIf { it.isNotEmpty() } }
        .flatten().onEach { queue.acknowledge(it.receipt) }.map { it.message.id }.toList()

    // next_attempt_at is stamped by the database clock (a container); this one is a few seconds ahead so that a message
    // appended a moment ago is already due, whatever the small difference between the two clocks.
    private val ahead: Clock = Clock.offset(Clock.systemUTC(), Duration.ofSeconds(5))

    private fun publisher(clock: Clock = ahead, target: TaskQueue = queue, batch: Int = 50) =
        PublishOutbox(transactionContext, store, target, clock, batch)

    @Test
    fun `messages committed while the publisher was stopped reach the queue when it starts`() {
        val ids = List(3) { append() }
        // The publisher is not running: nothing in the queue yet.
        assertThat(queue.receive(10, 1)).isEmpty()

        val result = publisher()()

        assertThat(result.published).isEqualTo(3)
        assertThat(drain()).containsExactlyInAnyOrderElementsOf(ids)
        ids.forEach {
            assertThat(superuser.queryForObject("select published_at is not null from platform.outbox_message where id = ?::uuid", Boolean::class.java, it)).isTrue()
        }
        // Published once: a second run finds nothing to do.
        assertThat(publisher()().published).isZero()
    }

    @Test
    fun `a failure keeps the message, counts the attempt and waits longer before the next one`() {
        val id = append()
        val clock = MutableClock(Instant.now().plusSeconds(5))
        val down = object : TaskQueue by queue {
            override fun send(message: QueueMessage) = error("queue is down")
        }

        assertThat(publisher(clock, down)().failed).isEqualTo(1)
        val first = superuser.queryForMap("select attempts, published_at, next_attempt_at from platform.outbox_message where id = ?::uuid", id)
        assertThat(first["attempts"]).isEqualTo(1)
        assertThat(first["published_at"]).isNull()

        // Not due yet: left alone.
        assertThat(publisher(clock, down)().failed).isZero()
        assertThat(superuser.queryForObject("select attempts from platform.outbox_message where id = ?::uuid", Int::class.java, id)).isEqualTo(1)

        // Due again after the wait: fails again, and the wait doubles.
        clock.now = clock.now.plus(Backoff.delay(1)).plusSeconds(1)
        assertThat(publisher(clock, down)().failed).isEqualTo(1)
        val second = superuser.queryForObject("select extract(epoch from next_attempt_at) from platform.outbox_message where id = ?::uuid", Double::class.java, id)!!
        assertThat(second).isCloseTo(clock.now.plus(Backoff.delay(2)).epochSecond.toDouble(), org.assertj.core.data.Offset.offset(1.5))
        assertThat(superuser.queryForObject("select attempts from platform.outbox_message where id = ?::uuid", Int::class.java, id)).isEqualTo(2)

        // The queue is back: it goes out.
        clock.now = clock.now.plus(Backoff.delay(2)).plusSeconds(1)
        assertThat(publisher(clock)().published).isEqualTo(1)
        assertThat(drain()).contains(id)
    }

    @Test
    fun `two publishers at the same time never send the same message twice`() {
        val ids = List(20) { append() }
        val sent = CopyOnWriteArrayList<String>()
        val slow = object : TaskQueue by queue {
            override fun send(message: QueueMessage) {
                Thread.sleep(40)
                sent += message.id
                queue.send(message)
            }
        }
        val pool = Executors.newFixedThreadPool(2)
        val runs = List(2) { pool.submit<Int> { generateSequence { publisher(target = slow, batch = 4)().published.takeIf { it > 0 } }.sum() } }
        val total = runs.sumOf { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown()

        assertThat(total).isEqualTo(20)
        assertThat(sent).containsExactlyInAnyOrderElementsOf(ids)
        assertThat(sent).doesNotHaveDuplicates()
        drain()
    }

    @Test
    fun `the batch size limits one run`() {
        List(5) { append() }
        assertThat(publisher(batch = 3)().published).isEqualTo(3)
        assertThat(publisher(batch = 3)().published).isEqualTo(2)
        drain()
    }
}
