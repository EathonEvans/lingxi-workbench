BEGIN;

-- AgentType 由 Java 枚举与发布校验维护。删除重复的数据库白名单后，
-- 新增 Agent 类型不再要求修改历史表约束，也避免控制面与数据库枚举漂移。
ALTER TABLE lingxi_core.ai_agent
    DROP CONSTRAINT IF EXISTS ck_ai_agent_type;

COMMENT ON COLUMN lingxi_core.ai_agent.agent_type IS
    'Agent类型：RAG_CHAT知识库问答，SUPERVISOR多Agent监督者';

COMMIT;
