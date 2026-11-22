BEGIN;

-- 只升级可编辑草稿；历史发布快照保持原样用于审计，并由新运行时拒绝旧Schema。
ALTER TABLE lingxi_core.ai_intent_tree_draft
    ALTER COLUMN schema_version SET DEFAULT 2,
    DROP CONSTRAINT IF EXISTS ck_ai_intent_tree_draft_schema;

UPDATE lingxi_core.ai_intent_tree_draft
SET schema_version = 2,
    definition = jsonb_set(
            jsonb_set(definition, '{schemaVersion}', '2'::jsonb, TRUE),
            '{routingPolicy}',
            (COALESCE(definition -> 'routingPolicy', '{}'::jsonb) - 'ambiguityMargin')
                || jsonb_build_object(
                    'chatModelInstanceId', NULL,
                    'decisionPromptVersionId', NULL,
                    'maxOutputTokens', 512,
                    'timeoutSeconds', 30,
                    'maxRepeatAttempts', 1,
                    'decisionFailureAction', 'DEFER'),
            TRUE);

ALTER TABLE lingxi_core.ai_intent_tree_draft
    ADD CONSTRAINT ck_ai_intent_tree_draft_schema CHECK (schema_version = 2);

ALTER TABLE lingxi_core.ai_application_draft
    ALTER COLUMN schema_version SET DEFAULT 12,
    DROP CONSTRAINT IF EXISTS ck_ai_application_draft_schema;

UPDATE lingxi_core.ai_application_draft
SET schema_version = 12,
    definition = jsonb_set(
            definition - 'optionalFastRoutes',
            '{schemaVersion}',
            '12'::jsonb,
            TRUE)
        || jsonb_build_object('intentRouting', COALESCE(definition -> 'optionalFastRoutes', 'null'::jsonb));

ALTER TABLE lingxi_core.ai_application_draft
    ADD CONSTRAINT ck_ai_application_draft_schema CHECK (schema_version = 12);

COMMIT;
