package com.heracles.platform.config

import org.flywaydb.core.Flyway
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.boot.flyway.autoconfigure.FlywayProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * One Flyway run per module, each with its own schema, folder and history table (CLAUDE.md, section 3). `platform` is the
 * one Spring Boot configures from `spring.flyway.*` and runs first (it creates the roles); the others follow in a fixed
 * order, on the same connection settings. The versions of each module are independent.
 */
@Configuration
class FlywayModulesConfig {
    @Bean
    fun flywayMigrationStrategy(properties: FlywayProperties): FlywayMigrationStrategy = FlywayMigrationStrategy { platform ->
        platform.migrate()
        MODULES.forEach { module ->
            Flyway.configure()
                .dataSource(properties.url, properties.user, properties.password)
                .schemas(module)
                .defaultSchema(module)
                .locations("classpath:db/migration/$module")
                .table("flyway_schema_history")
                .load()
                .migrate()
        }
    }

    private companion object {
        /** After `platform`, in this order. */
        val MODULES = listOf("consent", "profile")
    }
}
