package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.ProvisionUser
import com.heracles.platform.application.port.out.IdentityProvider
import com.heracles.platform.config.SpanAttributeAllowlist
import com.heracles.platform.domain.AuthenticationFailedException
import com.heracles.platform.domain.AppUser
import com.heracles.platform.domain.Identity
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
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
    private val provisionUser: ProvisionUser,
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
        // The platform user (and its role) is read once per request and reused by the controllers and the authorizer.
        val user = provisionUser(identity)
        SecurityContextHolder.getContext().authentication = IdentityAuthentication(identity, user)
        request.setAttribute(SpanAttributeAllowlist.USER_ID_REQUEST_ATTRIBUTE, user.id.toString())
        MDC.put(USER_ID, user.id.toString())
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(USER_ID)
        }
    }

    private companion object {
        const val PREFIX = "Bearer "
        const val USER_ID = "userId"
    }
}

/** The authenticated caller: who the token says, and the platform user with the role read from the database. */
class IdentityAuthentication(private val identity: Identity, val user: AppUser) : AbstractAuthenticationToken(emptyList()) {
    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): Identity = identity
    override fun getCredentials(): Any = ""
    override fun getName(): String = identity.subject
}
