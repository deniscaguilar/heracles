-- E2-S8.1 (plan, section 11, point 1; approved by the owner on 2026-11-04). Erasing a person unlinks them from the audit
-- trail instead of destroying the proof: the identifier is swapped for a random UUID that has no lookup table.
-- The audit trail stays append-only for everyone except one narrow exception: an UPDATE that changes nothing but the
-- identifier columns, executed as heracles_owner, which only the SECURITY DEFINER function below does.
SET LOCAL ROLE heracles_owner;

-- An erased person stays as a row with no `sub` and no profile, so the old `sub` can open a new, empty account (CA14).
ALTER TABLE platform.app_user ADD COLUMN deleted_at timestamptz;
ALTER TABLE platform.app_user ALTER COLUMN sub DROP NOT NULL;
ALTER TABLE platform.app_user ADD CONSTRAINT app_user_active_has_sub CHECK (deleted_at IS NOT NULL OR sub IS NOT NULL);
ALTER TABLE platform.app_user ADD CONSTRAINT app_user_deleted_has_no_sub CHECK (deleted_at IS NULL OR sub IS NULL);

-- Still refuses every DELETE, every TRUNCATE and every UPDATE, except the identifier swap by the owner.
CREATE OR REPLACE FUNCTION platform.refuse_audit_change() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND current_user = 'heracles_owner'
       AND (to_jsonb(NEW) - 'actor_user_id' - 'entity_id') = (to_jsonb(OLD) - 'actor_user_id' - 'entity_id') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'audit_event is append-only' USING ERRCODE = 'integrity_constraint_violation';
END
$$;

-- Row-level security leaves the owner no row to touch unless it has a policy; this one is for the swap only (the trigger
-- decides what may change), and the owner must be able to find the rows by the old identifier.
CREATE POLICY owner_finds ON platform.audit_event FOR SELECT TO heracles_owner USING (true);
CREATE POLICY owner_pseudonymizes ON platform.audit_event FOR UPDATE TO heracles_owner USING (true) WITH CHECK (true);

-- Returns how many rows changed, so a second run (nothing left to swap) is visibly a no-op.
CREATE FUNCTION platform.pseudonymize_user(p_old uuid, p_new uuid) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = platform, pg_temp
AS $$
DECLARE
    v_actor integer;
    v_entity integer;
BEGIN
    IF platform.current_app_scope() <> 'system' THEN
        RAISE EXCEPTION 'only the system scope pseudonymizes' USING ERRCODE = 'insufficient_privilege';
    END IF;
    UPDATE platform.audit_event SET actor_user_id = p_new WHERE actor_user_id = p_old;
    GET DIAGNOSTICS v_actor = ROW_COUNT;
    UPDATE platform.audit_event SET entity_id = p_new::text WHERE entity_type = 'user' AND entity_id = p_old::text;
    GET DIAGNOSTICS v_entity = ROW_COUNT;
    RETURN v_actor + v_entity;
END
$$;
REVOKE ALL ON FUNCTION platform.pseudonymize_user(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform.pseudonymize_user(uuid, uuid) TO heracles_app;
