-- E2-S8.3 (spec RF9, CA13): a reference to each export file, and the way a person reads the audit trail that names them.
SET LOCAL ROLE heracles_owner;

-- The file lives in the file storage for 24 hours; this row says where, for whom, and until when. Not health data itself.
CREATE TABLE platform.data_export (
    task_id     uuid PRIMARY KEY,
    user_id     uuid NOT NULL,
    storage_key text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL
);
CREATE INDEX data_export_expires ON platform.data_export (expires_at);

ALTER TABLE platform.data_export ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.data_export FORCE ROW LEVEL SECURITY;
CREATE POLICY own_reads ON platform.data_export FOR SELECT USING (user_id = platform.current_app_user_id());
CREATE POLICY own_inserts ON platform.data_export FOR INSERT WITH CHECK (user_id = platform.current_app_user_id());
-- The clean-up job (system scope) finds the expired ones and removes the rows after deleting the files.
CREATE POLICY system_reads ON platform.data_export FOR SELECT USING (platform.current_app_scope() = 'system');
CREATE POLICY system_deletes ON platform.data_export FOR DELETE USING (platform.current_app_scope() = 'system');

REVOKE ALL ON platform.data_export FROM heracles_app;
GRANT SELECT, INSERT, DELETE ON platform.data_export TO heracles_app;

-- The audit trail is for the backoffice (admin scope only). A person may read the lines about themselves, for their export:
-- this function returns those lines and nothing else, and only inside a user's transaction.
CREATE FUNCTION platform.my_audit_events()
    RETURNS TABLE (occurred_at timestamptz, action text, entity_type text, result text, fields text[])
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = platform, pg_temp
AS $$
DECLARE
    v_user uuid := platform.current_app_user_id();
BEGIN
    IF v_user IS NULL THEN
        RETURN;
    END IF;
    RETURN QUERY
    SELECT e.occurred_at, e.action, e.entity_type, e.result, e.fields
      FROM platform.audit_event e
     WHERE e.actor_user_id = v_user OR (e.entity_type = 'user' AND e.entity_id = v_user::text)
     ORDER BY e.occurred_at, e.id;
END
$$;
REVOKE ALL ON FUNCTION platform.my_audit_events() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform.my_audit_events() TO heracles_app;
