-- Consent is an event, never a state (spec RF2): who accepted or revoked which version of what, and when. Append-only.
-- The current state of each (user, type) is the last event, in the view below.
SET LOCAL ROLE heracles_owner;

CREATE TABLE consent.consent_event (
    seq               bigint GENERATED ALWAYS AS IDENTITY,
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid NOT NULL,
    type              text NOT NULL CHECK (type IN (
        'TERMS_OF_USE', 'PRIVACY_POLICY', 'HEALTH_DATA_CONSENT', 'AI_EXAMS_CONSENT',
        'COMMUNITY_CONSENT', 'NOTIFICATIONS_CONSENT', 'PROMOTIONAL_CONSENT')),
    action            text NOT NULL CHECK (action IN ('GRANT', 'REVOKE')),
    policy_version_id uuid NOT NULL REFERENCES consent.policy_version (id),
    client            text NOT NULL CHECK (client IN ('WEB', 'MOBILE')),
    occurred_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX consent_event_user_type ON consent.consent_event (user_id, type, seq DESC);

-- The database sets the time, and the version must be a published one of the same type.
CREATE FUNCTION consent.check_consent_event() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.occurred_at := clock_timestamp();
    IF NOT EXISTS (
        SELECT 1 FROM consent.policy_version
         WHERE id = NEW.policy_version_id AND type = NEW.type AND published_at IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'consent refers to a version that is not a published one of the same type'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER consent_event_check BEFORE INSERT ON consent.consent_event
    FOR EACH ROW EXECUTE FUNCTION consent.check_consent_event();

-- Append-only for every role. (E2-S8.1 replaces this function with one that keeps refusing everything except the
-- owner's function that swaps the user id when a person is erased.)
CREATE FUNCTION consent.refuse_event_change() RETURNS trigger
    LANGUAGE plpgsql
AS $$ BEGIN RAISE EXCEPTION 'consent_event is append-only' USING ERRCODE = 'integrity_constraint_violation'; END $$;
CREATE TRIGGER consent_event_no_row_change BEFORE UPDATE OR DELETE ON consent.consent_event
    FOR EACH ROW EXECUTE FUNCTION consent.refuse_event_change();
CREATE TRIGGER consent_event_no_truncate BEFORE TRUNCATE ON consent.consent_event
    FOR EACH STATEMENT EXECUTE FUNCTION consent.refuse_event_change();

ALTER TABLE consent.consent_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE consent.consent_event FORCE ROW LEVEL SECURITY;
CREATE POLICY own_reads ON consent.consent_event FOR SELECT
    USING (user_id = platform.current_app_user_id());
CREATE POLICY own_inserts ON consent.consent_event FOR INSERT
    WITH CHECK (user_id = platform.current_app_user_id());
-- The owner's functions only: the aggregate counts (no identity leaves) and, later, the erasure.
CREATE POLICY owner_reads ON consent.consent_event FOR SELECT TO heracles_owner
    USING (true);

REVOKE ALL ON consent.consent_event FROM heracles_app;
GRANT SELECT, INSERT ON consent.consent_event TO heracles_app;

-- The state: the last event of each (user, type). security_invoker, so the row-level security above applies.
CREATE VIEW consent.current_consent WITH (security_invoker = true) AS
    SELECT DISTINCT ON (user_id, type) user_id, type, action, policy_version_id, occurred_at
      FROM consent.consent_event
     ORDER BY user_id, type, seq DESC;
GRANT SELECT ON consent.current_consent TO heracles_app;

-- For the backoffice: how many people have each published version as their current acceptance. No identity leaves.
CREATE FUNCTION consent.version_counts() RETURNS TABLE (type text, version integer, accepted bigint)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = consent, pg_temp
AS $$
BEGIN
    PERFORM consent.require_admin();
    RETURN QUERY
    SELECT pv.type, pv.version, count(cc.user_id)
      FROM consent.policy_version pv
      LEFT JOIN (
          SELECT DISTINCT ON (e.user_id, e.type) e.user_id, e.type, e.action, e.policy_version_id
            FROM consent.consent_event e
           ORDER BY e.user_id, e.type, e.seq DESC
      ) cc ON cc.policy_version_id = pv.id AND cc.action = 'GRANT'
     WHERE pv.published_at IS NOT NULL
     GROUP BY pv.type, pv.version
     ORDER BY pv.type, pv.version;
END
$$;
REVOKE ALL ON FUNCTION consent.version_counts() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION consent.version_counts() TO heracles_app;
