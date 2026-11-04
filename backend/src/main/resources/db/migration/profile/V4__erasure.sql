-- E2-S8.2 (plan, section 4.2: "DELETE only by the erasure function"). The anamnesis stays append-only for everyone; the one
-- way a person's rows leave is profile.erase_user, called in the system scope by the erasure task. The trigger lets a DELETE
-- through only for heracles_owner while that function has raised its transaction-local flag; TRUNCATE and UPDATE stay refused.
SET LOCAL ROLE heracles_owner;

CREATE OR REPLACE FUNCTION profile.refuse_anamnesis_change() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_user = 'heracles_owner' AND current_setting('profile.erasing', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'the anamnesis is append-only' USING ERRCODE = 'integrity_constraint_violation';
END
$$;

-- Row-level security gives the owner no row unless it has a policy: these let the function find and delete one person's rows.
CREATE POLICY owner_finds ON profile.anamnesis_revision FOR SELECT TO heracles_owner USING (true);
CREATE POLICY owner_erases ON profile.anamnesis_revision FOR DELETE TO heracles_owner USING (true);
CREATE POLICY owner_finds ON profile.anamnesis_item FOR SELECT TO heracles_owner USING (true);
CREATE POLICY owner_erases ON profile.anamnesis_item FOR DELETE TO heracles_owner USING (true);

-- Returns how many revisions went away, so a second run is visibly a no-op.
CREATE FUNCTION profile.erase_user(p_user uuid) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = profile, pg_temp
AS $$
DECLARE
    v_revisions integer;
BEGIN
    IF platform.current_app_scope() <> 'system' THEN
        RAISE EXCEPTION 'only the system scope erases' USING ERRCODE = 'insufficient_privilege';
    END IF;
    PERFORM set_config('profile.erasing', 'on', true);
    DELETE FROM profile.anamnesis_item WHERE user_id = p_user;
    DELETE FROM profile.anamnesis_revision WHERE user_id = p_user;
    GET DIAGNOSTICS v_revisions = ROW_COUNT;
    PERFORM set_config('profile.erasing', 'off', true);
    RETURN v_revisions;
END
$$;
REVOKE ALL ON FUNCTION profile.erase_user(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION profile.erase_user(uuid) TO heracles_app;
