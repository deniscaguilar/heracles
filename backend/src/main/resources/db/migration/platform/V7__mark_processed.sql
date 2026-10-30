-- A consumer records the message it handles in the SAME transaction as the effect. The effect may run in the user scope
-- (row-level security), while processed_message is system-only, so the marker goes in through this function, as for the
-- outbox and the audit trail. Returns false when the message was already handled (the effect must be skipped).
SET LOCAL ROLE heracles_owner;

CREATE POLICY owner_inserts ON platform.processed_message FOR INSERT TO heracles_owner
    WITH CHECK (true);

CREATE FUNCTION platform.mark_processed(p_consumer text, p_message_id text) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = platform, pg_temp
AS $$
DECLARE
    inserted integer;
BEGIN
    -- With two transactions on the same key, the second waits here for the first to finish, then sees the conflict.
    INSERT INTO platform.processed_message (consumer, message_id) VALUES (p_consumer, p_message_id)
    ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS inserted = ROW_COUNT;
    RETURN inserted = 1;
END
$$;
REVOKE ALL ON FUNCTION platform.mark_processed(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform.mark_processed(text, text) TO heracles_app;
