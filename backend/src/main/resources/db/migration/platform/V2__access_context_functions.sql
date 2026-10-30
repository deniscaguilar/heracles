-- Reads the per-transaction context set by the application (ADR-0003). RLS policies use these functions.
-- Without a context the user is NULL, so `user_id = platform.current_app_user_id()` matches no row (fail closed).
SET LOCAL ROLE heracles_owner;

CREATE FUNCTION platform.current_app_user_id() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('app.user_id', true), '')::uuid $$;

CREATE FUNCTION platform.current_app_scope() RETURNS text
    LANGUAGE sql STABLE
AS $$ SELECT coalesce(nullif(current_setting('app.scope', true), ''), 'user') $$;
