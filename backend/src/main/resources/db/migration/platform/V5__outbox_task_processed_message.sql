-- Asynchronous flows (ADR-0004): transactional outbox, task status and idempotent consumers.
-- Messages carry identifiers only, never health data.
SET LOCAL ROLE heracles_owner;

-- Written in the same transaction as the state change; published to the queue by the publisher (system scope).
CREATE TABLE platform.outbox_message (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    type            text NOT NULL,
    aggregate_type  text NOT NULL,
    aggregate_id    text NOT NULL,
    payload         jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    created_at      timestamptz NOT NULL DEFAULT now(),
    published_at    timestamptz,
    attempts        integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX outbox_message_pending ON platform.outbox_message (next_attempt_at) WHERE published_at IS NULL;

-- Status of an asynchronous task. The owner reads it; only the system writes it. `user_id` is null for system tasks.
CREATE TABLE platform.task (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid,
    type            text NOT NULL,
    status          text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    attempts        integer NOT NULL DEFAULT 0,
    last_error_code text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    result_ref      text
);
CREATE INDEX task_user ON platform.task (user_id);

-- A consumer records each message it handled, so the same message twice has one effect.
CREATE TABLE platform.processed_message (
    consumer     text NOT NULL,
    message_id   text NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);

ALTER TABLE platform.outbox_message ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.outbox_message FORCE ROW LEVEL SECURITY;
CREATE POLICY system_all ON platform.outbox_message FOR ALL
    USING (platform.current_app_scope() = 'system') WITH CHECK (platform.current_app_scope() = 'system');

ALTER TABLE platform.processed_message ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.processed_message FORCE ROW LEVEL SECURITY;
CREATE POLICY system_all ON platform.processed_message FOR ALL
    USING (platform.current_app_scope() = 'system') WITH CHECK (platform.current_app_scope() = 'system');

ALTER TABLE platform.task ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.task FORCE ROW LEVEL SECURITY;
CREATE POLICY system_all ON platform.task FOR ALL
    USING (platform.current_app_scope() = 'system') WITH CHECK (platform.current_app_scope() = 'system');
-- The owner reads their own task; no write policy for users.
CREATE POLICY owner_reads ON platform.task FOR SELECT
    USING (user_id = platform.current_app_user_id());
