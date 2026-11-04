package com.heracles.platform.config

import com.heracles.platform.adapter.out.CognitoIdentityProvider
import com.heracles.platform.adapter.out.JwtIdentityProvider
import com.heracles.platform.application.port.out.IdentityProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/** Without a user pool configured the provider is the OIDC adapter (local and tests); with one, deleting goes to Cognito. */
@SpringBootTest
class DefaultIdentityProviderIT(@Autowired private val provider: IdentityProvider) {
    @Test
    fun `by default there is only the OIDC adapter`() {
        assertThat(provider).isInstanceOf(JwtIdentityProvider::class.java)
    }
}

@SpringBootTest(properties = ["heracles.identity.cognito.user-pool-id=sa-east-1_AbCdEf123", "heracles.identity.cognito.region=sa-east-1"])
class CognitoIdentityProviderWiringIT(@Autowired private val provider: IdentityProvider) {
    @Test
    fun `with a user pool configured the provider is the Cognito adapter`() {
        assertThat(provider).isInstanceOf(CognitoIdentityProvider::class.java)
    }
}
