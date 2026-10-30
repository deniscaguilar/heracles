package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.IdentityProvider
import com.heracles.platform.domain.AuthenticationFailedException
import com.heracles.platform.domain.Identity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component

/** OIDC adapter: the decoder checks signature, issuer, audience and expiry (see SecurityConfig). */
@Component
class JwtIdentityProvider(private val decoder: JwtDecoder) : IdentityProvider {
    override fun verify(token: String): Identity =
        try {
            Identity(decoder.decode(token).subject.orEmpty())
        } catch (e: JwtException) {
            throw AuthenticationFailedException(e)
        } catch (e: IllegalArgumentException) {
            throw AuthenticationFailedException(e)
        }
}
