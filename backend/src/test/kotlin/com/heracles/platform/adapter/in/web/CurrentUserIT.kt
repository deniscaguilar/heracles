package com.heracles.platform.adapter.`in`.web

import com.heracles.support.OidcInitializer
import com.heracles.support.PostgresInitializer
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

/** E2-S1.1: a controller of another module receives the caller as a CurrentUser (id and role from the database, not the token). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CurrentUserIT(@Value("\${local.server.port}") private val port: Int) {
    private val client = HttpClient.newHttpClient()
    private val admin = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )

    private fun call(subject: String): HttpResponse<String> {
        val token = OidcInitializer.server.issueToken(
            OidcInitializer.ISSUER_ID, "client",
            DefaultOAuth2TokenCallback(OidcInitializer.ISSUER_ID, subject, "JWT", listOf(OidcInitializer.AUDIENCE), emptyMap(), 3600),
        ).serialize()
        return client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/_who")).header("Authorization", "Bearer $token").GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    @Test
    fun `the controller gets the id and the role read from the database`() {
        val subject = "sub-${UUID.randomUUID()}"
        val first = call(subject)
        assertThat(first.statusCode()).isEqualTo(200)
        assertThat(first.body()).contains("\"role\":\"USER\"")

        admin.update("update platform.app_user set role = 'ADMIN' where sub = ?", subject)
        val second = call(subject)
        assertThat(second.body()).contains("\"role\":\"ADMIN\"")
        val id = admin.queryForObject("select id from platform.app_user where sub = ?", String::class.java, subject)
        assertThat(second.body()).contains(id!!)
    }
}
