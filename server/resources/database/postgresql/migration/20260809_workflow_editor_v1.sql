BEGIN;

ALTER TABLE lingxi_core.ai_workflow_draft
    ADD COLUMN IF NOT EXISTS canvas_state JSONB NOT NULL
        DEFAULT '{"nodePositions":{},"viewport":{"x":0,"y":0,"zoom":1}}'::JSONB;

ALTER TABLE lingxi_core.ai_workflow_version
    ADD COLUMN IF NOT EXISTS canvas_snapshot JSONB NOT NULL
        DEFAULT '{"nodePositions":{},"viewport":{"x":0,"y":0,"zoom":1}}'::JSONB;

COMMENT ON COLUMN lingxi_core.ai_workflow_draft.canvas_state
    IS '管理端可编辑画布状态，不参与Workflow执行';
COMMENT ON COLUMN lingxi_core.ai_workflow_version.canvas_snapshot
    IS '发布版本对应的只读画布快照';

COMMIT;
