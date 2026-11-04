-- The anamnesis is health data (spec RF8, ADR-0003): isolated by user with forced row-level security, and a history of
-- revisions that is append-only. Each save is a new revision, because the stage 4 plan must know which anamnesis it came from.
-- (E2-S8.1 replaces the refusing function with one that lets the erasure delete a person's rows and nothing else.)
SET LOCAL ROLE heracles_owner;

CREATE TABLE profile.anamnesis_revision (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               uuid NOT NULL,
    revision              integer NOT NULL CHECK (revision >= 1),
    created_at            timestamptz NOT NULL DEFAULT now(),
    objective             text NOT NULL,
    training_level        text NOT NULL,
    birth_date            date NOT NULL,
    biological_sex        text CHECK (biological_sex IN ('FEMALE', 'MALE')),   -- null: prefers not to say
    screening             jsonb NOT NULL DEFAULT '{}'::jsonb,                  -- the screening answers (E2-S7)
    eligible              boolean NOT NULL DEFAULT true,
    ineligibility_reasons text[] NOT NULL DEFAULT '{}',
    triage_rules_version  integer,
    CONSTRAINT anamnesis_revision_user_revision UNIQUE (user_id, revision),
    CONSTRAINT anamnesis_revision_id_user UNIQUE (id, user_id)
);

CREATE TABLE profile.anamnesis_item (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    revision_id  uuid NOT NULL,
    user_id      uuid NOT NULL,
    kind         text NOT NULL CHECK (kind IN (
        'ALLERGY', 'INTOLERANCE', 'RESTRICTION', 'FOOD_PREFERENCE', 'DISLIKED_FOOD', 'EQUIPMENT', 'INJURY')),
    catalog_code text,
    other_text   text CHECK (char_length(other_text) BETWEEN 1 AND 80),
    body_region  text,
    note         text CHECK (char_length(note) BETWEEN 1 AND 200),
    -- the item belongs to a revision of the same person
    FOREIGN KEY (revision_id, user_id) REFERENCES profile.anamnesis_revision (id, user_id),
    -- an injury is a body region (and an optional note); a disliked food is free text; every other kind is a catalog
    -- code or a short free text, never both and never neither
    CONSTRAINT anamnesis_item_shape CHECK (
        CASE kind
            WHEN 'INJURY' THEN body_region IS NOT NULL AND catalog_code IS NULL AND other_text IS NULL
            WHEN 'DISLIKED_FOOD' THEN other_text IS NOT NULL AND catalog_code IS NULL AND body_region IS NULL AND note IS NULL
            ELSE (catalog_code IS NULL) <> (other_text IS NULL) AND body_region IS NULL AND note IS NULL
        END)
);
CREATE INDEX anamnesis_item_revision ON profile.anamnesis_item (revision_id);

-- Values come from the catalogs. The application validates first (E2-S6.2); this is the last line of defense.
CREATE FUNCTION profile.check_revision_catalog() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.created_at := clock_timestamp();
    IF NOT EXISTS (SELECT 1 FROM profile.catalog_item WHERE kind = 'OBJECTIVE' AND code = NEW.objective)
       OR NOT EXISTS (SELECT 1 FROM profile.catalog_item WHERE kind = 'TRAINING_LEVEL' AND code = NEW.training_level) THEN
        RAISE EXCEPTION 'value outside the catalog' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER anamnesis_revision_check BEFORE INSERT ON profile.anamnesis_revision
    FOR EACH ROW EXECUTE FUNCTION profile.check_revision_catalog();

CREATE FUNCTION profile.check_item_catalog() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.catalog_code IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM profile.catalog_item WHERE kind = NEW.kind AND code = NEW.catalog_code) THEN
        RAISE EXCEPTION 'value outside the catalog' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF NEW.body_region IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM profile.catalog_item WHERE kind = 'BODY_REGION' AND code = NEW.body_region) THEN
        RAISE EXCEPTION 'value outside the catalog' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER anamnesis_item_check BEFORE INSERT ON profile.anamnesis_item
    FOR EACH ROW EXECUTE FUNCTION profile.check_item_catalog();

-- Append-only for every role.
CREATE FUNCTION profile.refuse_anamnesis_change() RETURNS trigger
    LANGUAGE plpgsql
AS $$ BEGIN RAISE EXCEPTION 'the anamnesis is append-only' USING ERRCODE = 'integrity_constraint_violation'; END $$;
CREATE TRIGGER anamnesis_revision_no_row_change BEFORE UPDATE OR DELETE ON profile.anamnesis_revision
    FOR EACH ROW EXECUTE FUNCTION profile.refuse_anamnesis_change();
CREATE TRIGGER anamnesis_revision_no_truncate BEFORE TRUNCATE ON profile.anamnesis_revision
    FOR EACH STATEMENT EXECUTE FUNCTION profile.refuse_anamnesis_change();
CREATE TRIGGER anamnesis_item_no_row_change BEFORE UPDATE OR DELETE ON profile.anamnesis_item
    FOR EACH ROW EXECUTE FUNCTION profile.refuse_anamnesis_change();
CREATE TRIGGER anamnesis_item_no_truncate BEFORE TRUNCATE ON profile.anamnesis_item
    FOR EACH STATEMENT EXECUTE FUNCTION profile.refuse_anamnesis_change();

-- Only the owner of the data reads it: not the admin scope, not the system scope, not a context without a user.
ALTER TABLE profile.anamnesis_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE profile.anamnesis_revision FORCE ROW LEVEL SECURITY;
CREATE POLICY own_reads ON profile.anamnesis_revision FOR SELECT USING (user_id = platform.current_app_user_id());
CREATE POLICY own_inserts ON profile.anamnesis_revision FOR INSERT WITH CHECK (user_id = platform.current_app_user_id());
ALTER TABLE profile.anamnesis_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE profile.anamnesis_item FORCE ROW LEVEL SECURITY;
CREATE POLICY own_reads ON profile.anamnesis_item FOR SELECT USING (user_id = platform.current_app_user_id());
CREATE POLICY own_inserts ON profile.anamnesis_item FOR INSERT WITH CHECK (user_id = platform.current_app_user_id());

REVOKE ALL ON profile.anamnesis_revision, profile.anamnesis_item FROM heracles_app;
GRANT SELECT, INSERT ON profile.anamnesis_revision, profile.anamnesis_item TO heracles_app;
