-- Append-only audit trail (CA8). Names of fields and identifiers only, never values (CLAUDE.md, section 4).
SET LOCAL ROLE heracles_owner;

-- Field entries are names (for example "name" or "reference_range.min"), so a value can never be stored by mistake.
CREATE FUNCTION platform.is_field_names(names text[]) RETURNS boolean
    LANGUAGE sql IMMUTABLE
AS $$ SELECT coalesce(bool_and(n ~ '^[A-Za-z][A-Za-z0-9_.]{0,63}$'), true) FROM unnest(names) AS n $$;

CREATE TABLE platform.audit_event (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at   timestamptz NOT NULL DEFAULT now(),
    actor_user_id uuid,
    actor_scope   text NOT NULL CHECK (actor_scope IN ('user', 'admin', 'system')),
    action        text NOT NULL,
    entity_type   text NOT NULL,
    entity_id     text,
    result        text NOT NULL CHECK (result IN ('SUCCESS', 'DENIED', 'FAILURE')),
    fields        text[] NOT NULL DEFAULT '{}' CHECK (platform.is_field_names(fields)),
    trace_id      text
);

CREATE FUNCTION platform.refuse_audit_change() RETURNS trigger
    LANGUAGE plpgsql
AS $$ BEGIN RAISE EXCEPTION 'audit_event is append-only' USING ERRCODE = 'integrity_constraint_violation'; END $$;

CREATE TRIGGER audit_event_no_row_change BEFORE UPDATE OR DELETE ON platform.audit_event
    FOR EACH ROW EXECUTE FUNCTION platform.refuse_audit_change();
CREATE TRIGGER audit_event_no_truncate BEFORE TRUNCATE ON platform.audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION platform.refuse_audit_change();

ALTER TABLE platform.audit_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.audit_event FORCE ROW LEVEL SECURITY;
CREATE POLICY admin_reads ON platform.audit_event FOR SELECT
    USING (platform.current_app_scope() = 'admin');
-- Only the owner (through the function below) inserts.
CREATE POLICY owner_inserts ON platform.audit_event FOR INSERT TO heracles_owner
    WITH CHECK (true);

-- The application reads (admin scope, by the policy) but neither writes nor changes the table directly.
REVOKE ALL ON platform.audit_event FROM heracles_app;
GRANT SELECT ON platform.audit_event TO heracles_app;

-- The single way in. SECURITY DEFINER runs as heracles_owner, which alone may insert.
CREATE FUNCTION platform.record_audit_event(
    p_actor_user_id uuid, p_actor_scope text, p_action text, p_entity_type text,
    p_entity_id text, p_result text, p_fields text[], p_trace_id text
) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = platform, pg_temp
AS $$
DECLARE
    v_id uuid := gen_random_uuid();
BEGIN
    -- No RETURNING: it would also need a SELECT policy, and the owner must not read the trail.
    INSERT INTO platform.audit_event (id, actor_user_id, actor_scope, action, entity_type, entity_id, result, fields, trace_id)
    VALUES (v_id, p_actor_user_id, p_actor_scope, p_action, p_entity_type, p_entity_id, p_result, p_fields, p_trace_id);
    RETURN v_id;
END
$$;
REVOKE ALL ON FUNCTION platform.record_audit_event(uuid, text, text, text, text, text, text[], text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform.record_audit_event(uuid, text, text, text, text, text, text[], text) TO heracles_app;
