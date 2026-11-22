BEGIN;

DROP INDEX IF EXISTS lingxi_core.uk_ai_conversation_initial_message;
ALTER TABLE lingxi_core.ai_conversation
    DROP COLUMN IF EXISTS initial_client_message_id;
ALTER TABLE lingxi_core.ai_conversation
    ADD COLUMN IF NOT EXISTS client_conversation_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_conversation_client
    ON lingxi_core.ai_conversation (user_id, client_conversation_id)
    WHERE client_conversation_id IS NOT NULL;
COMMENT ON COLUMN lingxi_core.ai_conversation.client_conversation_id
    IS '首次发送生成的前端幂等标识，仅用于懒创建会话';

COMMIT;
