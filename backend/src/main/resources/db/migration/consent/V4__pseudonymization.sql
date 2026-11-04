-- E2-S8.1 (plan, section 11, point 1). The proof of acceptance outlives the person, without a link to them: the same narrow
-- exception as the audit trail. Every UPDATE, DELETE and TRUNCATE is still refused, except an UPDATE by heracles_owner
-- that changes nothing but user_id, which only consent.pseudonymize_user does.
SET LOCAL ROLE heracles_owner;

CREATE OR REPLACE FUNCTION consent.refuse_event_change() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND current_user = 'heracles_owner'
       AND (to_jsonb(NEW) - 'user_id') = (to_jsonb(OLD) - 'user_id') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'consent_event is append-only' USING ERRCODE = 'integrity_constraint_violation';
END
$$;

-- The owner already reads (owner_reads); it also needs to be allowed to update, and the trigger limits what.
CREATE POLICY owner_pseudonymizes ON consent.consent_event FOR UPDATE TO heracles_owner USING (true) WITH CHECK (true);

CREATE FUNCTION consent.pseudonymize_user(p_old uuid, p_new uuid) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = consent, pg_temp
AS $$
DECLARE
    v_rows integer;
BEGIN
    IF platform.current_app_scope() <> 'system' THEN
        RAISE EXCEPTION 'only the system scope pseudonymizes' USING ERRCODE = 'insufficient_privilege';
    END IF;
    UPDATE consent.consent_event SET user_id = p_new WHERE user_id = p_old;
    GET DIAGNOSTICS v_rows = ROW_COUNT;
    RETURN v_rows;
END
$$;
REVOKE ALL ON FUNCTION consent.pseudonymize_user(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION consent.pseudonymize_user(uuid, uuid) TO heracles_app;
