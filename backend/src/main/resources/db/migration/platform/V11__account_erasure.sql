-- E2-S8.4 (spec RF9, CA14): the request to erase an account, with its 7 days to change one's mind.
SET LOCAL ROLE heracles_owner;

CREATE TABLE platform.account_erasure (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid NOT NULL,
    requested_at timestamptz NOT NULL,
    due_at       timestamptz NOT NULL CHECK (due_at > requested_at),
    -- RUNNING: the task was queued and the request can no longer be canceled
    status       text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CANCELED', 'RUNNING', 'DONE'))
);
-- One open request per person.
CREATE UNIQUE INDEX account_erasure_one_open ON platform.account_erasure (user_id) WHERE status IN ('PENDING', 'RUNNING');
CREATE INDEX account_erasure_due ON platform.account_erasure (due_at) WHERE status = 'PENDING';

ALTER TABLE platform.account_erasure ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.account_erasure FORCE ROW LEVEL SECURITY;
CREATE POLICY own_reads ON platform.account_erasure FOR SELECT USING (user_id = platform.current_app_user_id());
CREATE POLICY own_inserts ON platform.account_erasure FOR INSERT WITH CHECK (user_id = platform.current_app_user_id());
-- The person may cancel their own pending request, and only that.
CREATE POLICY own_cancels ON platform.account_erasure FOR UPDATE
    USING (user_id = platform.current_app_user_id() AND status = 'PENDING')
    WITH CHECK (user_id = platform.current_app_user_id() AND status = 'CANCELED');
-- The scheduler and the erasure task (system scope) find the due ones and move them along.
CREATE POLICY system_all ON platform.account_erasure FOR ALL
    USING (platform.current_app_scope() = 'system') WITH CHECK (platform.current_app_scope() = 'system');

REVOKE ALL ON platform.account_erasure FROM heracles_app;
GRANT SELECT, INSERT, UPDATE ON platform.account_erasure TO heracles_app;
