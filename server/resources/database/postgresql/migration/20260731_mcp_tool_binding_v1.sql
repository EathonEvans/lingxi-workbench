BEGIN;

-- Tool 继续作为 Agent 唯一绑定主体；MCP 连接与远端工具名称只描述执行来源。
-- mcp_server_id 是逻辑关联，不创建数据库外键，跨服务运行时自行校验状态。
ALTER TABLE lingxi_core.ai_tool
    ADD COLUMN IF NOT EXISTS mcp_server_id BIGINT,
    ADD COLUMN IF NOT EXISTS remote_tool_name VARCHAR(128);

-- 保留数据库已有的来源校验边界，只将当前新增的 MCP 来源加入允许范围。
ALTER TABLE lingxi_core.ai_tool
    DROP CONSTRAINT IF EXISTS ck_ai_tool_source_type;

ALTER TABLE lingxi_core.ai_tool
    ADD CONSTRAINT ck_ai_tool_source_type CHECK (source_type IN ('LOCAL_FUNCTION', 'MCP'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_tool_mcp_binding
    ON lingxi_core.ai_tool (mcp_server_id, remote_tool_name)
    WHERE mcp_server_id IS NOT NULL AND remote_tool_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ai_tool_mcp_server
    ON lingxi_core.ai_tool (mcp_server_id)
    WHERE mcp_server_id IS NOT NULL;

COMMENT ON COLUMN lingxi_core.ai_tool.mcp_server_id
    IS 'MCP来源工具绑定的Server主体ID，逻辑关联ai_mcp_server.id，不创建物理外键';
COMMENT ON COLUMN lingxi_core.ai_tool.remote_tool_name
    IS 'MCP Server通过tools/list暴露的原始工具名称';
COMMENT ON COLUMN lingxi_core.ai_tool.source_type
    IS '工具执行来源；LOCAL_FUNCTION为本地执行器，MCP为远端MCP Server';

COMMIT;
