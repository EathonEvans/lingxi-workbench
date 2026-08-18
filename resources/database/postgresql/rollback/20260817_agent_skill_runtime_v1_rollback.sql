BEGIN;

DROP TABLE IF EXISTS lingxi_core.ai_execution_run_skill;
DROP TABLE IF EXISTS lingxi_core.ai_agent_version_skill;

ALTER TABLE lingxi_core.ai_agent_draft
    DROP CONSTRAINT IF EXISTS ck_ai_agent_draft_schema_version,
    DROP CONSTRAINT IF EXISTS ck_ai_agent_draft_skill_ids,
    DROP COLUMN IF EXISTS skill_ids;
UPDATE lingxi_core.ai_agent_draft SET schema_version = 7;
ALTER TABLE lingxi_core.ai_agent_draft
    ADD CONSTRAINT ck_ai_agent_draft_schema_version CHECK (schema_version = 7);

COMMIT;
