package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.Authorizer
import com.heracles.platform.application.ProvisionUser
import com.heracles.platform.domain.Identity
import com.heracles.platform.domain.UserRole
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.core.Authentication
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.stereotype.Component
import java.util.function.Supplier

/** Routes under the admin prefix of the API are for ADMIN only. The role is read from the database for the caller's `sub`. */
@Component
class AdminAuthorizationManager(
    private val provisionUser: ProvisionUser,
    private val authorizer: Authorizer,
) : AuthorizationManager<RequestAuthorizationContext> {

    override fun authorize(authentication: Supplier<out Authentication?>, context: RequestAuthorizationContext): AuthorizationDecision {
        val identity = authentication.get()?.principal as? Identity ?: return AuthorizationDecision(false)
        val user = provisionUser(identity)
        return AuthorizationDecision(authorizer.authorize(user, setOf(UserRole.ADMIN), "admin.access"))
    }
}
