-- Agent Schema V2：回答阶段分别绑定 SYSTEM 与 USER PromptVersion。
-- 资源引用由管理端发布校验保证，按项目约定不新增数据库外键。

ALTER TABLE lingxi_core.ai_agent_draft
    ADD COLUMN IF NOT EXISTS system_prompt_version_id BIGINT,
    ADD COLUMN IF NOT EXISTS user_prompt_version_id BIGINT;

COMMENT ON COLUMN lingxi_core.ai_agent_draft.system_prompt_version_id
    IS '回答阶段SYSTEM角色PromptVersion主键，逻辑外键';
COMMENT ON COLUMN lingxi_core.ai_agent_draft.user_prompt_version_id
    IS '回答阶段USER角色PromptVersion主键，逻辑外键';

-- V1 的 answer_prompt_version_id 语义本来就是 SYSTEM Prompt，迁移到明确字段。
UPDATE lingxi_core.ai_agent_draft
   SET system_prompt_version_id = answer_prompt_version_id
 WHERE system_prompt_version_id IS NULL
   AND answer_prompt_version_id IS NOT NULL;

-- 已有草稿升级为 V2 后必须补充 USER Prompt 才能再次发布。
UPDATE lingxi_core.ai_agent_draft
   SET schema_version = 2
 WHERE schema_version < 2;
