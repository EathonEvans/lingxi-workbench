-- Prompt角色字段迁移。
-- 历史Prompt缺少角色语义，统一按系统Prompt处理。

BEGIN;

ALTER TABLE lingxi_core.ai_prompt
    ADD COLUMN IF NOT EXISTS role VARCHAR(16) NOT NULL DEFAULT 'SYSTEM';

COMMIT;
