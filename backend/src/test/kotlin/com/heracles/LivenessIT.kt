package com.heracles

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LivenessIT(@Value("\${local.server.port}") private val port: Int) {
    private val client = HttpClient.newHttpClient()

    private fun get(path: String): HttpResponse<String> =
        client.send(HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().build(), HttpResponse.BodyHandlers.ofString())

    @Test
    fun `liveness responds 200`() {
        val response = get("/actuator/health/liveness")
        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).contains("\"status\":\"UP\"")
    }

    @Test
    fun `health does not expose details`() {
        val response = get("/actuator/health")
        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).doesNotContain("components").doesNotContain("diskSpace")
    }

    @Test
    fun `other actuator endpoints are not exposed`() {
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404)
        assertThat(get("/actuator/beans").statusCode()).isEqualTo(404)
    }
}
