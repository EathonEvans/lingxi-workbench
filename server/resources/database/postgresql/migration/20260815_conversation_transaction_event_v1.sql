BEGIN;

-- 新会话只在首次发送时创建；同一用户重复提交相同前端会话标识时复用既有会话。
ALTER TABLE lingxi_core.ai_conversation
    ADD COLUMN IF NOT EXISTS client_conversation_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_conversation_client
    ON lingxi_core.ai_conversation (user_id, client_conversation_id)
    WHERE client_conversation_id IS NOT NULL;

COMMENT ON COLUMN lingxi_core.ai_conversation.client_conversation_id IS
    '首次发送生成的前端幂等标识，仅用于懒创建会话';

COMMIT;
