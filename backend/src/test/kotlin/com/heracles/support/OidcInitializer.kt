package com.heracles.support

import no.nav.security.mock.oauth2.MockOAuth2Server
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext

/** Points every Spring test context at one in-process OIDC server (mock-oauth2-server); registered in spring.factories. */
class OidcInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    override fun initialize(context: ConfigurableApplicationContext) {
        TestPropertyValues.of(
            "heracles.security.issuer=${server.issuerUrl(ISSUER_ID)}",
            "heracles.security.jwk-set-uri=${server.jwksUrl(ISSUER_ID)}",
            "heracles.security.audience=$AUDIENCE",
        ).applyTo(context)
    }

    companion object {
        const val ISSUER_ID = "heracles"
        const val AUDIENCE = "heracles-api"

        val server: MockOAuth2Server by lazy { MockOAuth2Server().also { it.start() } }

        /** A second server with its own signing keys, used to forge tokens with the right issuer and a wrong signature. */
        val forger: MockOAuth2Server by lazy { MockOAuth2Server().also { it.start() } }
    }
}
