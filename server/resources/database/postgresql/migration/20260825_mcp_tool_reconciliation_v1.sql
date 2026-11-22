BEGIN;

-- 只有 MCP Tool 可以声明按幂等键查询外部副作用结果的只读入口；补偿仍必须走独立审批，
-- 因此不在此保存可自动调用的反向操作名称。
ALTER TABLE lingxi_core.ai_tool
    ADD COLUMN IF NOT EXISTS reconciliation_query_tool_name VARCHAR(128);

ALTER TABLE lingxi_core.ai_tool
    DROP CONSTRAINT IF EXISTS ck_ai_tool_reconciliation_query;

ALTER TABLE lingxi_core.ai_tool
    ADD CONSTRAINT ck_ai_tool_reconciliation_query CHECK (
        reconciliation_query_tool_name IS NULL
        OR (source_type = 'MCP' AND approval_required = TRUE)
    );

COMMENT ON COLUMN lingxi_core.ai_tool.reconciliation_query_tool_name
    IS '同一MCP Server按幂等键查询外部副作用结果的只读Tool名称；不声明则仅允许人工对账';

ALTER TABLE lingxi_core.ai_capability_invocation
    ADD COLUMN IF NOT EXISTS tool_configuration_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS reconciliation_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_reconcile_at TIMESTAMPTZ;

-- 历史调用不会进入新的自动对账队列；仅为保留既有审计行而使用空摘要。
UPDATE lingxi_core.ai_capability_invocation
SET tool_configuration_hash = ''
WHERE tool_configuration_hash IS NULL;

ALTER TABLE lingxi_core.ai_capability_invocation
    ALTER COLUMN tool_configuration_hash SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_capability_invocation_reconciliation
    ON lingxi_core.ai_capability_invocation (status, next_reconcile_at)
    WHERE status = 'RECONCILIATION_PENDING' AND next_reconcile_at IS NOT NULL;

COMMIT;
