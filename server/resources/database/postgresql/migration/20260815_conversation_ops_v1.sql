BEGIN;

-- 仅记录记忆事件已执行一次兜底重发，不复制会话内容，也不作为消费成功事实。
ALTER TABLE lingxi_core.ai_conversation_message
    ADD COLUMN IF NOT EXISTS memory_reconciled_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_ai_conversation_message_memory_reconcile
    ON lingxi_core.ai_conversation_message (completed_at, id)
    WHERE role = 'ASSISTANT' AND status = 'COMPLETED' AND memory_reconciled_at IS NULL;

COMMENT ON COLUMN lingxi_core.ai_conversation_message.memory_reconciled_at IS
    '服务启动后完成的记忆事件已执行一次兜底重发的时间，不代表三个消费组均处理成功';

COMMIT;
