-- Schema of the module `profile` (CLAUDE.md, section 3: one schema, one migrations folder, one history table per module).
-- Runs as the database admin (Flyway user). The roles already exist (platform V1). heracles_owner owns everything and is
-- the only role that runs DDL; heracles_app only does DML on tables and calls functions, as in `platform`.
ALTER SCHEMA profile OWNER TO heracles_owner;
GRANT USAGE ON SCHEMA profile TO heracles_app;

ALTER DEFAULT PRIVILEGES FOR ROLE heracles_owner IN SCHEMA profile
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO heracles_app;
ALTER DEFAULT PRIVILEGES FOR ROLE heracles_owner IN SCHEMA profile
    GRANT USAGE, SELECT ON SEQUENCES TO heracles_app;
ALTER DEFAULT PRIVILEGES FOR ROLE heracles_owner IN SCHEMA profile
    GRANT EXECUTE ON FUNCTIONS TO heracles_app;

-- Flyway's history table stays readable but immutable for the application.
GRANT SELECT ON profile.flyway_schema_history TO heracles_app;
