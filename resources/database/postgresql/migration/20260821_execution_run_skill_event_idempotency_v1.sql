BEGIN;

-- Skill运行事实与单调ExecutionEvent序号一一绑定，事件重放时不得重复写入审计事实。
ALTER TABLE lingxi_core.ai_execution_run_skill
    ADD COLUMN IF NOT EXISTS event_sequence_no BIGINT;

-- 历史行没有事件序号，使用负主键保留唯一性，并与新事件的正序号隔离。
UPDATE lingxi_core.ai_execution_run_skill
SET event_sequence_no = -id
WHERE event_sequence_no IS NULL;

ALTER TABLE lingxi_core.ai_execution_run_skill
    ALTER COLUMN event_sequence_no SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_execution_run_skill_event
    ON lingxi_core.ai_execution_run_skill (run_id, event_sequence_no);

COMMIT;
