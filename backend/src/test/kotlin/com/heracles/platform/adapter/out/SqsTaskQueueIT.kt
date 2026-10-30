package com.heracles.platform.adapter.out

import com.heracles.platform.domain.QueueMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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

/** E1-S6.2: send, receive and acknowledge through SQS (ElasticMQ in a container, the same image as docker/compose.yml). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqsTaskQueueIT {
    private val elasticMq = GenericContainer(
        DockerImageName.parse("softwaremill/elasticmq-native:1.7.1@sha256:e4580ab9ad1bd5cd37b4ba04911bc5ccc8cd2d9ab4de56ece65acee71c24e05c"),
    ).withExposedPorts(9324).waitingFor(Wait.forListeningPort())

    private lateinit var sqs: SqsClient

    @BeforeAll
    fun start() {
        elasticMq.start()
        sqs = SqsClient.builder()
            .endpointOverride(URI("http://${elasticMq.host}:${elasticMq.getMappedPort(9324)}"))
            .region(Region.SA_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .build()
    }

    @AfterAll
    fun stop() {
        sqs.close()
        elasticMq.stop()
    }

    private fun newQueue(visibilitySeconds: Int = 30): SqsTaskQueue {
        val url = sqs.createQueue {
            it.queueName("probe-${UUID.randomUUID()}")
            it.attributes(mapOf(QueueAttributeName.VISIBILITY_TIMEOUT to visibilitySeconds.toString()))
        }.queueUrl()
        return SqsTaskQueue(sqs, url)
    }

    private fun message(id: String = UUID.randomUUID().toString()) =
        QueueMessage(id, "exam.import", mapOf("examId" to UUID.randomUUID().toString()))

    @Test
    fun `a message sent is received with its id, type and identifiers, then acknowledged`() {
        // Short visibility: a message that was NOT deleted would come back within the wait below.
        val queue = newQueue(visibilitySeconds = 1)
        val sent = message()
        queue.send(sent)

        val received = queue.receive(maxMessages = 5, waitSeconds = 2)
        assertThat(received).hasSize(1)
        assertThat(received.single().message).isEqualTo(sent)

        queue.acknowledge(received.single().receipt)
        Thread.sleep(1500)
        assertThat(queue.receive(5, 1)).isEmpty()
    }

    @Test
    fun `a message that is not acknowledged comes back after the visibility timeout`() {
        val queue = newQueue(visibilitySeconds = 1)
        queue.send(message())

        val first = queue.receive(1, 2)
        assertThat(first).hasSize(1)
        // Not acknowledged: after the timeout it is delivered again, which is why consumers must be idempotent.
        Thread.sleep(1500)
        val again = queue.receive(1, 2)
        assertThat(again.map { it.message.id }).containsExactly(first.single().message.id)
    }

    @Test
    fun `an empty queue returns nothing`() {
        assertThat(newQueue().receive(5, 1)).isEmpty()
    }

    @Test
    fun `the body on the wire holds only the id, the type and identifiers`() {
        val queue = newQueue()
        val sent = message()
        queue.send(sent)
        val url = queue.queueUrl
        val raw = sqs.receiveMessage { it.queueUrl(url).waitTimeSeconds(2) }.messages().single().body()
        assertThat(raw).contains(sent.id, "exam.import", "examId")
        assertThat(raw.length).isLessThan(300)
    }
}
