-- Roles and baseline privileges (ADR-0003). Runs as the database admin (Flyway user).
-- heracles_owner owns the objects and is the only role that runs DDL; heracles_app is what the
-- application connects as: no superuser, no BYPASSRLS, no DDL. Passwords come from Flyway
-- placeholders (environment or SSM in real environments, never from the repository).

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'heracles_owner') THEN
        CREATE ROLE heracles_owner LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'heracles_app') THEN
        CREATE ROLE heracles_app LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB;
    END IF;
END
$$;

ALTER ROLE heracles_owner WITH PASSWORD '${owner_password}';
ALTER ROLE heracles_app WITH PASSWORD '${app_password}';
ALTER ROLE heracles_owner SET search_path = platform;
ALTER ROLE heracles_app SET search_path = platform;

-- Lets the admin run later migrations as the owner (SET ROLE heracles_owner), also when it is not a superuser.
GRANT heracles_owner TO CURRENT_USER WITH SET TRUE;

ALTER SCHEMA platform OWNER TO heracles_owner;

-- Database and public schema: connect only, no object creation for anyone but the owner.
DO $$
BEGIN
    EXECUTE format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database());
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO heracles_owner, heracles_app', current_database());
END
$$;
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- heracles_app only uses what heracles_owner creates in platform: DML on tables, use of sequences.
GRANT USAGE ON SCHEMA platform TO heracles_app;
ALTER DEFAULT PRIVILEGES FOR ROLE heracles_owner IN SCHEMA platform
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO heracles_app;
ALTER DEFAULT PRIVILEGES FOR ROLE heracles_owner IN SCHEMA platform
    GRANT USAGE, SELECT ON SEQUENCES TO heracles_app;
ALTER DEFAULT PRIVILEGES FOR ROLE heracles_owner IN SCHEMA platform
    GRANT EXECUTE ON FUNCTIONS TO heracles_app;

-- Flyway's history table stays readable but immutable for the application.
GRANT SELECT ON platform.flyway_schema_history TO heracles_app;
