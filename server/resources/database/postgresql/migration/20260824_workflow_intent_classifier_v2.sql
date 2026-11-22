BEGIN;

ALTER TABLE lingxi_core.ai_workflow_node_attempt
    ADD COLUMN decision_status VARCHAR(32),
    ADD COLUMN reason_code VARCHAR(64),
    ADD COLUMN model_instance_id BIGINT,
    ADD COLUMN prompt_version_id BIGINT,
    ADD COLUMN model_calls INTEGER,
    ADD COLUMN retry_count INTEGER,
    ADD COLUMN input_tokens INTEGER,
    ADD COLUMN output_tokens INTEGER,
    ADD COLUMN total_tokens INTEGER,
    ADD CONSTRAINT ck_workflow_node_attempt_model_calls CHECK (model_calls IS NULL OR model_calls >= 0),
    ADD CONSTRAINT ck_workflow_node_attempt_retry_count CHECK (retry_count IS NULL OR retry_count >= 0),
    ADD CONSTRAINT ck_workflow_node_attempt_tokens CHECK (
        (input_tokens IS NULL OR input_tokens >= 0)
        AND (output_tokens IS NULL OR output_tokens >= 0)
        AND (total_tokens IS NULL OR total_tokens >= 0)
    );

ALTER TABLE lingxi_core.ai_workflow_run_event
    ADD COLUMN decision_status VARCHAR(32),
    ADD COLUMN reason_code VARCHAR(64),
    ADD COLUMN model_instance_id BIGINT,
    ADD COLUMN prompt_version_id BIGINT,
    ADD COLUMN model_calls INTEGER,
    ADD COLUMN retry_count INTEGER,
    ADD COLUMN input_tokens INTEGER,
    ADD COLUMN output_tokens INTEGER,
    ADD COLUMN total_tokens INTEGER,
    ADD CONSTRAINT ck_workflow_run_event_model_calls CHECK (model_calls IS NULL OR model_calls >= 0),
    ADD CONSTRAINT ck_workflow_run_event_retry_count CHECK (retry_count IS NULL OR retry_count >= 0),
    ADD CONSTRAINT ck_workflow_run_event_tokens CHECK (
        (input_tokens IS NULL OR input_tokens >= 0)
        AND (output_tokens IS NULL OR output_tokens >= 0)
        AND (total_tokens IS NULL OR total_tokens >= 0)
    );

-- 只迁移可编辑草稿；历史发布版本保持不可变，当前运行时直接拒绝 Workflow V1。
UPDATE lingxi_core.ai_workflow_draft draft
SET definition = jsonb_set(
        jsonb_set(draft.definition, '{schemaVersion}', '2'::JSONB),
        '{nodes}',
        COALESCE((
            SELECT jsonb_agg(
                CASE
                    WHEN node ->> 'type' = 'INTENT_CLASSIFIER' THEN
                        jsonb_set(
                            node,
                            '{config,outputMapping}',
                            (COALESCE(node #> '{config,outputMapping}', '{}'::JSONB) - 'confidence')
                                || jsonb_build_object(
                                    'similarity', COALESCE(
                                        node #>> '{config,outputMapping,similarity}',
                                        node #>> '{config,outputMapping,confidence}',
                                        'intentSimilarity'),
                                    'reasonCode', COALESCE(
                                        node #>> '{config,outputMapping,reasonCode}',
                                        'intentReasonCode'),
                                    'recognizedSlots', COALESCE(
                                        node #>> '{config,outputMapping,recognizedSlots}',
                                        'intentRecognizedSlots')
                                )
                        )
                    ELSE node
                END
                ORDER BY ordinal
            )
            FROM jsonb_array_elements(draft.definition -> 'nodes') WITH ORDINALITY AS nodes(node, ordinal)
        ), '[]'::JSONB)
    ),
    schema_version = 2,
    revision = revision + 1,
    change_note = '系统升级 Workflow 意图分类协议 V2',
    updated_at = CURRENT_TIMESTAMP
WHERE draft.schema_version = 1;

COMMIT;
