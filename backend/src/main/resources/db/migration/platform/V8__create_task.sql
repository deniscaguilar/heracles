-- A module creates the status row of an asynchronous task (export, erasure...) from the user's own transaction, and the
-- `task` table stays system-only for writes. The row goes in through this function, as for the outbox and the audit trail.
-- A user can create a task only for themselves; the system scope can create any.
SET LOCAL ROLE heracles_owner;

CREATE POLICY owner_inserts ON platform.task FOR INSERT TO heracles_owner
    WITH CHECK (true);

CREATE FUNCTION platform.create_task(p_id uuid, p_user_id uuid, p_type text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = platform, pg_temp
AS $$
BEGIN
    IF p_user_id IS NOT NULL
       AND p_user_id IS DISTINCT FROM platform.current_app_user_id()
       AND platform.current_app_scope() <> 'system' THEN
        RAISE EXCEPTION 'a task can be created only for the caller' USING ERRCODE = '42501';
    END IF;
    INSERT INTO platform.task (id, user_id, type) VALUES (p_id, p_user_id, p_type);
END
$$;
REVOKE ALL ON FUNCTION platform.create_task(uuid, uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform.create_task(uuid, uuid, text) TO heracles_app;
