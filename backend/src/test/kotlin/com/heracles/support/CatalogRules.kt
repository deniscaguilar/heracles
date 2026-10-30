package com.heracles.support

import org.springframework.jdbc.core.JdbcTemplate

/** Rules over the database catalog that every migration must keep true (CA7, ADR-0003). */
object CatalogRules {
    private const val OWNER = "heracles_owner"
    private val systemSchemas = "'pg_catalog', 'information_schema'"

    /** Human-readable violations; empty when the catalog is sound. [registered] is "schema.table" with an isolation test. */
    fun violations(jdbc: JdbcTemplate, registered: Set<String>): List<String> {
        val violations = mutableListOf<String>()
        val tables = jdbc.queryForList(
            """
            select n.nspname as schema_name, c.relname as table_name, pg_get_userbyid(c.relowner) as owner,
                   c.relrowsecurity as rls_enabled, c.relforcerowsecurity as rls_forced,
                   exists (select 1 from pg_attribute a
                           where a.attrelid = c.oid and a.attname = 'user_id' and a.attnum > 0 and not a.attisdropped) as has_user_id
              from pg_class c join pg_namespace n on n.oid = c.relnamespace
             where c.relkind in ('r', 'p')
               and n.nspname not in ($systemSchemas) and n.nspname not like 'pg\_toast%'
               and c.relname <> 'flyway_schema_history'
            """.trimIndent(),
        )
        tables.forEach { row ->
            val name = "${row["schema_name"]}.${row["table_name"]}"
            if (row["owner"] != OWNER) violations += "$name is owned by ${row["owner"]}, not $OWNER"
            if (row["has_user_id"] == true) {
                if (row["rls_enabled"] != true) violations += "$name has user_id but RLS is not enabled"
                else if (row["rls_forced"] != true) violations += "$name has user_id but RLS is not forced"
                if (name !in registered) violations += "$name has user_id but no registered isolation test"
            }
        }
        return violations
    }
}
