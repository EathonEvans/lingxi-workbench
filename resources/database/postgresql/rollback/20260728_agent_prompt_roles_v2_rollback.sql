-- 回滚前保留 V2 SYSTEM Prompt 到 V1 字段，避免丢失已有配置。
UPDATE lingxi_core.ai_agent_draft
   SET answer_prompt_version_id = system_prompt_version_id
 WHERE system_prompt_version_id IS NOT NULL;

UPDATE lingxi_core.ai_agent_draft
   SET schema_version = 1
 WHERE schema_version = 2;

ALTER TABLE lingxi_core.ai_agent_draft
    DROP COLUMN IF EXISTS user_prompt_version_id,
    DROP COLUMN IF EXISTS system_prompt_version_id;
