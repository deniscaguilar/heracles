package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.IdentityProvider
import com.heracles.platform.domain.Identity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException

/**
 * The Cognito adapter of the port (ADR-0008), against a fake client: what is sent to Cognito and what happens when it
 * answers each way. Not proven against a real user pool (there is none yet), see the note on the adapter.
 */
class CognitoIdentityProviderTest {
    private val subject = "7f1c1c52-8d3a-4d7e-9a3b-0b6f4c7e2a11"

    private class FakeCognito(private val answer: (AdminDeleteUserRequest) -> Unit = {}) : CognitoIdentityProviderClient {
        val requests = mutableListOf<AdminDeleteUserRequest>()
        override fun serviceName() = "cognito-idp"
        override fun close() {}
        override fun adminDeleteUser(request: AdminDeleteUserRequest): AdminDeleteUserResponse {
            requests += request
            answer(request)
            return AdminDeleteUserResponse.builder().build()
        }
    }

    private class FakeVerifier : IdentityProvider {
        val deleted = mutableListOf<String>()
        override fun verify(token: String) = Identity("sub-of-$token")
        override fun deleteUser(subject: String) { deleted += subject }
    }

    private fun adapter(cognito: FakeCognito, verifier: FakeVerifier = FakeVerifier()) = CognitoIdentityProvider(verifier, cognito, "sa-east-1_AbCdEf123")

    @Test
    fun `deleting removes the user of that pool, by the subject`() {
        val cognito = FakeCognito()
        adapter(cognito).deleteUser(subject)
        assertThat(cognito.requests).hasSize(1)
        assertThat(cognito.requests.single().userPoolId()).isEqualTo("sa-east-1_AbCdEf123")
        assertThat(cognito.requests.single().username()).isEqualTo(subject)
    }

    @Test
    fun `a user that is already gone is not an error, so the erasure can run again`() {
        val cognito = FakeCognito { throw UserNotFoundException.builder().message("User does not exist.").build() }
        adapter(cognito).deleteUser(subject)
        adapter(cognito).deleteUser(subject)
        assertThat(cognito.requests).hasSize(2)
    }

    @Test
    fun `any other failure is not hidden, so the erasure step fails and is tried again`() {
        val cognito = FakeCognito { throw NotAuthorizedException.builder().message("not allowed").build() }
        assertThatThrownBy { adapter(cognito).deleteUser(subject) }.isInstanceOf(NotAuthorizedException::class.java)
    }

    @Test
    fun `verifying the token is left to the OIDC adapter, and deleting never goes to it`() {
        val verifier = FakeVerifier()
        val cognito = FakeCognito()
        val provider = adapter(cognito, verifier)
        assertThat(provider.verify("abc")).isEqualTo(Identity("sub-of-abc"))
        provider.deleteUser(subject)
        assertThat(verifier.deleted).isEmpty()
        assertThat(cognito.requests).hasSize(1)
    }
}
