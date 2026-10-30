-- The platform user: one row per identity subject. Roles live here, not in the token (ADR-0008).
SET LOCAL ROLE heracles_owner;

CREATE TABLE platform.app_user (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sub        text NOT NULL UNIQUE,
    role       text NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN', 'MODERATOR')),
    status     text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE platform.app_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.app_user FORCE ROW LEVEL SECURITY;

-- A user reads only their own row; there is no UPDATE or DELETE policy for users, so nobody changes their own role.
CREATE POLICY own_row ON platform.app_user FOR SELECT
    USING (id = platform.current_app_user_id());
-- The backoffice lists users (not health data).
CREATE POLICY admin_reads ON platform.app_user FOR SELECT
    USING (platform.current_app_scope() = 'admin');
-- Provisioning happens before the user id is known, so it runs in the system scope.
CREATE POLICY system_all ON platform.app_user FOR ALL
    USING (platform.current_app_scope() = 'system')
    WITH CHECK (platform.current_app_scope() = 'system');
