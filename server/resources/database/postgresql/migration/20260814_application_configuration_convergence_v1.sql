BEGIN;

-- Application V11 收敛协作、记忆与上下文配置；运行快照仍完整冻结在 JSONB 中。
ALTER TABLE lingxi_core.ai_application_draft
    ALTER COLUMN schema_version SET DEFAULT 11,
    DROP CONSTRAINT IF EXISTS ck_ai_application_draft_schema;

UPDATE lingxi_core.ai_application_draft
SET schema_version = 11,
    definition = jsonb_set(definition, '{schemaVersion}', '11'::jsonb, TRUE);

ALTER TABLE lingxi_core.ai_application_draft
    ADD CONSTRAINT ck_ai_application_draft_schema CHECK (schema_version = 11);

COMMIT;
