package com.heracles.platform.adapter.out

import com.heracles.support.CatalogRules
import com.heracles.support.IsolationTests
import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource

/**
 * E1-S2.4 (CA7): protects the future. Any table with a `user_id` column must have RLS enabled and forced and a
 * registered isolation test; every table must belong to heracles_owner. A new migration that forgets this fails here.
 */
@SpringBootTest
class DatabaseCatalogIT(@Autowired private val jdbc: JdbcTemplate) {

    private val admin = JdbcTemplate(
        DriverManagerDataSource(
            PostgresInitializer.postgres.jdbcUrl, PostgresInitializer.postgres.username, PostgresInitializer.postgres.password,
        ),
    )

    @BeforeEach
    @AfterEach
    fun dropProbeSchema() {
        admin.execute("drop schema if exists catalog_probe cascade")
    }

    @Test
    fun `every table with user_id has forced RLS, a registered isolation test and the right owner`() {
        assertThat(CatalogRules.violations(jdbc, IsolationTests.covered.keys)).isEmpty()
    }

    @Test
    fun `the registered isolation tests are real tests`() {
        IsolationTests.covered.values.forEach { testClass ->
            assertThat(testClass.java.methods.filter { it.isAnnotationPresent(Test::class.java) })
                .describedAs(testClass.qualifiedName).isNotEmpty()
        }
    }

    @Test
    fun `a table with user_id and no RLS makes the check fail`() {
        probe("create table catalog_probe.no_rls (id int, user_id uuid)")
        assertThat(CatalogRules.violations(jdbc, IsolationTests.covered.keys))
            .anyMatch { it.contains("catalog_probe.no_rls") && it.contains("RLS") }
    }

    @Test
    fun `RLS enabled but not forced makes the check fail`() {
        probe(
            "create table catalog_probe.not_forced (id int, user_id uuid)",
            "alter table catalog_probe.not_forced enable row level security",
        )
        assertThat(CatalogRules.violations(jdbc, IsolationTests.covered.keys))
            .anyMatch { it.contains("catalog_probe.not_forced") && it.contains("forced") }
    }

    @Test
    fun `protected table without a registered isolation test makes the check fail`() {
        probe(
            "create table catalog_probe.unregistered (id int, user_id uuid)",
            "alter table catalog_probe.unregistered enable row level security",
            "alter table catalog_probe.unregistered force row level security",
        )
        assertThat(CatalogRules.violations(jdbc, IsolationTests.covered.keys))
            .anyMatch { it.contains("catalog_probe.unregistered") && it.contains("isolation test") }
    }

    @Test
    fun `a protected and registered table passes`() {
        probe(
            "create table catalog_probe.good (id int, user_id uuid)",
            "alter table catalog_probe.good enable row level security",
            "alter table catalog_probe.good force row level security",
        )
        val registered = IsolationTests.covered.keys + "catalog_probe.good"
        // Only the ownership rule can still complain: the probe was created by the admin, not by heracles_owner.
        assertThat(CatalogRules.violations(jdbc, registered)).noneMatch { it.contains("RLS") || it.contains("isolation test") }
    }

    @Test
    fun `a table not owned by heracles_owner makes the check fail`() {
        probe("create table catalog_probe.wrong_owner (id int)")
        assertThat(CatalogRules.violations(jdbc, IsolationTests.covered.keys))
            .anyMatch { it.contains("catalog_probe.wrong_owner") && it.contains("owner") }
    }

    private fun probe(vararg statements: String) {
        admin.execute("create schema catalog_probe")
        statements.forEach(admin::execute)
    }
}
