BEGIN;

DROP INDEX IF EXISTS lingxi_core.uq_execution_run_skill_event;
ALTER TABLE lingxi_core.ai_execution_run_skill
    DROP COLUMN IF EXISTS event_sequence_no;

COMMIT;
