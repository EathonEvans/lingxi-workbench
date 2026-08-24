BEGIN;

UPDATE lingxi_core.ai_workflow_draft draft
SET definition = jsonb_set(
        jsonb_set(draft.definition, '{schemaVersion}', '1'::JSONB),
        '{nodes}',
        COALESCE((
            SELECT jsonb_agg(
                CASE
                    WHEN node ->> 'type' = 'INTENT_CLASSIFIER' THEN
                        jsonb_set(
                            node,
                            '{config,outputMapping}',
                            (COALESCE(node #> '{config,outputMapping}', '{}'::JSONB)
                                - 'similarity' - 'reasonCode' - 'recognizedSlots')
                                || jsonb_build_object(
                                    'confidence', COALESCE(
                                        node #>> '{config,outputMapping,similarity}',
                                        'intentConfidence')
                                )
                        )
                    ELSE node
                END
                ORDER BY ordinal
            )
            FROM jsonb_array_elements(draft.definition -> 'nodes') WITH ORDINALITY AS nodes(node, ordinal)
        ), '[]'::JSONB)
    ),
    schema_version = 1,
    revision = revision + 1,
    change_note = '回滚 Workflow 意图分类协议 V1',
    updated_at = CURRENT_TIMESTAMP
WHERE draft.schema_version = 2;

ALTER TABLE lingxi_core.ai_workflow_run_event
    DROP CONSTRAINT IF EXISTS ck_workflow_run_event_tokens,
    DROP CONSTRAINT IF EXISTS ck_workflow_run_event_retry_count,
    DROP CONSTRAINT IF EXISTS ck_workflow_run_event_model_calls,
    DROP COLUMN IF EXISTS total_tokens,
    DROP COLUMN IF EXISTS output_tokens,
    DROP COLUMN IF EXISTS input_tokens,
    DROP COLUMN IF EXISTS retry_count,
    DROP COLUMN IF EXISTS model_calls,
    DROP COLUMN IF EXISTS prompt_version_id,
    DROP COLUMN IF EXISTS model_instance_id,
    DROP COLUMN IF EXISTS reason_code,
    DROP COLUMN IF EXISTS decision_status;

ALTER TABLE lingxi_core.ai_workflow_node_attempt
    DROP CONSTRAINT IF EXISTS ck_workflow_node_attempt_tokens,
    DROP CONSTRAINT IF EXISTS ck_workflow_node_attempt_retry_count,
    DROP CONSTRAINT IF EXISTS ck_workflow_node_attempt_model_calls,
    DROP COLUMN IF EXISTS total_tokens,
    DROP COLUMN IF EXISTS output_tokens,
    DROP COLUMN IF EXISTS input_tokens,
    DROP COLUMN IF EXISTS retry_count,
    DROP COLUMN IF EXISTS model_calls,
    DROP COLUMN IF EXISTS prompt_version_id,
    DROP COLUMN IF EXISTS model_instance_id,
    DROP COLUMN IF EXISTS reason_code,
    DROP COLUMN IF EXISTS decision_status;

COMMIT;
