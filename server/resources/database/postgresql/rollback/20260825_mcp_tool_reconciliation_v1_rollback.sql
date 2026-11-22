BEGIN;

-- 回滚前必须已停止新 Runtime；存在待对账调用时不能删除其配置摘要和重试事实。
DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM lingxi_core.ai_capability_invocation
        WHERE status = 'RECONCILIATION_PENDING'
    ) THEN
        RAISE EXCEPTION '存在 RECONCILIATION_PENDING 调用，不能回滚 MCP Tool 对账协议';
    END IF;
END
$$;

DROP INDEX IF EXISTS lingxi_core.idx_capability_invocation_reconciliation;

ALTER TABLE lingxi_core.ai_capability_invocation
    DROP COLUMN IF EXISTS next_reconcile_at,
    DROP COLUMN IF EXISTS reconciliation_attempts,
    DROP COLUMN IF EXISTS tool_configuration_hash;

ALTER TABLE lingxi_core.ai_tool
    DROP CONSTRAINT IF EXISTS ck_ai_tool_reconciliation_query,
    DROP COLUMN IF EXISTS reconciliation_query_tool_name;

COMMIT;
