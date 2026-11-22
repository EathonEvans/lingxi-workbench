BEGIN;

ALTER TABLE lingxi_core.ai_application_draft
    ALTER COLUMN schema_version SET DEFAULT 10,
    DROP CONSTRAINT IF EXISTS ck_ai_application_draft_schema;

UPDATE lingxi_core.ai_application_draft
SET schema_version = 10,
    definition = jsonb_set(definition, '{schemaVersion}', '10'::jsonb, TRUE);

ALTER TABLE lingxi_core.ai_application_draft
    ADD CONSTRAINT ck_ai_application_draft_schema CHECK (schema_version = 10);

COMMIT;
