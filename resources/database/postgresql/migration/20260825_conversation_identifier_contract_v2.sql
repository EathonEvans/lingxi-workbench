BEGIN;

DROP INDEX IF EXISTS lingxi_core.uk_ai_conversation_client;
ALTER TABLE lingxi_core.ai_conversation
    DROP COLUMN IF EXISTS client_conversation_id;
ALTER TABLE lingxi_core.ai_conversation
    ADD COLUMN IF NOT EXISTS initial_client_message_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_conversation_initial_message
    ON lingxi_core.ai_conversation (user_id, initial_client_message_id)
    WHERE initial_client_message_id IS NOT NULL;

COMMENT ON COLUMN lingxi_core.ai_conversation.initial_client_message_id
    IS '首轮clientMessageId，仅用于未携带conversationId时的幂等创建';

COMMIT;
