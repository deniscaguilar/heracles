package com.heracles.platform.application.port.out

import com.heracles.platform.domain.AuthenticationFailedException
import com.heracles.platform.domain.Identity

/** Turns a bearer token into an [Identity]. The provider (Cognito, or a fake OIDC server locally) stays behind this port. */
interface IdentityProvider {
    /** @throws AuthenticationFailedException when the token is not valid for this API. */
    fun verify(token: String): Identity

    /**
     * The person is erased: remove them at the provider. Safe to call again. The OIDC adapter has nothing to remove on its
     * own side (the platform row is emptied by the erasure itself); the Cognito adapter is a separate task (ADR-0008).
     */
    fun deleteUser(subject: String)
}
