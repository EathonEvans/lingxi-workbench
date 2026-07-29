BEGIN;

DROP INDEX IF EXISTS lingxi_core.idx_ai_conversation_message_application_version;
DROP INDEX IF EXISTS lingxi_core.idx_ai_conversation_message_application;

ALTER TABLE lingxi_core.ai_conversation_message
    DROP COLUMN IF EXISTS application_version_id,
    DROP COLUMN IF EXISTS application_id;

COMMIT;
