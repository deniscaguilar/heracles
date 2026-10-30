package com.heracles.platform.config

import com.heracles.support.OidcInitializer
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * E1-S5.2 (CA11): the API contract is versioned in backend/openapi/openapi.json and the build fails when the code
 * drifts from it. To accept a change on purpose: ./mvnw verify -Dit.test=OpenApiContractIT -Dopenapi.update=true
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiContractIT(@Value("\${local.server.port}") private val port: Int) {
    private val json = ObjectMapper()
    private val versioned = Path.of("openapi/openapi.json")

    private fun generated(): JsonNode {
        val token = OidcInitializer.server.issueToken(
            OidcInitializer.ISSUER_ID, "client",
            DefaultOAuth2TokenCallback(OidcInitializer.ISSUER_ID, "sub-${UUID.randomUUID()}", "JWT", listOf(OidcInitializer.AUDIENCE), emptyMap(), 3600),
        ).serialize()
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/v3/api-docs")).header("Authorization", "Bearer $token").GET().build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(200)
        // `servers` carries the random port of this run; it is not part of the contract.
        return json.readTree(response.body()).also { (it as ObjectNode).remove("servers") }
    }

    @Test
    fun `the generated contract equals the versioned one`() {
        val generated = generated()
        if (System.getProperty("openapi.update") == "true") {
            Files.createDirectories(versioned.parent)
            Files.writeString(versioned, json.writerWithDefaultPrettyPrinter().writeValueAsString(generated) + "\n")
            return
        }
        assertThat(versioned).describedAs("backend/openapi/openapi.json is missing; generate it with -Dopenapi.update=true").exists()
        assertThat(generated)
            .describedAs("The API changed and backend/openapi/openapi.json was not updated (-Dopenapi.update=true)")
            .isEqualTo(json.readTree(Files.readString(versioned)))
    }

    @Test
    fun `the contract lists the real endpoints, protected by bearer, and none of the test-only routes`() {
        val paths = generated().get("paths")
        val names = paths.propertyNames().toList()
        assertThat(names).contains("/api/v1/me", "/api/v1/admin/audit-events")
        assertThat(names).noneMatch { it.contains("_probe") || it.contains("_boom") || it.contains("_validated") || it.contains("_number") }
        assertThat(generated().at("/components/securitySchemes/bearerAuth/scheme").asString()).isEqualTo("bearer")
    }
}
