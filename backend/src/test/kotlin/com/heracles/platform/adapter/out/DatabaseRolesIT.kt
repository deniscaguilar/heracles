package com.heracles.platform.adapter.out

import com.heracles.support.PostgresInitializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.SQLException
import javax.sql.DataSource

/** E1-S2.1: the application connects with a role that cannot bypass RLS or change the schema (ADR-0003). */
@SpringBootTest
class DatabaseRolesIT(
    @Autowired private val appDataSource: DataSource,
) {
    private val app = JdbcTemplate(appDataSource)

    private fun roleFlags(role: String): Map<String, Any?> = app.queryForMap(
        "select rolsuper, rolbypassrls, rolcreaterole, rolcreatedb, rolcanlogin from pg_roles where rolname = ?",
        role,
    )

    @Test
    fun `application connects as heracles_app`() {
        assertThat(app.queryForObject("select current_user", String::class.java)).isEqualTo("heracles_app")
    }

    @Test
    fun `heracles_app is not superuser, does not bypass RLS and cannot create roles or databases`() {
        val flags = roleFlags("heracles_app")
        assertThat(flags["rolsuper"]).isEqualTo(false)
        assertThat(flags["rolbypassrls"]).isEqualTo(false)
        assertThat(flags["rolcreaterole"]).isEqualTo(false)
        assertThat(flags["rolcreatedb"]).isEqualTo(false)
        assertThat(flags["rolcanlogin"]).isEqualTo(true)
    }

    @Test
    fun `heracles_owner is not superuser and does not bypass RLS`() {
        val flags = roleFlags("heracles_owner")
        assertThat(flags["rolsuper"]).isEqualTo(false)
        assertThat(flags["rolbypassrls"]).isEqualTo(false)
    }

    @Test
    fun `heracles_app cannot run DDL`() {
        listOf(
            "create table platform.ddl_probe (id int)",
            "create table public.ddl_probe (id int)",
            "create schema ddl_probe",
            "drop schema platform cascade",
            "alter table platform.flyway_schema_history add column probe int",
        ).forEach { ddl ->
            assertThatThrownBy { app.execute(ddl) }
                .describedAs(ddl)
                .hasRootCauseInstanceOf(SQLException::class.java)
                .rootCause()
                .extracting { (it as SQLException).sqlState }
                .isEqualTo("42501") // insufficient_privilege
        }
    }

    @Test
    fun `platform schema has its own Flyway history and is owned by heracles_owner`() {
        val owner = app.queryForObject(
            "select pg_get_userbyid(nspowner) from pg_namespace where nspname = 'platform'",
            String::class.java,
        )
        assertThat(owner).isEqualTo(PostgresInitializer.OWNER_ROLE)
        val applied = app.queryForObject(
            "select count(*) from platform.flyway_schema_history where success",
            Int::class.java,
        )
        assertThat(applied).isGreaterThan(0)
    }
}
