-- Test-only tables (E1-S2.3): live in test resources and never ship. They exercise the isolation rules of ADR-0003.
-- Convention for every migration after V1: objects are created by heracles_owner.
SET LOCAL ROLE heracles_owner;

-- User data: each user reads and writes only their own rows.
CREATE TABLE platform.test_user_note (
    id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    body    text NOT NULL
);
ALTER TABLE platform.test_user_note ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.test_user_note FORCE ROW LEVEL SECURITY;
CREATE POLICY own_rows ON platform.test_user_note
    USING (user_id = platform.current_app_user_id())
    WITH CHECK (user_id = platform.current_app_user_id());

-- Health data: same policy, and no scope (admin, system) reads it outside the owner's own context.
CREATE TABLE platform.test_health_sample (
    id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    value   text NOT NULL
);
ALTER TABLE platform.test_health_sample ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.test_health_sample FORCE ROW LEVEL SECURITY;
CREATE POLICY own_rows ON platform.test_health_sample
    USING (user_id = platform.current_app_user_id())
    WITH CHECK (user_id = platform.current_app_user_id());
