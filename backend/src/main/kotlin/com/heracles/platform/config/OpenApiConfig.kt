package com.heracles.platform.config

import com.heracles.platform.api.CurrentUser
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** The generated contract (springdoc) is versioned in backend/openapi/openapi.json and guarded by OpenApiContractIT. */
@Configuration
class OpenApiConfig {
    init {
        // The caller is resolved from the token by CurrentUserArgumentResolver; it is not a request parameter.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser::class.java)
    }

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(Info().title("Heracles API").version("v1"))
        .components(
            Components().addSecuritySchemes(
                "bearerAuth", SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
            ),
        )
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
}
