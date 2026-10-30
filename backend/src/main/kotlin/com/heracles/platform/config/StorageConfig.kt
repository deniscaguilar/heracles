package com.heracles.platform.config

import com.heracles.platform.adapter.out.S3FileStorage
import com.heracles.platform.adapter.out.SystemClock
import com.heracles.platform.application.port.out.Clock
import com.heracles.platform.application.port.out.FileStorage
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

/** `endpoint` is set only locally (LocalStack); in AWS it is empty and the SDK uses the instance role. */
@ConfigurationProperties("heracles.storage")
data class StorageProperties(val bucket: String, val region: String, val endpoint: String = "")

@Configuration
@EnableConfigurationProperties(StorageProperties::class)
class StorageConfig {
    private fun local(properties: StorageProperties) = properties.endpoint.isNotBlank()
    private val localCredentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))

    @Bean(destroyMethod = "close")
    fun s3Client(properties: StorageProperties): S3Client {
        val builder = S3Client.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).region(Region.of(properties.region))
        if (local(properties)) builder.endpointOverride(URI(properties.endpoint)).credentialsProvider(localCredentials).forcePathStyle(true)
        return builder.build()
    }

    @Bean(destroyMethod = "close")
    fun s3Presigner(properties: StorageProperties): S3Presigner {
        val builder = S3Presigner.builder().region(Region.of(properties.region))
        if (local(properties)) {
            builder.endpointOverride(URI(properties.endpoint)).credentialsProvider(localCredentials)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        }
        return builder.build()
    }

    @Bean
    fun fileStorage(s3: S3Client, presigner: S3Presigner, properties: StorageProperties): FileStorage =
        S3FileStorage(s3, presigner, properties.bucket)

    @Bean
    fun clock(): Clock = SystemClock()
}
