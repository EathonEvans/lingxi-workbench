BEGIN;

-- 仅可在已回退 Application/Agent Runtime 且不再写入 Task 快照后执行；该操作会删除新协议的运行快照。
DROP INDEX IF EXISTS lingxi_core.idx_execution_run_supervisor_model;
DROP INDEX IF EXISTS lingxi_core.idx_execution_run_supervisor_prompt;

ALTER TABLE lingxi_core.ai_execution_run
    RENAME COLUMN supervisor_model_instance_id TO planner_model_instance_id;

ALTER TABLE lingxi_core.ai_execution_run
    RENAME COLUMN supervisor_prompt_version_id TO planner_prompt_version_id;

CREATE INDEX IF NOT EXISTS idx_execution_run_planner_model
    ON lingxi_core.ai_execution_run (planner_model_instance_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_execution_run_planner_prompt
    ON lingxi_core.ai_execution_run (planner_prompt_version_id, created_at DESC);

ALTER TABLE lingxi_core.ai_execution_step_attempt
    DROP CONSTRAINT IF EXISTS ck_execution_step_numbers;

ALTER TABLE lingxi_core.ai_execution_step_attempt
    RENAME COLUMN task_revision TO plan_no;

ALTER TABLE lingxi_core.ai_execution_step_attempt
    ADD CONSTRAINT ck_execution_step_numbers CHECK (plan_no > 0 AND attempt_no > 0);

DROP TABLE IF EXISTS lingxi_core.ai_execution_task_snapshot;

COMMIT;
