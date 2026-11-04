package com.heracles.support

import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

/** A small HTTP client for integration tests: real tokens from the in-process OIDC server, JSON in and out. */
class ApiTestClient(private val port: Int) {
    private val http = HttpClient.newHttpClient()
    private val mapper = JsonMapper()

    class Response(val status: Int, val body: String) {
        val json: JsonNode by lazy { JsonMapper().readTree(body.ifBlank { "null" }) }
        val code: String? get() = json.path("code").asString(null)
    }

    /** A new person: the subject is random, so every call of this starts from a user that does not exist yet. */
    fun newSubject(): String = "sub-${UUID.randomUUID()}"

    fun token(subject: String): String = OidcInitializer.server.issueToken(
        OidcInitializer.ISSUER_ID, "client",
        DefaultOAuth2TokenCallback(OidcInitializer.ISSUER_ID, subject, "JWT", listOf(OidcInitializer.AUDIENCE), emptyMap(), 3600),
    ).serialize()

    fun get(subject: String?, path: String) = send(subject, "GET", path, null)
    fun post(subject: String?, path: String, body: Any? = null) = send(subject, "POST", path, body)
    fun put(subject: String?, path: String, body: Any? = null) = send(subject, "PUT", path, body)
    fun delete(subject: String?, path: String) = send(subject, "DELETE", path, null)

    private fun send(subject: String?, method: String, path: String, body: Any?): Response {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
        subject?.let { builder.header("Authorization", "Bearer ${token(it)}") }
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))
        if (body != null) builder.header("Content-Type", "application/json")
        val response = http.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
        return Response(response.statusCode(), response.body())
    }
}
