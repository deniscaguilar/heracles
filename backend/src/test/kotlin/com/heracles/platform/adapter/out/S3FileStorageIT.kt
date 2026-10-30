package com.heracles.platform.adapter.out

import com.heracles.platform.domain.StorageKey
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
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/** E1-S6.10: FileStorage over S3, tested against LocalStack (same image and digest as docker/compose.yml). No real AWS. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3FileStorageIT {
    private val localStack = GenericContainer(
        DockerImageName.parse("localstack/localstack:4.14.0@sha256:3ebc37595918b8accb852f8048fef2aff047d465167edd655528065b07bc364a"),
    ).withEnv("SERVICES", "s3").withExposedPorts(4566).waitingFor(Wait.forHttp("/_localstack/health").forPort(4566).forStatusCode(200))

    private lateinit var s3: S3Client
    private lateinit var storage: S3FileStorage
    private val bucket = "heracles-test-${UUID.randomUUID()}"

    @BeforeAll
    fun start() {
        localStack.start()
        val endpoint = URI("http://${localStack.host}:${localStack.getMappedPort(4566)}")
        val credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))
        s3 = S3Client.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).endpointOverride(endpoint).region(Region.SA_EAST_1)
            .credentialsProvider(credentials).forcePathStyle(true).build()
        s3.createBucket { it.bucket(bucket) }
        val presigner = S3Presigner.builder().endpointOverride(endpoint).region(Region.SA_EAST_1).credentialsProvider(credentials)
            .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()
        storage = S3FileStorage(s3, presigner, bucket)
    }

    @AfterAll
    fun stop() {
        s3.close()
        localStack.stop()
    }

    private fun key() = StorageKey("users/${UUID.randomUUID()}/exams/${UUID.randomUUID()}.pdf")

    @Test
    fun `a file is stored, read back byte for byte, and deleted`() {
        val key = key()
        val content = ByteArray(2048) { (it % 251).toByte() }

        storage.put(key, content, "application/pdf")
        assertThat(storage.get(key)).isEqualTo(content)

        storage.delete(key)
        assertThat(storage.get(key)).isNull()
    }

    @Test
    fun `a file that does not exist reads as null`() {
        assertThat(storage.get(key())).isNull()
    }

    @Test
    fun `a presigned URL lets the holder download the file without credentials`() {
        val key = key()
        storage.put(key, "conteudo do arquivo".toByteArray(), "text/plain")

        val url = storage.downloadUrl(key, Duration.ofMinutes(5))

        val response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).isEqualTo("conteudo do arquivo")
        assertThat(url).contains("X-Amz-Expires=300")
    }
}
