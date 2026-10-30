package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.port.out.IdentityProvider
import com.heracles.platform.domain.AuthenticationFailedException
import com.heracles.platform.domain.Identity
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authenticates `Authorization: Bearer <jwt>` through the [IdentityProvider] port. No header leaves the request
 * anonymous (protected routes then answer 401); a bad token answers 401 right here, without saying why.
 */
@Component
class BearerTokenFilter(
    private val identityProvider: IdentityProvider,
    private val entryPoint: UnauthorizedEntryPoint,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header == null) {
            chain.doFilter(request, response)
            return
        }
        val token = header.takeIf { it.startsWith(PREFIX, ignoreCase = true) }?.substring(PREFIX.length)?.trim()
        val identity = try {
            identityProvider.verify(token.orEmpty())
        } catch (e: AuthenticationFailedException) {
            SecurityContextHolder.clearContext()
            entryPoint.respond(response)
            return
        }
        SecurityContextHolder.getContext().authentication = IdentityAuthentication(identity)
        chain.doFilter(request, response)
    }

    private companion object {
        const val PREFIX = "Bearer "
    }
}

/** The authenticated caller. Roles are not here: they come from the database once the user is provisioned (E1-S3.2). */
class IdentityAuthentication(private val identity: Identity) : AbstractAuthenticationToken(emptyList()) {
    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): Identity = identity
    override fun getCredentials(): Any = ""
    override fun getName(): String = identity.subject
}
