-- 应用监控按 Application 和时间范围聚合；逻辑引用字段只建索引，不创建物理外键。
CREATE INDEX IF NOT EXISTS idx_ai_observability_event_app_created
    ON lingxi_core.ai_observability_event(application_id, created_at DESC)
    WHERE application_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ai_observability_event_terminal_created
    ON lingxi_core.ai_observability_event(created_at DESC, application_id)
    WHERE event_type IN ('RUN_COMPLETED', 'RUN_FAILED');
