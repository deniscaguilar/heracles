package com.heracles.support

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Points every Spring test context at one shared real PostgreSQL (Testcontainers), registered through
 * META-INF/spring.factories. Flyway runs as the container superuser (as the admin does in production);
 * the application connects as the unprivileged heracles_app role.
 */
class PostgresInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    override fun initialize(context: ConfigurableApplicationContext) {
        TestPropertyValues.of(
            "spring.datasource.url=${postgres.jdbcUrl}",
            "spring.datasource.username=$APP_ROLE",
            "spring.datasource.password=$APP_PASSWORD",
            "spring.flyway.url=${postgres.jdbcUrl}",
            "spring.flyway.user=${postgres.username}",
            "spring.flyway.password=${postgres.password}",
            // Test-only tables (never shipped): migrations live in test resources, versions start at 1000.
            "spring.flyway.locations=classpath:db/migration/platform,classpath:db/test-migration/platform",
            "spring.flyway.placeholders.owner_password=$OWNER_PASSWORD",
            "spring.flyway.placeholders.app_password=$APP_PASSWORD",
            // The tests call the publisher themselves; a background one would race with them and with the missing queue.
            "heracles.outbox.publisher.enabled=false",
            "heracles.queue.consumer.enabled=false",
            "heracles.queue.metrics.enabled=false",
        ).applyTo(context)
    }

    companion object {
        const val APP_ROLE = "heracles_app"
        const val OWNER_ROLE = "heracles_owner"
        // Random per run; only ever valid inside the throwaway container.
        val APP_PASSWORD: String = java.util.UUID.randomUUID().toString()
        val OWNER_PASSWORD: String = java.util.UUID.randomUUID().toString()

        // Same image (by digest) as docker/compose.yml. Started once; Ryuk removes it when the JVM ends.
        val postgres: PostgreSQLContainer by lazy {
            PostgreSQLContainer(
                DockerImageName.parse("postgres:18@sha256:979bccfde35445db618c65107d27d08c6c21339d8dc3ad4c830c6633947be0e4")
                    .asCompatibleSubstituteFor("postgres"),
            ).also { it.start() }
        }
    }
}
