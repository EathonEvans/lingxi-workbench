BEGIN;

-- 运行消息记录实际执行的 Application / ApplicationVersion。
-- 两列均为逻辑外键：历史消息必须保留可追溯性，不创建数据库 FOREIGN KEY。
ALTER TABLE lingxi_core.ai_conversation_message
    ADD COLUMN IF NOT EXISTS application_id BIGINT,
    ADD COLUMN IF NOT EXISTS application_version_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_ai_conversation_message_application
    ON lingxi_core.ai_conversation_message (application_id)
    WHERE application_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ai_conversation_message_application_version
    ON lingxi_core.ai_conversation_message (application_version_id)
    WHERE application_version_id IS NOT NULL;

COMMIT;
