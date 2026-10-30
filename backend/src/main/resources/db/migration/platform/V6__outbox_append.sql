-- The outbox is appended to in the same transaction as the state change, which usually runs in the user scope.
-- The table stays system-only; the only way in from other scopes is this function (SECURITY DEFINER, like record_audit_event).
SET LOCAL ROLE heracles_owner;

-- A payload is a flat object whose values are identifiers (strings of the shape [A-Za-z0-9_-]{1,128}), never free text.
CREATE FUNCTION platform.is_identifier_map(payload jsonb) RETURNS boolean
    LANGUAGE sql IMMUTABLE
AS $$
    SELECT jsonb_typeof(payload) = 'object' AND NOT EXISTS (
        SELECT 1 FROM jsonb_each(payload) AS e
        WHERE jsonb_typeof(e.value) <> 'string' OR (e.value #>> '{}') !~ '^[A-Za-z0-9_-]{1,128}$'
    )
$$;

ALTER TABLE platform.outbox_message
    ADD CONSTRAINT payload_is_identifiers CHECK (platform.is_identifier_map(payload));

CREATE POLICY owner_inserts ON platform.outbox_message FOR INSERT TO heracles_owner
    WITH CHECK (true);

CREATE FUNCTION platform.append_outbox_message(
    p_id uuid, p_type text, p_aggregate_type text, p_aggregate_id text, p_payload jsonb
) RETURNS void
    LANGUAGE sql SECURITY DEFINER SET search_path = platform, pg_temp
AS $$
    INSERT INTO platform.outbox_message (id, type, aggregate_type, aggregate_id, payload)
    VALUES (p_id, p_type, p_aggregate_type, p_aggregate_id, p_payload)
$$;
REVOKE ALL ON FUNCTION platform.append_outbox_message(uuid, text, text, text, jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform.append_outbox_message(uuid, text, text, text, jsonb) TO heracles_app;
