-- Agent 全链路关键观测事件。所有业务引用均为逻辑外键，不创建物理外键。
CREATE TABLE IF NOT EXISTS lingxi_core.ai_observability_event (
    id BIGINT PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    span_id VARCHAR(32) NOT NULL,
    parent_span_id VARCHAR(32),
    event_type VARCHAR(32) NOT NULL,
    stage VARCHAR(32),
    application_id BIGINT,
    run_id VARCHAR(64),
    model_code VARCHAR(128),
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    latency_ms BIGINT,
    status VARCHAR(16) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_observability_event_trace
    ON lingxi_core.ai_observability_event(trace_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_observability_event_run
    ON lingxi_core.ai_observability_event(run_id)
    WHERE run_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ai_observability_event_type_created
    ON lingxi_core.ai_observability_event(event_type, created_at);
