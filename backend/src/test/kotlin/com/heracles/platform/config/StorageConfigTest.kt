package com.heracles.platform.config

import com.heracles.platform.adapter.out.S3FileStorage
import com.heracles.platform.domain.StorageKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * The link of a download is opened by a person's browser or phone, not by the backend: locally, the backend reaches the
 * storage by the name it has inside docker (`localstack:4566`), which the person cannot reach. So the link is signed for
 * the public address when there is one (found by the Android emulator run of Stage 2: the export link did not open).
 */
class StorageConfigTest {
    private val config = StorageConfig()
    private val key = StorageKey("exports/00000000-0000-0000-0000-000000000001/task.json")

    private fun linkOf(properties: StorageProperties): String {
        val storage = S3FileStorage(config.s3Client(properties), config.s3Presigner(properties), properties.bucket)
        return storage.downloadUrl(key, Duration.ofMinutes(15))
    }

    @Test
    fun `the link uses the public address when there is one, and the internal one for the calls of the backend`() {
        val url = linkOf(StorageProperties("heracles-files", "sa-east-1", endpoint = "http://localstack:4566", publicEndpoint = "http://localhost:4567"))
        assertThat(url).startsWith("http://localhost:4567/heracles-files/exports/")
        assertThat(url).doesNotContain("localstack")
    }

    @Test
    fun `without a public address the link uses the endpoint, as before`() {
        val url = linkOf(StorageProperties("heracles-files", "sa-east-1", endpoint = "http://localhost:4567"))
        assertThat(url).startsWith("http://localhost:4567/heracles-files/exports/")
    }
}
