package com.heracles.platform.config

import com.heracles.platform.adapter.out.SqsDeadLetters
import com.heracles.platform.adapter.out.SqsQueueDepths
import com.heracles.platform.adapter.out.SqsTaskQueue
import com.heracles.platform.application.port.out.DeadLetters
import com.heracles.platform.application.port.out.QueueDepths
import com.heracles.platform.application.port.out.TaskQueue
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI

/** `endpoint` is set only locally (ElasticMQ); in AWS it is empty and the SDK uses the instance role. */
@ConfigurationProperties("heracles.queue")
data class QueueProperties(
    val url: String,
    val deadLetterUrl: String,
    val region: String,
    val endpoint: String = "",
    val maxAttempts: Int = 5,
)

@Configuration
@EnableConfigurationProperties(QueueProperties::class)
class QueueConfig {
    @Bean(destroyMethod = "close")
    fun sqsClient(properties: QueueProperties): SqsClient {
        val builder = SqsClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).region(Region.of(properties.region))
        if (properties.endpoint.isNotBlank()) {
            builder.endpointOverride(URI(properties.endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
        }
        return builder.build()
    }

    @Bean
    fun taskQueue(sqs: SqsClient, properties: QueueProperties): TaskQueue = SqsTaskQueue(sqs, properties.url)

    @Bean
    fun deadLetters(sqs: SqsClient, properties: QueueProperties): DeadLetters = SqsDeadLetters(SqsTaskQueue(sqs, properties.deadLetterUrl))

    @Bean
    fun queueDepths(sqs: SqsClient, properties: QueueProperties): QueueDepths = SqsQueueDepths(sqs, properties.url, properties.deadLetterUrl)
}
