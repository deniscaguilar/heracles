-- Versioned policies and consent texts (spec RF1). A published version is immutable (CA2); a correction is a new version.
-- Not user data (no user_id), but only the admin scope writes, and only through the functions below.
SET LOCAL ROLE heracles_owner;

CREATE TABLE consent.policy_version (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    type         text NOT NULL CHECK (type IN (
        'TERMS_OF_USE', 'PRIVACY_POLICY', 'HEALTH_DATA_CONSENT', 'AI_EXAMS_CONSENT',
        'COMMUNITY_CONSENT', 'NOTIFICATIONS_CONSENT', 'PROMOTIONAL_CONSENT')),
    -- Null while it is a draft; the number is given when it is published.
    version      integer CHECK (version > 0),
    title        text NOT NULL CHECK (length(title) BETWEEN 1 AND 200),
    markdown     text NOT NULL CHECK (length(markdown) <= 60000),
    created_by   uuid NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    published_by uuid,
    published_at timestamptz,
    CHECK ((published_at IS NULL) = (version IS NULL) AND (published_at IS NULL) = (published_by IS NULL))
);
CREATE UNIQUE INDEX policy_version_number ON consent.policy_version (type, version) WHERE version IS NOT NULL;
CREATE UNIQUE INDEX policy_one_draft ON consent.policy_version (type) WHERE published_at IS NULL;

-- Once published, a version can no longer be changed or deleted, by anyone (the application, the owner or the superuser).
CREATE FUNCTION consent.refuse_published_change() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.published_at IS NOT NULL THEN
        RAISE EXCEPTION 'a published policy version is immutable' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER policy_version_immutable BEFORE UPDATE OR DELETE ON consent.policy_version
    FOR EACH ROW EXECUTE FUNCTION consent.refuse_published_change();

ALTER TABLE consent.policy_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE consent.policy_version FORCE ROW LEVEL SECURITY;
-- Everyone reads what is published; only the admin scope sees drafts.
CREATE POLICY readable ON consent.policy_version FOR SELECT
    USING (published_at IS NOT NULL OR platform.current_app_scope() = 'admin');
-- The functions below run as the owner.
CREATE POLICY owner_all ON consent.policy_version FOR ALL TO heracles_owner
    USING (true) WITH CHECK (true);

-- The application reads; it never writes the table directly.
REVOKE ALL ON consent.policy_version FROM heracles_app;
GRANT SELECT ON consent.policy_version TO heracles_app;

CREATE FUNCTION consent.require_admin() RETURNS void
    LANGUAGE plpgsql
AS $$
BEGIN
    IF platform.current_app_scope() <> 'admin' THEN
        RAISE EXCEPTION 'only the admin scope manages policies' USING ERRCODE = 'insufficient_privilege';
    END IF;
END
$$;

CREATE FUNCTION consent.create_draft(p_type text, p_title text, p_markdown text, p_actor uuid) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = consent, pg_temp
AS $$
DECLARE
    v_id uuid := gen_random_uuid();
BEGIN
    PERFORM consent.require_admin();
    -- A second draft of the same type hits policy_one_draft (unique_violation).
    INSERT INTO consent.policy_version (id, type, title, markdown, created_by) VALUES (v_id, p_type, p_title, p_markdown, p_actor);
    RETURN v_id;
END
$$;

CREATE FUNCTION consent.update_draft(p_id uuid, p_title text, p_markdown text) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = consent, pg_temp
AS $$
BEGIN
    PERFORM consent.require_admin();
    UPDATE consent.policy_version SET title = p_title, markdown = p_markdown WHERE id = p_id AND published_at IS NULL;
    RETURN FOUND;
END
$$;

CREATE FUNCTION consent.discard_draft(p_id uuid) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = consent, pg_temp
AS $$
BEGIN
    PERFORM consent.require_admin();
    DELETE FROM consent.policy_version WHERE id = p_id AND published_at IS NULL;
    RETURN FOUND;
END
$$;

-- Promotes the draft to the next number of its type. Returns the number, or null when there is no such draft.
CREATE FUNCTION consent.publish_version(p_id uuid, p_actor uuid) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = consent, pg_temp
AS $$
DECLARE
    v_type text;
    v_next integer;
BEGIN
    PERFORM consent.require_admin();
    SELECT type INTO v_type FROM consent.policy_version WHERE id = p_id AND published_at IS NULL FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT coalesce(max(version), 0) + 1 INTO v_next FROM consent.policy_version WHERE type = v_type;
    UPDATE consent.policy_version SET version = v_next, published_at = now(), published_by = p_actor WHERE id = p_id;
    RETURN v_next;
END
$$;

REVOKE ALL ON FUNCTION consent.create_draft(text, text, text, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION consent.update_draft(uuid, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION consent.discard_draft(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION consent.publish_version(uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION consent.require_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION consent.create_draft(text, text, text, uuid) TO heracles_app;
GRANT EXECUTE ON FUNCTION consent.update_draft(uuid, text, text) TO heracles_app;
GRANT EXECUTE ON FUNCTION consent.discard_draft(uuid) TO heracles_app;
GRANT EXECUTE ON FUNCTION consent.publish_version(uuid, uuid) TO heracles_app;
