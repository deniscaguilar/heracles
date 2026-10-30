package com.heracles.platform.adapter.out

import com.heracles.platform.application.CollectQueueMetrics
import com.heracles.platform.application.port.out.PipelineAges
import com.heracles.platform.application.port.out.QueueMetrics
import com.heracles.platform.domain.QueueMessage
import com.heracles.support.PostgresInitializer
import io.micrometer.core.instrument.MeterRegistry
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
import java.net.URI
import java.util.UUID

/** E1-S6.9 (CA16): the depth of the queues and the age of what is waiting come out as metrics. */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueMetricsIT(
    @Autowired private val metrics: QueueMetrics,
    @Autowired private val ages: PipelineAges,
    @Autowired private val registry: MeterRegistry,
) {
    private val elasticMq = GenericContainer(
        DockerImageName.parse("softwaremill/elasticmq-native:1.7.1@sha256:e4580ab9ad1bd5cd37b4ba04911bc5ccc8cd2d9ab4de56ece65acee71c24e05c"),
    ).withExposedPorts(9324).waitingFor(Wait.forListeningPort())
    private lateinit var sqs: SqsClient
    private lateinit var work: SqsTaskQueue
    private lateinit var dead: SqsTaskQueue
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
        work = SqsTaskQueue(sqs, sqs.createQueue { it.queueName("work-${UUID.randomUUID()}") }.queueUrl())
        dead = SqsTaskQueue(sqs, sqs.createQueue { it.queueName("dlq-${UUID.randomUUID()}") }.queueUrl())
    }

    @AfterAll
    fun stop() {
        sqs.close()
        elasticMq.stop()
    }

    private fun message() = QueueMessage(UUID.randomUUID().toString(), "probe.note", emptyMap())
    private fun collect() = CollectQueueMetrics(SqsQueueDepths(sqs, work.queueUrl, dead.queueUrl), ages, metrics)()
    private fun gauge(name: String, vararg tags: String) = registry.get(name).tags(*tags).gauge().value()

    @Test
    fun `depth of both queues and the age of the oldest waiting work are emitted, and fall back to zero`() {
        // Clean slate for the database part: nothing unpublished, nothing unfinished.
        superuser.update("update platform.outbox_message set published_at = now() where published_at is null")
        superuser.update("update platform.task set status = 'DONE' where status in ('PENDING', 'PROCESSING')")
        collect()
        assertThat(gauge("heracles.queue.depth", "queue", "work")).isZero()
        assertThat(gauge("heracles.queue.depth", "queue", "dlq")).isZero()
        assertThat(gauge("heracles.outbox.oldest.age")).isZero()
        assertThat(gauge("heracles.task.oldest.age")).isZero()

        // Work piles up: 3 in the work queue, 2 dead, an outbox message 2 minutes old, a task 10 minutes old.
        repeat(3) { work.send(message()) }
        repeat(2) { dead.send(message()) }
        val outbox = UUID.randomUUID()
        superuser.update(
            "insert into platform.outbox_message (id, type, aggregate_type, aggregate_id, payload, created_at) values (?, 't', 'a', 'i', '{}'::jsonb, now() - interval '2 minutes')",
            outbox,
        )
        val task = UUID.randomUUID()
        superuser.update("insert into platform.task (id, type, created_at) values (?, 'probe.task', now() - interval '10 minutes')", task)
        Thread.sleep(300) // ElasticMQ counts are approximate; give it a moment
        collect()

        assertThat(gauge("heracles.queue.depth", "queue", "work")).isEqualTo(3.0)
        assertThat(gauge("heracles.queue.depth", "queue", "dlq")).isEqualTo(2.0)
        assertThat(gauge("heracles.outbox.oldest.age")).isBetween(119.0, 180.0)
        assertThat(gauge("heracles.task.oldest.age")).isBetween(599.0, 700.0)

        // Everything gets handled: the metrics go back down.
        generateSequence { work.receive(10, 1).takeIf { it.isNotEmpty() } }.flatten().forEach { work.acknowledge(it.receipt) }
        generateSequence { dead.receive(10, 1).takeIf { it.isNotEmpty() } }.flatten().forEach { dead.acknowledge(it.receipt) }
        superuser.update("update platform.outbox_message set published_at = now() where id = ?", outbox)
        superuser.update("update platform.task set status = 'DONE' where id = ?", task)
        collect()
        assertThat(gauge("heracles.queue.depth", "queue", "work")).isZero()
        assertThat(gauge("heracles.queue.depth", "queue", "dlq")).isZero()
        assertThat(gauge("heracles.outbox.oldest.age")).isZero()
        assertThat(gauge("heracles.task.oldest.age")).isZero()
    }
}
