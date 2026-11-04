package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.IdentityProvider
import com.heracles.platform.domain.Identity
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException

/**
 * Amazon Cognito adapter of the port (ADR-0008). Verifying a token stays with the OIDC adapter (Cognito is a standard
 * OIDC issuer: the resource server checks signature, issuer, audience and expiry); what Cognito adds is removing the
 * user on erasure, through the administrative API.
 *
 * **Not proven against a real user pool** (there is none yet; the free LocalStack does not cover Cognito). The tests use
 * a fake client. To check when a pool exists: (1) the task role needs `cognito-idp:AdminDeleteUser` on the pool;
 * (2) `AdminDeleteUser` is called with the `sub` as the username, which is assumed to be accepted: if the pool signs
 * people in by e-mail, the username may be another value and this call must be changed; (3) for Apple sign-in the Apple
 * token must also be revoked (ADR-0008, App Store rule), which is not done here.
 */
class CognitoIdentityProvider(
    private val tokens: IdentityProvider,
    private val cognito: CognitoIdentityProviderClient,
    private val userPoolId: String,
) : IdentityProvider {
    override fun verify(token: String): Identity = tokens.verify(token)

    /** Safe to call again: a user that is already gone is the result we want, not an error. Any other failure surfaces, and the erasure step is tried again. */
    override fun deleteUser(subject: String) {
        try {
            cognito.adminDeleteUser { it.userPoolId(userPoolId).username(subject) }
        } catch (e: UserNotFoundException) {
            // already removed
        }
    }
}
