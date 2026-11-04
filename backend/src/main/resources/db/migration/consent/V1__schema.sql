-- Schema of the module `consent` (CLAUDE.md, section 3: one schema, one migrations folder, one history table per module).
-- Runs as the database admin (Flyway user). The roles already exist (platform V1). heracles_owner owns everything and is
-- the only role that runs DDL; heracles_app only does DML on tables and calls functions, as in `platform`.
ALTER SCHEMA consent OWNER TO heracles_owner;
GRANT USAGE ON SCHEMA consent TO heracles_app;

ALTER DEFAULT PRIVILEGES FOR ROLE heracles_owner IN SCHEMA consent
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO heracles_app;
ALTER DEFAULT PRIVILEGES FOR ROLE heracles_owner IN SCHEMA consent
    GRANT USAGE, SELECT ON SEQUENCES TO heracles_app;
ALTER DEFAULT PRIVILEGES FOR ROLE heracles_owner IN SCHEMA consent
    GRANT EXECUTE ON FUNCTIONS TO heracles_app;

-- Flyway's history table stays readable but immutable for the application.
GRANT SELECT ON consent.flyway_schema_history TO heracles_app;
