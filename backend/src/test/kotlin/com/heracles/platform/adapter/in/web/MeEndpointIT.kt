package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.domain.AccessContext
import com.heracles.support.OidcInitializer
import com.heracles.support.PostgresInitializer
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

/** E1-S3.2 (CA5): the first valid call of a new `sub` creates a USER; the answer has only id and role. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MeEndpointIT(
    @Value("\${local.server.port}") private val port: Int,
    @Autowired private val transactionContext: TransactionContext,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val client = HttpClient.newHttpClient()
    private val admin = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )

    private fun token(subject: String): String = OidcInitializer.server.issueToken(
        OidcInitializer.ISSUER_ID, "client",
        DefaultOAuth2TokenCallback(OidcInitializer.ISSUER_ID, subject, "JWT", listOf(OidcInitializer.AUDIENCE), emptyMap(), 3600),
    ).serialize()

    private fun me(subject: String?): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/me")).GET()
        subject?.let { request.header("Authorization", "Bearer ${token(it)}") }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun idOf(body: String) = Regex("\"id\":\"([0-9a-f-]{36})\"").find(body)!!.groupValues[1]

    @Test
    fun `new subject is created as USER and the answer has only id and role`() {
        val subject = "sub-${UUID.randomUUID()}"
        val response = me(subject)

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).matches("""\{("id":"[0-9a-f-]{36}","role":"USER"|"role":"USER","id":"[0-9a-f-]{36}")}""")
        assertThat(response.body()).doesNotContain(subject)
        assertThat(admin.queryForObject("select role from platform.app_user where sub = ?", String::class.java, subject))
            .isEqualTo("USER")
    }

    @Test
    fun `repeating the call returns the same user without creating another`() {
        val subject = "sub-${UUID.randomUUID()}"
        val first = idOf(me(subject).body())
        val second = idOf(me(subject).body())

        assertThat(second).isEqualTo(first)
        assertThat(admin.queryForObject("select count(*) from platform.app_user where sub = ?", Int::class.java, subject)).isEqualTo(1)
        assertThat(idOf(me("sub-${UUID.randomUUID()}").body())).isNotEqualTo(first)
    }

    @Test
    fun `without a token it is 401 and nothing is created`() {
        val before = admin.queryForObject("select count(*) from platform.app_user", Int::class.java)
        assertThat(me(null).statusCode()).isEqualTo(401)
        assertThat(admin.queryForObject("select count(*) from platform.app_user", Int::class.java)).isEqualTo(before)
    }

    @Test
    fun `a user reads only their own row, admin reads the list, no context reads nothing`() {
        val a = UUID.fromString(idOf(me("sub-${UUID.randomUUID()}").body()))
        val b = UUID.fromString(idOf(me("sub-${UUID.randomUUID()}").body()))
        val ids = { jdbc.queryForList("select id from app_user", UUID::class.java) }

        assertThat(transactionContext.run(AccessContext.forUser(a), ids)).containsExactly(a)
        assertThat(transactionContext.run(AccessContext.admin(), ids)).contains(a, b)
        assertThat(ids()).isEmpty()
    }

    @Test
    fun `a user cannot change their own role or anyone else's`() {
        val a = UUID.fromString(idOf(me("sub-${UUID.randomUUID()}").body()))
        val changed = transactionContext.run(AccessContext.forUser(a)) {
            jdbc.update("update app_user set role = 'ADMIN'") + jdbc.update("delete from app_user")
        }
        assertThat(changed).isZero()
        assertThat(admin.queryForObject("select role from platform.app_user where id = ?", String::class.java, a)).isEqualTo("USER")
    }
}
