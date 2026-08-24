BEGIN;

ALTER TABLE lingxi_core.ai_application_draft
    ALTER COLUMN schema_version SET DEFAULT 11,
    DROP CONSTRAINT IF EXISTS ck_ai_application_draft_schema;

UPDATE lingxi_core.ai_application_draft
SET schema_version = 11,
    definition = jsonb_set(
            definition - 'intentRouting',
            '{schemaVersion}',
            '11'::jsonb,
            TRUE)
        || jsonb_build_object('optionalFastRoutes', COALESCE(definition -> 'intentRouting', 'null'::jsonb));

ALTER TABLE lingxi_core.ai_application_draft
    ADD CONSTRAINT ck_ai_application_draft_schema CHECK (schema_version = 11);

ALTER TABLE lingxi_core.ai_intent_tree_draft
    ALTER COLUMN schema_version SET DEFAULT 1,
    DROP CONSTRAINT IF EXISTS ck_ai_intent_tree_draft_schema;

UPDATE lingxi_core.ai_intent_tree_draft
SET schema_version = 1,
    definition = jsonb_set(
            jsonb_set(definition, '{schemaVersion}', '1'::jsonb, TRUE),
            '{routingPolicy}',
            (COALESCE(definition -> 'routingPolicy', '{}'::jsonb)
                - 'chatModelInstanceId'
                - 'decisionPromptVersionId'
                - 'maxOutputTokens'
                - 'timeoutSeconds'
                - 'maxRepeatAttempts'
                - 'decisionFailureAction')
                || jsonb_build_object('ambiguityMargin', 0.08),
            TRUE);

ALTER TABLE lingxi_core.ai_intent_tree_draft
    ADD CONSTRAINT ck_ai_intent_tree_draft_schema CHECK (schema_version > 0);

COMMIT;
