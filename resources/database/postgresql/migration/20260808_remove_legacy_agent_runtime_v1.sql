BEGIN;

-- 新项目不迁移旧 Agent 语义；发现旧类型时中止，避免静默改变线上行为。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM lingxi_core.ai_agent
        WHERE agent_type = 'RAG_CHAT'
    ) THEN
        RAISE EXCEPTION '检测到 RAG_CHAT Agent，请重置旧 Agent 数据后重新执行迁移';
    END IF;
END
$$;

-- Application 不读取旧快照；发现旧 Schema 时要求先重置数据。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM lingxi_core.ai_application_draft
        WHERE schema_version <> 7
    ) OR EXISTS (
        SELECT 1
        FROM lingxi_core.ai_application_version
        WHERE schema_version <> 7
    ) THEN
        RAISE EXCEPTION '检测到旧 Application Schema，请重置 Application 草稿和版本数据后重新执行迁移';
    END IF;
END
$$;

-- 独立 Prompt 历史版本不属于 Agent 结构迁移范围；运行时变量校验会阻止旧变量再次发布使用。

ALTER TABLE lingxi_core.ai_agent
    ALTER COLUMN agent_type SET DEFAULT 'REACT';

ALTER TABLE lingxi_core.ai_application_draft
    ALTER COLUMN schema_version SET DEFAULT 7,
    DROP CONSTRAINT IF EXISTS ck_ai_application_draft_schema,
    ADD CONSTRAINT ck_ai_application_draft_schema CHECK (schema_version = 7);

ALTER TABLE lingxi_core.ai_application_version
    DROP CONSTRAINT IF EXISTS ck_ai_application_version_schema,
    ADD CONSTRAINT ck_ai_application_version_schema CHECK (schema_version = 7);

COMMENT ON COLUMN lingxi_core.ai_agent.agent_type IS
    'Agent类型：REACT通用推理执行，SUPERVISOR多Agent监督者';

-- Agent 引用统一由服务层校验并通过索引维护查询性能，不使用物理外键。
ALTER TABLE lingxi_core.ai_agent
    DROP CONSTRAINT IF EXISTS fk_ai_agent_published_version;

ALTER TABLE lingxi_core.ai_agent_draft
    DROP CONSTRAINT IF EXISTS fk_ai_agent_draft_agent,
    DROP CONSTRAINT IF EXISTS fk_ai_agent_draft_base_version,
    DROP CONSTRAINT IF EXISTS fk_ai_agent_draft_chat_model,
    DROP CONSTRAINT IF EXISTS fk_ai_agent_draft_rewrite_model,
    DROP CONSTRAINT IF EXISTS fk_ai_agent_draft_answer_prompt,
    DROP CONSTRAINT IF EXISTS fk_ai_agent_draft_rewrite_prompt,
    DROP COLUMN IF EXISTS rewrite_model_instance_id,
    DROP COLUMN IF EXISTS answer_prompt_version_id,
    DROP COLUMN IF EXISTS rewrite_prompt_version_id,
    DROP COLUMN IF EXISTS memory_policy,
    DROP COLUMN IF EXISTS rewrite_policy;

ALTER TABLE lingxi_core.ai_agent_draft_knowledge
    DROP CONSTRAINT IF EXISTS fk_ai_agent_draft_knowledge_draft,
    DROP CONSTRAINT IF EXISTS fk_ai_agent_draft_knowledge_base,
    DROP CONSTRAINT IF EXISTS fk_ai_agent_draft_knowledge_model;

ALTER TABLE lingxi_core.ai_agent_version
    DROP CONSTRAINT IF EXISTS fk_ai_agent_version_agent;

ALTER TABLE lingxi_core.ai_agent_version_knowledge
    DROP CONSTRAINT IF EXISTS fk_ai_agent_version_knowledge_version,
    DROP CONSTRAINT IF EXISTS fk_ai_agent_version_knowledge_base,
    DROP CONSTRAINT IF EXISTS fk_ai_agent_version_knowledge_model;

CREATE INDEX IF NOT EXISTS idx_ai_agent_published_version
    ON lingxi_core.ai_agent (published_version_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_draft_base_version
    ON lingxi_core.ai_agent_draft (base_version_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_draft_chat_model
    ON lingxi_core.ai_agent_draft (chat_model_instance_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_draft_system_prompt
    ON lingxi_core.ai_agent_draft (system_prompt_version_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_draft_user_prompt
    ON lingxi_core.ai_agent_draft (user_prompt_version_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_draft_knowledge_base
    ON lingxi_core.ai_agent_draft_knowledge (knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_draft_knowledge_model
    ON lingxi_core.ai_agent_draft_knowledge (embedding_model_instance_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_version_knowledge_base
    ON lingxi_core.ai_agent_version_knowledge (knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_version_knowledge_model
    ON lingxi_core.ai_agent_version_knowledge (embedding_model_instance_id);

COMMIT;
