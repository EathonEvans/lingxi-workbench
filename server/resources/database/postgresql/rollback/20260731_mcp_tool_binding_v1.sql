BEGIN;

-- 回滚到仅支持本地函数的结构前，先移除无法继续执行的 MCP Tool 定义。
DELETE FROM lingxi_core.ai_tool
WHERE source_type = 'MCP';

DROP INDEX IF EXISTS lingxi_core.uk_ai_tool_mcp_binding;
DROP INDEX IF EXISTS lingxi_core.idx_ai_tool_mcp_server;

ALTER TABLE lingxi_core.ai_tool
    DROP COLUMN IF EXISTS remote_tool_name,
    DROP COLUMN IF EXISTS mcp_server_id;

ALTER TABLE lingxi_core.ai_tool
    ADD CONSTRAINT ck_ai_tool_source_type CHECK (source_type IN ('LOCAL_FUNCTION'));

COMMIT;
