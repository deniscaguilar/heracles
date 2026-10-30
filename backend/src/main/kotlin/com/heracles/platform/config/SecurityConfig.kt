package com.heracles.platform.config

import com.heracles.platform.adapter.`in`.web.AdminAuthorizationManager
import com.heracles.platform.adapter.`in`.web.BearerTokenFilter
import com.heracles.platform.adapter.`in`.web.ForbiddenHandler
import com.heracles.platform.adapter.`in`.web.UnauthorizedEntryPoint
import jakarta.servlet.DispatcherType
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter

/** The issuer, the key set and the audience come from the environment; the same image runs everywhere. */
@ConfigurationProperties("heracles.security")
data class SecurityProperties(val issuer: String, val jwkSetUri: String, val audience: String)

@Configuration
@EnableConfigurationProperties(SecurityProperties::class)
class SecurityConfig {

    /** Keys are fetched lazily on the first token, so the application starts without the identity provider. */
    @Bean
    fun jwtDecoder(properties: SecurityProperties): JwtDecoder {
        val decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtTimestampValidator(),
                JwtIssuerValidator(properties.issuer),
                audienceValidator(properties.audience),
            ),
        )
        return decoder
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        bearerTokenFilter: BearerTokenFilter,
        entryPoint: UnauthorizedEntryPoint,
        forbiddenHandler: ForbiddenHandler,
        adminAuthorization: AdminAuthorizationManager,
    ): SecurityFilterChain =
        http
            .csrf(AbstractHttpConfigurer<*, *>::disable) // stateless bearer API: no cookies, no session
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic(AbstractHttpConfigurer<*, *>::disable)
            .formLogin(AbstractHttpConfigurer<*, *>::disable)
            .logout(AbstractHttpConfigurer<*, *>::disable)
            .exceptionHandling { it.authenticationEntryPoint(entryPoint).accessDeniedHandler(forbiddenHandler) }
            .authorizeHttpRequests {
                // The container's internal error dispatch (404, 500) must keep its own status instead of becoming a 401.
                it.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers("/actuator/**").permitAll() // only health is exposed (application.yml)
                    .requestMatchers("/api/v1/admin/**").access(adminAuthorization)
                    .anyRequest().authenticated()
            }
            .addFilterBefore(bearerTokenFilter, AuthorizationFilter::class.java)
            .build()

    private fun audienceValidator(audience: String) = OAuth2TokenValidator<Jwt> { jwt ->
        if (jwt.audience.orEmpty().contains(audience)) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "wrong audience", null))
        }
    }
}
