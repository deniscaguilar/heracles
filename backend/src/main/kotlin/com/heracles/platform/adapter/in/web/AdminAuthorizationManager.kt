package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.Authorizer
import com.heracles.platform.domain.UserRole
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.core.Authentication
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.stereotype.Component
import java.util.function.Supplier

/** Routes under the admin prefix of the API are for ADMIN only. The role is read from the database for the caller's `sub`. */
@Component
class AdminAuthorizationManager(private val authorizer: Authorizer) : AuthorizationManager<RequestAuthorizationContext> {

    override fun authorize(authentication: Supplier<out Authentication?>, context: RequestAuthorizationContext): AuthorizationDecision {
        val user = (authentication.get() as? IdentityAuthentication)?.user ?: return AuthorizationDecision(false)
        return AuthorizationDecision(authorizer.authorize(user, setOf(UserRole.ADMIN), "admin.access"))
    }
}
