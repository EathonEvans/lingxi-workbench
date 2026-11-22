-- Agent Schema V3：草稿绑定本地工具编码和工具调用边界。
-- 工具编码由代码目录和发布校验维护，按项目约定只使用逻辑外键，不新增数据库外键。

ALTER TABLE lingxi_core.ai_agent_draft
    ADD COLUMN IF NOT EXISTS tool_codes JSONB NOT NULL DEFAULT '[]'::JSONB,
    ADD COLUMN IF NOT EXISTS tool_policy JSONB NOT NULL
        DEFAULT '{"maxToolCalls":2,"maxResultCharacters":8000}'::JSONB;

COMMENT ON COLUMN lingxi_core.ai_agent_draft.tool_codes
    IS 'Agent允许暴露给模型的本地工具编码列表，由代码目录校验';
COMMENT ON COLUMN lingxi_core.ai_agent_draft.tool_policy
    IS '工具调用次数和单次结果字符数边界';

ALTER TABLE lingxi_core.ai_agent_draft
    DROP CONSTRAINT IF EXISTS ck_ai_agent_draft_tool_codes,
    DROP CONSTRAINT IF EXISTS ck_ai_agent_draft_tool_policy,
    ADD CONSTRAINT ck_ai_agent_draft_tool_codes
        CHECK (jsonb_typeof(tool_codes) = 'array'),
    ADD CONSTRAINT ck_ai_agent_draft_tool_policy
        CHECK (jsonb_typeof(tool_policy) = 'object');

UPDATE lingxi_core.ai_agent_draft
   SET schema_version = 3
 WHERE schema_version < 3;
