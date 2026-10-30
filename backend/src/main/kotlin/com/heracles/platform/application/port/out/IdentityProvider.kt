package com.heracles.platform.application.port.out

import com.heracles.platform.domain.AuthenticationFailedException
import com.heracles.platform.domain.Identity

/** Turns a bearer token into an [Identity]. The provider (Cognito, or a fake OIDC server locally) stays behind this port. */
interface IdentityProvider {
    /** @throws AuthenticationFailedException when the token is not valid for this API. */
    fun verify(token: String): Identity
}
