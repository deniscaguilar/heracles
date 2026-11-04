package com.heracles.platform.config

import com.heracles.platform.adapter.out.CognitoIdentityProvider
import com.heracles.platform.adapter.out.JwtIdentityProvider
import com.heracles.platform.application.port.out.IdentityProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient

/** Set only in an environment that has a Cognito user pool (ADR-0008); absent locally, where the OIDC adapter alone serves. */
@ConfigurationProperties("heracles.identity.cognito")
data class CognitoProperties(val userPoolId: String = "", val region: String = "sa-east-1")

@Configuration
@EnableConfigurationProperties(CognitoProperties::class)
@ConditionalOnProperty(prefix = "heracles.identity.cognito", name = ["user-pool-id"])
class IdentityConfig {
    @Bean(destroyMethod = "close")
    fun cognitoClient(properties: CognitoProperties): CognitoIdentityProviderClient =
        CognitoIdentityProviderClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).region(Region.of(properties.region)).build()

    /** Wins over the OIDC adapter for whoever asks for an [IdentityProvider]; it still uses that one to verify tokens. */
    @Bean
    @Primary
    fun cognitoIdentityProvider(tokens: JwtIdentityProvider, client: CognitoIdentityProviderClient, properties: CognitoProperties): IdentityProvider =
        CognitoIdentityProvider(tokens, client, properties.userPoolId)
}
