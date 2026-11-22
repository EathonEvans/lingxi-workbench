-- 回滚 Agent Schema V3 本地工具配置。

UPDATE lingxi_core.ai_agent_draft
   SET schema_version = 2
 WHERE schema_version = 3;

ALTER TABLE lingxi_core.ai_agent_draft
    DROP CONSTRAINT IF EXISTS ck_ai_agent_draft_tool_codes,
    DROP CONSTRAINT IF EXISTS ck_ai_agent_draft_tool_policy,
    DROP COLUMN IF EXISTS tool_codes,
    DROP COLUMN IF EXISTS tool_policy;
